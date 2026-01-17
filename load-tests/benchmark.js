import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';
import {
    fetchTestData,
    getFutureDate,
    getAvailableSlot,
    getBaseUrl,
} from './test-utils.js';

/**
 * Full Benchmark Test
 *
 * Comprehensive load test with multiple phases:
 * - Baseline (single user)
 * - Ramp up
 * - Steady state
 * - Peak load
 * - Concurrent booking stress
 *
 * All test data is dynamically fetched from the API - no hardcoded IDs.
 *
 * Run: k6 run load-tests/benchmark.js
 */

// Custom metrics
const createReservationMetric = new Trend('reservation_create_ms');
const checkAvailabilityMetric = new Trend('availability_check_ms');
const generateReportMetric = new Trend('report_generate_ms');
const cancelReservationMetric = new Trend('reservation_cancel_ms');

const reservationSuccess = new Counter('reservation_success');
const reservationFailed = new Counter('reservation_failed');
const capacityExceeded = new Counter('capacity_exceeded');

export const options = {
    scenarios: {
        // Phase 1: Baseline - single user to measure raw latency
        baseline: {
            executor: 'constant-vus',
            vus: 1,
            duration: '30s',
            startTime: '0s',
            tags: { phase: 'baseline' },
        },

        // Phase 2: Ramp up - gradually increase load
        ramp_up: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },
            ],
            startTime: '35s',
            tags: { phase: 'ramp_up' },
        },

        // Phase 3: Steady state - sustained load
        steady: {
            executor: 'constant-vus',
            vus: 10,
            duration: '60s',
            startTime: '70s',
            tags: { phase: 'steady' },
        },

        // Phase 4: Peak load
        peak: {
            executor: 'ramping-vus',
            startVUs: 10,
            stages: [
                { duration: '30s', target: 20 },
            ],
            startTime: '135s',
            tags: { phase: 'peak' },
        },

        // Phase 5: Peak hold
        peak_hold: {
            executor: 'constant-vus',
            vus: 20,
            duration: '60s',
            startTime: '170s',
            tags: { phase: 'peak_hold' },
        },

        // Phase 6: Concurrent stress - same slot booking
        concurrent_stress: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: 10,
            maxDuration: '30s',
            startTime: '235s',
            tags: { phase: 'concurrent' },
        },
    },

    thresholds: {
        'http_req_duration': ['p(95)<1000', 'p(99)<2000'],
        'reservation_create_ms': ['p(95)<500'],
        'availability_check_ms': ['p(95)<200'],
        'report_generate_ms': ['p(95)<2000'],
        'http_req_failed': ['rate<0.1'],
    },
};

export function setup() {
    console.log('\n' + '='.repeat(70));
    console.log('FULL BENCHMARK TEST');
    console.log('='.repeat(70));

    // Fetch test data dynamically from API
    const testData = fetchTestData();
    if (!testData.restaurantId || testData.spaces.length === 0) {
        console.log('ERROR: Could not fetch test data from API');
        console.log('Make sure the API is running and has seed data');
        return { error: true };
    }

    console.log(`Restaurant: ${testData.restaurantName} (ID: ${testData.restaurantId})`);
    console.log(`Spaces: ${testData.spaces.map(s => s.name).join(', ')}`);
    console.log('');
    console.log('Phases:');
    console.log('  1. Baseline (0-30s): 1 user - measure raw latency');
    console.log('  2. Ramp Up (35-65s): 0->10 users - gradual increase');
    console.log('  3. Steady (70-130s): 10 users - sustained load');
    console.log('  4. Peak (135-165s): 10->20 users - increase to peak');
    console.log('  5. Peak Hold (170-230s): 20 users - sustained peak');
    console.log('  6. Concurrent (235-265s): 10 users same slot - stress test');
    console.log('');
    console.log('Total Duration: ~4.5 minutes');
    console.log('='.repeat(70) + '\n');

    return {
        startTime: Date.now(),
        restaurantId: testData.restaurantId,
        spaces: testData.spaces,
    };
}

export default function (data) {
    if (data.error) {
        console.log('Skipping test due to setup error');
        return;
    }

    const BASE_URL = getBaseUrl();
    const { restaurantId, spaces } = data;
    const scenario = __ENV.K6_SCENARIO_NAME;
    const space = spaces[Math.floor(Math.random() * spaces.length)];

    // For concurrent stress, all users target the same slot
    const isConcurrentStress = scenario === 'concurrent_stress';
    const dateOffset = isConcurrentStress ? 70 : Math.floor(Math.random() * 60) + 7;
    const testDate = getFutureDate(dateOffset);

    // 1. Check Availability
    {
        const start = Date.now();
        const res = http.get(`${BASE_URL}/availability/spaces/${space.id}?date=${testDate}`);
        checkAvailabilityMetric.add(Date.now() - start);
        check(res, { 'availability ok': (r) => r.status === 200 });
    }

    sleep(0.2);

    // 2. Create Reservation
    let reservationId = null;
    {
        const startTime = getAvailableSlot(space.id, testDate);
        if (!startTime) {
            sleep(0.5);
            return;
        }

        const payload = JSON.stringify({
            spaceId: space.id,
            reservationDate: testDate,
            startTime: startTime,
            partySize: Math.floor(Math.random() * 4) + 2,
            customerName: `Benchmark ${scenario} VU${__VU}`,
            customerEmail: `bench_${scenario}_${__VU}_${Date.now()}@test.com`,
        });

        const start = Date.now();
        const res = http.post(`${BASE_URL}/reservations`, payload, {
            headers: { 'Content-Type': 'application/json' },
        });
        createReservationMetric.add(Date.now() - start);

        if (res.status === 201) {
            reservationSuccess.add(1);
            try {
                reservationId = JSON.parse(res.body).id;
            } catch (e) {}
        } else if (res.status === 409) {
            capacityExceeded.add(1);
        } else {
            reservationFailed.add(1);
        }

        check(res, {
            'reservation valid': (r) => r.status === 201 || r.status === 409 || r.status === 400,
            'no server error': (r) => r.status < 500,
        });
    }

    sleep(0.2);

    // 3. Generate Report (only 20% of iterations)
    if (Math.random() < 0.2) {
        const endDate = getFutureDate(0);
        const startDate = getFutureDate(-30);

        const start = Date.now();
        const res = http.get(
            `${BASE_URL}/reports/occupancy?restaurantId=${restaurantId}&startDate=${startDate}&endDate=${endDate}&granularity=DAILY`
        );
        generateReportMetric.add(Date.now() - start);
        check(res, { 'report ok': (r) => r.status === 200 });
    }

    sleep(0.2);

    // 4. Cancel Reservation (only some to free up capacity)
    if (reservationId && Math.random() < 0.3) {
        const start = Date.now();
        const res = http.post(`${BASE_URL}/reservations/${reservationId}/cancel`,
            JSON.stringify({ reason: 'Load test cleanup' }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        cancelReservationMetric.add(Date.now() - start);
        check(res, { 'cancel ok': (r) => r.status === 200 });
    }

    sleep(0.3);
}

export function handleSummary(data) {
    // Write results to JSON file
    const jsonResult = JSON.stringify({
        timestamp: new Date().toISOString(),
        metrics: {
            reservation_create_p95: data.metrics.reservation_create_ms?.values?.['p(95)'],
            availability_check_p95: data.metrics.availability_check_ms?.values?.['p(95)'],
            report_generate_p95: data.metrics.report_generate_ms?.values?.['p(95)'],
            http_req_duration_p95: data.metrics.http_req_duration?.values?.['p(95)'],
            reservation_success: data.metrics.reservation_success?.values?.count,
            reservation_failed: data.metrics.reservation_failed?.values?.count,
            capacity_exceeded: data.metrics.capacity_exceeded?.values?.count,
        },
        thresholds: data.thresholds,
    }, null, 2);

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'load-tests/results.json': jsonResult,
    };
}

export function teardown(data) {
    if (data.error) return;

    const { startTime } = data;
    const duration = Math.round((Date.now() - startTime) / 1000);

    console.log('\n' + '='.repeat(70));
    console.log('BENCHMARK COMPLETE');
    console.log('='.repeat(70));
    console.log(`Total Duration: ${duration} seconds`);

    // Note: Due to k6's VU isolation, we cannot track created reservation IDs across VUs.
    // Test reservations are identifiable by their email pattern: bench_*@test.com
    // Some reservations are already cancelled during the test (~30%).
    // Manual cleanup can be done via MongoDB if needed:
    //   db.reservations.updateMany(
    //     { customerEmail: { $regex: /^bench_.*@test\.com$/ }, status: "CONFIRMED" },
    //     { $set: { status: "CANCELLED", cancelledAt: new Date() } }
    //   )

    console.log('\nNote: Test reservations use email pattern bench_*@test.com');
    console.log('      ~30% are auto-cancelled during the test.');
    console.log('');
    console.log('Results saved to: load-tests/results.json');
    console.log('');
    console.log('Key metrics to review:');
    console.log('  - p95 latencies for each operation');
    console.log('  - Error rates per phase');
    console.log('  - Capacity exceeded count in concurrent phase');
    console.log('='.repeat(70) + '\n');
}

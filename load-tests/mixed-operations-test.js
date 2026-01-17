import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
    fetchTestData,
    getFutureDate,
    getAvailableSlot,
    getBaseUrl,
} from './test-utils.js';

/**
 * Mixed Operations Load Test
 *
 * Simulates realistic traffic with multiple operation types:
 * - Heavy availability checks (read-heavy)
 * - Moderate reservations (write with locking)
 * - Light reporting (aggregate queries)
 *
 * All test data is dynamically fetched from the API - no hardcoded IDs.
 *
 * Run: k6 run load-tests/mixed-operations-test.js
 */

const availabilityChecks = new Counter('availability_checks');
const reservationAttempts = new Counter('reservation_attempts');
const reportGenerated = new Counter('reports_generated');

const availabilityDuration = new Trend('availability_duration_ms');
const reservationDuration = new Trend('reservation_duration_ms');
const reportDuration = new Trend('report_duration_ms');

export const options = {
    scenarios: {
        // Heavy read load - availability checks
        availability_checks: {
            executor: 'constant-arrival-rate',
            rate: 20,
            timeUnit: '1s',
            duration: '2m',
            preAllocatedVUs: 20,
            maxVUs: 40,
        },

        // Moderate write load - reservations
        reservations: {
            executor: 'constant-arrival-rate',
            rate: 5,
            timeUnit: '1s',
            duration: '2m',
            preAllocatedVUs: 10,
            maxVUs: 20,
        },

        // Light read load - reports
        reports: {
            executor: 'constant-arrival-rate',
            rate: 1,
            timeUnit: '1s',
            duration: '2m',
            preAllocatedVUs: 5,
            maxVUs: 10,
        },
    },

    thresholds: {
        'availability_duration_ms': ['p(95)<200'],
        'reservation_duration_ms': ['p(95)<500'],
        'report_duration_ms': ['p(95)<2000'],
        'http_req_failed': ['rate<0.05'],
    },
};

export function setup() {
    console.log('\n' + '='.repeat(60));
    console.log('MIXED OPERATIONS LOAD TEST');
    console.log('='.repeat(60));

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
    console.log('Traffic Distribution:');
    console.log('  - Availability Checks: 20/second (read-heavy)');
    console.log('  - Reservations: 5/second (write with locking)');
    console.log('  - Reports: 1/second (aggregation queries)');
    console.log('');
    console.log('Duration: 2 minutes');
    console.log('');
    console.log('Performance Thresholds:');
    console.log('  - Availability p95 < 200ms');
    console.log('  - Reservation p95 < 500ms');
    console.log('  - Report p95 < 2000ms');
    console.log('  - Error rate < 5%');
    console.log('='.repeat(60) + '\n');

    return {
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

    if (scenario === 'availability_checks') {
        // Check availability for random space and date
        const space = spaces[Math.floor(Math.random() * spaces.length)];
        const date = getFutureDate(Math.floor(Math.random() * 30) + 1);

        const start = Date.now();
        const res = http.get(`${BASE_URL}/availability/spaces/${space.id}?date=${date}`);
        availabilityDuration.add(Date.now() - start);

        availabilityChecks.add(1);
        check(res, { 'availability ok': (r) => r.status === 200 });

    } else if (scenario === 'reservations') {
        // Make a reservation
        const space = spaces[Math.floor(Math.random() * spaces.length)];
        const date = getFutureDate(Math.floor(Math.random() * 60) + 10);
        const startTime = getAvailableSlot(space.id, date);

        if (!startTime) {
            sleep(0.5);
            return;
        }

        const payload = JSON.stringify({
            spaceId: space.id,
            reservationDate: date,
            startTime: startTime,
            partySize: Math.floor(Math.random() * 4) + 2,
            customerName: `Mixed Test User ${__VU}`,
            customerEmail: `mixed_${__VU}_${Date.now()}@test.com`,
        });

        const start = Date.now();
        const res = http.post(`${BASE_URL}/reservations`, payload, {
            headers: { 'Content-Type': 'application/json' },
        });
        reservationDuration.add(Date.now() - start);

        reservationAttempts.add(1);

        check(res, {
            'reservation valid': (r) => r.status === 201 || r.status === 409 || r.status === 400
        });

    } else if (scenario === 'reports') {
        // Generate occupancy report
        const endDate = getFutureDate(0);
        const startDate = getFutureDate(-30);

        const start = Date.now();
        const res = http.get(
            `${BASE_URL}/reports/occupancy?restaurantId=${restaurantId}&startDate=${startDate}&endDate=${endDate}&granularity=DAILY`
        );
        reportDuration.add(Date.now() - start);

        reportGenerated.add(1);
        check(res, { 'report ok': (r) => r.status === 200 });
    }
}

export function teardown(data) {
    if (data.error) return;

    console.log('\n' + '='.repeat(60));
    console.log('MIXED OPERATIONS TEST COMPLETE');
    console.log('='.repeat(60));

    // Note: Due to k6's VU isolation, we cannot track created reservation IDs across VUs.
    // Test reservations are identifiable by their email pattern: mixed_*@test.com
    // Manual cleanup can be done via MongoDB if needed:
    //   db.reservations.updateMany(
    //     { customerEmail: { $regex: /^mixed_.*@test\.com$/ }, status: "CONFIRMED" },
    //     { $set: { status: "CANCELLED", cancelledAt: new Date() } }
    //   )

    console.log('\nNote: Test reservations use email pattern mixed_*@test.com');
    console.log('');
    console.log('Check metrics for:');
    console.log('  - Response time distributions (p95 values)');
    console.log('  - Error rates per operation type');
    console.log('  - System stability under mixed load');
    console.log('='.repeat(60) + '\n');
}

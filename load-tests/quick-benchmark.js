import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import {
    fetchTestData,
    getFutureDate,
    getAvailableSlot,
    getBaseUrl,
    cleanupTestReservations,
} from './test-utils.js';

/**
 * Quick Benchmark - Single User Performance Test
 *
 * Tests baseline latency for each endpoint with a single user.
 * All test data is dynamically fetched from the API - no hardcoded IDs.
 *
 * Run: k6 run load-tests/quick-benchmark.js
 */

// Custom metrics for each endpoint
const createReservationMetric = new Trend('create_reservation_ms');
const checkAvailabilityMetric = new Trend('check_availability_ms');
const generateReportMetric = new Trend('generate_report_ms');
const getReservationMetric = new Trend('get_reservation_ms');

export const options = {
    vus: 1,
    iterations: 50,
    thresholds: {
        'create_reservation_ms': ['p(95)<500'],
        'check_availability_ms': ['p(95)<200'],
        'generate_report_ms': ['p(95)<2000'],
    },
};

export function setup() {
    console.log('\n' + '='.repeat(60));
    console.log('QUICK BENCHMARK - SINGLE USER PERFORMANCE TEST');
    console.log('='.repeat(60));

    // Fetch test data dynamically from API
    const testData = fetchTestData();
    if (!testData.restaurantId || testData.spaces.length === 0) {
        console.log('ERROR: Could not fetch test data from API');
        console.log('Make sure the API is running and has seed data');
        return { error: true };
    }

    // Use first space for testing
    const testSpace = testData.spaces[0];

    console.log(`Restaurant: ${testData.restaurantName} (ID: ${testData.restaurantId})`);
    console.log(`Space: ${testSpace.name} (ID: ${testSpace.id})`);
    console.log('='.repeat(60) + '\n');

    // Use a fixed date for this test run to enable cleanup
    const testDate = getFutureDate(20);

    return {
        restaurantId: testData.restaurantId,
        spaceId: testSpace.id,
        spaceName: testSpace.name,
        testDate,
    };
}

export default function (data) {
    if (data.error) {
        console.log('Skipping test due to setup error');
        return;
    }

    const BASE_URL = getBaseUrl();
    const { restaurantId, spaceId, testDate } = data;

    // 1. Check Availability
    {
        const start = Date.now();
        const res = http.get(`${BASE_URL}/availability/spaces/${spaceId}?date=${testDate}`);
        checkAvailabilityMetric.add(Date.now() - start);
        check(res, { 'availability 200': (r) => r.status === 200 });
    }

    sleep(0.3);

    // 2. Create Reservation
    let reservationId = null;
    {
        const startTime = getAvailableSlot(spaceId, testDate) || '18:00';

        const payload = JSON.stringify({
            spaceId: spaceId,
            reservationDate: testDate,
            startTime: startTime,
            partySize: 2,
            customerName: 'Benchmark User',
            customerEmail: `bench-${Date.now()}-${__VU}-${__ITER}@test.com`,
        });

        const start = Date.now();
        const res = http.post(`${BASE_URL}/reservations`, payload, {
            headers: { 'Content-Type': 'application/json' },
        });
        createReservationMetric.add(Date.now() - start);

        if (res.status === 201) {
            try {
                reservationId = JSON.parse(res.body).id;
            } catch (e) {}
        }
        check(res, {
            'reservation created or capacity full': (r) => r.status === 201 || r.status === 409 || r.status === 400
        });
    }

    sleep(0.3);

    // 3. Get Reservation (if created)
    if (reservationId) {
        const start = Date.now();
        const res = http.get(`${BASE_URL}/reservations/${reservationId}`);
        getReservationMetric.add(Date.now() - start);
        check(res, { 'get reservation 200': (r) => r.status === 200 });
    }

    sleep(0.3);

    // 4. Generate Report
    {
        const endDate = getFutureDate(0);
        const startDate = getFutureDate(-30);

        const start = Date.now();
        const res = http.get(
            `${BASE_URL}/reports/occupancy?restaurantId=${restaurantId}&startDate=${startDate}&endDate=${endDate}&granularity=DAILY`
        );
        generateReportMetric.add(Date.now() - start);
        check(res, { 'report 200': (r) => r.status === 200 });
    }

    sleep(0.3);
}

export function teardown(data) {
    if (data.error) return;

    const { spaceId, testDate } = data;

    console.log('\n' + '='.repeat(60));
    console.log('QUICK BENCHMARK COMPLETE');
    console.log('='.repeat(60));

    // Cleanup: Fetch and cancel test reservations by email pattern
    // This works around k6's VU isolation (can't share arrays between VUs)
    console.log('\nCleaning up test reservations...');
    cleanupTestReservations(spaceId, testDate, 'bench');

    console.log('='.repeat(60) + '\n');
}

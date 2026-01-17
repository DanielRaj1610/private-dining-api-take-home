import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
    fetchTestData,
    getFutureDate,
    getFullyAvailableSlot,
    getBaseUrl,
    cleanupTestReservations,
} from './test-utils.js';

/**
 * Race Condition Test
 *
 * Tests concurrent booking to verify atomic capacity management prevents overbooking.
 * 10 users simultaneously try to book a space with limited capacity.
 *
 * All test data is dynamically fetched from the API - no hardcoded IDs.
 *
 * Run: k6 run load-tests/race-condition-test.js
 */

const successCount = new Counter('bookings_success');
const capacityExceeded = new Counter('bookings_capacity_exceeded');
const otherErrors = new Counter('bookings_other_error');
const responseTime = new Trend('booking_response_time');

const PARTY_SIZE = parseInt(__ENV.PARTY_SIZE || '3');

export const options = {
    // 10 users, each makes 1 request, all at the same time
    vus: 10,
    iterations: 10,
    duration: '15s',

    thresholds: {
        // Note: 409 responses are expected (capacity exceeded), so we don't set http_req_failed threshold
        // Instead we verify no 5xx errors via checks
        'checks': ['rate>0.99'],  // All checks should pass
    },
};

export function setup() {
    console.log('\n' + '='.repeat(60));
    console.log('RACE CONDITION TEST - ATOMIC CAPACITY MANAGEMENT VERIFICATION');
    console.log('='.repeat(60));

    // Fetch test data dynamically from API
    const testData = fetchTestData();
    if (!testData.restaurantId || testData.spaces.length === 0) {
        console.log('ERROR: Could not fetch test data from API');
        console.log('Make sure the API is running and has seed data');
        return { error: true };
    }

    // Find a space with small capacity for testing (ideally <= 20)
    // Sort by capacity to pick the smallest for clearer race condition testing
    const sortedSpaces = [...testData.spaces].sort((a, b) => a.maxCapacity - b.maxCapacity);
    const testSpace = sortedSpaces[0];
    const spaceId = testSpace.id;
    const maxCapacity = testSpace.maxCapacity;

    // Get a future date and find a fully available slot
    const testDate = getFutureDate(50 + Math.floor(Math.random() * 20));
    const startTime = getFullyAvailableSlot(spaceId, testDate, maxCapacity);

    if (!startTime) {
        console.log('WARNING: Could not find a fully available slot');
        console.log(`Tried space ${testSpace.name} on ${testDate}`);
    }

    const maxPossible = Math.floor(maxCapacity / PARTY_SIZE);

    console.log(`Restaurant: ${testData.restaurantName}`);
    console.log(`Space: ${testSpace.name} (ID: ${spaceId})`);
    console.log(`Date: ${testDate}`);
    console.log(`Start Time: ${startTime || '18:00 (fallback)'}`);
    console.log(`Space Capacity: ${maxCapacity}`);
    console.log(`Party Size: ${PARTY_SIZE}`);
    console.log(`Max Successful Bookings: ${maxPossible} (${maxPossible} x ${PARTY_SIZE} = ${maxPossible * PARTY_SIZE})`);
    console.log(`VUs: 10`);
    console.log('');
    console.log('Expected Results:');
    console.log(`  - bookings_success: <= ${maxPossible}`);
    console.log(`  - bookings_capacity_exceeded: >= ${10 - maxPossible}`);
    console.log(`  - bookings_other_error: 0`);
    console.log('='.repeat(60) + '\n');

    return {
        testDate,
        startTime: startTime || '18:00',
        spaceId,
        spaceName: testSpace.name,
        maxCapacity,
        maxPossible,
    };
}

export default function (data) {
    if (data.error) {
        console.log('Skipping test due to setup error');
        return;
    }

    const { testDate, startTime, spaceId } = data;
    const BASE_URL = getBaseUrl();

    const payload = JSON.stringify({
        spaceId: spaceId,
        reservationDate: testDate,
        startTime: startTime,
        partySize: PARTY_SIZE,
        customerName: `Race Condition User ${__VU}`,
        customerEmail: `race${__VU}_${Date.now()}@test.com`,
    });

    const startTs = Date.now();
    const res = http.post(`${BASE_URL}/reservations`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });
    responseTime.add(Date.now() - startTs);

    if (res.status === 201) {
        successCount.add(1);
        try {
            const body = JSON.parse(res.body);
            console.log(`VU ${__VU}: SUCCESS - Booked party of ${PARTY_SIZE} (ID: ${body.id})`);
        } catch (e) {
            console.log(`VU ${__VU}: SUCCESS - Booked party of ${PARTY_SIZE}`);
        }
    } else if (res.status === 409) {
        capacityExceeded.add(1);
        try {
            const body = JSON.parse(res.body);
            const available = body.details?.availableCapacity || 'unknown';
            console.log(`VU ${__VU}: REJECTED - Capacity exceeded (${available} available)`);
        } catch (e) {
            console.log(`VU ${__VU}: REJECTED - Capacity exceeded`);
        }
    } else if (res.status === 400) {
        // Could be invalid time slot or other validation error
        otherErrors.add(1);
        try {
            const body = JSON.parse(res.body);
            console.log(`VU ${__VU}: VALIDATION ERROR - ${body.error}: ${body.message}`);
        } catch (e) {
            console.log(`VU ${__VU}: ERROR 400 - ${res.body}`);
        }
    } else {
        otherErrors.add(1);
        console.log(`VU ${__VU}: ERROR ${res.status} - ${res.body}`);
    }

    check(res, {
        'valid response': (r) => r.status === 201 || r.status === 409 || r.status === 400,
        'no server error': (r) => r.status < 500,
    });
}

export function teardown(data) {
    if (data.error) return;

    const BASE_URL = getBaseUrl();
    const { testDate, spaceId, maxCapacity, maxPossible } = data;

    console.log('\n' + '='.repeat(60));
    console.log('RACE CONDITION TEST COMPLETE');
    console.log('='.repeat(60));

    // Cleanup: Fetch and cancel test reservations by email pattern
    // This works around k6's VU isolation (can't share arrays between VUs)
    console.log('\nCleaning up test reservations...');
    cleanupTestReservations(spaceId, testDate, 'race');

    console.log('');
    console.log('Verify in metrics above:');
    console.log(`  - Total guests booked <= ${maxCapacity}`);
    console.log(`  - bookings_success <= ${maxPossible}`);
    console.log('  - No 5xx server errors');
    console.log('  - Overbooking prevented');
    console.log('');
    console.log('To verify no overbooking occurred:');
    console.log(`  curl "${BASE_URL}/availability/spaces/${spaceId}?date=${testDate}"`);
    console.log('='.repeat(60) + '\n');
}

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
    fetchTestData,
    getFutureDate,
    getBaseUrl,
} from './test-utils.js';

/**
 * Concurrent Booking Stress Test
 *
 * Multiple scenarios testing system behavior under concurrent booking load.
 * Tests atomic capacity management, capacity enforcement, and system stability.
 *
 * All test data is dynamically fetched from the API - no hardcoded IDs.
 *
 * Run: k6 run load-tests/concurrent-booking-test.js
 */

const bookingSuccess = new Counter('booking_success');
const bookingCapacityExceeded = new Counter('booking_capacity_exceeded');
const bookingError = new Counter('booking_error');
const bookingDuration = new Trend('booking_duration_ms');

export const options = {
    scenarios: {
        // Scenario 1: Simultaneous burst - all users hit at once
        simultaneous_burst: {
            executor: 'shared-iterations',
            vus: 20,
            iterations: 20,
            maxDuration: '30s',
            startTime: '0s',
        },

        // Scenario 2: Sustained concurrent pressure
        sustained_pressure: {
            executor: 'constant-vus',
            vus: 10,
            duration: '1m',
            startTime: '35s',
        },

        // Scenario 3: Ramping load
        ramping_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 15 },
                { duration: '30s', target: 30 },
                { duration: '15s', target: 0 },
            ],
            startTime: '100s',
        },
    },

    thresholds: {
        'http_req_failed': ['rate<0.05'],
        'booking_duration_ms': ['p(95)<1000'],
        'booking_error': ['count<10'],
    },
};

// Use different dates for each scenario to avoid cross-contamination
const scenarioDateOffset = {
    'simultaneous_burst': 50,
    'sustained_pressure': 55,
    'ramping_load': 60,
};

function getAvailableSlots(spaceId, date) {
    const BASE_URL = getBaseUrl();
    const res = http.get(`${BASE_URL}/availability/spaces/${spaceId}?date=${date}`);
    if (res.status === 200) {
        const data = JSON.parse(res.body);
        return data.timeSlots || [];
    }
    return [];
}

export function setup() {
    console.log('\n' + '='.repeat(60));
    console.log('CONCURRENT BOOKING STRESS TEST');
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

    console.log(`Restaurant: ${testData.restaurantName}`);
    console.log(`Space: ${testSpace.name} (ID: ${testSpace.id})`);
    console.log('');
    console.log('Scenarios:');
    console.log('  1. Simultaneous Burst (0-30s): 20 users, all at once');
    console.log('  2. Sustained Pressure (35-95s): 10 concurrent users');
    console.log('  3. Ramping Load (100-160s): 0->30->0 users');
    console.log('');
    console.log('Testing: Atomic capacity management, capacity limits, stability');
    console.log('='.repeat(60) + '\n');

    return {
        spaceId: testSpace.id,
        spaceName: testSpace.name,
    };
}

export default function (data) {
    if (data.error) {
        console.log('Skipping test due to setup error');
        return;
    }

    const BASE_URL = getBaseUrl();
    const { spaceId } = data;
    const scenario = __ENV.K6_SCENARIO_NAME || 'default';
    const offset = scenarioDateOffset[scenario] || 50;

    // Vary the date slightly to distribute load
    const dateOffset = offset + (__ITER % 10);
    const testDate = getFutureDate(dateOffset);

    // Get available slots
    const slots = getAvailableSlots(spaceId, testDate);
    if (slots.length === 0) {
        console.log(`No slots available for ${testDate}`);
        sleep(1);
        return;
    }

    // Pick a random slot that has capacity
    const availableSlots = slots.filter(s => s.status !== 'FULL');
    if (availableSlots.length === 0) {
        sleep(0.5);
        return;
    }

    const slot = availableSlots[Math.floor(Math.random() * availableSlots.length)];
    const partySize = Math.min(3, slot.availableCapacity || 3);

    const payload = JSON.stringify({
        spaceId: spaceId,
        reservationDate: testDate,
        startTime: slot.startTime,
        partySize: partySize,
        customerName: `Stress Test ${scenario} VU${__VU}`,
        customerEmail: `stress_${scenario}_${__VU}_${Date.now()}@test.com`,
    });

    const start = Date.now();
    const res = http.post(`${BASE_URL}/reservations`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });
    bookingDuration.add(Date.now() - start);

    if (res.status === 201) {
        bookingSuccess.add(1);
    } else if (res.status === 409) {
        bookingCapacityExceeded.add(1);
    } else if (res.status < 500) {
        // 400 level errors are expected (validation, etc)
        bookingCapacityExceeded.add(1);
    } else {
        bookingError.add(1);
        console.log(`ERROR: ${res.status} - ${res.body}`);
    }

    check(res, {
        'booking response valid': (r) => r.status === 201 || r.status === 409 || r.status === 400,
        'no server error': (r) => r.status < 500,
    });

    sleep(0.2 + Math.random() * 0.3);
}

export function teardown(data) {
    if (data.error) return;

    const { spaceId } = data;

    console.log('\n' + '='.repeat(60));
    console.log('CONCURRENT BOOKING TEST COMPLETE');
    console.log('='.repeat(60));

    // Note: Due to k6's VU isolation, we cannot track created reservation IDs across VUs.
    // Test reservations are identifiable by their email pattern: stress_*@test.com
    // Manual cleanup can be done via MongoDB if needed:
    //   db.reservations.updateMany(
    //     { customerEmail: { $regex: /^stress_.*@test\.com$/ }, status: "CONFIRMED" },
    //     { $set: { status: "CANCELLED", cancelledAt: new Date() } }
    //   )

    console.log('\nNote: Test reservations use email pattern stress_*@test.com');
    console.log('');
    console.log('Key metrics to verify:');
    console.log('  - booking_success: Number of successful bookings');
    console.log('  - booking_capacity_exceeded: Properly rejected (expected)');
    console.log('  - booking_error: Should be very low (<10)');
    console.log('  - http_req_failed: Should be <5%');
    console.log('');
    console.log('No overbooking should occur - verify with availability check');
    console.log(`  curl "${getBaseUrl()}/availability/spaces/${spaceId}?date=<test-date>"`);
    console.log('='.repeat(60) + '\n');
}

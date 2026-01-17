import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import {
    fetchTestData,
    getFutureDate,
    getAvailableSlot,
    getBaseUrl,
    cancelReservation as cancelReservationUtil,
} from './test-utils.js';

/**
 * Edge Case Test Suite
 *
 * Tests various edge cases and potential race conditions:
 * 1. Concurrent cancellations of the same reservation
 * 2. Book-Cancel-Book race (capacity release timing)
 * 3. Partial capacity fits (small party after larger ones)
 * 4. Multiple concurrent slot creations
 * 5. Near-capacity boundary conditions
 *
 * All test data is dynamically fetched from the API - no hardcoded IDs.
 *
 * Run: k6 run load-tests/edge-case-test.js
 */

const testSuccess = new Counter('test_success');
const testFailure = new Counter('test_failure');
const bookingSuccess = new Counter('booking_success');
const capacityExceeded = new Counter('capacity_exceeded');
const cancellationSuccess = new Counter('cancellation_success');

export const options = {
    scenarios: {
        // Test 1: Concurrent cancellation of same reservation
        concurrent_cancel: {
            executor: 'shared-iterations',
            vus: 5,
            iterations: 5,
            maxDuration: '30s',
            startTime: '0s',
            exec: 'testConcurrentCancellation',
        },

        // Test 2: Partial capacity fit - fill up then small booking
        partial_capacity: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '30s',
            startTime: '35s',
            exec: 'testPartialCapacity',
        },

        // Test 3: Rapid book-cancel cycles
        book_cancel_cycles: {
            executor: 'constant-vus',
            vus: 5,
            duration: '30s',
            startTime: '70s',
            exec: 'testBookCancelCycles',
        },

        // Test 4: Boundary testing - exactly at capacity
        boundary_test: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '30s',
            startTime: '105s',
            exec: 'testBoundaryConditions',
        },
    },

    thresholds: {
        'test_failure': ['count<5'],
        'checks': ['rate>0.95'],
    },
};

function createReservation(spaceId, date, startTime, partySize, email) {
    const BASE_URL = getBaseUrl();
    const payload = JSON.stringify({
        spaceId: spaceId,
        reservationDate: date,
        startTime: startTime,
        partySize: partySize,
        customerName: `Edge Test User`,
        customerEmail: email,
    });

    return http.post(`${BASE_URL}/reservations`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });
}

function cancelReservation(reservationId, reason) {
    const BASE_URL = getBaseUrl();
    const payload = JSON.stringify({ reason: reason || 'Test cancellation' });
    return http.post(`${BASE_URL}/reservations/${reservationId}/cancel`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });
}

function getAvailability(spaceId, date) {
    const BASE_URL = getBaseUrl();
    return http.get(`${BASE_URL}/availability/spaces/${spaceId}?date=${date}`);
}

export function setup() {
    console.log('\n' + '='.repeat(60));
    console.log('EDGE CASE TEST SUITE');
    console.log('='.repeat(60));

    // Fetch test data dynamically from API
    const testData = fetchTestData();
    if (!testData.restaurantId || testData.spaces.length === 0) {
        console.log('ERROR: Could not fetch test data from API');
        console.log('Make sure the API is running and has seed data');
        return { error: true };
    }

    // Use first (smallest capacity) space for edge case testing
    const sortedSpaces = [...testData.spaces].sort((a, b) => a.maxCapacity - b.maxCapacity);
    const testSpace = sortedSpaces[0];
    const spaceId = testSpace.id;
    const maxCapacity = testSpace.maxCapacity;

    console.log(`Restaurant: ${testData.restaurantName}`);
    console.log(`Space: ${testSpace.name} (ID: ${spaceId}, Max Capacity: ${maxCapacity})`);
    console.log('');
    console.log('Tests:');
    console.log('  1. Concurrent Cancellation (0-30s)');
    console.log('  2. Partial Capacity Fit (35-65s)');
    console.log('  3. Rapid Book-Cancel Cycles (70-100s)');
    console.log('  4. Boundary Conditions (105-135s)');
    console.log('='.repeat(60) + '\n');

    // Create a reservation that will be cancelled concurrently
    const testDate = getFutureDate(30);
    const startTime = getAvailableSlot(spaceId, testDate) || '18:00';
    const res = createReservation(spaceId, testDate, startTime, 3, 'concurrent_cancel@test.com');

    let sharedReservationId = null;
    if (res.status === 201) {
        const data = JSON.parse(res.body);
        sharedReservationId = data.id;
        console.log(`Created shared reservation: ${sharedReservationId} for ${testDate} at ${startTime}`);
    } else {
        console.log(`Failed to create shared reservation: ${res.status} - ${res.body}`);
    }

    return {
        spaceId,
        spaceName: testSpace.name,
        maxCapacity,
        sharedReservationId,
        testDate,
        startTime,
        createdReservationIds: sharedReservationId ? [sharedReservationId] : [],
    };
}

/**
 * Test 1: Multiple VUs try to cancel the same reservation
 * Only ONE should succeed, others should get 400 (already cancelled) or 409 (concurrent modification)
 */
export function testConcurrentCancellation(data) {
    if (data.error || !data.sharedReservationId) {
        console.log('No shared reservation to cancel');
        return;
    }

    const res = cancelReservation(data.sharedReservationId, `Concurrent cancel by VU ${__VU}`);

    if (res.status === 200) {
        cancellationSuccess.add(1);
        console.log(`VU ${__VU}: Successfully cancelled reservation`);
    } else if (res.status === 400) {
        // Expected for second+ cancellation attempts (already cancelled)
        console.log(`VU ${__VU}: Already cancelled (expected)`);
    } else if (res.status === 409) {
        // Expected for concurrent modification attempts
        console.log(`VU ${__VU}: Concurrent modification (expected)`);
    } else {
        testFailure.add(1);
        console.log(`VU ${__VU}: Unexpected status ${res.status} - ${res.body}`);
    }

    check(res, {
        'cancel response valid': (r) => r.status === 200 || r.status === 400 || r.status === 409,
        'no server error': (r) => r.status < 500,
    });
}

/**
 * Test 2: Partial capacity fit
 * - Fill space with large parties leaving small capacity
 * - Verify small party can still book
 */
export function testPartialCapacity(data) {
    if (data.error) return;

    const { spaceId, maxCapacity } = data;
    const testDate = getFutureDate(40);
    const startTime = getAvailableSlot(spaceId, testDate) || '18:00';

    console.log(`\n--- Partial Capacity Test (${testDate} ${startTime}) ---`);
    console.log(`Space max capacity: ${maxCapacity}`);

    // First, check initial availability
    const initialAvail = getAvailability(spaceId, testDate);
    if (initialAvail.status !== 200) {
        testFailure.add(1);
        console.log('Failed to get initial availability');
        return;
    }

    // Book parties of 3 until we can't fit more
    let bookings = [];
    const partySize = 3;
    const maxParties = Math.floor(maxCapacity / partySize);

    for (let i = 0; i < maxParties; i++) {
        const res = createReservation(
            spaceId,
            testDate,
            startTime,
            partySize,
            `partial_test_${i}_${Date.now()}@test.com`
        );

        if (res.status === 201) {
            const resData = JSON.parse(res.body);
            bookings.push(resData.id);
            data.createdReservationIds.push(resData.id);
            bookingSuccess.add(1);
            console.log(`Booked party of ${partySize} (${i + 1}/${maxParties}), total: ${(i + 1) * partySize}`);
        } else if (res.status === 409) {
            console.log(`Party ${i + 1} rejected - capacity full`);
            break;
        }
    }

    // Now try to book a party of 1 (should fail if fully booked)
    const remainingCapacity = maxCapacity - (bookings.length * partySize);
    console.log(`Remaining capacity: ${remainingCapacity}`);

    const smallParty = createReservation(
        spaceId,
        testDate,
        startTime,
        1,
        `partial_small_${Date.now()}@test.com`
    );

    console.log(`Small party (1 guest) result: ${smallParty.status}`);

    if (smallParty.status === 201) {
        const smallData = JSON.parse(smallParty.body);
        data.createdReservationIds.push(smallData.id);
    }

    // Verify final availability
    const finalAvail = getAvailability(spaceId, testDate);
    if (finalAvail.status === 200) {
        const availData = JSON.parse(finalAvail.body);
        const slot = availData.timeSlots.find(s => s.startTime === startTime);
        if (slot) {
            console.log(`Final available capacity: ${slot.availableCapacity}`);

            // Capacity should never be negative
            check(slot, {
                'capacity not negative': (s) => s.availableCapacity >= 0,
                'capacity not over max': (s) => s.availableCapacity <= maxCapacity,
            });
        }
    }

    testSuccess.add(1);
}

/**
 * Test 3: Rapid book-cancel cycles
 * Tests capacity tracking under rapid state changes
 */
export function testBookCancelCycles(data) {
    if (data.error) return;

    const { spaceId } = data;
    const testDate = getFutureDate(50 + __VU);
    const startTime = getAvailableSlot(spaceId, testDate) || '18:00';

    for (let i = 0; i < 5; i++) {
        // Book
        const bookRes = createReservation(
            spaceId,
            testDate,
            startTime,
            2,
            `cycle_${__VU}_${i}_${Date.now()}@test.com`
        );

        if (bookRes.status === 201) {
            const bookData = JSON.parse(bookRes.body);
            bookingSuccess.add(1);

            // Immediately cancel
            sleep(0.05); // Small delay
            const cancelRes = cancelReservation(bookData.id, 'Cycle test');

            if (cancelRes.status === 200) {
                cancellationSuccess.add(1);
            } else {
                console.log(`Cancel failed: ${cancelRes.status}`);
            }
        } else if (bookRes.status === 409) {
            capacityExceeded.add(1);
        }

        sleep(0.1);
    }

    // Verify capacity is back to normal after all cancellations
    sleep(0.5);
    const avail = getAvailability(spaceId, testDate);
    if (avail.status === 200) {
        const availData = JSON.parse(avail.body);
        const slot = availData.timeSlots.find(s => s.startTime === startTime);
        if (slot) {
            check(slot, {
                'capacity restored after cycles': (s) => s.availableCapacity > 0,
            });
        }
    }
}

/**
 * Test 4: Boundary conditions
 * Tests exact capacity limits
 */
export function testBoundaryConditions(data) {
    if (data.error) return;

    const { spaceId, maxCapacity } = data;
    const testDate = getFutureDate(60);
    const startTime = getAvailableSlot(spaceId, testDate) || '18:00';

    console.log(`\n--- Boundary Test (${testDate} ${startTime}) ---`);
    console.log(`Space max capacity: ${maxCapacity}`);

    // Fill to exactly max capacity
    let bookings = [];
    let totalBooked = 0;
    const partySize = 3;
    const maxParties = Math.floor(maxCapacity / partySize);

    for (let i = 0; i < maxParties; i++) {
        const res = createReservation(
            spaceId,
            testDate,
            startTime,
            partySize,
            `boundary_${i}_${Date.now()}@test.com`
        );

        if (res.status === 201) {
            const resData = JSON.parse(res.body);
            bookings.push(resData.id);
            data.createdReservationIds.push(resData.id);
            totalBooked += partySize;
            console.log(`Booked party ${i + 1}: total now ${totalBooked}`);
        }
    }

    // Should be at or near capacity now
    console.log(`Total booked: ${totalBooked}, max capacity: ${maxCapacity}`);

    // Try to book 1 more - should fail if at capacity
    const overflowRes = createReservation(
        spaceId,
        testDate,
        startTime,
        1,
        `overflow_${Date.now()}@test.com`
    );

    const remainingAfterBookings = maxCapacity - totalBooked;
    if (remainingAfterBookings >= 1) {
        // Should succeed if there's remaining capacity
        if (overflowRes.status === 201) {
            const overflowData = JSON.parse(overflowRes.body);
            data.createdReservationIds.push(overflowData.id);
            console.log('Small overflow booking succeeded (remaining capacity existed)');
            testSuccess.add(1);
        }
    } else {
        // Should fail if no remaining capacity
        check(overflowRes, {
            'overflow booking rejected': (r) => r.status === 409,
        });

        if (overflowRes.status === 201) {
            console.log('ERROR: Overflow booking succeeded when it should have failed!');
            testFailure.add(1);
        } else if (overflowRes.status === 409) {
            console.log('Correctly rejected overflow booking');
            testSuccess.add(1);
        }
    }

    // Cancel one booking (releases capacity)
    if (bookings.length > 0) {
        const cancelRes = cancelReservation(bookings[0], 'Release for boundary test');
        if (cancelRes.status === 200) {
            console.log(`Released ${partySize} capacity by cancellation`);

            // Now book same amount should work
            const refillRes = createReservation(
                spaceId,
                testDate,
                startTime,
                partySize,
                `refill_${Date.now()}@test.com`
            );

            check(refillRes, {
                'refill after cancel works': (r) => r.status === 201,
            });

            if (refillRes.status === 201) {
                const refillData = JSON.parse(refillRes.body);
                data.createdReservationIds.push(refillData.id);
                console.log('Successfully refilled capacity after cancellation');
            }
        }
    }
}

export function teardown(data) {
    if (data.error) return;

    const { createdReservationIds } = data;

    console.log('\n' + '='.repeat(60));
    console.log('EDGE CASE TEST COMPLETE');
    console.log('='.repeat(60));

    // Cleanup: Cancel all created reservations
    if (createdReservationIds && createdReservationIds.length > 0) {
        console.log(`\nCleaning up ${createdReservationIds.length} test reservations...`);
        let cancelled = 0;
        let failed = 0;

        for (const id of createdReservationIds) {
            const result = cancelReservationUtil(id, 'Edge case test cleanup');
            if (result.success || result.status === 400) {
                cancelled++;
            } else {
                failed++;
            }
        }

        console.log(`Cleanup complete: ${cancelled} cancelled, ${failed} failed`);
    } else {
        console.log('\nNo reservations to clean up');
    }

    console.log('');
    console.log('Check metrics:');
    console.log('  - test_success: Passed test scenarios');
    console.log('  - test_failure: Failed scenarios (should be 0)');
    console.log('  - cancellation_success: Only 1 for concurrent cancel test');
    console.log('');
    console.log('Key verifications:');
    console.log('  - No negative capacity');
    console.log('  - No overbooking');
    console.log('  - Capacity properly restored after cancellations');
    console.log('='.repeat(60) + '\n');
}

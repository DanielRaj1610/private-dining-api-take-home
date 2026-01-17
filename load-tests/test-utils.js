/**
 * Shared Test Utilities for k6 Load Tests
 *
 * Provides dynamic data fetching from the API to avoid hardcoded IDs.
 * All test data is fetched from the running API instance.
 */

import http from 'k6/http';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

/**
 * Fetch all restaurants from the API
 * @returns {Array} List of restaurant objects with id and name
 */
export function fetchRestaurants() {
    const res = http.get(`${BASE_URL}/restaurants?size=100`);
    if (res.status === 200) {
        const data = JSON.parse(res.body);
        return data.content || [];
    }
    console.log(`Failed to fetch restaurants: ${res.status}`);
    return [];
}

/**
 * Fetch spaces for a restaurant from the API
 * @param {string} restaurantId - The restaurant ID
 * @returns {Array} List of space objects with id, name, maxCapacity
 */
export function fetchSpacesForRestaurant(restaurantId) {
    const res = http.get(`${BASE_URL}/restaurants/${restaurantId}/spaces`);
    if (res.status === 200) {
        const data = JSON.parse(res.body);
        // Handle both array response and paginated response
        return Array.isArray(data) ? data : (data.content || []);
    }
    console.log(`Failed to fetch spaces for restaurant ${restaurantId}: ${res.status}`);
    return [];
}

/**
 * Fetch all spaces from the first available restaurant
 * @returns {Object} { restaurantId, spaces: [...] }
 */
export function fetchTestData() {
    const restaurants = fetchRestaurants();
    if (restaurants.length === 0) {
        console.log('ERROR: No restaurants found in database');
        return { restaurantId: null, spaces: [] };
    }

    // Use the first restaurant
    const restaurant = restaurants[0];
    const restaurantId = restaurant.id || restaurant._id;

    const spaces = fetchSpacesForRestaurant(restaurantId);
    if (spaces.length === 0) {
        console.log(`ERROR: No spaces found for restaurant ${restaurantId}`);
        return { restaurantId, spaces: [] };
    }

    console.log(`Loaded test data: Restaurant=${restaurant.name}, Spaces=${spaces.length}`);

    return {
        restaurantId,
        restaurantName: restaurant.name,
        spaces: spaces.map(s => ({
            id: s.id || s._id,
            name: s.name,
            maxCapacity: s.maxCapacity,
            slotDurationMinutes: s.slotDurationMinutes || 60,
        })),
    };
}

/**
 * Get a future date that's not a closed day (Sunday/Monday)
 * @param {number} daysAhead - Number of days from today
 * @param {number} maxDays - Maximum days ahead (default 85 to stay within 90-day limit)
 * @returns {string} Date in YYYY-MM-DD format
 */
export function getFutureDate(daysAhead, maxDays = 85) {
    const d = new Date();
    const actualDays = Math.min(daysAhead, maxDays);
    d.setDate(d.getDate() + actualDays);
    // Skip Sunday (0) and Monday (1) - restaurant closed
    while (d.getDay() === 0 || d.getDay() === 1) {
        d.setDate(d.getDate() + 1);
    }
    return d.toISOString().split('T')[0];
}

/**
 * Get an available time slot for a space on a date
 * @param {string} spaceId - The space ID
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {string|null} Start time in HH:mm format, or null if none available
 */
export function getAvailableSlot(spaceId, date) {
    const res = http.get(`${BASE_URL}/availability/spaces/${spaceId}?date=${date}`);
    if (res.status === 200) {
        const data = JSON.parse(res.body);
        if (data.timeSlots && data.timeSlots.length > 0) {
            // Prefer fully available slots
            const availableSlot = data.timeSlots.find(s => s.status === 'AVAILABLE');
            if (availableSlot) return availableSlot.startTime;
            // Fallback to any slot with capacity
            const limitedSlot = data.timeSlots.find(s => s.status === 'LIMITED' && s.availableCapacity > 0);
            if (limitedSlot) return limitedSlot.startTime;
        }
    }
    return null;
}

/**
 * Get an available slot with full capacity (for race condition tests)
 * @param {string} spaceId - The space ID
 * @param {string} date - Date in YYYY-MM-DD format
 * @param {number} maxCapacity - Expected max capacity of the space
 * @returns {string|null} Start time in HH:mm format, or null if none available
 */
export function getFullyAvailableSlot(spaceId, date, maxCapacity) {
    const res = http.get(`${BASE_URL}/availability/spaces/${spaceId}?date=${date}`);
    if (res.status === 200) {
        const data = JSON.parse(res.body);
        if (data.timeSlots && data.timeSlots.length > 0) {
            const slot = data.timeSlots.find(
                s => s.status === 'AVAILABLE' && s.availableCapacity === maxCapacity
            );
            if (slot) return slot.startTime;
        }
    }
    return null;
}

/**
 * Create a reservation
 * @param {Object} params - Reservation parameters
 * @returns {Object} { success: boolean, id: string|null, status: number }
 */
export function createReservation(params) {
    const payload = JSON.stringify({
        spaceId: params.spaceId,
        reservationDate: params.date,
        startTime: params.startTime,
        partySize: params.partySize || 2,
        customerName: params.customerName || 'Load Test User',
        customerEmail: params.customerEmail || `loadtest_${Date.now()}@test.com`,
    });

    const res = http.post(`${BASE_URL}/reservations`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    let id = null;
    if (res.status === 201) {
        try {
            id = JSON.parse(res.body).id;
        } catch (e) {}
    }

    return {
        success: res.status === 201,
        id,
        status: res.status,
        body: res.body,
    };
}

/**
 * Cancel a reservation
 * @param {string} reservationId - The reservation ID
 * @param {string} reason - Cancellation reason
 * @returns {Object} { success: boolean, status: number }
 */
export function cancelReservation(reservationId, reason = 'Load test cleanup') {
    const payload = JSON.stringify({ reason });
    const res = http.post(`${BASE_URL}/reservations/${reservationId}/cancel`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    return {
        success: res.status === 200,
        status: res.status,
    };
}

/**
 * Delete a reservation (permanent)
 * @param {string} reservationId - The reservation ID
 * @returns {Object} { success: boolean, status: number }
 */
export function deleteReservation(reservationId) {
    const res = http.del(`${BASE_URL}/reservations/${reservationId}`);
    return {
        success: res.status === 204,
        status: res.status,
    };
}

/**
 * Cleanup test reservations by cancelling them
 * @param {Array} reservationIds - List of reservation IDs to cancel
 */
export function cleanupReservations(reservationIds) {
    let cancelled = 0;
    let failed = 0;

    for (const id of reservationIds) {
        if (id) {
            const result = cancelReservation(id, 'Test cleanup');
            if (result.success || result.status === 400) { // 400 = already cancelled
                cancelled++;
            } else {
                failed++;
            }
        }
    }

    console.log(`Cleanup: ${cancelled} cancelled, ${failed} failed`);
}

/**
 * Get BASE_URL
 * @returns {string} The base URL
 */
export function getBaseUrl() {
    return BASE_URL;
}

/**
 * Fetch reservations by space and date range for cleanup
 * @param {string} spaceId - The space ID
 * @param {string} date - The reservation date (YYYY-MM-DD)
 * @returns {Array} List of reservation objects
 */
export function fetchReservationsForCleanup(spaceId, date) {
    const res = http.get(`${BASE_URL}/reservations?spaceId=${spaceId}&date=${date}&status=CONFIRMED&size=100`);
    if (res.status === 200) {
        const data = JSON.parse(res.body);
        return data.content || [];
    }
    return [];
}

/**
 * Cleanup test reservations by fetching from API and cancelling
 * This works around k6's VU isolation - we can't share arrays between VUs
 * @param {string} spaceId - The space ID used in tests
 * @param {string} date - The test date (YYYY-MM-DD)
 * @param {string} emailPattern - Pattern to match (e.g., 'race' to match race*@test.com)
 */
export function cleanupTestReservations(spaceId, date, emailPattern) {
    // First try to fetch reservations via the API
    const res = http.get(`${BASE_URL}/reservations?spaceId=${spaceId}&date=${date}&status=CONFIRMED&size=100`);

    if (res.status !== 200) {
        console.log(`Could not fetch reservations for cleanup: ${res.status}`);
        return;
    }

    let data;
    try {
        data = JSON.parse(res.body);
    } catch (e) {
        console.log('Could not parse reservations response');
        return;
    }

    const reservations = data.content || [];
    const testReservations = reservations.filter(r =>
        r.customerEmail && r.customerEmail.includes(emailPattern) && r.customerEmail.endsWith('@test.com')
    );

    if (testReservations.length === 0) {
        console.log('No test reservations found to clean up');
        return;
    }

    console.log(`Found ${testReservations.length} test reservations to clean up`);

    let cancelled = 0;
    let failed = 0;

    for (const reservation of testReservations) {
        const result = cancelReservation(reservation.id, 'Test cleanup');
        if (result.success || result.status === 400) {
            cancelled++;
        } else {
            failed++;
        }
    }

    console.log(`Cleanup complete: ${cancelled} cancelled, ${failed} failed`);
}

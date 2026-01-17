package com.opentable.privatedining.integration;

import com.opentable.privatedining.dto.request.CreateReservationRequest;
import com.opentable.privatedining.exception.CapacityExceededException;
import com.opentable.privatedining.model.OperatingHours;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.model.enums.ReservationStatus;
import com.opentable.privatedining.repository.ReservationRepository;
import com.opentable.privatedining.repository.RestaurantRepository;
import com.opentable.privatedining.repository.SlotCapacityRepository;
import com.opentable.privatedining.repository.SpaceRepository;
import com.opentable.privatedining.service.ReservationService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for concurrent reservation scenarios.
 * These tests verify that the atomic capacity management prevents overbooking
 * under concurrent load.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Concurrency Integration Tests")
class ConcurrencyIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private SlotCapacityRepository slotCapacityRepository;

    private Restaurant testRestaurant;
    private Space testSpace;
    private ObjectId restaurantId;
    private UUID spaceId;

    @BeforeEach
    void setUp() {
        // Clean up
        reservationRepository.deleteAll();
        slotCapacityRepository.deleteAll();
        spaceRepository.deleteAll();
        restaurantRepository.deleteAll();

        // Create operating hours for all days (9:00 - 22:00)
        List<OperatingHours> operatingHours = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            operatingHours.add(OperatingHours.builder()
                    .dayOfWeek(i)
                    .openTime("09:00")
                    .closeTime("22:00")
                    .isClosed(false)
                    .build());
        }

        // Create test restaurant
        testRestaurant = Restaurant.builder()
                .name("Concurrency Test Restaurant")
                .timezone("America/New_York")
                .address("123 Test St")
                .city("New York")
                .state("NY")
                .operatingHours(operatingHours)
                .isActive(true)
                .build();
        testRestaurant = restaurantRepository.save(testRestaurant);
        restaurantId = testRestaurant.getId();

        // Create test space with capacity 9 (for 3 parties of 3)
        spaceId = UUID.randomUUID();
        testSpace = Space.builder()
                .id(spaceId)
                .restaurantId(restaurantId.toHexString())
                .name("Test Space")
                .maxCapacity(9)
                .minCapacity(1)
                .slotDurationMinutes(60)
                .bufferMinutes(15)
                .isActive(true)
                .build();
        testSpace = spaceRepository.save(testSpace);
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        slotCapacityRepository.deleteAll();
        spaceRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    @DisplayName("Should prevent overbooking when 10 concurrent requests try to book same slot")
    void shouldPreventOverbookingUnderConcurrentLoad() throws Exception {
        // Given: Space has capacity 9, party size is 3
        // Expected: Only 3 reservations should succeed (9 / 3 = 3)
        int partySize = 3;
        int concurrentRequests = 10;
        LocalDate testDate = LocalDate.now().plusDays(14);
        String startTime = "18:00";

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger capacityExceededCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);
        List<String> createdReservationIds = new CopyOnWriteArrayList<>();

        // When: 10 concurrent requests try to book
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    CreateReservationRequest request = CreateReservationRequest.builder()
                            .spaceId(spaceId)
                            .reservationDate(testDate)
                            .startTime(startTime)
                            .partySize(partySize)
                            .customerName("Concurrent User " + index)
                            .customerEmail("concurrent_" + index + "@test.com")
                            .build();

                    Reservation reservation = reservationService.createReservation(request);
                    successCount.incrementAndGet();
                    createdReservationIds.add(reservation.getId().toHexString());

                } catch (CapacityExceededException e) {
                    capacityExceededCount.incrementAndGet();
                } catch (Exception e) {
                    otherErrorCount.incrementAndGet();
                    System.err.println("Unexpected error: " + e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        completionLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: Verify results
        int expectedSuccesses = testSpace.getMaxCapacity() / partySize; // 9 / 3 = 3

        assertEquals(expectedSuccesses, successCount.get(),
                "Expected exactly " + expectedSuccesses + " successful reservations");
        assertEquals(concurrentRequests - expectedSuccesses, capacityExceededCount.get(),
                "Expected " + (concurrentRequests - expectedSuccesses) + " capacity exceeded errors");
        assertEquals(0, otherErrorCount.get(), "Expected no other errors");

        // Verify total booked capacity
        List<Reservation> allReservations = reservationRepository
                .findBySpaceIdAndReservationDateAndStatus(spaceId, testDate, ReservationStatus.CONFIRMED);

        int totalBooked = allReservations.stream()
                .mapToInt(Reservation::getPartySize)
                .sum();

        assertTrue(totalBooked <= testSpace.getMaxCapacity(),
                "Total booked (" + totalBooked + ") should not exceed max capacity (" + testSpace.getMaxCapacity() + ")");
        assertEquals(expectedSuccesses * partySize, totalBooked,
                "Total booked should be " + (expectedSuccesses * partySize));
    }

    @Test
    @DisplayName("Should allow all concurrent bookings when within capacity")
    void shouldAllowAllBookingsWhenWithinCapacity() throws Exception {
        // Given: Space has capacity 9, 3 requests for 2 guests each (total 6 < 9)
        int partySize = 2;
        int concurrentRequests = 3;
        LocalDate testDate = LocalDate.now().plusDays(15);
        String startTime = "19:00";

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When: 3 concurrent requests try to book 2 guests each
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    CreateReservationRequest request = CreateReservationRequest.builder()
                            .spaceId(spaceId)
                            .reservationDate(testDate)
                            .startTime(startTime)
                            .partySize(partySize)
                            .customerName("Success User " + index)
                            .customerEmail("success_" + index + "@test.com")
                            .build();

                    reservationService.createReservation(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completionLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: All should succeed
        assertEquals(concurrentRequests, successCount.get(),
                "All " + concurrentRequests + " reservations should succeed");
        assertEquals(0, failureCount.get(), "No reservations should fail");

        // Verify total
        List<Reservation> allReservations = reservationRepository
                .findBySpaceIdAndReservationDateAndStatus(spaceId, testDate, ReservationStatus.CONFIRMED);
        assertEquals(concurrentRequests, allReservations.size());
    }

    @Test
    @DisplayName("Should handle exact capacity boundary correctly")
    void shouldHandleExactCapacityBoundary() throws Exception {
        // Given: Space has capacity 9, one request for exactly 9 guests
        // should succeed, second should fail
        LocalDate testDate = LocalDate.now().plusDays(16);
        String startTime = "20:00";

        // First request - exactly at capacity
        CreateReservationRequest firstRequest = CreateReservationRequest.builder()
                .spaceId(spaceId)
                .reservationDate(testDate)
                .startTime(startTime)
                .partySize(9)
                .customerName("Full Capacity User")
                .customerEmail("full@test.com")
                .build();

        Reservation firstReservation = reservationService.createReservation(firstRequest);
        assertNotNull(firstReservation);
        assertEquals(9, firstReservation.getPartySize());

        // Second request - should fail even for 1 guest
        CreateReservationRequest secondRequest = CreateReservationRequest.builder()
                .spaceId(spaceId)
                .reservationDate(testDate)
                .startTime(startTime)
                .partySize(1)
                .customerName("Overflow User")
                .customerEmail("overflow@test.com")
                .build();

        assertThrows(CapacityExceededException.class,
                () -> reservationService.createReservation(secondRequest));

        // Verify only one reservation exists
        List<Reservation> allReservations = reservationRepository
                .findBySpaceIdAndReservationDateAndStatus(spaceId, testDate, ReservationStatus.CONFIRMED);
        assertEquals(1, allReservations.size());
        assertEquals(9, allReservations.get(0).getPartySize());
    }

    @Test
    @DisplayName("Should handle high concurrency stress test")
    void shouldHandleHighConcurrencyStressTest() throws Exception {
        // Given: 20 concurrent requests, space capacity 9, party size varies 1-4
        int concurrentRequests = 20;
        LocalDate testDate = LocalDate.now().plusDays(17);
        String startTime = "17:00";

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger capacityExceededCount = new AtomicInteger(0);

        // When: 20 concurrent requests with varying party sizes
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            final int partySize = (index % 4) + 1; // Party sizes: 1, 2, 3, 4

            executor.submit(() -> {
                try {
                    startLatch.await();

                    CreateReservationRequest request = CreateReservationRequest.builder()
                            .spaceId(spaceId)
                            .reservationDate(testDate)
                            .startTime(startTime)
                            .partySize(partySize)
                            .customerName("Stress User " + index)
                            .customerEmail("stress_" + index + "@test.com")
                            .build();

                    reservationService.createReservation(request);
                    successCount.incrementAndGet();

                } catch (CapacityExceededException e) {
                    capacityExceededCount.incrementAndGet();
                } catch (Exception e) {
                    // Log unexpected errors
                    System.err.println("Unexpected error in stress test: " + e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completionLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: Verify no overbooking
        List<Reservation> allReservations = reservationRepository
                .findBySpaceIdAndReservationDateAndStatus(spaceId, testDate, ReservationStatus.CONFIRMED);

        int totalBooked = allReservations.stream()
                .mapToInt(Reservation::getPartySize)
                .sum();

        assertTrue(totalBooked <= testSpace.getMaxCapacity(),
                "Total booked (" + totalBooked + ") must not exceed capacity (" + testSpace.getMaxCapacity() + ")");

        // All requests should be accounted for
        assertEquals(concurrentRequests, successCount.get() + capacityExceededCount.get(),
                "All requests should be accounted for as either success or capacity exceeded");

        System.out.println("Stress test results: " + successCount.get() + " succeeded, " +
                capacityExceededCount.get() + " rejected, total booked: " + totalBooked);
    }

    @Test
    @DisplayName("Should correctly release capacity when reservation is cancelled")
    void shouldReleaseCapacityOnCancellation() throws Exception {
        // Given: Book to full capacity
        LocalDate testDate = LocalDate.now().plusDays(18);
        String startTime = "16:00";

        CreateReservationRequest request1 = CreateReservationRequest.builder()
                .spaceId(spaceId)
                .reservationDate(testDate)
                .startTime(startTime)
                .partySize(9)
                .customerName("Full Capacity User")
                .customerEmail("full@test.com")
                .build();

        Reservation firstReservation = reservationService.createReservation(request1);

        // Verify slot is full
        CreateReservationRequest overflowRequest = CreateReservationRequest.builder()
                .spaceId(spaceId)
                .reservationDate(testDate)
                .startTime(startTime)
                .partySize(1)
                .customerName("Overflow User")
                .customerEmail("overflow@test.com")
                .build();

        assertThrows(CapacityExceededException.class,
                () -> reservationService.createReservation(overflowRequest));

        // When: Cancel the first reservation
        reservationService.cancelReservation(firstReservation.getId(),
                new com.opentable.privatedining.dto.request.CancellationRequest("Test cancellation"));

        // Then: New reservation should succeed
        CreateReservationRequest newRequest = CreateReservationRequest.builder()
                .spaceId(spaceId)
                .reservationDate(testDate)
                .startTime(startTime)
                .partySize(5)
                .customerName("After Cancel User")
                .customerEmail("aftercancel@test.com")
                .build();

        Reservation newReservation = reservationService.createReservation(newRequest);
        assertNotNull(newReservation);
        assertEquals(5, newReservation.getPartySize());
    }
}

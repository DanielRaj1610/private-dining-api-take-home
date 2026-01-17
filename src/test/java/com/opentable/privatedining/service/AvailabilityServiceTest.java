package com.opentable.privatedining.service;

import com.opentable.privatedining.dto.response.AvailabilityResponse;
import com.opentable.privatedining.dto.response.TimeSlotResponse;
import com.opentable.privatedining.exception.SpaceNotFoundException;
import com.opentable.privatedining.model.OperatingHours;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.model.enums.ReservationStatus;
import com.opentable.privatedining.model.enums.SlotStatus;
import com.opentable.privatedining.repository.ReservationRepository;
import com.opentable.privatedining.repository.SpaceRepository;
import com.opentable.privatedining.repository.TotalPartySizeResult;
import com.opentable.privatedining.util.TimeSlotGenerator;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AvailabilityService Tests")
class AvailabilityServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RestaurantService restaurantService;

    @Mock
    private TimeSlotGenerator timeSlotGenerator;

    @InjectMocks
    private AvailabilityService availabilityService;

    private Space testSpace;
    private Restaurant testRestaurant;
    private UUID spaceId;
    private ObjectId restaurantId;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        restaurantId = new ObjectId();
        testDate = LocalDate.now().plusDays(7);

        testSpace = Space.builder()
                .id(spaceId)
                .restaurantId(restaurantId.toHexString())
                .name("Garden Room")
                .maxCapacity(20)
                .minCapacity(2)
                .slotDurationMinutes(60)
                .bufferMinutes(15)
                .isActive(true)
                .build();

        List<OperatingHours> operatingHours = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            operatingHours.add(OperatingHours.builder()
                    .dayOfWeek(i)
                    .openTime("09:00")
                    .closeTime("22:00")
                    .isClosed(i == 0) // Sunday closed
                    .build());
        }

        testRestaurant = Restaurant.builder()
                .id(restaurantId)
                .name("Test Restaurant")
                .operatingHours(operatingHours)
                .build();
    }

    @Nested
    @DisplayName("getAvailableCapacity tests")
    class GetAvailableCapacityTests {

        @Test
        @DisplayName("Should return full capacity when no bookings exist")
        void shouldReturnFullCapacityWhenNoBookings() {
            // Given
            when(spaceRepository.findActiveById(spaceId)).thenReturn(Optional.of(testSpace));
            when(reservationRepository.sumPartySizeForOverlappingReservations(
                    eq(spaceId), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            int result = availabilityService.getAvailableCapacity(
                    spaceId, testDate, "18:00", "19:00");

            // Then
            assertEquals(20, result); // Full capacity
        }

        @Test
        @DisplayName("Should return reduced capacity when bookings exist")
        void shouldReturnReducedCapacityWithBookings() {
            // Given
            TotalPartySizeResult partySizeResult = mock(TotalPartySizeResult.class);
            when(partySizeResult.getTotalPartySize()).thenReturn(12);

            when(spaceRepository.findActiveById(spaceId)).thenReturn(Optional.of(testSpace));
            when(reservationRepository.sumPartySizeForOverlappingReservations(
                    eq(spaceId), any(), any(), any()))
                    .thenReturn(List.of(partySizeResult));

            // When
            int result = availabilityService.getAvailableCapacity(
                    spaceId, testDate, "18:00", "19:00");

            // Then
            assertEquals(8, result); // 20 - 12 = 8
        }

        @Test
        @DisplayName("Should return zero when fully booked")
        void shouldReturnZeroWhenFullyBooked() {
            // Given
            TotalPartySizeResult partySizeResult = mock(TotalPartySizeResult.class);
            when(partySizeResult.getTotalPartySize()).thenReturn(20);

            when(spaceRepository.findActiveById(spaceId)).thenReturn(Optional.of(testSpace));
            when(reservationRepository.sumPartySizeForOverlappingReservations(
                    eq(spaceId), any(), any(), any()))
                    .thenReturn(List.of(partySizeResult));

            // When
            int result = availabilityService.getAvailableCapacity(
                    spaceId, testDate, "18:00", "19:00");

            // Then
            assertEquals(0, result);
        }

        @Test
        @DisplayName("Should throw SpaceNotFoundException when space not found")
        void shouldThrowExceptionWhenSpaceNotFound() {
            // Given
            UUID nonExistentSpaceId = UUID.randomUUID();
            when(spaceRepository.findActiveById(nonExistentSpaceId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThrows(SpaceNotFoundException.class,
                    () -> availabilityService.getAvailableCapacity(
                            nonExistentSpaceId, testDate, "18:00", "19:00"));
        }
    }

    @Nested
    @DisplayName("getBookedCapacity tests")
    class GetBookedCapacityTests {

        @Test
        @DisplayName("Should return booked capacity from aggregation")
        void shouldReturnBookedCapacity() {
            // Given
            TotalPartySizeResult partySizeResult = mock(TotalPartySizeResult.class);
            when(partySizeResult.getTotalPartySize()).thenReturn(15);

            when(reservationRepository.sumPartySizeForOverlappingReservations(
                    eq(spaceId), any(), any(), any()))
                    .thenReturn(List.of(partySizeResult));

            // When
            int result = availabilityService.getBookedCapacity(
                    spaceId, testDate, "18:00", "19:00");

            // Then
            assertEquals(15, result);
        }

        @Test
        @DisplayName("Should return zero when no bookings")
        void shouldReturnZeroWhenNoBookings() {
            // Given
            when(reservationRepository.sumPartySizeForOverlappingReservations(
                    eq(spaceId), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            int result = availabilityService.getBookedCapacity(
                    spaceId, testDate, "18:00", "19:00");

            // Then
            assertEquals(0, result);
        }

        @Test
        @DisplayName("Should return zero when aggregation returns null")
        void shouldReturnZeroWhenAggregationReturnsNull() {
            // Given
            TotalPartySizeResult partySizeResult = mock(TotalPartySizeResult.class);
            when(partySizeResult.getTotalPartySize()).thenReturn(null);

            when(reservationRepository.sumPartySizeForOverlappingReservations(
                    eq(spaceId), any(), any(), any()))
                    .thenReturn(List.of(partySizeResult));

            // When
            int result = availabilityService.getBookedCapacity(
                    spaceId, testDate, "18:00", "19:00");

            // Then
            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("hasCapacity tests")
    class HasCapacityTests {

        @Test
        @DisplayName("Should return true when capacity available")
        void shouldReturnTrueWhenCapacityAvailable() {
            // Given
            when(spaceRepository.findActiveById(spaceId)).thenReturn(Optional.of(testSpace));
            when(reservationRepository.sumPartySizeForOverlappingReservations(
                    eq(spaceId), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            boolean result = availabilityService.hasCapacity(
                    spaceId, testDate, "18:00", "19:00", 10);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when insufficient capacity")
        void shouldReturnFalseWhenInsufficientCapacity() {
            // Given
            TotalPartySizeResult partySizeResult = mock(TotalPartySizeResult.class);
            when(partySizeResult.getTotalPartySize()).thenReturn(15);

            when(spaceRepository.findActiveById(spaceId)).thenReturn(Optional.of(testSpace));
            when(reservationRepository.sumPartySizeForOverlappingReservations(
                    eq(spaceId), any(), any(), any()))
                    .thenReturn(List.of(partySizeResult));

            // When
            boolean result = availabilityService.hasCapacity(
                    spaceId, testDate, "18:00", "19:00", 10);

            // Then
            assertFalse(result); // 5 available < 10 requested
        }

        @Test
        @DisplayName("Should return true when exactly at capacity")
        void shouldReturnTrueWhenExactlyAtCapacity() {
            // Given
            TotalPartySizeResult partySizeResult = mock(TotalPartySizeResult.class);
            when(partySizeResult.getTotalPartySize()).thenReturn(15);

            when(spaceRepository.findActiveById(spaceId)).thenReturn(Optional.of(testSpace));
            when(reservationRepository.sumPartySizeForOverlappingReservations(
                    eq(spaceId), any(), any(), any()))
                    .thenReturn(List.of(partySizeResult));

            // When
            boolean result = availabilityService.hasCapacity(
                    spaceId, testDate, "18:00", "19:00", 5);

            // Then
            assertTrue(result); // Exactly 5 available = 5 requested
        }
    }

    @Nested
    @DisplayName("getAvailability tests")
    class GetAvailabilityTests {

        @Test
        @DisplayName("Should return availability response with time slots")
        void shouldReturnAvailabilityResponse() {
            // Given
            List<TimeSlotResponse> mockTimeSlots = List.of(
                    TimeSlotResponse.builder()
                            .startTime("18:00")
                            .endTime("19:00")
                            .availableCapacity(15)
                            .status(SlotStatus.AVAILABLE)
                            .build()
            );

            when(spaceRepository.findActiveById(spaceId)).thenReturn(Optional.of(testSpace));
            when(restaurantService.getRestaurantById(any(ObjectId.class)))
                    .thenReturn(Optional.of(testRestaurant));
            when(reservationRepository.findBySpaceIdAndReservationDateAndStatus(
                    eq(spaceId), any(), eq(ReservationStatus.CONFIRMED)))
                    .thenReturn(Collections.emptyList());
            when(timeSlotGenerator.generateTimeSlots(any(), eq(testSpace), any()))
                    .thenReturn(mockTimeSlots);

            // When
            AvailabilityResponse result = availabilityService.getAvailability(spaceId, testDate);

            // Then
            assertNotNull(result);
            assertEquals(spaceId, result.getSpaceId());
            assertEquals("Garden Room", result.getSpaceName());
            assertEquals(20, result.getMaxCapacity());
            assertEquals(testDate, result.getDate());
            assertFalse(result.getTimeSlots().isEmpty());
            assertTrue(result.getIsOpen());
        }

        @Test
        @DisplayName("Should return closed status for closed days")
        void shouldReturnClosedStatusForClosedDays() {
            // Given - Sunday (dayOfWeek = 0) is closed
            LocalDate sunday = testDate.plusDays((7 - testDate.getDayOfWeek().getValue()) % 7);

            when(spaceRepository.findActiveById(spaceId)).thenReturn(Optional.of(testSpace));
            when(restaurantService.getRestaurantById(any(ObjectId.class)))
                    .thenReturn(Optional.of(testRestaurant));
            when(reservationRepository.findBySpaceIdAndReservationDateAndStatus(
                    eq(spaceId), any(), eq(ReservationStatus.CONFIRMED)))
                    .thenReturn(Collections.emptyList());
            when(timeSlotGenerator.generateTimeSlots(any(), eq(testSpace), any()))
                    .thenReturn(Collections.emptyList());

            // When
            AvailabilityResponse result = availabilityService.getAvailability(spaceId, sunday);

            // Then
            assertNotNull(result);
            assertFalse(result.getIsOpen());
            assertEquals("Closed", result.getOperatingHours());
        }

        @Test
        @DisplayName("Should throw exception when space not found")
        void shouldThrowExceptionWhenSpaceNotFound() {
            // Given
            UUID nonExistentSpaceId = UUID.randomUUID();
            when(spaceRepository.findActiveById(nonExistentSpaceId))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThrows(SpaceNotFoundException.class,
                    () -> availabilityService.getAvailability(nonExistentSpaceId, testDate));
        }
    }

    @Nested
    @DisplayName("getOverlappingReservations tests")
    class GetOverlappingReservationsTests {

        @Test
        @DisplayName("Should return overlapping reservations")
        void shouldReturnOverlappingReservations() {
            // Given
            Reservation reservation = Reservation.builder()
                    .id(new ObjectId())
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("18:00")
                    .endTime("19:00")
                    .partySize(8)
                    .status(ReservationStatus.CONFIRMED)
                    .build();

            when(reservationRepository.findOverlappingReservations(
                    eq(spaceId), any(), any(), any()))
                    .thenReturn(List.of(reservation));

            // When
            List<Reservation> result = availabilityService.getOverlappingReservations(
                    spaceId, testDate, "17:30", "18:30");

            // Then
            assertEquals(1, result.size());
            assertEquals(8, result.get(0).getPartySize());
        }

        @Test
        @DisplayName("Should return empty list when no overlapping reservations")
        void shouldReturnEmptyListWhenNoOverlap() {
            // Given
            when(reservationRepository.findOverlappingReservations(
                    eq(spaceId), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            List<Reservation> result = availabilityService.getOverlappingReservations(
                    spaceId, testDate, "18:00", "19:00");

            // Then
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("calculateEndTime tests")
    class CalculateEndTimeTests {

        @Test
        @DisplayName("Should calculate end time correctly")
        void shouldCalculateEndTime() {
            // Given
            when(timeSlotGenerator.calculateEndTimeString("18:00", 60))
                    .thenReturn("19:00");

            // When
            String result = availabilityService.calculateEndTime(testSpace, "18:00");

            // Then
            assertEquals("19:00", result);
            verify(timeSlotGenerator).calculateEndTimeString("18:00", 60);
        }

        @Test
        @DisplayName("Should handle 90 minute slots")
        void shouldHandle90MinuteSlots() {
            // Given
            Space spaceWith90MinSlots = Space.builder()
                    .id(spaceId)
                    .slotDurationMinutes(90)
                    .build();

            when(timeSlotGenerator.calculateEndTimeString("18:00", 90))
                    .thenReturn("19:30");

            // When
            String result = availabilityService.calculateEndTime(spaceWith90MinSlots, "18:00");

            // Then
            assertEquals("19:30", result);
        }
    }
}

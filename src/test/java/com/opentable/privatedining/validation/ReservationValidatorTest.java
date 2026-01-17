package com.opentable.privatedining.validation;

import com.opentable.privatedining.dto.request.CreateReservationRequest;
import com.opentable.privatedining.exception.*;
import com.opentable.privatedining.model.OperatingHours;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReservationValidator Tests")
class ReservationValidatorTest {

    private ReservationValidator validator;
    private Restaurant testRestaurant;
    private Space testSpace;

    @BeforeEach
    void setUp() {
        validator = new ReservationValidator();

        // Create operating hours for all days (9:00 - 22:00), Sunday closed
        List<OperatingHours> operatingHours = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            operatingHours.add(OperatingHours.builder()
                    .dayOfWeek(i)
                    .openTime("09:00")
                    .closeTime("22:00")
                    .isClosed(i == 0) // Sunday (day 0) closed
                    .build());
        }

        testRestaurant = Restaurant.builder()
                .id(new ObjectId())
                .name("Test Restaurant")
                .operatingHours(operatingHours)
                .build();

        testSpace = Space.builder()
                .id(UUID.randomUUID())
                .name("Garden Room")
                .minCapacity(2)
                .maxCapacity(20)
                .slotDurationMinutes(60)
                .bufferMinutes(15)
                .build();
    }

    @Nested
    @DisplayName("validateOperatingHours tests")
    class ValidateOperatingHoursTests {

        @Test
        @DisplayName("Should pass for valid time within operating hours")
        void shouldPassForValidTimeWithinOperatingHours() {
            // Given - Monday (day 1 in our setup)
            LocalDate monday = getNextMonday();
            LocalTime startTime = LocalTime.of(18, 0);
            LocalTime endTime = LocalTime.of(19, 0);

            // When & Then - Should not throw
            assertDoesNotThrow(() ->
                    validator.validateOperatingHours(testRestaurant, monday, startTime, endTime));
        }

        @Test
        @DisplayName("Should throw for closed day (Sunday)")
        void shouldThrowForClosedDay() {
            // Given
            LocalDate sunday = getNextSunday();
            LocalTime startTime = LocalTime.of(18, 0);
            LocalTime endTime = LocalTime.of(19, 0);

            // When & Then
            assertThrows(OutsideOperatingHoursException.class,
                    () -> validator.validateOperatingHours(testRestaurant, sunday, startTime, endTime));
        }

        @Test
        @DisplayName("Should throw when start time is before opening")
        void shouldThrowWhenStartTimeBeforeOpening() {
            // Given
            LocalDate monday = getNextMonday();
            LocalTime startTime = LocalTime.of(8, 0); // Before 9:00 open
            LocalTime endTime = LocalTime.of(9, 0);

            // When & Then
            assertThrows(OutsideOperatingHoursException.class,
                    () -> validator.validateOperatingHours(testRestaurant, monday, startTime, endTime));
        }

        @Test
        @DisplayName("Should throw when end time is after closing")
        void shouldThrowWhenEndTimeAfterClosing() {
            // Given
            LocalDate monday = getNextMonday();
            LocalTime startTime = LocalTime.of(21, 30);
            LocalTime endTime = LocalTime.of(22, 30); // After 22:00 close

            // When & Then
            assertThrows(OutsideOperatingHoursException.class,
                    () -> validator.validateOperatingHours(testRestaurant, monday, startTime, endTime));
        }

        @Test
        @DisplayName("Should pass at exact opening time")
        void shouldPassAtExactOpeningTime() {
            // Given
            LocalDate monday = getNextMonday();
            LocalTime startTime = LocalTime.of(9, 0); // Exact opening
            LocalTime endTime = LocalTime.of(10, 0);

            // When & Then
            assertDoesNotThrow(() ->
                    validator.validateOperatingHours(testRestaurant, monday, startTime, endTime));
        }

        @Test
        @DisplayName("Should pass at exact closing time")
        void shouldPassAtExactClosingTime() {
            // Given
            LocalDate monday = getNextMonday();
            LocalTime startTime = LocalTime.of(21, 0);
            LocalTime endTime = LocalTime.of(22, 0); // Exact closing

            // When & Then
            assertDoesNotThrow(() ->
                    validator.validateOperatingHours(testRestaurant, monday, startTime, endTime));
        }
    }

    @Nested
    @DisplayName("validateTimeSlotAlignment tests")
    class ValidateTimeSlotAlignmentTests {

        @Test
        @DisplayName("Should pass for aligned start time")
        void shouldPassForAlignedStartTime() {
            // Given - 60 minute slots starting at 9:00
            LocalTime openTime = LocalTime.of(9, 0);
            LocalTime startTime = LocalTime.of(10, 0); // 1 hour after opening
            int slotDuration = 60;

            // When & Then
            assertDoesNotThrow(() ->
                    validator.validateTimeSlotAlignment(startTime, openTime, slotDuration));
        }

        @Test
        @DisplayName("Should throw for misaligned start time")
        void shouldThrowForMisalignedStartTime() {
            // Given
            LocalTime openTime = LocalTime.of(9, 0);
            LocalTime startTime = LocalTime.of(10, 30); // 90 minutes after (not aligned to 60 min slots)
            int slotDuration = 60;

            // When & Then
            assertThrows(InvalidTimeSlotException.class,
                    () -> validator.validateTimeSlotAlignment(startTime, openTime, slotDuration));
        }

        @Test
        @DisplayName("Should pass for 30 minute slots")
        void shouldPassFor30MinuteSlots() {
            // Given
            LocalTime openTime = LocalTime.of(9, 0);
            LocalTime startTime = LocalTime.of(10, 30); // 90 minutes after (aligned to 30 min slots)
            int slotDuration = 30;

            // When & Then
            assertDoesNotThrow(() ->
                    validator.validateTimeSlotAlignment(startTime, openTime, slotDuration));
        }

        @Test
        @DisplayName("Should throw when start time is before opening")
        void shouldThrowWhenStartTimeBeforeOpening() {
            // Given
            LocalTime openTime = LocalTime.of(9, 0);
            LocalTime startTime = LocalTime.of(8, 0); // Before opening
            int slotDuration = 60;

            // When & Then
            assertThrows(InvalidTimeSlotException.class,
                    () -> validator.validateTimeSlotAlignment(startTime, openTime, slotDuration));
        }

        @ParameterizedTest
        @ValueSource(ints = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21})
        @DisplayName("Should pass for all valid hourly slots")
        void shouldPassForAllValidHourlySlots(int hour) {
            // Given
            LocalTime openTime = LocalTime.of(9, 0);
            LocalTime startTime = LocalTime.of(hour, 0);
            int slotDuration = 60;

            // When & Then
            assertDoesNotThrow(() ->
                    validator.validateTimeSlotAlignment(startTime, openTime, slotDuration));
        }
    }

    @Nested
    @DisplayName("validatePartySize tests")
    class ValidatePartySizeTests {

        @Test
        @DisplayName("Should pass for valid party size")
        void shouldPassForValidPartySize() {
            // Given
            int partySize = 10;

            // When & Then
            assertDoesNotThrow(() -> validator.validatePartySize(testSpace, partySize));
        }

        @Test
        @DisplayName("Should pass for minimum party size (1)")
        void shouldPassForMinimumPartySize() {
            // Given
            int partySize = 1;

            // When & Then - Min capacity is informational, party of 1 should be allowed
            assertDoesNotThrow(() -> validator.validatePartySize(testSpace, partySize));
        }

        @Test
        @DisplayName("Should pass for maximum capacity")
        void shouldPassForMaximumCapacity() {
            // Given
            int partySize = 20; // Exactly max capacity

            // When & Then
            assertDoesNotThrow(() -> validator.validatePartySize(testSpace, partySize));
        }

        @Test
        @DisplayName("Should throw for party size exceeding max capacity")
        void shouldThrowForPartySizeExceedingMaxCapacity() {
            // Given
            int partySize = 21; // Exceeds max of 20

            // When & Then
            InvalidPartySizeException exception = assertThrows(
                    InvalidPartySizeException.class,
                    () -> validator.validatePartySize(testSpace, partySize));

            assertTrue(exception.getMessage().contains("20"));
        }

        @Test
        @DisplayName("Should throw for zero party size")
        void shouldThrowForZeroPartySize() {
            // Given
            int partySize = 0;

            // When & Then
            assertThrows(InvalidPartySizeException.class,
                    () -> validator.validatePartySize(testSpace, partySize));
        }

        @Test
        @DisplayName("Should throw for negative party size")
        void shouldThrowForNegativePartySize() {
            // Given
            int partySize = -1;

            // When & Then
            assertThrows(InvalidPartySizeException.class,
                    () -> validator.validatePartySize(testSpace, partySize));
        }
    }

    @Nested
    @DisplayName("validateAdvanceBookingLimit tests")
    class ValidateAdvanceBookingLimitTests {

        @Test
        @DisplayName("Should pass for date within 90 days")
        void shouldPassForDateWithin90Days() {
            // Given
            LocalDate validDate = LocalDate.now().plusDays(30);

            // When & Then
            assertDoesNotThrow(() -> validator.validateAdvanceBookingLimit(validDate));
        }

        @Test
        @DisplayName("Should pass for date exactly at 90 days")
        void shouldPassForDateExactlyAt90Days() {
            // Given
            LocalDate validDate = LocalDate.now().plusDays(90);

            // When & Then
            assertDoesNotThrow(() -> validator.validateAdvanceBookingLimit(validDate));
        }

        @Test
        @DisplayName("Should throw for date beyond 90 days")
        void shouldThrowForDateBeyond90Days() {
            // Given
            LocalDate tooFarDate = LocalDate.now().plusDays(91);

            // When & Then
            assertThrows(AdvanceBookingLimitException.class,
                    () -> validator.validateAdvanceBookingLimit(tooFarDate));
        }

        @Test
        @DisplayName("Should use custom limit when provided")
        void shouldUseCustomLimitWhenProvided() {
            // Given
            LocalDate date = LocalDate.now().plusDays(31);
            int customLimit = 30;

            // When & Then
            assertThrows(AdvanceBookingLimitException.class,
                    () -> validator.validateAdvanceBookingLimit(date, customLimit));
        }
    }

    @Nested
    @DisplayName("validateReservationDate tests")
    class ValidateReservationDateTests {

        @Test
        @DisplayName("Should pass for today")
        void shouldPassForToday() {
            // Given
            LocalDate today = LocalDate.now();

            // When & Then
            assertDoesNotThrow(() -> validator.validateReservationDate(today));
        }

        @Test
        @DisplayName("Should pass for future date")
        void shouldPassForFutureDate() {
            // Given
            LocalDate futureDate = LocalDate.now().plusDays(7);

            // When & Then
            assertDoesNotThrow(() -> validator.validateReservationDate(futureDate));
        }

        @Test
        @DisplayName("Should throw for past date")
        void shouldThrowForPastDate() {
            // Given
            LocalDate pastDate = LocalDate.now().minusDays(1);

            // When & Then
            ReservationException exception = assertThrows(
                    ReservationException.class,
                    () -> validator.validateReservationDate(pastDate));

            assertTrue(exception.getMessage().contains("future"));
        }
    }

    @Nested
    @DisplayName("validateCapacity tests")
    class ValidateCapacityTests {

        @Test
        @DisplayName("Should pass when capacity available")
        void shouldPassWhenCapacityAvailable() {
            // Given
            int currentlyBooked = 10;
            int requestedPartySize = 5;

            // When & Then
            assertDoesNotThrow(() ->
                    validator.validateCapacity(testSpace, currentlyBooked, requestedPartySize));
        }

        @Test
        @DisplayName("Should pass when exactly at capacity")
        void shouldPassWhenExactlyAtCapacity() {
            // Given
            int currentlyBooked = 15;
            int requestedPartySize = 5;

            // When & Then
            assertDoesNotThrow(() ->
                    validator.validateCapacity(testSpace, currentlyBooked, requestedPartySize));
        }

        @Test
        @DisplayName("Should throw when would exceed capacity")
        void shouldThrowWhenWouldExceedCapacity() {
            // Given
            int currentlyBooked = 16;
            int requestedPartySize = 5; // Would total 21, exceeds 20

            // When & Then
            CapacityExceededException exception = assertThrows(
                    CapacityExceededException.class,
                    () -> validator.validateCapacity(testSpace, currentlyBooked, requestedPartySize));

            assertEquals("Garden Room", exception.getSpaceName());
            assertEquals(20, exception.getMaxCapacity());
            assertEquals(4, exception.getAvailableCapacity());
            assertEquals(5, exception.getRequestedPartySize());
        }
    }

    @Nested
    @DisplayName("validateReservationRequest tests")
    class ValidateReservationRequestTests {

        @Test
        @DisplayName("Should pass for valid complete request")
        void shouldPassForValidCompleteRequest() {
            // Given
            LocalDate monday = getNextMonday();
            CreateReservationRequest request = CreateReservationRequest.builder()
                    .spaceId(testSpace.getId())
                    .reservationDate(monday)
                    .startTime("18:00")
                    .partySize(8)
                    .customerName("John Doe")
                    .customerEmail("john@example.com")
                    .build();
            LocalTime endTime = LocalTime.of(19, 0);

            // When & Then
            assertDoesNotThrow(() ->
                    validator.validateReservationRequest(request, testRestaurant, testSpace, endTime));
        }

        @Test
        @DisplayName("Should throw for past date in request")
        void shouldThrowForPastDateInRequest() {
            // Given
            CreateReservationRequest request = CreateReservationRequest.builder()
                    .reservationDate(LocalDate.now().minusDays(1))
                    .startTime("18:00")
                    .partySize(8)
                    .build();
            LocalTime endTime = LocalTime.of(19, 0);

            // When & Then
            assertThrows(ReservationException.class,
                    () -> validator.validateReservationRequest(request, testRestaurant, testSpace, endTime));
        }

        @Test
        @DisplayName("Should throw for date too far in advance")
        void shouldThrowForDateTooFarInAdvance() {
            // Given
            CreateReservationRequest request = CreateReservationRequest.builder()
                    .reservationDate(LocalDate.now().plusDays(100))
                    .startTime("18:00")
                    .partySize(8)
                    .build();
            LocalTime endTime = LocalTime.of(19, 0);

            // When & Then
            assertThrows(AdvanceBookingLimitException.class,
                    () -> validator.validateReservationRequest(request, testRestaurant, testSpace, endTime));
        }
    }

    // Helper methods
    private LocalDate getNextMonday() {
        LocalDate today = LocalDate.now();
        int daysUntilMonday = (8 - today.getDayOfWeek().getValue()) % 7;
        if (daysUntilMonday == 0) daysUntilMonday = 7;
        return today.plusDays(daysUntilMonday);
    }

    private LocalDate getNextSunday() {
        LocalDate today = LocalDate.now();
        int daysUntilSunday = 7 - today.getDayOfWeek().getValue();
        if (daysUntilSunday == 0) daysUntilSunday = 7;
        return today.plusDays(daysUntilSunday);
    }
}

package com.opentable.privatedining.validation;

import com.opentable.privatedining.dto.request.CreateReservationRequest;
import com.opentable.privatedining.exception.*;
import com.opentable.privatedining.model.OperatingHours;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Validator for reservation business rules.
 */
@Component
public class ReservationValidator {

    private static final int DEFAULT_ADVANCE_BOOKING_DAYS = 90;

    /**
     * Validate operating hours for a reservation.
     *
     * @param restaurant The restaurant
     * @param date The reservation date
     * @param startTime The start time
     * @param endTime The end time
     * @throws OutsideOperatingHoursException if the reservation is outside operating hours
     */
    public void validateOperatingHours(Restaurant restaurant, LocalDate date,
                                       LocalTime startTime, LocalTime endTime) {
        // Get day of week (0 = Sunday, 6 = Saturday for compatibility)
        int dayOfWeek = date.getDayOfWeek().getValue() % 7;

        OperatingHours hours = restaurant.getOperatingHoursForDay(dayOfWeek);

        // Check if closed
        if (hours == null || Boolean.TRUE.equals(hours.getIsClosed())) {
            throw new OutsideOperatingHoursException(date);
        }

        LocalTime openTime = hours.getOpenTimeAsLocalTime();
        LocalTime closeTime = hours.getCloseTimeAsLocalTime();

        if (openTime == null || closeTime == null) {
            throw new OutsideOperatingHoursException(date);
        }

        // Check if reservation times are within operating hours
        // Also detect if endTime crosses midnight (endTime < startTime means it's the next day)
        boolean endTimeCrossedMidnight = endTime.isBefore(startTime) || endTime.equals(LocalTime.MIDNIGHT);
        if (startTime.isBefore(openTime) || endTime.isAfter(closeTime) || endTimeCrossedMidnight) {
            throw new OutsideOperatingHoursException(date, startTime, endTime, openTime, closeTime);
        }
    }

    /**
     * Validate time slot alignment using modular arithmetic relative to opening time.
     *
     * ALGORITHM: Time Slot Boundary Alignment Validation
     * --------------------------------------------------
     * This ensures reservations start at valid slot boundaries relative to opening time,
     * preventing slot fragmentation and maintaining consistent spacing.
     *
     * Formula: (startTime - openTime) % slotDuration == 0
     *
     * Example with 90-minute slots, opening at 18:00:
     *   Valid times:   18:00 (0 min), 19:30 (90 min), 21:00 (180 min), 22:30 (270 min)
     *   Invalid times: 18:15 (15 min), 19:45 (105 min), 20:00 (120 min)
     *   Calculation for 19:30: (1170 - 1080) % 90 = 90 % 90 = 0 ✓
     *   Calculation for 19:45: (1185 - 1080) % 90 = 105 % 90 = 15 ✗
     *
     * Example with 30-minute slots, opening at 17:00:
     *   Valid times:   17:00, 17:30, 18:00, 18:30, 19:00, etc.
     *   Invalid times: 17:15, 17:45, 18:10, 18:25, etc.
     *
     * Why This Matters:
     * Without alignment, users could book overlapping misaligned slots like 18:15-19:45
     * and 19:30-21:00, causing management complexity and capacity calculation errors.
     *
     * @param startTime The requested start time
     * @param openTime The restaurant opening time (slot boundary origin)
     * @param slotDurationMinutes The slot duration in minutes
     * @throws InvalidTimeSlotException if the start time doesn't align to valid boundaries
     */
    public void validateTimeSlotAlignment(LocalTime startTime, LocalTime openTime, int slotDurationMinutes) {
        // Convert both times to total minutes since midnight for arithmetic
        int startMinutes = startTime.getHour() * 60 + startTime.getMinute();
        int openMinutes = openTime.getHour() * 60 + openTime.getMinute();
        int minutesFromOpen = startMinutes - openMinutes;

        // Check: 1) Start time is at or after opening, 2) Aligns to slot boundary
        if (minutesFromOpen < 0 || minutesFromOpen % slotDurationMinutes != 0) {
            throw new InvalidTimeSlotException(startTime, slotDurationMinutes);
        }
    }

    /**
     * Validate party size against space capacity.
     *
     * @param space The space
     * @param partySize The requested party size
     * @throws InvalidPartySizeException if party size exceeds capacity
     */
    public void validatePartySize(Space space, int partySize) {
        if (partySize < 1) {
            throw new InvalidPartySizeException("Party size must be at least 1");
        }

        // Note: We only enforce max capacity, not min capacity (per CLAUDE.md spec)
        if (partySize > space.getMaxCapacity()) {
            throw new InvalidPartySizeException(partySize, space.getMinCapacity(), space.getMaxCapacity());
        }
    }

    /**
     * Validate the reservation date is not too far in the future.
     *
     * @param reservationDate The reservation date
     * @throws AdvanceBookingLimitException if the date is too far in advance
     */
    public void validateAdvanceBookingLimit(LocalDate reservationDate) {
        validateAdvanceBookingLimit(reservationDate, DEFAULT_ADVANCE_BOOKING_DAYS);
    }

    /**
     * Validate the reservation date is not too far in the future.
     *
     * @param reservationDate The reservation date
     * @param maxAdvanceDays Maximum days in advance
     * @throws AdvanceBookingLimitException if the date is too far in advance
     */
    public void validateAdvanceBookingLimit(LocalDate reservationDate, int maxAdvanceDays) {
        LocalDate maxDate = LocalDate.now().plusDays(maxAdvanceDays);
        if (reservationDate.isAfter(maxDate)) {
            throw new AdvanceBookingLimitException(reservationDate, maxAdvanceDays);
        }
    }

    /**
     * Validate the reservation date is today or in the future.
     *
     * @param reservationDate The reservation date
     * @throws ReservationException if the date is in the past
     */
    public void validateReservationDate(LocalDate reservationDate) {
        if (reservationDate.isBefore(LocalDate.now())) {
            throw new ReservationException("Reservation date must be today or in the future",
                    "PAST_DATE");
        }
    }

    /**
     * Validate capacity check for flexible booking model.
     * Throws CapacityExceededException if adding the party would exceed space capacity.
     *
     * @param space The space
     * @param currentlyBooked Current sum of party sizes in overlapping reservations
     * @param requestedPartySize The new party size to add
     * @throws CapacityExceededException if capacity would be exceeded
     */
    public void validateCapacity(Space space, int currentlyBooked, int requestedPartySize) {
        int availableCapacity = space.getMaxCapacity() - currentlyBooked;

        if (requestedPartySize > availableCapacity) {
            throw new CapacityExceededException(
                    space.getName(),
                    space.getMaxCapacity(),
                    availableCapacity,
                    requestedPartySize
            );
        }
    }

    /**
     * Validate a complete reservation request.
     *
     * @param request The reservation request
     * @param restaurant The restaurant
     * @param space The space
     * @param endTime The calculated end time
     */
    public void validateReservationRequest(CreateReservationRequest request,
                                           Restaurant restaurant, Space space,
                                           LocalTime endTime) {
        LocalTime startTime = LocalTime.parse(request.getStartTime());

        // 1. Validate date is not in the past
        validateReservationDate(request.getReservationDate());

        // 2. Validate not too far in advance
        validateAdvanceBookingLimit(request.getReservationDate());

        // 3. Get operating hours and validate
        int dayOfWeek = request.getReservationDate().getDayOfWeek().getValue() % 7;
        var hours = restaurant.getOperatingHoursForDay(dayOfWeek);

        validateOperatingHours(restaurant, request.getReservationDate(), startTime, endTime);

        // 4. Validate time slot alignment (relative to opening time)
        LocalTime openTime = hours != null ? hours.getOpenTimeAsLocalTime() : LocalTime.of(9, 0);
        validateTimeSlotAlignment(startTime, openTime, space.getSlotDurationMinutes());

        // 5. Validate party size against space max capacity
        validatePartySize(space, request.getPartySize());
    }
}

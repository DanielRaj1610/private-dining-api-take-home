package com.opentable.privatedining.util;

import com.opentable.privatedining.dto.response.TimeSlotResponse;
import com.opentable.privatedining.model.OperatingHours;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.model.enums.SlotStatus;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for generating time slots for availability queries.
 */
@Component
public class TimeSlotGenerator {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Generate all possible time slots for a space based on operating hours.
     *
     * ALGORITHM: Time Slot Generation with Interval Overlap Detection
     * ---------------------------------------------------------------
     * Complexity: O(n * m) where n = number of slots, m = number of reservations
     * For each time slot, we check all existing reservations for overlap.
     *
     * Example:
     *   Operating Hours: 18:00 - 23:00
     *   Slot Duration: 90 minutes
     *   Buffer: 0 minutes
     *   Generated Slots:
     *     1. 18:00 - 19:30
     *     2. 19:30 - 21:00
     *     3. 21:00 - 22:30
     *     (22:30 + 90min = 00:00 next day, stop)
     *
     * @param operatingHours Operating hours for the day
     * @param space The space with slot configuration
     * @param existingReservations Existing reservations for the time period
     * @return List of time slots with availability information
     */
    public List<TimeSlotResponse> generateTimeSlots(OperatingHours operatingHours,
                                                    Space space,
                                                    List<Reservation> existingReservations) {
        List<TimeSlotResponse> slots = new ArrayList<>();

        if (operatingHours == null || Boolean.TRUE.equals(operatingHours.getIsClosed())) {
            return slots;  // Restaurant closed on this day
        }

        LocalTime openTime = operatingHours.getOpenTimeAsLocalTime();
        LocalTime closeTime = operatingHours.getCloseTimeAsLocalTime();

        if (openTime == null || closeTime == null) {
            return slots;  // Invalid operating hours
        }

        int slotDuration = space.getSlotDurationMinutes();
        int bufferMinutes = space.getBufferMinutes() != null ? space.getBufferMinutes() : 0;
        int maxCapacity = space.getMaxCapacity();

        LocalTime currentSlotStart = openTime;

        // Generate slots while:
        // 1. The slot end time doesn't exceed close time
        // 2. The slot start is still before close time (prevent wraparound issues)
        while (true) {
            LocalTime slotEnd = currentSlotStart.plusMinutes(slotDuration);

            // EDGE CASE: Midnight wraparound detection
            // If slotEnd < currentSlotStart, LocalTime wrapped to next day (e.g., 23:30 + 60min = 00:30)
            // This prevents generating invalid slots that cross midnight
            boolean wrapsAroundMidnight = slotEnd.isBefore(currentSlotStart);

            // Stop if: slot end is after close time OR we wrapped around midnight
            if (wrapsAroundMidnight || slotEnd.isAfter(closeTime)) {
                break;  // No more valid slots fit within operating hours
            }

            // Calculate booked capacity for this slot (sum of all overlapping reservation party sizes)
            int bookedCapacity = calculateBookedCapacity(existingReservations,
                    currentSlotStart, slotEnd);
            int existingReservationCount = countOverlappingReservations(existingReservations,
                    currentSlotStart, slotEnd);
            int availableCapacity = maxCapacity - bookedCapacity;

            // Determine slot status
            SlotStatus status = determineSlotStatus(availableCapacity, maxCapacity);

            TimeSlotResponse slot = TimeSlotResponse.builder()
                    .startTime(currentSlotStart.format(TIME_FORMATTER))
                    .endTime(slotEnd.format(TIME_FORMATTER))
                    .availableCapacity(availableCapacity)
                    .bookedCapacity(bookedCapacity)
                    .status(status)
                    .existingReservations(existingReservationCount)
                    .build();

            slots.add(slot);

            // Move to next slot (slot duration + buffer)
            currentSlotStart = currentSlotStart.plusMinutes(slotDuration);
        }

        return slots;
    }

    /**
     * Calculate the total booked capacity for a time slot.
     */
    private int calculateBookedCapacity(List<Reservation> reservations,
                                        LocalTime slotStart, LocalTime slotEnd) {
        return reservations.stream()
                .filter(r -> isOverlapping(r, slotStart, slotEnd))
                .mapToInt(r -> r.getPartySize() != null ? r.getPartySize() : 0)
                .sum();
    }

    /**
     * Count the number of overlapping reservations.
     */
    private int countOverlappingReservations(List<Reservation> reservations,
                                             LocalTime slotStart, LocalTime slotEnd) {
        return (int) reservations.stream()
                .filter(r -> isOverlapping(r, slotStart, slotEnd))
                .count();
    }

    /**
     * Check if a reservation overlaps with a time slot using interval intersection formula.
     *
     * ALGORITHM: Interval Intersection Detection
     * ------------------------------------------
     * Formula: resStart < slotEnd AND resEnd > slotStart
     *
     * Explanation: Two time intervals overlap if they share any moment in time.
     * The formula works by checking if the reservation starts before the slot ends
     * AND if the reservation ends after the slot starts.
     *
     * Overlap Cases:
     * 1. Reservation starts before slot and ends during: (17:30-18:45) overlaps (18:00-19:30)
     * 2. Reservation starts during slot and ends after: (19:00-20:00) overlaps (18:00-19:30)
     * 3. Reservation completely contains slot: (17:00-21:00) overlaps (18:00-19:30)
     * 4. Slot completely contains reservation: (18:00-19:30) overlaps (18:30-19:00)
     *
     * Non-Overlap Cases:
     * 1. Reservation ends before slot starts: (17:00-17:30) does NOT overlap (18:00-19:30)
     * 2. Reservation starts after slot ends: (20:00-21:00) does NOT overlap (18:00-19:30)
     * 3. Touching endpoints (same time): (17:00-18:00) does NOT overlap (18:00-19:30)
     *    because isBefore(18:00, 18:00) = false
     *
     * @param reservation The reservation to check
     * @param slotStart Slot start time
     * @param slotEnd Slot end time
     * @return true if reservation overlaps with the slot
     */
    private boolean isOverlapping(Reservation reservation, LocalTime slotStart, LocalTime slotEnd) {
        LocalTime resStart = reservation.getStartTimeAsLocalTime();
        LocalTime resEnd = reservation.getEndTimeAsLocalTime();

        if (resStart == null || resEnd == null) {
            return false;
        }

        // Classic interval intersection: overlap if reservation starts before slot ends
        // AND reservation ends after slot starts
        return resStart.isBefore(slotEnd) && resEnd.isAfter(slotStart);
    }

    /**
     * Determine the slot status based on available capacity.
     */
    private SlotStatus determineSlotStatus(int availableCapacity, int maxCapacity) {
        if (availableCapacity <= 0) {
            return SlotStatus.FULL;
        } else if (availableCapacity < maxCapacity * 0.25) {
            return SlotStatus.LIMITED;
        } else {
            return SlotStatus.AVAILABLE;
        }
    }

    /**
     * Calculate end time for a reservation.
     *
     * @param startTime Start time string (HH:mm)
     * @param slotDurationMinutes Duration in minutes
     * @return End time as LocalTime
     */
    public LocalTime calculateEndTime(String startTime, int slotDurationMinutes) {
        LocalTime start = LocalTime.parse(startTime, TIME_FORMATTER);
        return start.plusMinutes(slotDurationMinutes);
    }

    /**
     * Calculate end time string for a reservation.
     *
     * @param startTime Start time string (HH:mm)
     * @param slotDurationMinutes Duration in minutes
     * @return End time string (HH:mm)
     */
    public String calculateEndTimeString(String startTime, int slotDurationMinutes) {
        return calculateEndTime(startTime, slotDurationMinutes).format(TIME_FORMATTER);
    }
}

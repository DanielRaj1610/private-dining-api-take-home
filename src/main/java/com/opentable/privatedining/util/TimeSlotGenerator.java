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
            return slots;
        }

        LocalTime openTime = operatingHours.getOpenTimeAsLocalTime();
        LocalTime closeTime = operatingHours.getCloseTimeAsLocalTime();

        if (openTime == null || closeTime == null) {
            return slots;
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

            // Check if slot end exceeds close time or wraps around midnight
            // If slotEnd is before currentSlotStart, it means we wrapped around midnight
            boolean wrapsAroundMidnight = slotEnd.isBefore(currentSlotStart);

            // Stop if: slot end is after close time OR we wrapped around midnight
            if (wrapsAroundMidnight || slotEnd.isAfter(closeTime)) {
                break;
            }

            // Calculate booked capacity for this slot
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
     * Check if a reservation overlaps with a time slot.
     * Overlap occurs when: resStart < slotEnd AND resEnd > slotStart
     */
    private boolean isOverlapping(Reservation reservation, LocalTime slotStart, LocalTime slotEnd) {
        LocalTime resStart = reservation.getStartTimeAsLocalTime();
        LocalTime resEnd = reservation.getEndTimeAsLocalTime();

        if (resStart == null || resEnd == null) {
            return false;
        }

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

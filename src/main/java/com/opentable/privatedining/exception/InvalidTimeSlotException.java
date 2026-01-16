package com.opentable.privatedining.exception;

import java.time.LocalTime;

/**
 * Exception thrown when a reservation start time doesn't align with valid slot boundaries.
 */
public class InvalidTimeSlotException extends RuntimeException {

    private final LocalTime requestedTime;
    private final int slotDurationMinutes;

    public InvalidTimeSlotException(LocalTime requestedTime, int slotDurationMinutes) {
        super(String.format(
                "Start time %s must align to slot boundaries. " +
                "Valid start times are at %d-minute intervals (e.g., on the hour%s).",
                requestedTime, slotDurationMinutes,
                slotDurationMinutes == 30 ? " or half-hour" : ""));
        this.requestedTime = requestedTime;
        this.slotDurationMinutes = slotDurationMinutes;
    }

    public InvalidTimeSlotException(String message) {
        super(message);
        this.requestedTime = null;
        this.slotDurationMinutes = 0;
    }

    public LocalTime getRequestedTime() {
        return requestedTime;
    }

    public int getSlotDurationMinutes() {
        return slotDurationMinutes;
    }
}

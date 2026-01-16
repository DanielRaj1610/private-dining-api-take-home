package com.opentable.privatedining.exception;

import java.time.LocalDate;

/**
 * Exception thrown when a reservation is attempted too far in advance.
 */
public class AdvanceBookingLimitException extends RuntimeException {

    private static final int DEFAULT_ADVANCE_DAYS = 90;

    private final LocalDate requestedDate;
    private final int maxAdvanceDays;

    public AdvanceBookingLimitException(LocalDate requestedDate, int maxAdvanceDays) {
        super(String.format(
                "Reservations cannot be made more than %d days in advance. " +
                "Requested date %s is too far in the future.",
                maxAdvanceDays, requestedDate));
        this.requestedDate = requestedDate;
        this.maxAdvanceDays = maxAdvanceDays;
    }

    public AdvanceBookingLimitException(LocalDate requestedDate) {
        this(requestedDate, DEFAULT_ADVANCE_DAYS);
    }

    public AdvanceBookingLimitException(String message) {
        super(message);
        this.requestedDate = null;
        this.maxAdvanceDays = DEFAULT_ADVANCE_DAYS;
    }

    public LocalDate getRequestedDate() {
        return requestedDate;
    }

    public int getMaxAdvanceDays() {
        return maxAdvanceDays;
    }
}

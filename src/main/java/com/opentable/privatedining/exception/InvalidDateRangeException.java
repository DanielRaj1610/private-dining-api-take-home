package com.opentable.privatedining.exception;

import java.time.LocalDate;

/**
 * Exception thrown when a date range is invalid.
 */
public class InvalidDateRangeException extends RuntimeException {

    private final LocalDate startDate;
    private final LocalDate endDate;

    public InvalidDateRangeException(LocalDate startDate, LocalDate endDate) {
        super(String.format(
                "Invalid date range: start date %s must be before or equal to end date %s.",
                startDate, endDate));
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public InvalidDateRangeException(String message) {
        super(message);
        this.startDate = null;
        this.endDate = null;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}

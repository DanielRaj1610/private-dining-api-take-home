package com.opentable.privatedining.exception;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Exception thrown when a reservation is attempted outside operating hours.
 */
public class OutsideOperatingHoursException extends RuntimeException {

    private final LocalDate date;
    private final LocalTime requestedStartTime;
    private final LocalTime requestedEndTime;
    private final LocalTime operatingStart;
    private final LocalTime operatingEnd;
    private final boolean isClosed;

    public OutsideOperatingHoursException(LocalDate date, LocalTime requestedStartTime,
                                          LocalTime requestedEndTime, LocalTime operatingStart,
                                          LocalTime operatingEnd) {
        super(String.format(
                "Reservation time %s-%s is outside operating hours. Restaurant operates from %s to %s on %s.",
                requestedStartTime, requestedEndTime, operatingStart, operatingEnd,
                date.getDayOfWeek().toString()));
        this.date = date;
        this.requestedStartTime = requestedStartTime;
        this.requestedEndTime = requestedEndTime;
        this.operatingStart = operatingStart;
        this.operatingEnd = operatingEnd;
        this.isClosed = false;
    }

    public OutsideOperatingHoursException(LocalDate date) {
        super(String.format("Restaurant is closed on %s (%s).", date, date.getDayOfWeek().toString()));
        this.date = date;
        this.requestedStartTime = null;
        this.requestedEndTime = null;
        this.operatingStart = null;
        this.operatingEnd = null;
        this.isClosed = true;
    }

    public OutsideOperatingHoursException(String message) {
        super(message);
        this.date = null;
        this.requestedStartTime = null;
        this.requestedEndTime = null;
        this.operatingStart = null;
        this.operatingEnd = null;
        this.isClosed = false;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getRequestedStartTime() {
        return requestedStartTime;
    }

    public LocalTime getRequestedEndTime() {
        return requestedEndTime;
    }

    public LocalTime getOperatingStart() {
        return operatingStart;
    }

    public LocalTime getOperatingEnd() {
        return operatingEnd;
    }

    public boolean isClosed() {
        return isClosed;
    }
}

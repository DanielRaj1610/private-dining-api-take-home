package com.opentable.privatedining.exception;

/**
 * General exception for reservation-related errors.
 */
public class ReservationException extends RuntimeException {

    private final String errorCode;

    public ReservationException(String message) {
        super(message);
        this.errorCode = "RESERVATION_ERROR";
    }

    public ReservationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ReservationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "RESERVATION_ERROR";
    }

    public String getErrorCode() {
        return errorCode;
    }
}

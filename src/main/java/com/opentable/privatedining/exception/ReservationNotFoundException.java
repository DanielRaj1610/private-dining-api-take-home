package com.opentable.privatedining.exception;

import org.bson.types.ObjectId;

public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(ObjectId reservationId) {
        super("Reservation not found with ID: " + reservationId.toString());
    }

    public ReservationNotFoundException(String message) {
        super(message);
    }
}
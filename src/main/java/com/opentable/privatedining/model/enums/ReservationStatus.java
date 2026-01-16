package com.opentable.privatedining.model.enums;

/**
 * Status of a reservation throughout its lifecycle.
 */
public enum ReservationStatus {
    /**
     * Reservation is confirmed and active.
     */
    CONFIRMED,

    /**
     * Reservation has been cancelled by customer or staff.
     */
    CANCELLED,

    /**
     * Reservation has been completed (customer showed up and finished).
     */
    COMPLETED,

    /**
     * Customer did not show up for the reservation.
     */
    NO_SHOW
}

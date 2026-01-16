package com.opentable.privatedining.model.enums;

/**
 * Availability status for a time slot.
 */
public enum SlotStatus {
    /**
     * Slot has full capacity available.
     */
    AVAILABLE,

    /**
     * Slot has limited capacity (50% or more used).
     */
    LIMITED,

    /**
     * Slot is fully booked.
     */
    FULL
}

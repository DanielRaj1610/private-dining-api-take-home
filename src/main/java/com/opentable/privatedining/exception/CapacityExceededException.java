package com.opentable.privatedining.exception;

/**
 * Exception thrown when a reservation would exceed the space's capacity.
 * This is used for the flexible capacity model where concurrent reservations are allowed
 * but total party size cannot exceed max capacity.
 */
public class CapacityExceededException extends RuntimeException {

    private final String spaceName;
    private final int maxCapacity;
    private final int currentlyBooked;
    private final int availableCapacity;
    private final int requestedPartySize;

    public CapacityExceededException(String spaceName, int maxCapacity,
                                     int availableCapacity, int requestedPartySize) {
        super(String.format(
                "Insufficient capacity. Space '%s' has %d max capacity. " +
                "Current bookings: %d guests. Your party of %d would exceed capacity. " +
                "Available capacity: %d.",
                spaceName, maxCapacity, maxCapacity - availableCapacity,
                requestedPartySize, availableCapacity));
        this.spaceName = spaceName;
        this.maxCapacity = maxCapacity;
        this.currentlyBooked = maxCapacity - availableCapacity;
        this.availableCapacity = availableCapacity;
        this.requestedPartySize = requestedPartySize;
    }

    public CapacityExceededException(String message) {
        super(message);
        this.spaceName = null;
        this.maxCapacity = 0;
        this.currentlyBooked = 0;
        this.availableCapacity = 0;
        this.requestedPartySize = 0;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public int getCurrentlyBooked() {
        return currentlyBooked;
    }

    public int getAvailableCapacity() {
        return availableCapacity;
    }

    public int getRequestedPartySize() {
        return requestedPartySize;
    }
}

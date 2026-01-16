package com.opentable.privatedining.exception;

/**
 * Exception thrown when optimistic locking fails due to concurrent modifications.
 * This typically happens when another transaction modified the same reservation.
 */
public class ConcurrentModificationException extends RuntimeException {

    private final String entityType;
    private final String entityId;
    private final int retryCount;

    public ConcurrentModificationException(String entityType, String entityId, int retryCount) {
        super(String.format(
                "Concurrent modification detected for %s with ID %s after %d retry attempts. " +
                "Please try your request again.",
                entityType, entityId, retryCount));
        this.entityType = entityType;
        this.entityId = entityId;
        this.retryCount = retryCount;
    }

    public ConcurrentModificationException(String message) {
        super(message);
        this.entityType = null;
        this.entityId = null;
        this.retryCount = 0;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public int getRetryCount() {
        return retryCount;
    }
}

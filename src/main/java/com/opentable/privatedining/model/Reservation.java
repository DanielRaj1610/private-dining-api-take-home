package com.opentable.privatedining.model;

import com.opentable.privatedining.model.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Reservation for a private dining space.
 * Uses @Version for optimistic locking to handle concurrent bookings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reservations")
public class Reservation {

    @Id
    private ObjectId id;

    /**
     * Reference to the restaurant.
     */
    private ObjectId restaurantId;

    /**
     * Reference to the space (UUID).
     */
    private UUID spaceId;

    /**
     * Date of the reservation.
     */
    private LocalDate reservationDate;

    /**
     * Start time in HH:mm format.
     */
    private String startTime;

    /**
     * End time in HH:mm format (calculated from start + slot duration).
     */
    private String endTime;

    /**
     * Number of guests.
     */
    private Integer partySize;

    /**
     * Customer's full name.
     */
    private String customerName;

    /**
     * Customer's email address.
     */
    private String customerEmail;

    /**
     * Customer's phone number (optional).
     */
    private String customerPhone;

    /**
     * Current status of the reservation.
     */
    @Builder.Default
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    /**
     * Special requests or notes from the customer.
     */
    private String specialRequests;

    /**
     * Reason for cancellation (if cancelled).
     */
    private String cancellationReason;

    /**
     * Timestamp when the reservation was cancelled.
     */
    private LocalDateTime cancelledAt;

    /**
     * Version for optimistic locking.
     * MongoDB will automatically increment this on each save.
     */
    @Version
    private Long version;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Legacy constructor for backward compatibility.
     */
    public Reservation(ObjectId restaurantId, UUID spaceId, String customerEmail,
                       LocalDateTime startTime, LocalDateTime endTime,
                       Integer partySize, String status) {
        this.restaurantId = restaurantId;
        this.spaceId = spaceId;
        this.customerEmail = customerEmail;
        this.reservationDate = startTime.toLocalDate();
        this.startTime = startTime.toLocalTime().toString();
        this.endTime = endTime.toLocalTime().toString();
        this.partySize = partySize;
        this.status = ReservationStatus.valueOf(status);
    }

    /**
     * Get start time as LocalTime.
     */
    public LocalTime getStartTimeAsLocalTime() {
        return startTime != null ? LocalTime.parse(startTime) : null;
    }

    /**
     * Get end time as LocalTime.
     */
    public LocalTime getEndTimeAsLocalTime() {
        return endTime != null ? LocalTime.parse(endTime) : null;
    }

    /**
     * Get start as LocalDateTime (combines date and time).
     */
    public LocalDateTime getStartDateTime() {
        if (reservationDate != null && startTime != null) {
            return LocalDateTime.of(reservationDate, LocalTime.parse(startTime));
        }
        return null;
    }

    /**
     * Get end as LocalDateTime (combines date and time).
     */
    public LocalDateTime getEndDateTime() {
        if (reservationDate != null && endTime != null) {
            return LocalDateTime.of(reservationDate, LocalTime.parse(endTime));
        }
        return null;
    }
}

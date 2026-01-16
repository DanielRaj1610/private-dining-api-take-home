package com.opentable.privatedining.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Private dining space/room within a restaurant.
 * Stored in separate collection for flexible querying.
 * Uses UUID as the primary identifier for compatibility with existing code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "spaces")
public class Space {

    /**
     * UUID-based identifier for the space.
     */
    @Id
    private UUID id;

    /**
     * Reference to the parent restaurant (ObjectId as String).
     */
    private String restaurantId;

    private String name;

    private String description;

    @Builder.Default
    private Integer minCapacity = 1;

    private Integer maxCapacity;

    /**
     * Duration of each time slot in minutes.
     */
    @Builder.Default
    private Integer slotDurationMinutes = 60;

    /**
     * Buffer time between reservations in minutes.
     */
    @Builder.Default
    private Integer bufferMinutes = 15;

    /**
     * Hourly rate for the space (optional).
     */
    private BigDecimal hourlyRate;

    /**
     * Available amenities (e.g., "projector", "whiteboard", "sound_system").
     */
    private List<String> amenities;

    /**
     * Whether the space is active and available for booking.
     */
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Constructor for backward compatibility.
     */
    public Space(String name, Integer minCapacity, Integer maxCapacity) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.minCapacity = minCapacity;
        this.maxCapacity = maxCapacity;
        this.slotDurationMinutes = 60;
        this.bufferMinutes = 15;
        this.isActive = true;
    }
}

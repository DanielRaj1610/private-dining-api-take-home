package com.opentable.privatedining.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tracks capacity for a specific time slot.
 * Used for atomic capacity checks during concurrent bookings.
 *
 * The compound ID ensures uniqueness per space-date-time combination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "slot_capacities")
public class SlotCapacity {

    /**
     * Compound ID: spaceId:date:startTime
     * Example: "9cb34a37-d514-4103-bae9-b0ed1f7c7d09:2024-02-15:17:00"
     */
    @Id
    private String id;

    private UUID spaceId;
    private LocalDate date;
    private String startTime;
    private String endTime;

    /**
     * Total capacity currently booked for this slot.
     */
    private Integer bookedCapacity;

    /**
     * Maximum capacity allowed (copied from Space for quick checks).
     */
    private Integer maxCapacity;

    /**
     * Version for optimistic locking.
     */
    @Version
    private Long version;

    /**
     * Generate the compound ID for a slot.
     */
    public static String generateId(UUID spaceId, LocalDate date, String startTime) {
        return String.format("%s:%s:%s", spaceId.toString(), date.toString(), startTime);
    }

    /**
     * Calculate available capacity.
     */
    public int getAvailableCapacity() {
        return maxCapacity - bookedCapacity;
    }

    /**
     * Check if adding the party size would exceed capacity.
     */
    public boolean canAccommodate(int partySize) {
        return (bookedCapacity + partySize) <= maxCapacity;
    }
}

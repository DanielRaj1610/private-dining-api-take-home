package com.opentable.privatedining.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Restaurant entity with operating hours configuration.
 * Spaces are stored in a separate collection (not embedded).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "restaurants")
public class Restaurant {

    @Id
    private ObjectId id;

    private String name;

    private String address;

    private String city;

    private String state;

    private String zipCode;

    private String phone;

    private String email;

    private String cuisineType;

    /**
     * Timezone for the restaurant (e.g., "America/New_York").
     */
    @Builder.Default
    private String timezone = "America/New_York";

    /**
     * Operating hours for each day of the week.
     */
    @Builder.Default
    private List<OperatingHours> operatingHours = new ArrayList<>();

    /**
     * Whether the restaurant is active and accepting reservations.
     */
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Get operating hours for a specific day of week.
     * @param dayOfWeek 0 = Sunday, 6 = Saturday
     * @return OperatingHours for the day, or null if not configured
     */
    public OperatingHours getOperatingHoursForDay(int dayOfWeek) {
        if (operatingHours == null) {
            return null;
        }
        return operatingHours.stream()
                .filter(oh -> oh.getDayOfWeek() != null && oh.getDayOfWeek() == dayOfWeek)
                .findFirst()
                .orElse(null);
    }
}

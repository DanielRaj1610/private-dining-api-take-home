package com.opentable.privatedining.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for availability check.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Availability response with time slots for a space")
public class AvailabilityResponse {

    @Schema(description = "UUID of the space", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID spaceId;

    @Schema(description = "Name of the space", example = "Garden Room")
    private String spaceName;

    @Schema(description = "Maximum capacity of the space", example = "20")
    private Integer maxCapacity;

    @Schema(description = "Date for availability check", example = "2024-02-15")
    private LocalDate date;

    @Schema(description = "Available time slots with capacity information")
    private List<TimeSlotResponse> timeSlots;

    @Schema(description = "Operating hours for this day", example = "09:00 - 22:00")
    private String operatingHours;

    @Schema(description = "Whether the restaurant is open on this day", example = "true")
    private Boolean isOpen;
}

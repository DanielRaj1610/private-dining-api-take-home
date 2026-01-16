package com.opentable.privatedining.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Occupancy data for a single space.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Occupancy data for a single space")
public class SpaceOccupancy {

    @Schema(description = "UUID of the space", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID spaceId;

    @Schema(description = "Name of the space", example = "Garden Room")
    private String spaceName;

    @Schema(description = "Maximum capacity of the space", example = "20")
    private Integer maxCapacity;

    @Schema(description = "Number of reservations", example = "5")
    private Long reservations;

    @Schema(description = "Total guests in this space", example = "32")
    private Long guests;

    @Schema(description = "Utilization percentage for this space", example = "80.0")
    private BigDecimal utilizationPercentage;
}

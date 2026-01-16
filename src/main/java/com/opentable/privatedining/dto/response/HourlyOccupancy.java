package com.opentable.privatedining.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Hourly breakdown of occupancy data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Occupancy data for a single hour")
public class HourlyOccupancy {

    @Schema(description = "Hour in HH:mm format", example = "19:00")
    private String hour;

    @Schema(description = "Number of reservations in this hour", example = "4")
    private Long reservations;

    @Schema(description = "Number of guests in this hour", example = "28")
    private Long guests;

    @Schema(description = "Utilization percentage for this hour", example = "87.5")
    private BigDecimal utilizationPercentage;
}

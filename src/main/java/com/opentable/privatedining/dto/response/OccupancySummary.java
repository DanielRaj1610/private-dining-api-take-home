package com.opentable.privatedining.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Summary statistics for occupancy report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary statistics for the occupancy report")
public class OccupancySummary {

    @Schema(description = "Total number of reservations in the period", example = "145")
    private Long totalReservations;

    @Schema(description = "Total number of guests across all reservations", example = "892")
    private Long totalGuests;

    @Schema(description = "Average party size", example = "6.15")
    private BigDecimal averagePartySize;

    @Schema(description = "Average utilization percentage across the period", example = "72.5")
    private BigDecimal averageUtilizationPercentage;

    @Schema(description = "Total operating slots available in the period", example = "620")
    private Long totalOperatingSlots;

    @Schema(description = "Total slots that were booked", example = "449")
    private Long totalBookedSlots;

    @Schema(description = "Number of cancelled reservations", example = "12")
    private Long cancelledReservations;

    @Schema(description = "Cancellation rate as percentage", example = "7.6")
    private BigDecimal cancellationRate;
}

package com.opentable.privatedining.dto.request;

import com.opentable.privatedining.model.enums.ReportGranularity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for generating occupancy reports.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request parameters for occupancy report generation")
public class OccupancyReportRequest {

    @NotNull(message = "Restaurant ID is required")
    @Schema(description = "Restaurant ID to generate report for", example = "507f1f77bcf86cd799439011", requiredMode = Schema.RequiredMode.REQUIRED)
    private String restaurantId;

    @NotNull(message = "Start date is required")
    @Schema(description = "Start date of the reporting period (inclusive)", example = "2024-01-01", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Schema(description = "End date of the reporting period (inclusive)", example = "2024-01-31", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate endDate;

    @Builder.Default
    @Schema(description = "Report granularity level", example = "DAILY", defaultValue = "DAILY")
    private ReportGranularity granularity = ReportGranularity.DAILY;

    @Schema(description = "Optional: Filter by specific space UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID spaceId;
}

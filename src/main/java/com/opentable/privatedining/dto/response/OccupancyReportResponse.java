package com.opentable.privatedining.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for occupancy report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete occupancy report with summary and breakdowns")
public class OccupancyReportResponse {

    @Schema(description = "Restaurant ID", example = "507f1f77bcf86cd799439011")
    private String restaurantId;

    @Schema(description = "Restaurant name", example = "The Grand Restaurant")
    private String restaurantName;

    @Schema(description = "Report period information")
    private ReportPeriod period;

    @Schema(description = "Summary statistics for the report period")
    private OccupancySummary summary;

    @Schema(description = "Daily breakdown of occupancy data")
    private List<DailyOccupancy> dailyBreakdown;

    @Schema(description = "Insights and recommendations based on the data")
    private OccupancyInsights insights;

    /**
     * Report period definition.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Report period dates")
    public static class ReportPeriod {
        @Schema(description = "Start date of the reporting period", example = "2024-01-01")
        private LocalDate startDate;

        @Schema(description = "End date of the reporting period", example = "2024-01-31")
        private LocalDate endDate;
    }
}

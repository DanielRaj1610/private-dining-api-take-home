package com.opentable.privatedining.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

/**
 * Insights and recommendations from occupancy analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Insights and recommendations based on occupancy data")
public class OccupancyInsights {

    @Schema(description = "Busiest day of the week", example = "SATURDAY")
    private DayOfWeek busiestDay;

    @Schema(description = "Average utilization on the busiest day", example = "89.5")
    private BigDecimal busiestDayAverageUtilization;

    @Schema(description = "Slowest day of the week", example = "MONDAY")
    private DayOfWeek slowestDay;

    @Schema(description = "Average utilization on the slowest day", example = "45.2")
    private BigDecimal slowestDayAverageUtilization;

    @Schema(description = "Busiest hour of the day", example = "19:00")
    private String busiestHour;

    @Schema(description = "Average utilization during the busiest hour", example = "92.3")
    private BigDecimal busiestHourAverageUtilization;

    @Schema(description = "Slowest hour of the day", example = "15:00")
    private String slowestHour;

    @Schema(description = "Average utilization during the slowest hour", example = "22.1")
    private BigDecimal slowestHourAverageUtilization;

    @Schema(description = "System-generated recommendations based on the data")
    private List<String> recommendations;
}

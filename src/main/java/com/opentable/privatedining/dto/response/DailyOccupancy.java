package com.opentable.privatedining.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily breakdown of occupancy data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Occupancy data for a single day")
public class DailyOccupancy {

    @Schema(description = "Date", example = "2024-01-01")
    private LocalDate date;

    @Schema(description = "Day of the week", example = "MONDAY")
    private DayOfWeek dayOfWeek;

    @Schema(description = "Total reservations for the day", example = "8")
    private Long totalReservations;

    @Schema(description = "Total guests for the day", example = "52")
    private Long totalGuests;

    @Schema(description = "Utilization percentage for the day", example = "65.0")
    private BigDecimal utilizationPercentage;

    @Schema(description = "Peak hour of the day", example = "19:00")
    private String peakHour;

    @Schema(description = "Peak hour utilization percentage", example = "95.0")
    private BigDecimal peakHourUtilization;

    @Schema(description = "Breakdown by space")
    private List<SpaceOccupancy> spaceBreakdown;

    @Schema(description = "Hourly breakdown (included when granularity is HOURLY)")
    private List<HourlyOccupancy> hourlyBreakdown;
}

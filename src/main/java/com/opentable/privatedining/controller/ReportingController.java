package com.opentable.privatedining.controller;

import com.opentable.privatedining.dto.request.OccupancyReportRequest;
import com.opentable.privatedining.dto.response.OccupancyReportResponse;
import com.opentable.privatedining.model.enums.ReportGranularity;
import com.opentable.privatedining.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller for occupancy and analytics reports.
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Occupancy analytics and reporting API")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/occupancy")
    @Operation(
            summary = "Get occupancy report",
            description = "Generate an occupancy report for a restaurant within a date range. " +
                    "Includes daily breakdowns, peak hours, and utilization insights."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Report generated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OccupancyReportResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid date range or parameters"),
            @ApiResponse(responseCode = "404", description = "Restaurant not found")
    })
    public ResponseEntity<OccupancyReportResponse> getOccupancyReport(
            @Parameter(description = "Restaurant ID", required = true)
            @RequestParam String restaurantId,

            @Parameter(description = "Start date (inclusive)", required = true, example = "2024-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "End date (inclusive)", required = true, example = "2024-01-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Report granularity (DAILY or HOURLY)", example = "DAILY")
            @RequestParam(required = false, defaultValue = "DAILY") ReportGranularity granularity,

            @Parameter(description = "Optional: Filter by specific space UUID")
            @RequestParam(required = false) UUID spaceId
    ) {
        OccupancyReportRequest request = OccupancyReportRequest.builder()
                .restaurantId(restaurantId)
                .startDate(startDate)
                .endDate(endDate)
                .granularity(granularity)
                .spaceId(spaceId)
                .build();

        OccupancyReportResponse response = reportingService.generateOccupancyReport(request);
        return ResponseEntity.ok(response);
    }
}

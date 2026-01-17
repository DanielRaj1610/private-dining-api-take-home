package com.opentable.privatedining.controller;

import com.opentable.privatedining.dto.response.AvailabilityResponse;
import com.opentable.privatedining.service.AvailabilityService;
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
 * Controller for checking availability.
 */
@RestController
@RequestMapping("/api/v1/availability")
@Tag(name = "Availability", description = "Space availability and time slot API")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/spaces/{spaceId}")
    @Operation(
            summary = "Get space availability",
            description = "Get available time slots for a space on a specific date. " +
                    "Shows capacity information for each slot, allowing for flexible booking " +
                    "where multiple reservations can share the same time slot if capacity allows."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Availability retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AvailabilityResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Space not found")
    })
    public ResponseEntity<AvailabilityResponse> getSpaceAvailability(
            @Parameter(description = "Space UUID", required = true)
            @PathVariable UUID spaceId,

            @Parameter(description = "Date to check availability", required = true, example = "2024-02-15")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        AvailabilityResponse response = availabilityService.getAvailability(spaceId, date);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/spaces/{spaceId}/capacity")
    @Operation(
            summary = "Get available capacity for a time slot",
            description = "Get the remaining available capacity for a specific time slot."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Capacity information retrieved"),
            @ApiResponse(responseCode = "404", description = "Space not found")
    })
    public ResponseEntity<CapacityInfo> getAvailableCapacity(
            @Parameter(description = "Space UUID", required = true)
            @PathVariable UUID spaceId,

            @Parameter(description = "Date", required = true, example = "2024-02-15")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,

            @Parameter(description = "Start time (HH:mm)", required = true, example = "18:00")
            @RequestParam String startTime,

            @Parameter(description = "End time (HH:mm)", required = true, example = "19:00")
            @RequestParam String endTime
    ) {
        int availableCapacity = availabilityService.getAvailableCapacity(spaceId, date, startTime, endTime);
        int bookedCapacity = availabilityService.getBookedCapacity(spaceId, date, startTime, endTime);

        CapacityInfo info = new CapacityInfo(availableCapacity, bookedCapacity);
        return ResponseEntity.ok(info);
    }

    /**
     * Simple record for capacity information response.
     */
    @Schema(description = "Capacity information for a time slot")
    public record CapacityInfo(
            @Schema(description = "Available capacity", example = "12")
            int availableCapacity,

            @Schema(description = "Currently booked capacity", example = "8")
            int bookedCapacity
    ) {}
}

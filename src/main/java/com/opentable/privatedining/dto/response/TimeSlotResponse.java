package com.opentable.privatedining.dto.response;

import com.opentable.privatedining.model.enums.SlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for a single time slot with availability information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Time slot with availability and capacity information")
public class TimeSlotResponse {

    @Schema(description = "Start time in HH:mm format", example = "18:00")
    private String startTime;

    @Schema(description = "End time in HH:mm format", example = "19:00")
    private String endTime;

    @Schema(description = "Available capacity for this slot", example = "12")
    private Integer availableCapacity;

    @Schema(description = "Currently booked capacity", example = "8")
    private Integer bookedCapacity;

    @Schema(description = "Status of the slot", example = "AVAILABLE")
    private SlotStatus status;

    @Schema(description = "Number of existing reservations in this slot", example = "2")
    private Integer existingReservations;
}

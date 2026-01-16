package com.opentable.privatedining.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for cancellation confirmation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cancellation confirmation response")
public class CancellationResponse {

    @Schema(description = "ID of the cancelled reservation", example = "507f1f77bcf86cd799439011")
    private String reservationId;

    @Schema(description = "Confirmation message", example = "Reservation successfully cancelled")
    private String message;

    @Schema(description = "Reason for cancellation", example = "Change of plans due to unexpected travel")
    private String cancellationReason;

    @Schema(description = "Timestamp when the reservation was cancelled")
    private LocalDateTime cancelledAt;
}

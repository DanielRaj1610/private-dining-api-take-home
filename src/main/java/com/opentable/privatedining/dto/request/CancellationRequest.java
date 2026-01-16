package com.opentable.privatedining.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for cancelling a reservation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to cancel an existing reservation")
public class CancellationRequest {

    @Size(max = 500, message = "Cancellation reason cannot exceed 500 characters")
    @Schema(description = "Reason for cancellation (optional)", example = "Change of plans due to unexpected travel")
    private String reason;
}

package com.opentable.privatedining.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a new reservation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new private dining reservation")
public class CreateReservationRequest {

    @NotNull(message = "Space ID is required")
    @Schema(description = "UUID of the space to reserve", example = "123e4567-e89b-12d3-a456-426614174000", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID spaceId;

    @NotNull(message = "Reservation date is required")
    @FutureOrPresent(message = "Reservation date must be today or in the future")
    @Schema(description = "Date of the reservation", example = "2024-02-15", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate reservationDate;

    @NotNull(message = "Start time is required")
    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "Start time must be in HH:mm format")
    @Schema(description = "Start time in HH:mm format", example = "18:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private String startTime;

    @NotNull(message = "Party size is required")
    @Min(value = 1, message = "Party size must be at least 1")
    @Max(value = 100, message = "Party size cannot exceed 100")
    @Schema(description = "Number of guests", example = "8", minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer partySize;

    @NotBlank(message = "Customer name is required")
    @Size(max = 255, message = "Customer name cannot exceed 255 characters")
    @Schema(description = "Full name of the customer", example = "John Smith", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerName;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    @Schema(description = "Email address of the customer", example = "john.smith@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerEmail;

    @Size(max = 50, message = "Phone number cannot exceed 50 characters")
    @Schema(description = "Phone number of the customer (optional)", example = "+1-555-123-4567")
    private String customerPhone;

    @Size(max = 1000, message = "Special requests cannot exceed 1000 characters")
    @Schema(description = "Special requests or notes", example = "Anniversary dinner, please prepare a cake")
    private String specialRequests;
}

package com.opentable.privatedining.dto.response;

import com.opentable.privatedining.model.enums.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for reservation details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reservation response with full details")
public class ReservationResponse {

    @Schema(description = "Unique identifier for the reservation", example = "507f1f77bcf86cd799439011")
    private String id;

    @Schema(description = "UUID of the reserved space", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID spaceId;

    @Schema(description = "Name of the reserved space", example = "Garden Room")
    private String spaceName;

    @Schema(description = "ID of the restaurant", example = "507f191e810c19729de860ea")
    private String restaurantId;

    @Schema(description = "Name of the restaurant", example = "The Grand Restaurant")
    private String restaurantName;

    @Schema(description = "Date of the reservation", example = "2024-02-15")
    private LocalDate reservationDate;

    @Schema(description = "Start time in HH:mm format", example = "18:00")
    private String startTime;

    @Schema(description = "End time in HH:mm format", example = "19:00")
    private String endTime;

    @Schema(description = "Number of guests", example = "8")
    private Integer partySize;

    @Schema(description = "Full name of the customer", example = "John Smith")
    private String customerName;

    @Schema(description = "Email address of the customer", example = "john.smith@example.com")
    private String customerEmail;

    @Schema(description = "Phone number of the customer", example = "+1-555-123-4567")
    private String customerPhone;

    @Schema(description = "Special requests or notes", example = "Anniversary dinner, please prepare a cake")
    private String specialRequests;

    @Schema(description = "Current status of the reservation", example = "CONFIRMED")
    private ReservationStatus status;

    @Schema(description = "Timestamp when the reservation was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the reservation was last updated")
    private LocalDateTime updatedAt;
}

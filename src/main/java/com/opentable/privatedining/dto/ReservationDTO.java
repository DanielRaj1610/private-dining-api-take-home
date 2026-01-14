package com.opentable.privatedining.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

public class ReservationDTO {

    @Schema(description = "Unique identifier for the reservation", example = "507f1f77bcf86cd799439011", type = "string")
    private String id;

    @Schema(description = "ID of the restaurant for this reservation", example = "507f191e810c19729de860ea", type = "string")
    private String restaurantId;

    @Schema(description = "UUID of the specific space being reserved", example = "123e4567-e89b-12d3-a456-426614174000", type = "string")
    private UUID spaceId;

    @Schema(description = "Email address of the customer making the reservation", example = "customer@example.com")
    private String customerEmail;

    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    @Schema(type = "string", description = "Start date and time of the reservation", example = "15-01-2026 19:30", pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "dd-MM-yyyy HH:mm")
    @Schema(type = "string", description = "End date and time of the reservation", example = "15-01-2026 21:30", pattern = "dd-MM-yyyy HH:mm")
    private LocalDateTime endTime;

    @Schema(description = "Number of people in the party", example = "4")
    private Integer partySize;

    @Schema(description = "Status of the reservation", example = "CONFIRMED")
    private String status;

    public ReservationDTO() {}

    public ReservationDTO(String restaurantId, UUID spaceId, String customerEmail, LocalDateTime startTime, LocalDateTime endTime, Integer partySize, String status) {
        this.restaurantId = restaurantId;
        this.spaceId = spaceId;
        this.customerEmail = customerEmail;
        this.startTime = startTime;
        this.endTime = endTime;
        this.partySize = partySize;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(String restaurantId) {
        this.restaurantId = restaurantId;
    }

    public UUID getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(UUID spaceId) {
        this.spaceId = spaceId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getPartySize() {
        return partySize;
    }

    public void setPartySize(Integer partySize) {
        this.partySize = partySize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
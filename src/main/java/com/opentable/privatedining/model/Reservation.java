package com.opentable.privatedining.model;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "reservations")
public class Reservation {

    @Id
    private ObjectId id;
    private ObjectId restaurantId;
    private UUID spaceId;
    private String customerEmail;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer partySize;
    private String status;

    public Reservation() {}

    public Reservation(ObjectId restaurantId, UUID spaceId, String customerEmail, LocalDateTime startTime, LocalDateTime endTime, Integer partySize, String status) {
        this.restaurantId = restaurantId;
        this.spaceId = spaceId;
        this.customerEmail = customerEmail;
        this.startTime = startTime;
        this.endTime = endTime;
        this.partySize = partySize;
        this.status = status;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public ObjectId getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(ObjectId restaurantId) {
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
package com.opentable.privatedining.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

public class RestaurantDTO {

    @Schema(description = "Unique identifier for the restaurant", example = "507f1f77bcf86cd799439011", type = "string")
    private String id;

    @Schema(description = "Name of the restaurant", example = "The French Laundry")
    private String name;

    @Schema(description = "Address of the restaurant", example = "6640 Washington St, Yountville, CA 94599")
    private String address;

    @Schema(description = "Type of cuisine served", example = "French")
    private String cuisineType;

    @Schema(description = "Total capacity of the restaurant", example = "100")
    private Integer capacity;

    @Schema(description = "List of spaces available in the restaurant")
    private List<SpaceDTO> spaces;

    public RestaurantDTO() {
        this.spaces = new ArrayList<>();
    }

    public RestaurantDTO(String name, String address, String cuisineType, Integer capacity) {
        this.name = name;
        this.address = address;
        this.cuisineType = cuisineType;
        this.capacity = capacity;
        this.spaces = new ArrayList<>();
    }

    public RestaurantDTO(String id, String name, String address, String cuisineType, Integer capacity, List<SpaceDTO> spaces) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.cuisineType = cuisineType;
        this.capacity = capacity;
        this.spaces = spaces != null ? spaces : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCuisineType() {
        return cuisineType;
    }

    public void setCuisineType(String cuisineType) {
        this.cuisineType = cuisineType;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public List<SpaceDTO> getSpaces() {
        return spaces;
    }

    public void setSpaces(List<SpaceDTO> spaces) {
        this.spaces = spaces;
    }
}
package com.opentable.privatedining.exception;

import org.bson.types.ObjectId;

public class RestaurantNotFoundException extends RuntimeException {

    public RestaurantNotFoundException(ObjectId restaurantId) {
        super("Restaurant not found with ID: " + restaurantId.toString());
    }

    public RestaurantNotFoundException(String message) {
        super(message);
    }
}
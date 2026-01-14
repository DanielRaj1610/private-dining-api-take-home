package com.opentable.privatedining.onetime;

import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.Restaurant;

import java.util.List;

public class Data {
    private List<Restaurant> restaurants;
    private List<Reservation> reservations;

    public List<Restaurant> getRestaurants() {
        return restaurants;
    }

    public void setRestaurants(List<Restaurant> restaurants) {
        this.restaurants = restaurants;
    }

    public List<Reservation> getReservations() {
        return reservations;
    }

    public void setReservations(List<Reservation> reservations) {
        this.reservations = reservations;
    }
}

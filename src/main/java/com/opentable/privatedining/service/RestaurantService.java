package com.opentable.privatedining.service;

import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.repository.RestaurantRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    public List<Restaurant> getAllRestaurants() {
        return restaurantRepository.findAll();
    }

    public Optional<Restaurant> getRestaurantById(ObjectId id) {
        return restaurantRepository.findById(id);
    }

    public Restaurant createRestaurant(Restaurant restaurant) {
        return restaurantRepository.save(restaurant);
    }

    public Optional<Restaurant> updateRestaurant(ObjectId id, Restaurant restaurant) {
        Optional<Restaurant> existingRestaurant = restaurantRepository.findById(id);
        if (existingRestaurant.isPresent()) {
            restaurant.setId(id);
            return Optional.of(restaurantRepository.save(restaurant));
        }
        return Optional.empty();
    }

    public boolean deleteRestaurant(ObjectId id) {
        Optional<Restaurant> existingRestaurant = restaurantRepository.findById(id);
        if (existingRestaurant.isPresent()) {
            restaurantRepository.deleteById(id);
            return true;
        }
        return false;
    }

    public Optional<Restaurant> addSpaceToRestaurant(ObjectId restaurantId, Space space) {
        Optional<Restaurant> restaurantOpt = restaurantRepository.findById(restaurantId);
        if (restaurantOpt.isPresent()) {
            Restaurant restaurant = restaurantOpt.get();
            restaurant.getSpaces().add(space);
            return Optional.of(restaurantRepository.save(restaurant));
        }
        return Optional.empty();
    }

    public Optional<Restaurant> removeSpaceFromRestaurant(ObjectId restaurantId, UUID spaceId) {
        Optional<Restaurant> restaurantOpt = restaurantRepository.findById(restaurantId);
        if (restaurantOpt.isPresent()) {
            Restaurant restaurant = restaurantOpt.get();
            restaurant.getSpaces().removeIf(space -> space.getId().equals(spaceId));
            return Optional.of(restaurantRepository.save(restaurant));
        }
        return Optional.empty();
    }

    public Optional<Space> getSpaceById(ObjectId restaurantId, UUID spaceId) {
        Optional<Restaurant> restaurantOpt = restaurantRepository.findById(restaurantId);
        if (restaurantOpt.isPresent()) {
            return restaurantOpt.get().getSpaces().stream()
                    .filter(space -> space.getId().equals(spaceId))
                    .findFirst();
        }
        return Optional.empty();
    }

    public boolean spaceExistsInRestaurant(ObjectId restaurantId, UUID spaceId) {
        return getSpaceById(restaurantId, spaceId).isPresent();
    }
}
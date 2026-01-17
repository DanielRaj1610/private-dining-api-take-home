package com.opentable.privatedining.service;

import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.repository.RestaurantRepository;
import com.opentable.privatedining.repository.SpaceRepository;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Restaurant management.
 * Spaces are now stored in a separate collection and managed via SpaceRepository.
 */
@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final SpaceRepository spaceRepository;

    public RestaurantService(RestaurantRepository restaurantRepository,
                             SpaceRepository spaceRepository) {
        this.restaurantRepository = restaurantRepository;
        this.spaceRepository = spaceRepository;
    }

    public List<Restaurant> getAllRestaurants() {
        return restaurantRepository.findAll();
    }

    /**
     * Get all restaurants with pagination.
     */
    public Page<Restaurant> getAllRestaurants(Pageable pageable) {
        return restaurantRepository.findAll(pageable);
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

    /**
     * Add a space to a restaurant.
     * Creates the space in the spaces collection with a reference to the restaurant.
     */
    public Optional<Space> addSpaceToRestaurant(ObjectId restaurantId, Space space) {
        Optional<Restaurant> restaurantOpt = restaurantRepository.findById(restaurantId);
        if (restaurantOpt.isPresent()) {
            if (space.getId() == null) {
                space.setId(UUID.randomUUID());
            }
            space.setRestaurantId(restaurantId.toHexString());
            return Optional.of(spaceRepository.save(space));
        }
        return Optional.empty();
    }

    /**
     * Remove a space from a restaurant.
     * Deletes the space from the spaces collection.
     */
    public boolean removeSpaceFromRestaurant(ObjectId restaurantId, UUID spaceId) {
        Optional<Space> spaceOpt = spaceRepository.findByIdAndRestaurantId(
                spaceId, restaurantId.toHexString());
        if (spaceOpt.isPresent()) {
            spaceRepository.deleteById(spaceId);
            return true;
        }
        return false;
    }

    /**
     * Get a space by ID within a restaurant.
     */
    public Optional<Space> getSpaceById(ObjectId restaurantId, UUID spaceId) {
        return spaceRepository.findByIdAndRestaurantId(spaceId, restaurantId.toHexString());
    }

    /**
     * Get all spaces for a restaurant.
     */
    public List<Space> getSpacesForRestaurant(ObjectId restaurantId) {
        return spaceRepository.findByRestaurantId(restaurantId.toHexString());
    }

    /**
     * Get all active spaces for a restaurant.
     */
    public List<Space> getActiveSpacesForRestaurant(ObjectId restaurantId) {
        return spaceRepository.findByRestaurantIdAndIsActiveTrue(restaurantId.toHexString());
    }

    /**
     * Check if a space exists in a restaurant.
     */
    public boolean spaceExistsInRestaurant(ObjectId restaurantId, UUID spaceId) {
        return getSpaceById(restaurantId, spaceId).isPresent();
    }
}

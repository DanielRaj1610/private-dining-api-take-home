package com.opentable.privatedining.repository;

import com.opentable.privatedining.model.Space;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Space entities.
 * Uses UUID as the identifier type.
 */
@Repository
public interface SpaceRepository extends MongoRepository<Space, UUID> {

    /**
     * Find all spaces for a restaurant.
     */
    List<Space> findByRestaurantId(String restaurantId);

    /**
     * Find all active spaces for a restaurant.
     */
    List<Space> findByRestaurantIdAndIsActiveTrue(String restaurantId);

    /**
     * Find a space by ID and restaurant ID.
     */
    Optional<Space> findByIdAndRestaurantId(UUID id, String restaurantId);

    /**
     * Find active space by ID.
     */
    @Query("{ '_id': ?0, 'isActive': true }")
    Optional<Space> findActiveById(UUID id);

    /**
     * Check if a space exists and is active.
     */
    boolean existsByIdAndIsActiveTrue(UUID id);

    /**
     * Find spaces by restaurant with capacity range.
     */
    @Query("{ 'restaurantId': ?0, 'minCapacity': { $lte: ?1 }, 'maxCapacity': { $gte: ?1 }, 'isActive': true }")
    List<Space> findByRestaurantIdAndCapacityRange(String restaurantId, int partySize);
}

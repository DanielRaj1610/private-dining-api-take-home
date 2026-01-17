package com.opentable.privatedining.service;

import com.opentable.privatedining.exception.SpaceNotFoundException;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.repository.SpaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Space management.
 * Spaces are stored in their own collection (not embedded in Restaurant).
 */
@Service
public class SpaceService {

    private final SpaceRepository spaceRepository;

    public SpaceService(SpaceRepository spaceRepository) {
        this.spaceRepository = spaceRepository;
    }

    /**
     * Get all spaces.
     */
    public List<Space> getAllSpaces() {
        return spaceRepository.findAll();
    }

    /**
     * Get space by ID.
     */
    public Optional<Space> getSpaceById(UUID id) {
        return spaceRepository.findById(id);
    }

    /**
     * Get active space by ID.
     */
    public Optional<Space> getActiveSpaceById(UUID id) {
        return spaceRepository.findActiveById(id);
    }

    /**
     * Get space by ID, throw exception if not found.
     */
    public Space getSpaceByIdOrThrow(UUID id) {
        return spaceRepository.findById(id)
                .orElseThrow(() -> new SpaceNotFoundException(id));
    }

    /**
     * Get active space by ID, throw exception if not found.
     */
    public Space getActiveSpaceByIdOrThrow(UUID id) {
        return spaceRepository.findActiveById(id)
                .orElseThrow(() -> new SpaceNotFoundException(id));
    }

    /**
     * Get all spaces for a restaurant.
     */
    public List<Space> getSpacesByRestaurantId(String restaurantId) {
        return spaceRepository.findByRestaurantId(restaurantId);
    }

    /**
     * Get all active spaces for a restaurant.
     */
    public List<Space> getActiveSpacesByRestaurantId(String restaurantId) {
        return spaceRepository.findByRestaurantIdAndIsActiveTrue(restaurantId);
    }

    /**
     * Find spaces suitable for a party size.
     */
    public List<Space> findSpacesForPartySize(String restaurantId, int partySize) {
        return spaceRepository.findByRestaurantIdAndCapacityRange(restaurantId, partySize);
    }

    /**
     * Create a new space.
     */
    public Space createSpace(Space space) {
        if (space.getId() == null) {
            space.setId(UUID.randomUUID());
        }
        return spaceRepository.save(space);
    }

    /**
     * Update an existing space.
     */
    public Optional<Space> updateSpace(UUID id, Space spaceUpdate) {
        return spaceRepository.findById(id)
                .map(existing -> {
                    // Preserve ID and restaurant association
                    spaceUpdate.setId(existing.getId());
                    if (spaceUpdate.getRestaurantId() == null) {
                        spaceUpdate.setRestaurantId(existing.getRestaurantId());
                    }
                    return spaceRepository.save(spaceUpdate);
                });
    }

    /**
     * Deactivate a space (soft delete).
     */
    public Optional<Space> deactivateSpace(UUID id) {
        return spaceRepository.findById(id)
                .map(space -> {
                    space.setIsActive(false);
                    return spaceRepository.save(space);
                });
    }

    /**
     * Delete a space permanently.
     */
    public boolean deleteSpace(UUID id) {
        if (spaceRepository.existsById(id)) {
            spaceRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Check if a space exists and is active.
     */
    public boolean isSpaceActive(UUID id) {
        return spaceRepository.existsByIdAndIsActiveTrue(id);
    }
}

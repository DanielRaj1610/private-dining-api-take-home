package com.opentable.privatedining.repository;

import com.opentable.privatedining.model.SlotCapacity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for slot capacity tracking.
 */
@Repository
public interface SlotCapacityRepository extends MongoRepository<SlotCapacity, String> {

    /**
     * Find slot capacity by compound ID.
     */
    Optional<SlotCapacity> findById(String id);

    /**
     * Find all slots for a space on a specific date.
     */
    List<SlotCapacity> findBySpaceIdAndDate(UUID spaceId, LocalDate date);

    /**
     * Find overlapping slots for capacity check.
     * A slot overlaps if its time range intersects with the given range.
     */
    @Query("{ 'spaceId': ?0, 'date': ?1, 'startTime': { '$lt': ?3 }, 'endTime': { '$gt': ?2 } }")
    List<SlotCapacity> findOverlappingSlots(UUID spaceId, LocalDate date, String startTime, String endTime);

    /**
     * Delete all slot capacities for a space (useful for cleanup).
     */
    void deleteBySpaceId(UUID spaceId);

    /**
     * Delete all slot capacities for a specific date (useful for cleanup).
     */
    void deleteByDate(LocalDate date);
}

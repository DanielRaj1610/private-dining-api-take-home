package com.opentable.privatedining.service;

import com.opentable.privatedining.model.SlotCapacity;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.repository.SlotCapacityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing slot capacity with atomic operations.
 * Uses findAndModify for atomic capacity reservations to prevent race conditions.
 */
@Service
public class SlotCapacityService {

    private static final Logger logger = LoggerFactory.getLogger(SlotCapacityService.class);

    private final SlotCapacityRepository slotCapacityRepository;
    private final MongoTemplate mongoTemplate;

    public SlotCapacityService(SlotCapacityRepository slotCapacityRepository,
                               MongoTemplate mongoTemplate) {
        this.slotCapacityRepository = slotCapacityRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Atomically try to reserve capacity for a booking.
     * Returns true if capacity was successfully reserved, false if insufficient capacity.
     *
     * ALGORITHM: Atomic Compare-And-Swap for Concurrent Capacity Management
     * -----------------------------------------------------------------------
     * This method prevents race conditions in concurrent booking scenarios using
     * MongoDB's findAndModify operation, which guarantees atomicity.
     *
     * Race Condition Example (WITHOUT atomic operation):
     *   Thread A: Read bookedCapacity = 8, maxCapacity = 10, requesting 3
     *   Thread B: Read bookedCapacity = 8, maxCapacity = 10, requesting 3
     *   Thread A: Check 8 + 3 <= 10 (PASS), Write bookedCapacity = 11
     *   Thread B: Check 8 + 3 <= 10 (PASS), Write bookedCapacity = 14
     *   Result: OVERBOOKING! 14 > 10 capacity exceeded
     *
     * With Atomic Operation:
     *   Both threads execute findAndModify with condition: bookedCapacity <= (10 - 3)
     *   Only ONE thread's condition will succeed (whoever arrives first)
     *   Second thread gets null result = booking rejected
     *
     * @param space The space
     * @param date The reservation date
     * @param startTime The start time
     * @param endTime The end time
     * @param partySize The party size to reserve
     * @return true if capacity was reserved, false if insufficient
     */
    public boolean tryReserveCapacity(Space space, LocalDate date, String startTime,
                                       String endTime, int partySize) {
        String slotId = SlotCapacity.generateId(space.getId(), date, startTime);
        int maxCapacity = space.getMaxCapacity();

        // First, ensure the slot capacity document exists (idempotent upsert)
        ensureSlotExists(slotId, space.getId(), date, startTime, endTime, maxCapacity);

        // CRITICAL: Atomic capacity reservation using findAndModify
        // This single operation does: check condition + update + return result
        // The condition ensures we only update if capacity is available
        Query query = new Query(Criteria.where("_id").is(slotId)
                .and("bookedCapacity").lte(maxCapacity - partySize));  // Only update if room available

        Update update = new Update().inc("bookedCapacity", partySize);

        // MongoDB findAndModify guarantees atomicity: if condition fails, returns null
        SlotCapacity result = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                SlotCapacity.class
        );

        if (result != null) {
            logger.debug("Reserved {} capacity for slot {} (new total: {})",
                    partySize, slotId, result.getBookedCapacity());
            return true;
        } else {
            logger.debug("Failed to reserve {} capacity for slot {} - insufficient capacity",
                    partySize, slotId);
            return false;
        }
    }

    /**
     * Release capacity when a reservation is cancelled.
     *
     * @param spaceId The space ID
     * @param date The reservation date
     * @param startTime The start time
     * @param partySize The party size to release
     */
    public void releaseCapacity(UUID spaceId, LocalDate date, String startTime, int partySize) {
        String slotId = SlotCapacity.generateId(spaceId, date, startTime);

        Query query = new Query(Criteria.where("_id").is(slotId));
        Update update = new Update().inc("bookedCapacity", -partySize);

        SlotCapacity result = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                SlotCapacity.class
        );

        if (result != null) {
            logger.debug("Released {} capacity for slot {} (new total: {})",
                    partySize, slotId, result.getBookedCapacity());

            // Ensure booked capacity doesn't go negative (safety check)
            if (result.getBookedCapacity() < 0) {
                Query fixQuery = new Query(Criteria.where("_id").is(slotId));
                Update fixUpdate = new Update().set("bookedCapacity", 0);
                mongoTemplate.updateFirst(fixQuery, fixUpdate, SlotCapacity.class);
                logger.warn("Fixed negative capacity for slot {}", slotId);
            }
        }
    }

    /**
     * Get current available capacity for a slot.
     *
     * @param spaceId The space ID
     * @param date The date
     * @param startTime The start time
     * @param maxCapacity The max capacity of the space
     * @return Available capacity
     */
    public int getAvailableCapacity(UUID spaceId, LocalDate date, String startTime, int maxCapacity) {
        String slotId = SlotCapacity.generateId(spaceId, date, startTime);
        Optional<SlotCapacity> slot = slotCapacityRepository.findById(slotId);

        if (slot.isPresent()) {
            return maxCapacity - slot.get().getBookedCapacity();
        }
        return maxCapacity; // No bookings yet
    }

    /**
     * Get current booked capacity for a slot.
     */
    public int getBookedCapacity(UUID spaceId, LocalDate date, String startTime) {
        String slotId = SlotCapacity.generateId(spaceId, date, startTime);
        Optional<SlotCapacity> slot = slotCapacityRepository.findById(slotId);
        return slot.map(SlotCapacity::getBookedCapacity).orElse(0);
    }

    /**
     * Ensure a slot capacity document exists, creating it if necessary.
     * Uses upsert to handle concurrent creation attempts.
     */
    private void ensureSlotExists(String slotId, UUID spaceId, LocalDate date,
                                   String startTime, String endTime, int maxCapacity) {
        Query query = new Query(Criteria.where("_id").is(slotId));

        Update update = new Update()
                .setOnInsert("spaceId", spaceId)
                .setOnInsert("date", date)
                .setOnInsert("startTime", startTime)
                .setOnInsert("endTime", endTime)
                .setOnInsert("maxCapacity", maxCapacity)
                .setOnInsert("bookedCapacity", 0);

        mongoTemplate.upsert(query, update, SlotCapacity.class);
    }

    /**
     * Sync slot capacity from existing reservations.
     * Useful for recalculating capacity after data changes.
     */
    public void syncSlotCapacity(UUID spaceId, LocalDate date, String startTime,
                                  String endTime, int totalBooked, int maxCapacity) {
        String slotId = SlotCapacity.generateId(spaceId, date, startTime);

        Query query = new Query(Criteria.where("_id").is(slotId));

        Update update = new Update()
                .set("spaceId", spaceId)
                .set("date", date)
                .set("startTime", startTime)
                .set("endTime", endTime)
                .set("maxCapacity", maxCapacity)
                .set("bookedCapacity", totalBooked);

        mongoTemplate.upsert(query, update, SlotCapacity.class);
        logger.debug("Synced slot capacity {} with booked={}", slotId, totalBooked);
    }
}

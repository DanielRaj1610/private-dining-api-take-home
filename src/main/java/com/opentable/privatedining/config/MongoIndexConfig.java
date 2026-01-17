package com.opentable.privatedining.config;

import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.Space;
import com.mongodb.MongoCommandException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * MongoDB index configuration for optimized query performance.
 * Creates compound indexes on startup for efficient availability and reporting queries.
 *
 * When using Docker with mongo-init scripts, indexes are already created.
 * This class handles index conflicts gracefully.
 */
@Configuration
public class MongoIndexConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexConfig.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Value("${app.mongo.create-indexes:true}")
    private boolean createIndexes;

    @PostConstruct
    public void initIndexes() {
        if (!createIndexes) {
            log.info("Skipping index creation (app.mongo.create-indexes=false)");
            return;
        }

        log.info("Creating MongoDB indexes...");
        createReservationIndexes();
        createSpaceIndexes();
        log.info("MongoDB index creation complete");
    }

    private void createReservationIndexes() {
        // Index for availability queries - most critical for concurrency
        ensureIndexSafely(Reservation.class,
                new Index()
                        .on("spaceId", Sort.Direction.ASC)
                        .on("reservationDate", Sort.Direction.ASC)
                        .on("status", Sort.Direction.ASC)
                        .named("idx_reservation_space_date_status"),
                "reservation space-date-status");

        // Index for time range queries
        ensureIndexSafely(Reservation.class,
                new Index()
                        .on("reservationDate", Sort.Direction.ASC)
                        .on("startTime", Sort.Direction.ASC)
                        .on("endTime", Sort.Direction.ASC)
                        .named("idx_reservation_date_time_range"),
                "reservation date-time-range");

        // Index for restaurant-level reporting
        ensureIndexSafely(Reservation.class,
                new Index()
                        .on("restaurantId", Sort.Direction.ASC)
                        .on("reservationDate", Sort.Direction.ASC)
                        .named("idx_reservation_restaurant_date"),
                "reservation restaurant-date");

        // Index for customer lookups
        ensureIndexSafely(Reservation.class,
                new Index()
                        .on("customerEmail", Sort.Direction.ASC)
                        .named("idx_reservation_customer_email"),
                "reservation customer-email");
    }

    private void createSpaceIndexes() {
        // Index for finding active spaces by restaurant
        ensureIndexSafely(Space.class,
                new Index()
                        .on("restaurantId", Sort.Direction.ASC)
                        .on("isActive", Sort.Direction.ASC)
                        .named("idx_space_restaurant_active"),
                "space restaurant-active");
    }

    /**
     * Safely ensure an index exists, handling conflicts gracefully.
     * If an index with the same fields but different name exists, log and continue.
     */
    private void ensureIndexSafely(Class<?> entityClass, Index index, String description) {
        try {
            mongoTemplate.indexOps(entityClass).ensureIndex(index);
            log.debug("Created index: {}", description);
        } catch (Exception e) {
            if (e.getCause() instanceof MongoCommandException mce) {
                // Error code 85 = IndexOptionsConflict (index exists with different name)
                // Error code 86 = IndexKeySpecsConflict
                if (mce.getErrorCode() == 85 || mce.getErrorCode() == 86) {
                    log.debug("Index already exists for {}, skipping: {}", description, mce.getMessage());
                    return;
                }
            }
            log.warn("Failed to create index for {}: {}", description, e.getMessage());
        }
    }
}

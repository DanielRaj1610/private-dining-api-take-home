package com.opentable.privatedining.repository;

import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.enums.ReservationStatus;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Reservation entities with queries for availability and reporting.
 */
@Repository
public interface ReservationRepository extends MongoRepository<Reservation, ObjectId> {

    /**
     * Find all reservations for a space on a specific date.
     */
    List<Reservation> findBySpaceIdAndReservationDate(UUID spaceId, LocalDate date);

    /**
     * Find confirmed reservations for a space on a specific date.
     */
    List<Reservation> findBySpaceIdAndReservationDateAndStatus(
            UUID spaceId, LocalDate date, ReservationStatus status);

    /**
     * Find overlapping confirmed reservations for a space.
     * Used for capacity checking.
     *
     * An overlap occurs when:
     * - existing.startTime < requested.endTime AND existing.endTime > requested.startTime
     */
    @Query("{ " +
           "  'spaceId': ?0, " +
           "  'reservationDate': ?1, " +
           "  'status': 'CONFIRMED', " +
           "  'startTime': { $lt: ?3 }, " +
           "  'endTime': { $gt: ?2 } " +
           "}")
    List<Reservation> findOverlappingReservations(
            UUID spaceId, LocalDate date, String startTime, String endTime);

    /**
     * Find all reservations for a restaurant within a date range.
     * Used for reporting.
     */
    List<Reservation> findByRestaurantIdAndReservationDateBetweenAndStatus(
            ObjectId restaurantId, LocalDate startDate, LocalDate endDate, ReservationStatus status);

    /**
     * Find all reservations for a restaurant within a date range (all statuses).
     */
    List<Reservation> findByRestaurantIdAndReservationDateBetween(
            ObjectId restaurantId, LocalDate startDate, LocalDate endDate);

    /**
     * Find reservations by customer email.
     */
    List<Reservation> findByCustomerEmailIgnoreCase(String email);

    /**
     * Find upcoming reservations for a customer.
     */
    @Query("{ 'customerEmail': { $regex: ?0, $options: 'i' }, 'reservationDate': { $gte: ?1 }, 'status': 'CONFIRMED' }")
    List<Reservation> findUpcomingReservationsByEmail(String email, LocalDate fromDate);

    /**
     * Sum of party sizes for overlapping reservations.
     * This aggregation returns the total booked capacity for a time slot.
     */
    @Aggregation(pipeline = {
            "{ $match: { " +
            "    'spaceId': ?0, " +
            "    'reservationDate': ?1, " +
            "    'status': 'CONFIRMED', " +
            "    'startTime': { $lt: ?3 }, " +
            "    'endTime': { $gt: ?2 } " +
            "  } " +
            "}",
            "{ $group: { _id: null, totalPartySize: { $sum: '$partySize' } } }"
    })
    List<TotalPartySizeResult> sumPartySizeForOverlappingReservations(
            UUID spaceId, LocalDate date, String startTime, String endTime);

    /**
     * Daily occupancy aggregation for reporting.
     */
    @Aggregation(pipeline = {
            "{ $match: { " +
            "    'restaurantId': ?0, " +
            "    'reservationDate': { $gte: ?1, $lte: ?2 }, " +
            "    'status': 'CONFIRMED' " +
            "  } " +
            "}",
            "{ $group: { " +
            "    _id: '$reservationDate', " +
            "    totalReservations: { $sum: 1 }, " +
            "    totalGuests: { $sum: '$partySize' } " +
            "  } " +
            "}",
            "{ $sort: { _id: 1 } }"
    })
    List<DailyOccupancyResult> aggregateDailyOccupancy(
            ObjectId restaurantId, LocalDate startDate, LocalDate endDate);

    /**
     * Aggregation result for daily occupancy.
     */
    interface DailyOccupancyResult {
        LocalDate getId();
        Long getTotalReservations();
        Long getTotalGuests();
    }

    /**
     * Hourly occupancy aggregation for reporting.
     */
    @Aggregation(pipeline = {
            "{ $match: { " +
            "    'restaurantId': ?0, " +
            "    'reservationDate': { $gte: ?1, $lte: ?2 }, " +
            "    'status': 'CONFIRMED' " +
            "  } " +
            "}",
            "{ $group: { " +
            "    _id: { date: '$reservationDate', hour: { $substr: ['$startTime', 0, 2] } }, " +
            "    totalReservations: { $sum: 1 }, " +
            "    totalGuests: { $sum: '$partySize' } " +
            "  } " +
            "}",
            "{ $sort: { '_id.date': 1, '_id.hour': 1 } }"
    })
    List<HourlyOccupancyResult> aggregateHourlyOccupancy(
            ObjectId restaurantId, LocalDate startDate, LocalDate endDate);

    /**
     * Aggregation result for hourly occupancy.
     */
    interface HourlyOccupancyResult {
        DateHourKey getId();
        Long getTotalReservations();
        Long getTotalGuests();

        interface DateHourKey {
            LocalDate getDate();
            String getHour();
        }
    }

    /**
     * Space-level occupancy aggregation.
     */
    @Aggregation(pipeline = {
            "{ $match: { " +
            "    'restaurantId': ?0, " +
            "    'reservationDate': { $gte: ?1, $lte: ?2 }, " +
            "    'status': 'CONFIRMED' " +
            "  } " +
            "}",
            "{ $group: { " +
            "    _id: { date: '$reservationDate', spaceId: '$spaceId' }, " +
            "    totalReservations: { $sum: 1 }, " +
            "    totalGuests: { $sum: '$partySize' } " +
            "  } " +
            "}",
            "{ $sort: { '_id.date': 1, '_id.spaceId': 1 } }"
    })
    List<SpaceOccupancyResult> aggregateSpaceOccupancy(
            ObjectId restaurantId, LocalDate startDate, LocalDate endDate);

    /**
     * Aggregation result for space occupancy.
     */
    interface SpaceOccupancyResult {
        DateSpaceKey getId();
        Long getTotalReservations();
        Long getTotalGuests();

        interface DateSpaceKey {
            LocalDate getDate();
            UUID getSpaceId();
        }
    }

    /**
     * Count reservations by status within date range.
     */
    long countByRestaurantIdAndReservationDateBetweenAndStatus(
            ObjectId restaurantId, LocalDate startDate, LocalDate endDate, ReservationStatus status);

    /**
     * Find reservations for a specific space within date range.
     */
    List<Reservation> findBySpaceIdAndReservationDateBetweenAndStatus(
            UUID spaceId, LocalDate startDate, LocalDate endDate, ReservationStatus status);
}

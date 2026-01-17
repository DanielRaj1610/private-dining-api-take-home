package com.opentable.privatedining.service;

import com.opentable.privatedining.dto.response.AvailabilityResponse;
import com.opentable.privatedining.dto.response.TimeSlotResponse;
import com.opentable.privatedining.exception.SpaceNotFoundException;
import com.opentable.privatedining.model.OperatingHours;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.model.enums.ReservationStatus;
import com.opentable.privatedining.repository.ReservationRepository;
import com.opentable.privatedining.repository.SpaceRepository;
import com.opentable.privatedining.repository.TotalPartySizeResult;
import com.opentable.privatedining.util.TimeSlotGenerator;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Service for checking availability and capacity.
 * Implements the flexible capacity model where concurrent reservations are allowed
 * as long as total party size doesn't exceed max capacity.
 */
@Service
public class AvailabilityService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;
    private final RestaurantService restaurantService;
    private final TimeSlotGenerator timeSlotGenerator;

    public AvailabilityService(SpaceRepository spaceRepository,
                               ReservationRepository reservationRepository,
                               RestaurantService restaurantService,
                               TimeSlotGenerator timeSlotGenerator) {
        this.spaceRepository = spaceRepository;
        this.reservationRepository = reservationRepository;
        this.restaurantService = restaurantService;
        this.timeSlotGenerator = timeSlotGenerator;
    }

    /**
     * Get available capacity for a specific time slot.
     * This is used during reservation creation to check if there's room.
     *
     * @param spaceId The space ID
     * @param date The date
     * @param startTime Start time (HH:mm)
     * @param endTime End time (HH:mm)
     * @return Available capacity (max capacity - currently booked)
     */
    public int getAvailableCapacity(UUID spaceId, LocalDate date, String startTime, String endTime) {
        Space space = spaceRepository.findActiveById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));

        int bookedCapacity = getBookedCapacity(spaceId, date, startTime, endTime);
        return space.getMaxCapacity() - bookedCapacity;
    }

    /**
     * Get the currently booked capacity for a time slot.
     *
     * @param spaceId The space ID
     * @param date The date
     * @param startTime Start time (HH:mm)
     * @param endTime End time (HH:mm)
     * @return Sum of party sizes for overlapping confirmed reservations
     */
    public int getBookedCapacity(UUID spaceId, LocalDate date, String startTime, String endTime) {
        List<TotalPartySizeResult> results =
                reservationRepository.sumPartySizeForOverlappingReservations(
                        spaceId, date, startTime, endTime);

        if (results.isEmpty() || results.get(0).getTotalPartySize() == null) {
            return 0;
        }
        return results.get(0).getTotalPartySize();
    }

    /**
     * Get full availability response for a space on a specific date.
     * Includes all time slots with their capacity information.
     *
     * @param spaceId The space ID
     * @param date The date to check
     * @return Availability response with all time slots
     */
    public AvailabilityResponse getAvailability(UUID spaceId, LocalDate date) {
        Space space = spaceRepository.findActiveById(spaceId)
                .orElseThrow(() -> new SpaceNotFoundException(spaceId));

        // Get restaurant for operating hours
        Restaurant restaurant = restaurantService.getRestaurantById(
                        new ObjectId(space.getRestaurantId()))
                .orElse(null);

        // Get operating hours for the day
        OperatingHours operatingHours = null;
        boolean isOpen = false;
        String operatingHoursString = "Closed";

        if (restaurant != null) {
            int dayOfWeek = date.getDayOfWeek().getValue() % 7;
            operatingHours = restaurant.getOperatingHoursForDay(dayOfWeek);

            if (operatingHours != null && !Boolean.TRUE.equals(operatingHours.getIsClosed())) {
                isOpen = true;
                operatingHoursString = operatingHours.getOpenTime() + " - " + operatingHours.getCloseTime();
            }
        }

        // Get existing confirmed reservations for the date
        List<Reservation> existingReservations =
                reservationRepository.findBySpaceIdAndReservationDateAndStatus(
                        spaceId, date, ReservationStatus.CONFIRMED);

        // Generate time slots with availability
        List<TimeSlotResponse> timeSlots = timeSlotGenerator.generateTimeSlots(
                operatingHours, space, existingReservations);

        return AvailabilityResponse.builder()
                .spaceId(spaceId)
                .spaceName(space.getName())
                .maxCapacity(space.getMaxCapacity())
                .date(date)
                .timeSlots(timeSlots)
                .operatingHours(operatingHoursString)
                .isOpen(isOpen)
                .build();
    }

    /**
     * Check if a reservation can be made with the given party size.
     *
     * @param spaceId The space ID
     * @param date The date
     * @param startTime Start time (HH:mm)
     * @param endTime End time (HH:mm)
     * @param partySize The requested party size
     * @return true if there's sufficient capacity
     */
    public boolean hasCapacity(UUID spaceId, LocalDate date, String startTime, String endTime, int partySize) {
        int availableCapacity = getAvailableCapacity(spaceId, date, startTime, endTime);
        return partySize <= availableCapacity;
    }

    /**
     * Get overlapping reservations for a time slot.
     * Used for detailed capacity analysis.
     *
     * @param spaceId The space ID
     * @param date The date
     * @param startTime Start time (HH:mm)
     * @param endTime End time (HH:mm)
     * @return List of overlapping confirmed reservations
     */
    public List<Reservation> getOverlappingReservations(UUID spaceId, LocalDate date,
                                                        String startTime, String endTime) {
        return reservationRepository.findOverlappingReservations(spaceId, date, startTime, endTime);
    }

    /**
     * Calculate end time based on space slot duration.
     *
     * @param space The space
     * @param startTime Start time (HH:mm)
     * @return End time (HH:mm)
     */
    public String calculateEndTime(Space space, String startTime) {
        return timeSlotGenerator.calculateEndTimeString(startTime, space.getSlotDurationMinutes());
    }
}

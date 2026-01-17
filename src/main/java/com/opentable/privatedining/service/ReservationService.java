package com.opentable.privatedining.service;

import com.opentable.privatedining.dto.request.CancellationRequest;
import com.opentable.privatedining.dto.request.CreateReservationRequest;
import com.opentable.privatedining.dto.response.CancellationResponse;
import com.opentable.privatedining.dto.response.ReservationResponse;
import com.opentable.privatedining.exception.*;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.model.enums.ReservationStatus;
import com.opentable.privatedining.repository.ReservationRepository;
import com.opentable.privatedining.validation.ReservationValidator;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for reservation management with atomic capacity control for concurrency.
 * Uses SlotCapacityService for atomic capacity reservation to prevent overbooking.
 */
@Service
public class ReservationService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;

    private final ReservationRepository reservationRepository;
    private final RestaurantService restaurantService;
    private final SpaceService spaceService;
    private final AvailabilityService availabilityService;
    private final ReservationValidator reservationValidator;
    private final SlotCapacityService slotCapacityService;

    public ReservationService(ReservationRepository reservationRepository,
                              RestaurantService restaurantService,
                              SpaceService spaceService,
                              AvailabilityService availabilityService,
                              ReservationValidator reservationValidator,
                              SlotCapacityService slotCapacityService) {
        this.reservationRepository = reservationRepository;
        this.restaurantService = restaurantService;
        this.spaceService = spaceService;
        this.availabilityService = availabilityService;
        this.reservationValidator = reservationValidator;
        this.slotCapacityService = slotCapacityService;
    }

    /**
     * Get all reservations.
     */
    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    /**
     * Get all reservations with pagination.
     */
    public Page<Reservation> getAllReservations(Pageable pageable) {
        return reservationRepository.findAll(pageable);
    }

    /**
     * Get reservation by ID.
     */
    public Optional<Reservation> getReservationById(ObjectId id) {
        return reservationRepository.findById(id);
    }

    /**
     * Get reservation by ID, throw if not found.
     */
    public Reservation getReservationByIdOrThrow(ObjectId id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    /**
     * Create a new reservation with retry logic for optimistic locking.
     * Implements flexible capacity model (concurrent bookings allowed if sum ≤ max capacity).
     */
    @Transactional
    public Reservation createReservation(CreateReservationRequest request) {
        int attempts = 0;
        OptimisticLockingFailureException lastException = null;

        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                return doCreateReservation(request);
            } catch (OptimisticLockingFailureException e) {
                lastException = e;
                attempts++;
                logger.warn("Optimistic locking failure on reservation creation, attempt {}/{}",
                        attempts, MAX_RETRY_ATTEMPTS);

                if (attempts < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ReservationException("Reservation creation interrupted", ie);
                    }
                }
            }
        }

        logger.error("Failed to create reservation after {} attempts", MAX_RETRY_ATTEMPTS);
        throw new ConcurrentModificationException("Reservation", request.getSpaceId().toString(), MAX_RETRY_ATTEMPTS);
    }

    /**
     * Internal method to create reservation (single attempt).
     * Uses atomic capacity reservation to prevent overbooking under concurrent load.
     */
    private Reservation doCreateReservation(CreateReservationRequest request) {
        // 1. Get space
        Space space = spaceService.getActiveSpaceByIdOrThrow(request.getSpaceId());

        // 2. Get restaurant
        Restaurant restaurant = restaurantService.getRestaurantById(
                        new ObjectId(space.getRestaurantId()))
                .orElseThrow(() -> new RestaurantNotFoundException(
                        new ObjectId(space.getRestaurantId())));

        // 3. Calculate end time
        String endTime = availabilityService.calculateEndTime(space, request.getStartTime());
        LocalTime endTimeLocal = LocalTime.parse(endTime);

        // 4. Run validations (operating hours, time slot alignment, party size)
        reservationValidator.validateReservationRequest(request, restaurant, space, endTimeLocal);

        // 5. ATOMIC capacity reservation using findAndModify
        // This is the critical section that prevents race conditions
        boolean capacityReserved = slotCapacityService.tryReserveCapacity(
                space,
                request.getReservationDate(),
                request.getStartTime(),
                endTime,
                request.getPartySize()
        );

        if (!capacityReserved) {
            // Get current availability for error message
            int availableCapacity = slotCapacityService.getAvailableCapacity(
                    request.getSpaceId(),
                    request.getReservationDate(),
                    request.getStartTime(),
                    space.getMaxCapacity()
            );
            throw new CapacityExceededException(
                    space.getName(),
                    space.getMaxCapacity(),
                    availableCapacity,
                    request.getPartySize()
            );
        }

        // 6. Create and save reservation
        // If this fails, we need to release the capacity
        Reservation reservation;
        try {
            reservation = Reservation.builder()
                    .restaurantId(new ObjectId(space.getRestaurantId()))
                    .spaceId(request.getSpaceId())
                    .reservationDate(request.getReservationDate())
                    .startTime(request.getStartTime())
                    .endTime(endTime)
                    .partySize(request.getPartySize())
                    .customerName(request.getCustomerName())
                    .customerEmail(request.getCustomerEmail())
                    .customerPhone(request.getCustomerPhone())
                    .specialRequests(request.getSpecialRequests())
                    .status(ReservationStatus.CONFIRMED)
                    .build();

            reservation = reservationRepository.save(reservation);
        } catch (Exception e) {
            // Release the reserved capacity on failure
            logger.error("Failed to save reservation, releasing capacity", e);
            slotCapacityService.releaseCapacity(
                    request.getSpaceId(),
                    request.getReservationDate(),
                    request.getStartTime(),
                    request.getPartySize()
            );
            throw e;
        }

        logger.info("Created reservation {} for {} guests at space {} on {}",
                reservation.getId(), reservation.getPartySize(), reservation.getSpaceId(),
                reservation.getReservationDate());

        return reservation;
    }

    /**
     * Cancel a reservation.
     * Releases the reserved capacity back to the slot.
     */
    @Transactional
    public CancellationResponse cancelReservation(ObjectId reservationId, CancellationRequest request) {
        Reservation reservation = getReservationByIdOrThrow(reservationId);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ReservationException("Reservation is already cancelled", "ALREADY_CANCELLED");
        }

        // Release capacity before updating status
        slotCapacityService.releaseCapacity(
                reservation.getSpaceId(),
                reservation.getReservationDate(),
                reservation.getStartTime(),
                reservation.getPartySize()
        );

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancellationReason(request.getReason());
        reservation.setCancelledAt(LocalDateTime.now());

        reservationRepository.save(reservation);

        logger.info("Cancelled reservation {} and released {} capacity",
                reservationId, reservation.getPartySize());

        return CancellationResponse.builder()
                .reservationId(reservationId.toHexString())
                .message("Reservation successfully cancelled")
                .cancellationReason(request.getReason())
                .cancelledAt(reservation.getCancelledAt())
                .build();
    }

    /**
     * Delete a reservation permanently.
     * Releases capacity if the reservation was confirmed.
     */
    public boolean deleteReservation(ObjectId id) {
        Optional<Reservation> optReservation = reservationRepository.findById(id);

        if (optReservation.isPresent()) {
            Reservation reservation = optReservation.get();

            // Release capacity if this was a confirmed reservation
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                slotCapacityService.releaseCapacity(
                        reservation.getSpaceId(),
                        reservation.getReservationDate(),
                        reservation.getStartTime(),
                        reservation.getPartySize()
                );
                logger.info("Released {} capacity on delete for reservation {}",
                        reservation.getPartySize(), id);
            }

            reservationRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Get reservations for a space on a specific date.
     */
    public List<Reservation> getReservationsForSpaceAndDate(UUID spaceId, LocalDate date) {
        return reservationRepository.findBySpaceIdAndReservationDate(spaceId, date);
    }

    /**
     * Get confirmed reservations for a space on a specific date.
     */
    public List<Reservation> getConfirmedReservationsForSpaceAndDate(UUID spaceId, LocalDate date) {
        return reservationRepository.findBySpaceIdAndReservationDateAndStatus(
                spaceId, date, ReservationStatus.CONFIRMED);
    }

    /**
     * Get reservations for a restaurant within a date range.
     */
    public List<Reservation> getReservationsForRestaurant(ObjectId restaurantId,
                                                          LocalDate startDate,
                                                          LocalDate endDate) {
        return reservationRepository.findByRestaurantIdAndReservationDateBetween(
                restaurantId, startDate, endDate);
    }

    /**
     * Get confirmed reservations for a restaurant within a date range.
     */
    public List<Reservation> getConfirmedReservationsForRestaurant(ObjectId restaurantId,
                                                                   LocalDate startDate,
                                                                   LocalDate endDate) {
        return reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                restaurantId, startDate, endDate, ReservationStatus.CONFIRMED);
    }

    /**
     * Get reservations by customer email.
     */
    public List<Reservation> getReservationsByCustomerEmail(String email) {
        return reservationRepository.findByCustomerEmailIgnoreCase(email);
    }

    /**
     * Get upcoming reservations for a customer.
     */
    public List<Reservation> getUpcomingReservationsForCustomer(String email) {
        return reservationRepository.findUpcomingReservationsByEmail(email, LocalDate.now());
    }

    /**
     * Convert Reservation entity to ReservationResponse DTO.
     */
    public ReservationResponse toResponse(Reservation reservation) {
        Space space = spaceService.getSpaceById(reservation.getSpaceId()).orElse(null);
        Restaurant restaurant = restaurantService.getRestaurantById(reservation.getRestaurantId()).orElse(null);

        return ReservationResponse.builder()
                .id(reservation.getId().toHexString())
                .spaceId(reservation.getSpaceId())
                .spaceName(space != null ? space.getName() : null)
                .restaurantId(reservation.getRestaurantId().toHexString())
                .restaurantName(restaurant != null ? restaurant.getName() : null)
                .reservationDate(reservation.getReservationDate())
                .startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime())
                .partySize(reservation.getPartySize())
                .customerName(reservation.getCustomerName())
                .customerEmail(reservation.getCustomerEmail())
                .customerPhone(reservation.getCustomerPhone())
                .specialRequests(reservation.getSpecialRequests())
                .status(reservation.getStatus())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }
}

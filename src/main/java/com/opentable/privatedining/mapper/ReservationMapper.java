package com.opentable.privatedining.mapper;

import com.opentable.privatedining.dto.ReservationDTO;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.enums.ReservationStatus;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Mapper for Reservation entity and DTO.
 * Handles conversion between the new model (date + time strings) and legacy DTO (LocalDateTime).
 */
@Component
public class ReservationMapper {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Convert Reservation entity to legacy DTO.
     * The DTO uses LocalDateTime for compatibility with existing clients.
     */
    public ReservationDTO toDTO(Reservation reservation) {
        if (reservation == null) {
            return null;
        }

        // Convert date + time string to LocalDateTime for legacy DTO
        LocalDateTime startDateTime = reservation.getStartDateTime();
        LocalDateTime endDateTime = reservation.getEndDateTime();

        ReservationStatus status = reservation.getStatus() != null
                ? reservation.getStatus()
                : ReservationStatus.CONFIRMED;

        ReservationDTO dto = new ReservationDTO(
                reservation.getRestaurantId() != null ? reservation.getRestaurantId().toHexString() : null,
                reservation.getSpaceId(),
                reservation.getCustomerEmail(),
                startDateTime,
                endDateTime,
                reservation.getPartySize(),
                status
        );

        if (reservation.getId() != null) {
            dto.setId(reservation.getId().toHexString());
        }

        return dto;
    }

    /**
     * Convert legacy DTO to Reservation entity.
     * Converts LocalDateTime to the new format (date + time string).
     */
    public Reservation toModel(ReservationDTO reservationDTO) {
        if (reservationDTO == null) {
            return null;
        }

        Reservation reservation = Reservation.builder()
                .spaceId(reservationDTO.getSpaceId())
                .customerEmail(reservationDTO.getCustomerEmail())
                .partySize(reservationDTO.getPartySize())
                .build();

        // Convert LocalDateTime to date + time string
        if (reservationDTO.getStartTime() != null) {
            reservation.setReservationDate(reservationDTO.getStartTime().toLocalDate());
            reservation.setStartTime(reservationDTO.getStartTime().toLocalTime().format(TIME_FORMATTER));
        }

        if (reservationDTO.getEndTime() != null) {
            reservation.setEndTime(reservationDTO.getEndTime().toLocalTime().format(TIME_FORMATTER));
        }

        // Set status (now an enum)
        if (reservationDTO.getStatus() != null) {
            reservation.setStatus(reservationDTO.getStatus());
        } else {
            reservation.setStatus(ReservationStatus.CONFIRMED);
        }

        // Set IDs
        if (reservationDTO.getId() != null && !reservationDTO.getId().isEmpty()) {
            try {
                reservation.setId(new ObjectId(reservationDTO.getId()));
            } catch (IllegalArgumentException e) {
                // Invalid ObjectId format, leave it null for new entities
            }
        }

        if (reservationDTO.getRestaurantId() != null && !reservationDTO.getRestaurantId().isEmpty()) {
            try {
                reservation.setRestaurantId(new ObjectId(reservationDTO.getRestaurantId()));
            } catch (IllegalArgumentException e) {
                // Invalid ObjectId format, this should be handled by validation
            }
        }

        return reservation;
    }
}

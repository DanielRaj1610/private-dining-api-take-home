package com.opentable.privatedining.mapper;

import com.opentable.privatedining.dto.ReservationDTO;
import com.opentable.privatedining.model.Reservation;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {

    public ReservationDTO toDTO(Reservation reservation) {
        if (reservation == null) {
            return null;
        }

        ReservationDTO dto = new ReservationDTO(
                reservation.getRestaurantId() != null ? reservation.getRestaurantId().toString() : null,
                reservation.getSpaceId(),
                reservation.getCustomerEmail(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getPartySize(),
                reservation.getStatus()
        );

        if (reservation.getId() != null) {
            dto.setId(reservation.getId().toString());
        }

        return dto;
    }

    public Reservation toModel(ReservationDTO reservationDTO) {
        if (reservationDTO == null) {
            return null;
        }

        Reservation reservation = new Reservation();
        reservation.setCustomerEmail(reservationDTO.getCustomerEmail());
        reservation.setSpaceId(reservationDTO.getSpaceId());
        reservation.setStartTime(reservationDTO.getStartTime());
        reservation.setEndTime(reservationDTO.getEndTime());
        reservation.setPartySize(reservationDTO.getPartySize());
        reservation.setStatus(reservationDTO.getStatus());

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
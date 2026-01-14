package com.opentable.privatedining.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opentable.privatedining.dto.ReservationDTO;
import com.opentable.privatedining.exception.GlobalExceptionHandler;
import com.opentable.privatedining.exception.InvalidPartySizeException;
import com.opentable.privatedining.exception.ReservationConflictException;
import com.opentable.privatedining.exception.ReservationNotFoundException;
import com.opentable.privatedining.exception.RestaurantNotFoundException;
import com.opentable.privatedining.exception.SpaceNotFoundException;
import com.opentable.privatedining.mapper.ReservationMapper;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.service.ReservationService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ReservationController.class, GlobalExceptionHandler.class})
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    @MockBean
    private ReservationMapper reservationMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllReservations_ShouldReturnListOfReservations() throws Exception {
        // Given
        Reservation reservation1 = createTestReservation("customer1@example.com", 4);
        Reservation reservation2 = createTestReservation("customer2@example.com", 6);
        List<Reservation> reservations = Arrays.asList(reservation1, reservation2);

        ReservationDTO reservationDTO1 = createTestReservationDTO("customer1@example.com", 4);
        ReservationDTO reservationDTO2 = createTestReservationDTO("customer2@example.com", 6);

        when(reservationService.getAllReservations()).thenReturn(reservations);
        when(reservationMapper.toDTO(any(Reservation.class))).thenReturn(reservationDTO1).thenReturn(reservationDTO2);

        // When & Then
        mockMvc.perform(get("/v1/reservations"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].customerEmail").value("customer1@example.com"))
                .andExpect(jsonPath("$[0].partySize").value(4))
                .andExpect(jsonPath("$[1].customerEmail").value("customer2@example.com"))
                .andExpect(jsonPath("$[1].partySize").value(6));
    }

    @Test
    void getReservationById_WhenReservationExists_ShouldReturnReservation() throws Exception {
        // Given
        ObjectId reservationId = new ObjectId();
        Reservation reservation = createTestReservation("customer@example.com", 4);
        reservation.setId(reservationId);

        ReservationDTO reservationDTO = createTestReservationDTO("customer@example.com", 4);
        reservationDTO.setId(reservationId.toString());

        when(reservationService.getReservationById(reservationId)).thenReturn(Optional.of(reservation));
        when(reservationMapper.toDTO(any(Reservation.class))).thenReturn(reservationDTO);

        // When & Then
        mockMvc.perform(get("/v1/reservations/" + reservationId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerEmail").value("customer@example.com"))
                .andExpect(jsonPath("$.partySize").value(4));
    }

    @Test
    void getReservationById_WhenReservationNotFound_ShouldReturn404() throws Exception {
        // Given
        ObjectId reservationId = new ObjectId();
        when(reservationService.getReservationById(reservationId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/v1/reservations/" + reservationId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReservationById_WhenInvalidId_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(get("/v1/reservations/invalid-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReservation_WhenValidReservation_ShouldReturnCreatedReservation() throws Exception {
        // Given
        ReservationDTO inputReservationDTO = createTestReservationDTO("customer@example.com", 4);
        Reservation reservation = createTestReservation("customer@example.com", 4);
        Reservation savedReservation = createTestReservation("customer@example.com", 4);
        savedReservation.setId(new ObjectId());
        ReservationDTO savedReservationDTO = createTestReservationDTO("customer@example.com", 4);
        savedReservationDTO.setId(savedReservation.getId().toString());

        when(reservationMapper.toModel(any(ReservationDTO.class))).thenReturn(reservation);
        when(reservationService.createReservation(any(Reservation.class))).thenReturn(savedReservation);
        when(reservationMapper.toDTO(any(Reservation.class))).thenReturn(savedReservationDTO);

        // When & Then
        mockMvc.perform(post("/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputReservationDTO)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerEmail").value("customer@example.com"))
                .andExpect(jsonPath("$.partySize").value(4));
    }

    @Test
    void createReservation_WhenInvalidPartySize_ShouldReturn400() throws Exception {
        // Given
        ReservationDTO inputReservationDTO = createTestReservationDTO("invalid@example.com", 4);
        Reservation reservation = createTestReservation("invalid@example.com", 4);

        when(reservationMapper.toModel(any(ReservationDTO.class))).thenReturn(reservation);
        when(reservationService.createReservation(any(Reservation.class))).thenThrow(new InvalidPartySizeException(4, 2, 3));

        // When & Then
        mockMvc.perform(post("/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputReservationDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReservation_WhenReservationExists_ShouldReturn204() throws Exception {
        // Given
        ObjectId reservationId = new ObjectId();
        when(reservationService.deleteReservation(reservationId)).thenReturn(true);

        // When & Then
        mockMvc.perform(delete("/v1/reservations/" + reservationId.toString()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReservation_WhenReservationNotFound_ShouldReturn404() throws Exception {
        // Given
        ObjectId reservationId = new ObjectId();
        when(reservationService.deleteReservation(reservationId)).thenReturn(false);

        // When & Then
        mockMvc.perform(delete("/v1/reservations/" + reservationId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReservation_WhenInvalidId_ShouldReturn400() throws Exception {
        // When & Then
        mockMvc.perform(delete("/v1/reservations/invalid-id"))
                .andExpect(status().isBadRequest());
    }

    private Reservation createTestReservation(String customerEmail, int partySize) {
        Reservation reservation = new Reservation();
        reservation.setCustomerEmail(customerEmail);
        reservation.setPartySize(partySize);
        reservation.setRestaurantId(new ObjectId());
        reservation.setSpaceId(UUID.randomUUID());
        reservation.setStartTime(LocalDateTime.now().plusDays(1));
        reservation.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        reservation.setStatus("CONFIRMED");
        return reservation;
    }

    private ReservationDTO createTestReservationDTO(String customerEmail, int partySize) {
        ReservationDTO reservationDTO = new ReservationDTO();
        reservationDTO.setCustomerEmail(customerEmail);
        reservationDTO.setPartySize(partySize);
        reservationDTO.setRestaurantId(new ObjectId().toString());
        reservationDTO.setSpaceId(UUID.randomUUID());
        reservationDTO.setStartTime(LocalDateTime.now().plusDays(1));
        reservationDTO.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        reservationDTO.setStatus("CONFIRMED");
        return reservationDTO;
    }
}
package com.opentable.privatedining.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opentable.privatedining.dto.request.CancellationRequest;
import com.opentable.privatedining.dto.request.CreateReservationRequest;
import com.opentable.privatedining.dto.response.CancellationResponse;
import com.opentable.privatedining.dto.response.ReservationResponse;
import com.opentable.privatedining.exception.CapacityExceededException;
import com.opentable.privatedining.exception.GlobalExceptionHandler;
import com.opentable.privatedining.exception.ReservationNotFoundException;
import com.opentable.privatedining.mapper.ReservationMapper;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.enums.ReservationStatus;
import com.opentable.privatedining.service.ReservationService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationController Tests")
class ReservationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReservationService reservationService;

    @Mock
    private ReservationMapper reservationMapper;

    @InjectMocks
    private ReservationController reservationController;

    private ObjectMapper objectMapper;
    private UUID spaceId;
    private ObjectId reservationId;
    private ObjectId restaurantId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reservationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        spaceId = UUID.randomUUID();
        reservationId = new ObjectId();
        restaurantId = new ObjectId();
    }

    private Reservation createTestReservation() {
        return Reservation.builder()
                .id(reservationId)
                .restaurantId(restaurantId)
                .spaceId(spaceId)
                .reservationDate(LocalDate.now().plusDays(7))
                .startTime("18:00")
                .endTime("19:00")
                .partySize(8)
                .customerName("John Smith")
                .customerEmail("john@example.com")
                .status(ReservationStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ReservationResponse createTestResponse() {
        return ReservationResponse.builder()
                .id(reservationId.toHexString())
                .spaceId(spaceId)
                .spaceName("Garden Room")
                .restaurantId(restaurantId.toHexString())
                .restaurantName("Test Restaurant")
                .reservationDate(LocalDate.now().plusDays(7))
                .startTime("18:00")
                .endTime("19:00")
                .partySize(8)
                .customerName("John Smith")
                .customerEmail("john@example.com")
                .status(ReservationStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/reservations - Create Reservation")
    class CreateReservationTests {

        @Test
        @DisplayName("Should create reservation successfully")
        void shouldCreateReservationSuccessfully() throws Exception {
            // Given
            CreateReservationRequest request = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(LocalDate.now().plusDays(7))
                    .startTime("18:00")
                    .partySize(8)
                    .customerName("John Smith")
                    .customerEmail("john@example.com")
                    .build();

            Reservation savedReservation = createTestReservation();
            ReservationResponse response = createTestResponse();

            when(reservationService.createReservation(any(CreateReservationRequest.class)))
                    .thenReturn(savedReservation);
            when(reservationService.toResponse(savedReservation)).thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(reservationId.toHexString()))
                    .andExpect(jsonPath("$.spaceName").value("Garden Room"))
                    .andExpect(jsonPath("$.partySize").value(8))
                    .andExpect(jsonPath("$.customerName").value("John Smith"))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));

            verify(reservationService).createReservation(any(CreateReservationRequest.class));
        }

        @Test
        @DisplayName("Should return 409 when capacity exceeded")
        void shouldReturn409WhenCapacityExceeded() throws Exception {
            // Given
            CreateReservationRequest request = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(LocalDate.now().plusDays(7))
                    .startTime("18:00")
                    .partySize(15)
                    .customerName("John Smith")
                    .customerEmail("john@example.com")
                    .build();

            when(reservationService.createReservation(any(CreateReservationRequest.class)))
                    .thenThrow(new CapacityExceededException("Garden Room", 20, 5, 15));

            // When & Then
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CAPACITY_EXCEEDED"));
        }

        @Test
        @DisplayName("Should return 400 for invalid request - missing required fields")
        void shouldReturn400ForInvalidRequest() throws Exception {
            // Given - Missing required fields
            String invalidRequest = "{}";

            // When & Then
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for invalid email format")
        void shouldReturn400ForInvalidEmail() throws Exception {
            // Given
            CreateReservationRequest request = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(LocalDate.now().plusDays(7))
                    .startTime("18:00")
                    .partySize(8)
                    .customerName("John Smith")
                    .customerEmail("invalid-email")
                    .build();

            // When & Then
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reservations/{id} - Get Reservation")
    class GetReservationTests {

        @Test
        @DisplayName("Should return reservation when found")
        void shouldReturnReservationWhenFound() throws Exception {
            // Given
            Reservation reservation = createTestReservation();
            ReservationResponse response = createTestResponse();

            when(reservationService.getReservationById(reservationId))
                    .thenReturn(Optional.of(reservation));
            when(reservationService.toResponse(reservation)).thenReturn(response);

            // When & Then
            mockMvc.perform(get("/api/v1/reservations/{id}", reservationId.toHexString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reservationId.toHexString()))
                    .andExpect(jsonPath("$.customerName").value("John Smith"));
        }

        @Test
        @DisplayName("Should return 404 when reservation not found")
        void shouldReturn404WhenNotFound() throws Exception {
            // Given
            when(reservationService.getReservationById(any(ObjectId.class)))
                    .thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(get("/api/v1/reservations/{id}", new ObjectId().toHexString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 for invalid ID format")
        void shouldReturn400ForInvalidIdFormat() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/reservations/{id}", "invalid-id"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/reservations/{id}/cancel - Cancel Reservation")
    class CancelReservationTests {

        @Test
        @DisplayName("Should cancel reservation successfully")
        void shouldCancelReservationSuccessfully() throws Exception {
            // Given
            CancellationRequest cancelRequest = CancellationRequest.builder()
                    .reason("Change of plans")
                    .build();

            CancellationResponse response = CancellationResponse.builder()
                    .reservationId(reservationId.toHexString())
                    .message("Reservation successfully cancelled")
                    .cancellationReason("Change of plans")
                    .cancelledAt(LocalDateTime.now())
                    .build();

            when(reservationService.cancelReservation(eq(reservationId), any(CancellationRequest.class)))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/api/v1/reservations/{id}/cancel", reservationId.toHexString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cancelRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservationId").value(reservationId.toHexString()))
                    .andExpect(jsonPath("$.message").value("Reservation successfully cancelled"))
                    .andExpect(jsonPath("$.cancellationReason").value("Change of plans"));

            verify(reservationService).cancelReservation(eq(reservationId), any(CancellationRequest.class));
        }

        @Test
        @DisplayName("Should cancel reservation without reason")
        void shouldCancelReservationWithoutReason() throws Exception {
            // Given
            CancellationResponse response = CancellationResponse.builder()
                    .reservationId(reservationId.toHexString())
                    .message("Reservation successfully cancelled")
                    .cancelledAt(LocalDateTime.now())
                    .build();

            when(reservationService.cancelReservation(eq(reservationId), any(CancellationRequest.class)))
                    .thenReturn(response);

            // When & Then - Empty body
            mockMvc.perform(post("/api/v1/reservations/{id}/cancel", reservationId.toHexString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservationId").value(reservationId.toHexString()));
        }

        @Test
        @DisplayName("Should return 404 when reservation not found for cancellation")
        void shouldReturn404WhenReservationNotFoundForCancellation() throws Exception {
            // Given
            when(reservationService.cancelReservation(any(ObjectId.class), any(CancellationRequest.class)))
                    .thenThrow(new ReservationNotFoundException(reservationId));

            // When & Then
            mockMvc.perform(post("/api/v1/reservations/{id}/cancel", reservationId.toHexString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 for invalid ID format")
        void shouldReturn400ForInvalidIdFormatOnCancel() throws Exception {
            // When & Then
            mockMvc.perform(post("/api/v1/reservations/{id}/cancel", "invalid-id")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/reservations/{id} - Delete Reservation")
    class DeleteReservationTests {

        @Test
        @DisplayName("Should delete reservation successfully")
        void shouldDeleteReservationSuccessfully() throws Exception {
            // Given
            when(reservationService.deleteReservation(reservationId)).thenReturn(true);

            // When & Then
            mockMvc.perform(delete("/api/v1/reservations/{id}", reservationId.toHexString()))
                    .andExpect(status().isNoContent());

            verify(reservationService).deleteReservation(reservationId);
        }

        @Test
        @DisplayName("Should return 404 when reservation not found for deletion")
        void shouldReturn404WhenReservationNotFoundForDeletion() throws Exception {
            // Given
            when(reservationService.deleteReservation(any(ObjectId.class))).thenReturn(false);

            // When & Then
            mockMvc.perform(delete("/api/v1/reservations/{id}", new ObjectId().toHexString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 for invalid ID format on delete")
        void shouldReturn400ForInvalidIdFormatOnDelete() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/v1/reservations/{id}", "invalid-id"))
                    .andExpect(status().isBadRequest());
        }
    }
}

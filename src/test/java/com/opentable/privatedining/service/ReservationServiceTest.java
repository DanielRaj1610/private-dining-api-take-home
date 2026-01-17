package com.opentable.privatedining.service;

import com.opentable.privatedining.dto.request.CancellationRequest;
import com.opentable.privatedining.dto.request.CreateReservationRequest;
import com.opentable.privatedining.dto.response.CancellationResponse;
import com.opentable.privatedining.exception.CapacityExceededException;
import com.opentable.privatedining.exception.ConcurrentModificationException;
import com.opentable.privatedining.exception.ReservationException;
import com.opentable.privatedining.exception.ReservationNotFoundException;
import com.opentable.privatedining.model.OperatingHours;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.model.enums.ReservationStatus;
import com.opentable.privatedining.repository.ReservationRepository;
import com.opentable.privatedining.validation.ReservationValidator;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationService Tests")
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RestaurantService restaurantService;

    @Mock
    private SpaceService spaceService;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private ReservationValidator reservationValidator;

    @Mock
    private SlotCapacityService slotCapacityService;

    @InjectMocks
    private ReservationService reservationService;

    private Space testSpace;
    private Restaurant testRestaurant;
    private ObjectId restaurantId;
    private UUID spaceId;

    @BeforeEach
    void setUp() {
        restaurantId = new ObjectId();
        spaceId = UUID.randomUUID();

        testSpace = Space.builder()
                .id(spaceId)
                .restaurantId(restaurantId.toHexString())
                .name("Garden Room")
                .minCapacity(2)
                .maxCapacity(20)
                .slotDurationMinutes(60)
                .bufferMinutes(15)
                .isActive(true)
                .build();

        List<OperatingHours> operatingHours = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            operatingHours.add(OperatingHours.builder()
                    .dayOfWeek(i)
                    .openTime("09:00")
                    .closeTime("22:00")
                    .isClosed(i == 0) // Sunday closed
                    .build());
        }

        testRestaurant = Restaurant.builder()
                .id(restaurantId)
                .name("Test Restaurant")
                .timezone("America/New_York")
                .operatingHours(operatingHours)
                .build();
    }

    private CreateReservationRequest createValidRequest() {
        return CreateReservationRequest.builder()
                .spaceId(spaceId)
                .reservationDate(LocalDate.now().plusDays(7))
                .startTime("18:00")
                .partySize(8)
                .customerName("John Smith")
                .customerEmail("john@example.com")
                .customerPhone("+1-555-123-4567")
                .specialRequests("Anniversary dinner")
                .build();
    }

    private Reservation createTestReservation() {
        return Reservation.builder()
                .id(new ObjectId())
                .restaurantId(restaurantId)
                .spaceId(spaceId)
                .reservationDate(LocalDate.now().plusDays(7))
                .startTime("18:00")
                .endTime("19:00")
                .partySize(8)
                .customerName("John Smith")
                .customerEmail("john@example.com")
                .status(ReservationStatus.CONFIRMED)
                .build();
    }

    @Nested
    @DisplayName("createReservation tests")
    class CreateReservationTests {

        @Test
        @DisplayName("Should successfully create reservation when capacity available")
        void shouldCreateReservationSuccessfully() {
            // Given
            CreateReservationRequest request = createValidRequest();
            Reservation expectedReservation = createTestReservation();

            when(spaceService.getActiveSpaceByIdOrThrow(spaceId)).thenReturn(testSpace);
            when(restaurantService.getRestaurantById(any(ObjectId.class)))
                    .thenReturn(Optional.of(testRestaurant));
            when(availabilityService.calculateEndTime(testSpace, "18:00")).thenReturn("19:00");
            doNothing().when(reservationValidator).validateReservationRequest(
                    any(), any(), any(), any());
            when(slotCapacityService.tryReserveCapacity(
                    eq(testSpace), any(), eq("18:00"), eq("19:00"), eq(8)))
                    .thenReturn(true);
            when(reservationRepository.save(any(Reservation.class))).thenReturn(expectedReservation);

            // When
            Reservation result = reservationService.createReservation(request);

            // Then
            assertNotNull(result);
            assertEquals("John Smith", result.getCustomerName());
            assertEquals(8, result.getPartySize());
            assertEquals(ReservationStatus.CONFIRMED, result.getStatus());

            verify(slotCapacityService).tryReserveCapacity(
                    eq(testSpace), any(), eq("18:00"), eq("19:00"), eq(8));
            verify(reservationRepository).save(any(Reservation.class));
        }

        @Test
        @DisplayName("Should throw CapacityExceededException when insufficient capacity")
        void shouldThrowCapacityExceededWhenInsufficientCapacity() {
            // Given
            CreateReservationRequest request = createValidRequest();

            when(spaceService.getActiveSpaceByIdOrThrow(spaceId)).thenReturn(testSpace);
            when(restaurantService.getRestaurantById(any(ObjectId.class)))
                    .thenReturn(Optional.of(testRestaurant));
            when(availabilityService.calculateEndTime(testSpace, "18:00")).thenReturn("19:00");
            doNothing().when(reservationValidator).validateReservationRequest(
                    any(), any(), any(), any());
            when(slotCapacityService.tryReserveCapacity(
                    eq(testSpace), any(), eq("18:00"), eq("19:00"), eq(8)))
                    .thenReturn(false);
            when(slotCapacityService.getAvailableCapacity(
                    eq(spaceId), any(), eq("18:00"), eq(20)))
                    .thenReturn(5);

            // When & Then
            CapacityExceededException exception = assertThrows(
                    CapacityExceededException.class,
                    () -> reservationService.createReservation(request)
            );

            assertEquals("Garden Room", exception.getSpaceName());
            assertEquals(20, exception.getMaxCapacity());
            assertEquals(5, exception.getAvailableCapacity());
            assertEquals(8, exception.getRequestedPartySize());

            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should release capacity when save fails")
        void shouldReleaseCapacityWhenSaveFails() {
            // Given
            CreateReservationRequest request = createValidRequest();

            when(spaceService.getActiveSpaceByIdOrThrow(spaceId)).thenReturn(testSpace);
            when(restaurantService.getRestaurantById(any(ObjectId.class)))
                    .thenReturn(Optional.of(testRestaurant));
            when(availabilityService.calculateEndTime(testSpace, "18:00")).thenReturn("19:00");
            doNothing().when(reservationValidator).validateReservationRequest(
                    any(), any(), any(), any());
            when(slotCapacityService.tryReserveCapacity(
                    eq(testSpace), any(), eq("18:00"), eq("19:00"), eq(8)))
                    .thenReturn(true);
            when(reservationRepository.save(any(Reservation.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then
            assertThrows(RuntimeException.class, () -> reservationService.createReservation(request));

            verify(slotCapacityService).releaseCapacity(eq(spaceId), any(), eq("18:00"), eq(8));
        }

        @Test
        @DisplayName("Should retry on optimistic locking failure and eventually fail")
        void shouldRetryOnOptimisticLockingFailure() {
            // Given
            CreateReservationRequest request = createValidRequest();

            when(spaceService.getActiveSpaceByIdOrThrow(spaceId)).thenReturn(testSpace);
            when(restaurantService.getRestaurantById(any(ObjectId.class)))
                    .thenReturn(Optional.of(testRestaurant));
            when(availabilityService.calculateEndTime(testSpace, "18:00")).thenReturn("19:00");
            doNothing().when(reservationValidator).validateReservationRequest(
                    any(), any(), any(), any());
            when(slotCapacityService.tryReserveCapacity(any(), any(), any(), any(), anyInt()))
                    .thenThrow(new OptimisticLockingFailureException("Concurrent modification"));

            // When & Then
            ConcurrentModificationException exception = assertThrows(
                    ConcurrentModificationException.class,
                    () -> reservationService.createReservation(request)
            );

            assertTrue(exception.getMessage().contains("3"));
            verify(slotCapacityService, times(3)).tryReserveCapacity(
                    any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("Should succeed on retry after optimistic locking failure")
        void shouldSucceedOnRetryAfterOptimisticLockingFailure() {
            // Given
            CreateReservationRequest request = createValidRequest();
            Reservation expectedReservation = createTestReservation();

            when(spaceService.getActiveSpaceByIdOrThrow(spaceId)).thenReturn(testSpace);
            when(restaurantService.getRestaurantById(any(ObjectId.class)))
                    .thenReturn(Optional.of(testRestaurant));
            when(availabilityService.calculateEndTime(testSpace, "18:00")).thenReturn("19:00");
            doNothing().when(reservationValidator).validateReservationRequest(
                    any(), any(), any(), any());
            when(slotCapacityService.tryReserveCapacity(any(), any(), any(), any(), anyInt()))
                    .thenThrow(new OptimisticLockingFailureException("Concurrent modification"))
                    .thenReturn(true);
            when(reservationRepository.save(any(Reservation.class))).thenReturn(expectedReservation);

            // When
            Reservation result = reservationService.createReservation(request);

            // Then
            assertNotNull(result);
            verify(slotCapacityService, times(2)).tryReserveCapacity(
                    any(), any(), any(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("cancelReservation tests")
    class CancelReservationTests {

        @Test
        @DisplayName("Should successfully cancel reservation")
        void shouldCancelReservationSuccessfully() {
            // Given
            Reservation reservation = createTestReservation();
            ObjectId reservationId = reservation.getId();
            CancellationRequest cancelRequest = CancellationRequest.builder()
                    .reason("Change of plans")
                    .build();

            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            CancellationResponse result = reservationService.cancelReservation(reservationId, cancelRequest);

            // Then
            assertNotNull(result);
            assertEquals(reservationId.toHexString(), result.getReservationId());
            assertEquals("Change of plans", result.getCancellationReason());
            assertNotNull(result.getCancelledAt());

            verify(slotCapacityService).releaseCapacity(
                    eq(spaceId), any(), eq("18:00"), eq(8));
            verify(reservationRepository).save(argThat(r ->
                    r.getStatus() == ReservationStatus.CANCELLED &&
                    r.getCancellationReason().equals("Change of plans")
            ));
        }

        @Test
        @DisplayName("Should throw exception when reservation not found")
        void shouldThrowExceptionWhenReservationNotFound() {
            // Given
            ObjectId reservationId = new ObjectId();
            CancellationRequest cancelRequest = CancellationRequest.builder()
                    .reason("Change of plans")
                    .build();

            when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(ReservationNotFoundException.class,
                    () -> reservationService.cancelReservation(reservationId, cancelRequest));

            verify(slotCapacityService, never()).releaseCapacity(any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("Should throw exception when reservation already cancelled")
        void shouldThrowExceptionWhenAlreadyCancelled() {
            // Given
            Reservation reservation = createTestReservation();
            reservation.setStatus(ReservationStatus.CANCELLED);
            ObjectId reservationId = reservation.getId();
            CancellationRequest cancelRequest = CancellationRequest.builder()
                    .reason("Change of plans")
                    .build();

            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

            // When & Then
            ReservationException exception = assertThrows(
                    ReservationException.class,
                    () -> reservationService.cancelReservation(reservationId, cancelRequest)
            );

            assertTrue(exception.getMessage().contains("already cancelled"));
            verify(slotCapacityService, never()).releaseCapacity(any(), any(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("getReservationById tests")
    class GetReservationTests {

        @Test
        @DisplayName("Should return reservation when found")
        void shouldReturnReservationWhenFound() {
            // Given
            Reservation reservation = createTestReservation();
            ObjectId reservationId = reservation.getId();

            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

            // When
            Optional<Reservation> result = reservationService.getReservationById(reservationId);

            // Then
            assertTrue(result.isPresent());
            assertEquals(reservationId, result.get().getId());
        }

        @Test
        @DisplayName("Should return empty when reservation not found")
        void shouldReturnEmptyWhenNotFound() {
            // Given
            ObjectId reservationId = new ObjectId();
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

            // When
            Optional<Reservation> result = reservationService.getReservationById(reservationId);

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Should throw exception when using getByIdOrThrow and not found")
        void shouldThrowExceptionWhenUsingGetByIdOrThrowAndNotFound() {
            // Given
            ObjectId reservationId = new ObjectId();
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(ReservationNotFoundException.class,
                    () -> reservationService.getReservationByIdOrThrow(reservationId));
        }
    }

    @Nested
    @DisplayName("deleteReservation tests")
    class DeleteReservationTests {

        @Test
        @DisplayName("Should delete confirmed reservation and release capacity")
        void shouldDeleteConfirmedReservationAndReleaseCapacity() {
            // Given
            Reservation reservation = createTestReservation();
            ObjectId reservationId = reservation.getId();

            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

            // When
            boolean result = reservationService.deleteReservation(reservationId);

            // Then
            assertTrue(result);
            verify(slotCapacityService).releaseCapacity(
                    eq(spaceId), any(), eq("18:00"), eq(8));
            verify(reservationRepository).deleteById(reservationId);
        }

        @Test
        @DisplayName("Should delete cancelled reservation without releasing capacity")
        void shouldDeleteCancelledReservationWithoutReleasingCapacity() {
            // Given
            Reservation reservation = createTestReservation();
            reservation.setStatus(ReservationStatus.CANCELLED);
            ObjectId reservationId = reservation.getId();

            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

            // When
            boolean result = reservationService.deleteReservation(reservationId);

            // Then
            assertTrue(result);
            verify(slotCapacityService, never()).releaseCapacity(any(), any(), any(), anyInt());
            verify(reservationRepository).deleteById(reservationId);
        }

        @Test
        @DisplayName("Should return false when reservation not found")
        void shouldReturnFalseWhenReservationNotFound() {
            // Given
            ObjectId reservationId = new ObjectId();
            when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

            // When
            boolean result = reservationService.deleteReservation(reservationId);

            // Then
            assertFalse(result);
            verify(reservationRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("Query tests")
    class QueryTests {

        @Test
        @DisplayName("Should get all reservations")
        void shouldGetAllReservations() {
            // Given
            List<Reservation> reservations = List.of(
                    createTestReservation(),
                    createTestReservation()
            );
            when(reservationRepository.findAll()).thenReturn(reservations);

            // When
            List<Reservation> result = reservationService.getAllReservations();

            // Then
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should get reservations by customer email")
        void shouldGetReservationsByCustomerEmail() {
            // Given
            String email = "john@example.com";
            List<Reservation> reservations = List.of(createTestReservation());
            when(reservationRepository.findByCustomerEmailIgnoreCase(email))
                    .thenReturn(reservations);

            // When
            List<Reservation> result = reservationService.getReservationsByCustomerEmail(email);

            // Then
            assertEquals(1, result.size());
            assertEquals(email, result.get(0).getCustomerEmail());
        }

        @Test
        @DisplayName("Should get confirmed reservations for space and date")
        void shouldGetConfirmedReservationsForSpaceAndDate() {
            // Given
            LocalDate date = LocalDate.now().plusDays(7);
            List<Reservation> reservations = List.of(createTestReservation());
            when(reservationRepository.findBySpaceIdAndReservationDateAndStatus(
                    spaceId, date, ReservationStatus.CONFIRMED))
                    .thenReturn(reservations);

            // When
            List<Reservation> result = reservationService
                    .getConfirmedReservationsForSpaceAndDate(spaceId, date);

            // Then
            assertEquals(1, result.size());
            assertEquals(ReservationStatus.CONFIRMED, result.get(0).getStatus());
        }
    }

    @Nested
    @DisplayName("toResponse tests")
    class ToResponseTests {

        @Test
        @DisplayName("Should convert reservation to response DTO")
        void shouldConvertReservationToResponse() {
            // Given
            Reservation reservation = createTestReservation();

            when(spaceService.getSpaceById(spaceId)).thenReturn(Optional.of(testSpace));
            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));

            // When
            var result = reservationService.toResponse(reservation);

            // Then
            assertNotNull(result);
            assertEquals(reservation.getId().toHexString(), result.getId());
            assertEquals("Garden Room", result.getSpaceName());
            assertEquals("Test Restaurant", result.getRestaurantName());
            assertEquals(reservation.getCustomerName(), result.getCustomerName());
            assertEquals(reservation.getPartySize(), result.getPartySize());
        }
    }
}

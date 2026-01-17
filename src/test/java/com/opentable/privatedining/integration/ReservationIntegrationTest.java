package com.opentable.privatedining.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opentable.privatedining.dto.request.CancellationRequest;
import com.opentable.privatedining.dto.request.CreateReservationRequest;
import com.opentable.privatedining.dto.response.ReservationResponse;
import com.opentable.privatedining.model.OperatingHours;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.model.enums.ReservationStatus;
import com.opentable.privatedining.repository.ReservationRepository;
import com.opentable.privatedining.repository.RestaurantRepository;
import com.opentable.privatedining.repository.SlotCapacityRepository;
import com.opentable.privatedining.repository.SpaceRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for the Reservation API.
 * Tests the complete flow from HTTP request through controller, service, and database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Reservation Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReservationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    @Autowired
    private SlotCapacityRepository slotCapacityRepository;

    private ObjectMapper objectMapper;
    private Restaurant testRestaurant;
    private Space testSpace;
    private ObjectId restaurantId;
    private UUID spaceId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Clean up
        reservationRepository.deleteAll();
        slotCapacityRepository.deleteAll();
        spaceRepository.deleteAll();
        restaurantRepository.deleteAll();

        // Create operating hours for all days (9:00 - 22:00), Sunday closed
        List<OperatingHours> operatingHours = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            operatingHours.add(OperatingHours.builder()
                    .dayOfWeek(i)
                    .openTime("09:00")
                    .closeTime("22:00")
                    .isClosed(i == 0) // Sunday closed
                    .build());
        }

        // Create test restaurant
        testRestaurant = Restaurant.builder()
                .name("Integration Test Restaurant")
                .timezone("America/New_York")
                .address("456 Test Ave")
                .city("New York")
                .state("NY")
                .operatingHours(operatingHours)
                .isActive(true)
                .build();
        testRestaurant = restaurantRepository.save(testRestaurant);
        restaurantId = testRestaurant.getId();

        // Create test space
        spaceId = UUID.randomUUID();
        testSpace = Space.builder()
                .id(spaceId)
                .restaurantId(restaurantId.toHexString())
                .name("Grand Ballroom")
                .maxCapacity(50)
                .minCapacity(10)
                .slotDurationMinutes(60)
                .bufferMinutes(15)
                .isActive(true)
                .build();
        testSpace = spaceRepository.save(testSpace);
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        slotCapacityRepository.deleteAll();
        spaceRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Nested
    @DisplayName("Complete Reservation Flow Tests")
    class CompleteFlowTests {

        @Test
        @Order(1)
        @DisplayName("Should create, retrieve, and cancel reservation successfully")
        void shouldCompleteFullReservationLifecycle() throws Exception {
            LocalDate testDate = getNextWeekday();

            // Step 1: Create reservation
            CreateReservationRequest createRequest = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("18:00")
                    .partySize(15)
                    .customerName("Integration Test Customer")
                    .customerEmail("integration@test.com")
                    .customerPhone("+1-555-123-4567")
                    .specialRequests("Window table please")
                    .build();

            MvcResult createResult = mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.spaceId").value(spaceId.toString()))
                    .andExpect(jsonPath("$.spaceName").value("Grand Ballroom"))
                    .andExpect(jsonPath("$.partySize").value(15))
                    .andExpect(jsonPath("$.customerName").value("Integration Test Customer"))
                    .andExpect(jsonPath("$.customerEmail").value("integration@test.com"))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.startTime").value("18:00"))
                    .andExpect(jsonPath("$.endTime").value("19:00"))
                    .andReturn();

            String responseContent = createResult.getResponse().getContentAsString();
            ReservationResponse createdReservation = objectMapper.readValue(responseContent, ReservationResponse.class);
            String reservationId = createdReservation.getId();

            // Step 2: Retrieve the reservation
            mockMvc.perform(get("/api/v1/reservations/{id}", reservationId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reservationId))
                    .andExpect(jsonPath("$.customerName").value("Integration Test Customer"))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));

            // Step 3: Cancel the reservation
            CancellationRequest cancelRequest = CancellationRequest.builder()
                    .reason("Plans changed")
                    .build();

            mockMvc.perform(post("/api/v1/reservations/{id}/cancel", reservationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cancelRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservationId").value(reservationId))
                    .andExpect(jsonPath("$.message").value(containsString("cancelled")))
                    .andExpect(jsonPath("$.cancellationReason").value("Plans changed"))
                    .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

            // Step 4: Verify status changed
            mockMvc.perform(get("/api/v1/reservations/{id}", reservationId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @Order(2)
        @DisplayName("Should handle multiple reservations for same slot within capacity")
        void shouldAllowMultipleReservationsWithinCapacity() throws Exception {
            LocalDate testDate = getNextWeekday();

            // Create first reservation for 20 guests
            CreateReservationRequest request1 = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("19:00")
                    .partySize(20)
                    .customerName("First Customer")
                    .customerEmail("first@test.com")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isCreated());

            // Create second reservation for 20 guests (total 40 < 50 capacity)
            CreateReservationRequest request2 = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("19:00")
                    .partySize(20)
                    .customerName("Second Customer")
                    .customerEmail("second@test.com")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(status().isCreated());

            // Verify both reservations exist
            var reservations = reservationRepository.findBySpaceIdAndReservationDateAndStatus(
                    spaceId, testDate, ReservationStatus.CONFIRMED);
            assertEquals(2, reservations.size());

            int totalBooked = reservations.stream().mapToInt(r -> r.getPartySize()).sum();
            assertEquals(40, totalBooked);
        }
    }

    @Nested
    @DisplayName("Validation Integration Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should return 409 when capacity exceeded")
        void shouldReturn409WhenCapacityExceeded() throws Exception {
            LocalDate testDate = getNextWeekday();

            // First fill up the capacity
            CreateReservationRequest fullRequest = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("12:00")
                    .partySize(45)
                    .customerName("Big Party")
                    .customerEmail("bigparty@test.com")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(fullRequest)))
                    .andExpect(status().isCreated());

            // Try to book more than remaining capacity
            CreateReservationRequest overflowRequest = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("12:00")
                    .partySize(10) // 45 + 10 > 50
                    .customerName("Overflow Customer")
                    .customerEmail("overflow@test.com")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(overflowRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CAPACITY_EXCEEDED"))
                    .andExpect(jsonPath("$.message").value(containsString("Grand Ballroom")));
        }

        @Test
        @DisplayName("Should return 400 for past date")
        void shouldReturn400ForPastDate() throws Exception {
            CreateReservationRequest request = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(LocalDate.now().minusDays(1))
                    .startTime("18:00")
                    .partySize(10)
                    .customerName("Past Date Customer")
                    .customerEmail("pastdate@test.com")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("PAST_DATE"));
        }

        @Test
        @DisplayName("Should return 400 for date beyond 90 days")
        void shouldReturn400ForDateBeyond90Days() throws Exception {
            CreateReservationRequest request = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(LocalDate.now().plusDays(91))
                    .startTime("18:00")
                    .partySize(10)
                    .customerName("Far Future Customer")
                    .customerEmail("farfuture@test.com")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ADVANCE_BOOKING_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("Should return 400 for outside operating hours")
        void shouldReturn400ForOutsideOperatingHours() throws Exception {
            LocalDate testDate = getNextWeekday();

            CreateReservationRequest request = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("08:00") // Before 09:00 opening
                    .partySize(10)
                    .customerName("Early Bird")
                    .customerEmail("early@test.com")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("OUTSIDE_OPERATING_HOURS"));
        }

        @Test
        @DisplayName("Should return 400 for closed day (Sunday)")
        void shouldReturn400ForClosedDay() throws Exception {
            LocalDate nextSunday = getNextSunday();

            CreateReservationRequest request = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(nextSunday)
                    .startTime("18:00")
                    .partySize(10)
                    .customerName("Sunday Customer")
                    .customerEmail("sunday@test.com")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("OUTSIDE_OPERATING_HOURS"));
        }

        @Test
        @DisplayName("Should return 400 for invalid email format")
        void shouldReturn400ForInvalidEmail() throws Exception {
            LocalDate testDate = getNextWeekday();

            CreateReservationRequest request = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("18:00")
                    .partySize(10)
                    .customerName("Invalid Email")
                    .customerEmail("not-an-email")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_EMAIL"));
        }

        @Test
        @DisplayName("Should return 400 for missing required fields")
        void shouldReturn400ForMissingRequiredFields() throws Exception {
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent space")
        void shouldReturn404ForNonExistentSpace() throws Exception {
            LocalDate testDate = getNextWeekday();

            CreateReservationRequest request = CreateReservationRequest.builder()
                    .spaceId(UUID.randomUUID()) // Non-existent space
                    .reservationDate(testDate)
                    .startTime("18:00")
                    .partySize(10)
                    .customerName("No Space Customer")
                    .customerEmail("nospace@test.com")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SPACE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Retrieval Integration Tests")
    class RetrievalTests {

        @Test
        @DisplayName("Should return 404 for non-existent reservation")
        void shouldReturn404ForNonExistentReservation() throws Exception {
            String nonExistentId = new ObjectId().toHexString();

            mockMvc.perform(get("/api/v1/reservations/{id}", nonExistentId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 for invalid ObjectId format")
        void shouldReturn400ForInvalidObjectIdFormat() throws Exception {
            mockMvc.perform(get("/api/v1/reservations/{id}", "invalid-id"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should retrieve all reservations with pagination")
        void shouldRetrieveAllReservationsWithPagination() throws Exception {
            // Create 5 reservations on valid weekdays (skip Sundays)
            LocalDate testDate = getNextWeekday();
            int created = 0;
            int offset = 0;

            while (created < 5) {
                LocalDate reservationDate = testDate.plusDays(offset);
                offset++;

                // Skip Sundays (day value 7 in Java)
                if (reservationDate.getDayOfWeek().getValue() == 7) {
                    continue;
                }

                CreateReservationRequest request = CreateReservationRequest.builder()
                        .spaceId(spaceId)
                        .reservationDate(reservationDate)
                        .startTime("18:00")
                        .partySize(5)
                        .customerName("Customer " + created)
                        .customerEmail("customer" + created + "@test.com")
                        .build();

                mockMvc.perform(post("/api/v1/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated());
                created++;
            }

            // Retrieve with pagination
            mockMvc.perform(get("/api/v1/reservations")
                            .param("page", "0")
                            .param("size", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }
    }

    @Nested
    @DisplayName("Deletion Integration Tests")
    class DeletionTests {

        @Test
        @DisplayName("Should delete reservation successfully")
        void shouldDeleteReservationSuccessfully() throws Exception {
            LocalDate testDate = getNextWeekday();

            // Create a reservation
            CreateReservationRequest createRequest = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("14:00")
                    .partySize(10)
                    .customerName("Delete Test Customer")
                    .customerEmail("delete@test.com")
                    .build();

            MvcResult createResult = mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andReturn();

            ReservationResponse created = objectMapper.readValue(
                    createResult.getResponse().getContentAsString(), ReservationResponse.class);
            String reservationId = created.getId();

            // Delete the reservation
            mockMvc.perform(delete("/api/v1/reservations/{id}", reservationId))
                    .andExpect(status().isNoContent());

            // Verify it's deleted
            mockMvc.perform(get("/api/v1/reservations/{id}", reservationId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent reservation")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            String nonExistentId = new ObjectId().toHexString();

            mockMvc.perform(delete("/api/v1/reservations/{id}", nonExistentId))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Capacity Release Integration Tests")
    class CapacityReleaseTests {

        @Test
        @DisplayName("Should release capacity after cancellation allowing new booking")
        void shouldReleaseCapacityAfterCancellation() throws Exception {
            LocalDate testDate = getNextWeekday();

            // Fill up capacity
            CreateReservationRequest fullRequest = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("15:00")
                    .partySize(50)
                    .customerName("Full Capacity")
                    .customerEmail("full@test.com")
                    .build();

            MvcResult createResult = mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(fullRequest)))
                    .andExpect(status().isCreated())
                    .andReturn();

            ReservationResponse fullReservation = objectMapper.readValue(
                    createResult.getResponse().getContentAsString(), ReservationResponse.class);

            // Verify capacity is full
            CreateReservationRequest overflowRequest = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("15:00")
                    .partySize(1)
                    .customerName("Overflow")
                    .customerEmail("overflow@test.com")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(overflowRequest)))
                    .andExpect(status().isConflict());

            // Cancel the full reservation
            mockMvc.perform(post("/api/v1/reservations/{id}/cancel", fullReservation.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());

            // Now should be able to book
            CreateReservationRequest newRequest = CreateReservationRequest.builder()
                    .spaceId(spaceId)
                    .reservationDate(testDate)
                    .startTime("15:00")
                    .partySize(30)
                    .customerName("New Booking")
                    .customerEmail("newbooking@test.com")
                    .build();

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newRequest)))
                    .andExpect(status().isCreated());
        }
    }

    // Helper methods
    private LocalDate getNextWeekday() {
        LocalDate date = LocalDate.now().plusDays(7);
        // Ensure it's not Sunday (day 0 in our operating hours)
        while (date.getDayOfWeek().getValue() == 7) { // Java DayOfWeek: Sunday = 7
            date = date.plusDays(1);
        }
        return date;
    }

    private LocalDate getNextSunday() {
        LocalDate today = LocalDate.now();
        int daysUntilSunday = 7 - today.getDayOfWeek().getValue();
        if (daysUntilSunday == 0) daysUntilSunday = 7;
        return today.plusDays(daysUntilSunday);
    }
}

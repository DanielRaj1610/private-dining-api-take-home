package com.opentable.privatedining.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opentable.privatedining.model.OperatingHours;
import com.opentable.privatedining.model.Reservation;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Reporting API.
 * Tests the complete flow from HTTP request through controller, service, and database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Reporting Integration Tests")
class ReportingIntegrationTest {

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
    private Space testSpace1;
    private Space testSpace2;
    private ObjectId restaurantId;
    private UUID spaceId1;
    private UUID spaceId2;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Clean up
        reservationRepository.deleteAll();
        slotCapacityRepository.deleteAll();
        spaceRepository.deleteAll();
        restaurantRepository.deleteAll();

        // Create operating hours for all days (9:00 - 22:00)
        List<OperatingHours> operatingHours = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            operatingHours.add(OperatingHours.builder()
                    .dayOfWeek(i)
                    .openTime("09:00")
                    .closeTime("22:00")
                    .isClosed(false)
                    .build());
        }

        // Create test restaurant
        testRestaurant = Restaurant.builder()
                .name("Reporting Test Restaurant")
                .timezone("America/New_York")
                .address("789 Report St")
                .city("New York")
                .state("NY")
                .operatingHours(operatingHours)
                .isActive(true)
                .build();
        testRestaurant = restaurantRepository.save(testRestaurant);
        restaurantId = testRestaurant.getId();

        // Create test spaces
        spaceId1 = UUID.randomUUID();
        testSpace1 = Space.builder()
                .id(spaceId1)
                .restaurantId(restaurantId.toHexString())
                .name("Main Dining Hall")
                .maxCapacity(100)
                .minCapacity(10)
                .slotDurationMinutes(60)
                .bufferMinutes(15)
                .isActive(true)
                .build();
        testSpace1 = spaceRepository.save(testSpace1);

        spaceId2 = UUID.randomUUID();
        testSpace2 = Space.builder()
                .id(spaceId2)
                .restaurantId(restaurantId.toHexString())
                .name("VIP Lounge")
                .maxCapacity(30)
                .minCapacity(5)
                .slotDurationMinutes(60)
                .bufferMinutes(15)
                .isActive(true)
                .build();
        testSpace2 = spaceRepository.save(testSpace2);
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        slotCapacityRepository.deleteAll();
        spaceRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    private Reservation createTestReservation(LocalDate date, String startTime, int partySize,
                                               UUID spaceId, ReservationStatus status) {
        Reservation reservation = Reservation.builder()
                .restaurantId(restaurantId)
                .spaceId(spaceId)
                .reservationDate(date)
                .startTime(startTime)
                .endTime(calculateEndTime(startTime))
                .partySize(partySize)
                .customerName("Test Customer")
                .customerEmail("test@example.com")
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
        return reservationRepository.save(reservation);
    }

    private String calculateEndTime(String startTime) {
        int hour = Integer.parseInt(startTime.substring(0, 2));
        return String.format("%02d:00", hour + 1);
    }

    @Nested
    @DisplayName("GET /api/v1/reports/occupancy - Occupancy Report Tests")
    class OccupancyReportTests {

        @Test
        @DisplayName("Should generate occupancy report successfully")
        void shouldGenerateOccupancyReportSuccessfully() throws Exception {
            // Given
            LocalDate testDate = LocalDate.of(2024, 3, 15);

            // When & Then - Verify report structure
            mockMvc.perform(get("/api/v1/reports/occupancy")
                            .param("restaurantId", restaurantId.toHexString())
                            .param("startDate", testDate.toString())
                            .param("endDate", testDate.toString())
                            .param("granularity", "DAILY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.restaurantId").value(restaurantId.toHexString()))
                    .andExpect(jsonPath("$.restaurantName").value("Reporting Test Restaurant"))
                    .andExpect(jsonPath("$.period").exists())
                    .andExpect(jsonPath("$.summary").exists())
                    .andExpect(jsonPath("$.dailyBreakdown").isArray());
        }

        @Test
        @DisplayName("Should include daily breakdown with space breakdown")
        void shouldIncludeDailyBreakdownWithSpaceBreakdown() throws Exception {
            // Given
            LocalDate testDate = LocalDate.of(2024, 3, 16);

            // When & Then - Verify structure
            mockMvc.perform(get("/api/v1/reports/occupancy")
                            .param("restaurantId", restaurantId.toHexString())
                            .param("startDate", testDate.toString())
                            .param("endDate", testDate.toString())
                            .param("granularity", "DAILY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dailyBreakdown[0]").exists())
                    .andExpect(jsonPath("$.dailyBreakdown[0].date").exists())
                    .andExpect(jsonPath("$.dailyBreakdown[0].spaceBreakdown").isArray());
        }

        @Test
        @DisplayName("Should include hourly breakdown when granularity is HOURLY")
        void shouldIncludeHourlyBreakdownWhenGranularityIsHourly() throws Exception {
            // Given
            LocalDate testDate = LocalDate.of(2024, 3, 17);

            // When & Then - Verify hourly breakdown exists
            mockMvc.perform(get("/api/v1/reports/occupancy")
                            .param("restaurantId", restaurantId.toHexString())
                            .param("startDate", testDate.toString())
                            .param("endDate", testDate.toString())
                            .param("granularity", "HOURLY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dailyBreakdown[0].hourlyBreakdown").isArray());
        }

        @Test
        @DisplayName("Should generate report for date range with multiple days")
        void shouldGenerateReportForDateRange() throws Exception {
            // Given
            LocalDate day1 = LocalDate.of(2024, 3, 18);
            LocalDate day3 = LocalDate.of(2024, 3, 20);

            // When & Then - Verify daily breakdown covers 3 days
            mockMvc.perform(get("/api/v1/reports/occupancy")
                            .param("restaurantId", restaurantId.toHexString())
                            .param("startDate", day1.toString())
                            .param("endDate", day3.toString())
                            .param("granularity", "DAILY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dailyBreakdown.length()").value(3));
        }

        @Test
        @DisplayName("Should return response with insights section")
        void shouldReturnResponseWithInsightsSection() throws Exception {
            // Given
            LocalDate testDate = LocalDate.of(2024, 3, 23);

            // When & Then
            mockMvc.perform(get("/api/v1/reports/occupancy")
                            .param("restaurantId", restaurantId.toHexString())
                            .param("startDate", testDate.toString())
                            .param("endDate", testDate.toString())
                            .param("granularity", "DAILY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.insights").exists());
        }

        @Test
        @DisplayName("Should handle empty date range gracefully")
        void shouldHandleEmptyDateRangeGracefully() throws Exception {
            // Given - No reservations for this date
            LocalDate testDate = LocalDate.of(2024, 4, 1);

            // When & Then
            mockMvc.perform(get("/api/v1/reports/occupancy")
                            .param("restaurantId", restaurantId.toHexString())
                            .param("startDate", testDate.toString())
                            .param("endDate", testDate.toString())
                            .param("granularity", "DAILY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.totalReservations").value(0))
                    .andExpect(jsonPath("$.summary.totalGuests").value(0))
                    .andExpect(jsonPath("$.dailyBreakdown.length()").value(1));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should return 400 for invalid date range")
        void shouldReturn400ForInvalidDateRange() throws Exception {
            // When end date is before start date
            mockMvc.perform(get("/api/v1/reports/occupancy")
                            .param("restaurantId", restaurantId.toHexString())
                            .param("startDate", "2024-03-20")
                            .param("endDate", "2024-03-15")
                            .param("granularity", "DAILY"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_DATE_RANGE"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent restaurant")
        void shouldReturn404ForNonExistentRestaurant() throws Exception {
            String nonExistentId = new ObjectId().toHexString();

            mockMvc.perform(get("/api/v1/reports/occupancy")
                            .param("restaurantId", nonExistentId)
                            .param("startDate", "2024-03-15")
                            .param("endDate", "2024-03-20")
                            .param("granularity", "DAILY"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RESTAURANT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Granularity Tests")
    class GranularityTests {

        @Test
        @DisplayName("Should default to DAILY granularity when not specified")
        void shouldDefaultToDailyGranularityWhenNotSpecified() throws Exception {
            // Given
            LocalDate testDate = LocalDate.of(2024, 3, 24);
            createTestReservation(testDate, "18:00", 20, spaceId1, ReservationStatus.CONFIRMED);

            // When - No granularity specified
            mockMvc.perform(get("/api/v1/reports/occupancy")
                            .param("restaurantId", restaurantId.toHexString())
                            .param("startDate", testDate.toString())
                            .param("endDate", testDate.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dailyBreakdown[0].hourlyBreakdown").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Summary Calculation Tests")
    class SummaryCalculationTests {

        @Test
        @DisplayName("Should include summary fields in response")
        void shouldIncludeSummaryFieldsInResponse() throws Exception {
            // Given
            LocalDate testDate = LocalDate.of(2024, 3, 25);

            // When & Then - Verify summary structure
            mockMvc.perform(get("/api/v1/reports/occupancy")
                            .param("restaurantId", restaurantId.toHexString())
                            .param("startDate", testDate.toString())
                            .param("endDate", testDate.toString())
                            .param("granularity", "DAILY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.totalReservations").exists())
                    .andExpect(jsonPath("$.summary.totalGuests").exists())
                    .andExpect(jsonPath("$.summary.averagePartySize").exists())
                    .andExpect(jsonPath("$.summary.averageUtilizationPercentage").exists());
        }
    }
}

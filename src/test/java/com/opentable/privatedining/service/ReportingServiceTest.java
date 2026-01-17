package com.opentable.privatedining.service;

import com.opentable.privatedining.dto.request.OccupancyReportRequest;
import com.opentable.privatedining.dto.response.*;
import com.opentable.privatedining.exception.InvalidDateRangeException;
import com.opentable.privatedining.exception.RestaurantNotFoundException;
import com.opentable.privatedining.model.Reservation;
import com.opentable.privatedining.model.Restaurant;
import com.opentable.privatedining.model.Space;
import com.opentable.privatedining.model.enums.ReportGranularity;
import com.opentable.privatedining.model.enums.ReservationStatus;
import com.opentable.privatedining.repository.ReservationRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportingService Tests")
class ReportingServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RestaurantService restaurantService;

    @Mock
    private SpaceService spaceService;

    @InjectMocks
    private ReportingService reportingService;

    private ObjectId restaurantId;
    private Restaurant testRestaurant;
    private Space testSpace1;
    private Space testSpace2;
    private UUID spaceId1;
    private UUID spaceId2;

    @BeforeEach
    void setUp() {
        restaurantId = new ObjectId();

        testRestaurant = Restaurant.builder()
                .id(restaurantId)
                .name("Test Restaurant")
                .build();

        spaceId1 = UUID.randomUUID();
        spaceId2 = UUID.randomUUID();

        testSpace1 = Space.builder()
                .id(spaceId1)
                .name("Garden Room")
                .maxCapacity(20)
                .build();

        testSpace2 = Space.builder()
                .id(spaceId2)
                .name("Wine Cellar")
                .maxCapacity(30)
                .build();
    }

    private Reservation createReservation(LocalDate date, String startTime, int partySize, UUID spaceId) {
        return Reservation.builder()
                .id(new ObjectId())
                .restaurantId(restaurantId)
                .spaceId(spaceId)
                .reservationDate(date)
                .startTime(startTime)
                .endTime(calculateEndTime(startTime))
                .partySize(partySize)
                .customerName("Test Customer")
                .customerEmail("test@example.com")
                .status(ReservationStatus.CONFIRMED)
                .build();
    }

    private String calculateEndTime(String startTime) {
        int hour = Integer.parseInt(startTime.substring(0, 2));
        return String.format("%02d:00", hour + 1);
    }

    @Nested
    @DisplayName("generateOccupancyReport tests")
    class GenerateOccupancyReportTests {

        @Test
        @DisplayName("Should generate report with valid data")
        void shouldGenerateReportWithValidData() {
            // Given
            LocalDate startDate = LocalDate.of(2024, 1, 15);
            LocalDate endDate = LocalDate.of(2024, 1, 17);

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(startDate)
                    .endDate(endDate)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            List<Reservation> reservations = Arrays.asList(
                    createReservation(startDate, "18:00", 10, spaceId1),
                    createReservation(startDate, "19:00", 8, spaceId1),
                    createReservation(startDate.plusDays(1), "18:00", 15, spaceId2)
            );

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1, testSpace2));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    eq(restaurantId), eq(startDate), eq(endDate), eq(ReservationStatus.CONFIRMED)))
                    .thenReturn(reservations);
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    eq(restaurantId), eq(startDate), eq(endDate), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(2L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            assertNotNull(response);
            assertEquals(restaurantId.toHexString(), response.getRestaurantId());
            assertEquals("Test Restaurant", response.getRestaurantName());
            assertEquals(startDate, response.getPeriod().getStartDate());
            assertEquals(endDate, response.getPeriod().getEndDate());

            // Check summary
            assertNotNull(response.getSummary());
            assertEquals(3L, response.getSummary().getTotalReservations());
            assertEquals(33L, response.getSummary().getTotalGuests()); // 10 + 8 + 15

            // Check daily breakdown
            assertNotNull(response.getDailyBreakdown());
            assertEquals(3, response.getDailyBreakdown().size()); // 3 days
        }

        @Test
        @DisplayName("Should throw exception for invalid date range")
        void shouldThrowExceptionForInvalidDateRange() {
            // Given
            LocalDate startDate = LocalDate.of(2024, 1, 20);
            LocalDate endDate = LocalDate.of(2024, 1, 15); // Before start date

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(startDate)
                    .endDate(endDate)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            // When & Then
            assertThrows(InvalidDateRangeException.class,
                    () -> reportingService.generateOccupancyReport(request));

            verifyNoInteractions(restaurantService);
        }

        @Test
        @DisplayName("Should throw exception when restaurant not found")
        void shouldThrowExceptionWhenRestaurantNotFound() {
            // Given
            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(new ObjectId().toHexString())
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusDays(7))
                    .granularity(ReportGranularity.DAILY)
                    .build();

            when(restaurantService.getRestaurantById(any(ObjectId.class)))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThrows(RestaurantNotFoundException.class,
                    () -> reportingService.generateOccupancyReport(request));
        }

        @Test
        @DisplayName("Should handle empty reservations")
        void shouldHandleEmptyReservations() {
            // Given
            LocalDate startDate = LocalDate.of(2024, 2, 1);
            LocalDate endDate = LocalDate.of(2024, 2, 3);

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(startDate)
                    .endDate(endDate)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1, testSpace2));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            assertNotNull(response);
            assertEquals(0L, response.getSummary().getTotalReservations());
            assertEquals(0L, response.getSummary().getTotalGuests());
            assertEquals(BigDecimal.ZERO, response.getSummary().getAveragePartySize());
        }

        @Test
        @DisplayName("Should include hourly breakdown when granularity is HOURLY")
        void shouldIncludeHourlyBreakdownForHourlyGranularity() {
            // Given
            LocalDate testDate = LocalDate.of(2024, 1, 15);

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(testDate)
                    .endDate(testDate)
                    .granularity(ReportGranularity.HOURLY)
                    .build();

            List<Reservation> reservations = Arrays.asList(
                    createReservation(testDate, "18:00", 10, spaceId1),
                    createReservation(testDate, "18:00", 5, spaceId2),
                    createReservation(testDate, "19:00", 8, spaceId1)
            );

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1, testSpace2));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(reservations);
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            assertNotNull(response);
            assertFalse(response.getDailyBreakdown().isEmpty());

            DailyOccupancy dailyOccupancy = response.getDailyBreakdown().get(0);
            assertNotNull(dailyOccupancy.getHourlyBreakdown());
            assertFalse(dailyOccupancy.getHourlyBreakdown().isEmpty());

            // Check hourly data
            List<HourlyOccupancy> hourlyBreakdown = dailyOccupancy.getHourlyBreakdown();
            assertTrue(hourlyBreakdown.stream().anyMatch(h -> h.getHour().equals("18:00")));
            assertTrue(hourlyBreakdown.stream().anyMatch(h -> h.getHour().equals("19:00")));
        }

        @Test
        @DisplayName("Should not include hourly breakdown for DAILY granularity")
        void shouldNotIncludeHourlyBreakdownForDailyGranularity() {
            // Given
            LocalDate testDate = LocalDate.of(2024, 1, 15);

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(testDate)
                    .endDate(testDate)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            List<Reservation> reservations = Arrays.asList(
                    createReservation(testDate, "18:00", 10, spaceId1)
            );

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1, testSpace2));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(reservations);
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            assertNotNull(response);
            assertFalse(response.getDailyBreakdown().isEmpty());
            DailyOccupancy dailyOccupancy = response.getDailyBreakdown().get(0);
            assertNull(dailyOccupancy.getHourlyBreakdown());
        }
    }

    @Nested
    @DisplayName("Summary Calculation Tests")
    class SummaryCalculationTests {

        @Test
        @DisplayName("Should calculate average party size correctly")
        void shouldCalculateAveragePartySizeCorrectly() {
            // Given
            LocalDate testDate = LocalDate.of(2024, 1, 15);

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(testDate)
                    .endDate(testDate)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            List<Reservation> reservations = Arrays.asList(
                    createReservation(testDate, "18:00", 4, spaceId1),
                    createReservation(testDate, "19:00", 6, spaceId1),
                    createReservation(testDate, "20:00", 8, spaceId2)
            );

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1, testSpace2));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(reservations);
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            // Average = (4 + 6 + 8) / 3 = 6.0
            assertEquals(new BigDecimal("6.00"), response.getSummary().getAveragePartySize());
        }

        @Test
        @DisplayName("Should calculate cancellation rate correctly")
        void shouldCalculateCancellationRateCorrectly() {
            // Given
            LocalDate testDate = LocalDate.of(2024, 1, 15);

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(testDate)
                    .endDate(testDate)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            List<Reservation> reservations = Arrays.asList(
                    createReservation(testDate, "18:00", 10, spaceId1),
                    createReservation(testDate, "19:00", 10, spaceId1),
                    createReservation(testDate, "20:00", 10, spaceId1),
                    createReservation(testDate, "21:00", 10, spaceId1)
            );

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), eq(ReservationStatus.CONFIRMED)))
                    .thenReturn(reservations);
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), eq(ReservationStatus.CANCELLED)))
                    .thenReturn(1L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            // Cancellation rate = 1 / (4 + 1) * 100 = 20%
            assertEquals(new BigDecimal("20.0"), response.getSummary().getCancellationRate());
        }
    }

    @Nested
    @DisplayName("Space Breakdown Tests")
    class SpaceBreakdownTests {

        @Test
        @DisplayName("Should calculate space-level breakdown correctly")
        void shouldCalculateSpaceLevelBreakdownCorrectly() {
            // Given
            LocalDate testDate = LocalDate.of(2024, 1, 15);

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(testDate)
                    .endDate(testDate)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            List<Reservation> reservations = Arrays.asList(
                    createReservation(testDate, "18:00", 10, spaceId1),
                    createReservation(testDate, "19:00", 5, spaceId1),
                    createReservation(testDate, "18:00", 20, spaceId2)
            );

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1, testSpace2));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(reservations);
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            assertNotNull(response);
            DailyOccupancy dailyOccupancy = response.getDailyBreakdown().get(0);
            assertNotNull(dailyOccupancy.getSpaceBreakdown());
            assertEquals(2, dailyOccupancy.getSpaceBreakdown().size());

            // Check Garden Room (spaceId1)
            SpaceOccupancy gardenRoom = dailyOccupancy.getSpaceBreakdown().stream()
                    .filter(s -> s.getSpaceId().equals(spaceId1))
                    .findFirst()
                    .orElse(null);

            assertNotNull(gardenRoom);
            assertEquals("Garden Room", gardenRoom.getSpaceName());
            assertEquals(2, gardenRoom.getReservations());
            assertEquals(15, gardenRoom.getGuests()); // 10 + 5

            // Check Wine Cellar (spaceId2)
            SpaceOccupancy wineCellar = dailyOccupancy.getSpaceBreakdown().stream()
                    .filter(s -> s.getSpaceId().equals(spaceId2))
                    .findFirst()
                    .orElse(null);

            assertNotNull(wineCellar);
            assertEquals("Wine Cellar", wineCellar.getSpaceName());
            assertEquals(1, wineCellar.getReservations());
            assertEquals(20, wineCellar.getGuests());
        }
    }

    @Nested
    @DisplayName("Insights Generation Tests")
    class InsightsGenerationTests {

        @Test
        @DisplayName("Should identify busiest day correctly")
        void shouldIdentifyBusiestDayCorrectly() {
            // Given
            LocalDate monday = LocalDate.of(2024, 1, 15); // Monday
            LocalDate saturday = LocalDate.of(2024, 1, 20); // Saturday

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(monday)
                    .endDate(saturday)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            // Saturday has more guests
            List<Reservation> reservations = Arrays.asList(
                    createReservation(monday, "18:00", 10, spaceId1),
                    createReservation(saturday, "18:00", 30, spaceId1),
                    createReservation(saturday, "19:00", 20, spaceId2)
            );

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1, testSpace2));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(reservations);
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            assertNotNull(response.getInsights());
            assertEquals(DayOfWeek.SATURDAY, response.getInsights().getBusiestDay());
        }

        @Test
        @DisplayName("Should identify peak hour correctly")
        void shouldIdentifyPeakHourCorrectly() {
            // Given
            LocalDate testDate = LocalDate.of(2024, 1, 15);

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(testDate)
                    .endDate(testDate)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            List<Reservation> reservations = Arrays.asList(
                    createReservation(testDate, "12:00", 5, spaceId1),
                    createReservation(testDate, "18:00", 15, spaceId1),
                    createReservation(testDate, "18:00", 10, spaceId2),
                    createReservation(testDate, "19:00", 8, spaceId1)
            );

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1, testSpace2));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(reservations);
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            assertNotNull(response.getInsights());
            assertEquals("18:00", response.getInsights().getBusiestHour()); // 25 guests at 18:00
        }

        @Test
        @DisplayName("Should generate recommendations for low utilization")
        void shouldGenerateRecommendationsForLowUtilization() {
            // Given
            LocalDate testDate = LocalDate.of(2024, 1, 15);

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(testDate)
                    .endDate(testDate)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            // Very low utilization data
            List<Reservation> reservations = Arrays.asList(
                    createReservation(testDate, "15:00", 2, spaceId1) // Low utilization hour
            );

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1, testSpace2));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(reservations);
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            assertNotNull(response.getInsights());
            assertNotNull(response.getInsights().getRecommendations());
            assertFalse(response.getInsights().getRecommendations().isEmpty());
        }
    }

    @Nested
    @DisplayName("Peak Hour Calculation Tests")
    class PeakHourCalculationTests {

        @Test
        @DisplayName("Should identify peak hour in daily occupancy")
        void shouldIdentifyPeakHourInDailyOccupancy() {
            // Given
            LocalDate testDate = LocalDate.of(2024, 1, 15);

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(testDate)
                    .endDate(testDate)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            List<Reservation> reservations = Arrays.asList(
                    createReservation(testDate, "12:00", 5, spaceId1),
                    createReservation(testDate, "19:00", 20, spaceId1), // Peak
                    createReservation(testDate, "20:00", 10, spaceId1)
            );

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1, testSpace2));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(reservations);
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            assertNotNull(response);
            DailyOccupancy dailyOccupancy = response.getDailyBreakdown().get(0);
            assertEquals("19:00", dailyOccupancy.getPeakHour());
        }

        @Test
        @DisplayName("Should handle null peak hour when no reservations")
        void shouldHandleNullPeakHourWhenNoReservations() {
            // Given
            LocalDate testDate = LocalDate.of(2024, 1, 15);

            OccupancyReportRequest request = OccupancyReportRequest.builder()
                    .restaurantId(restaurantId.toHexString())
                    .startDate(testDate)
                    .endDate(testDate)
                    .granularity(ReportGranularity.DAILY)
                    .build();

            when(restaurantService.getRestaurantById(restaurantId))
                    .thenReturn(Optional.of(testRestaurant));
            when(spaceService.getActiveSpacesByRestaurantId(restaurantId.toHexString()))
                    .thenReturn(Arrays.asList(testSpace1, testSpace2));
            when(reservationRepository.findByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                    any(), any(), any(), any()))
                    .thenReturn(0L);

            // When
            OccupancyReportResponse response = reportingService.generateOccupancyReport(request);

            // Then
            assertNotNull(response);
            DailyOccupancy dailyOccupancy = response.getDailyBreakdown().get(0);
            assertNull(dailyOccupancy.getPeakHour());
        }
    }
}

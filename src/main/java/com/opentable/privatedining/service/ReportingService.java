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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating occupancy and analytics reports.
 */
@Service
public class ReportingService {

    private final ReservationRepository reservationRepository;
    private final RestaurantService restaurantService;
    private final SpaceService spaceService;

    public ReportingService(ReservationRepository reservationRepository,
                            RestaurantService restaurantService,
                            SpaceService spaceService) {
        this.reservationRepository = reservationRepository;
        this.restaurantService = restaurantService;
        this.spaceService = spaceService;
    }

    /**
     * Generate an occupancy report for a restaurant.
     */
    public OccupancyReportResponse generateOccupancyReport(OccupancyReportRequest request) {
        // Validate date range
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new InvalidDateRangeException(request.getStartDate(), request.getEndDate());
        }

        ObjectId restaurantId = new ObjectId(request.getRestaurantId());

        // Get restaurant
        Restaurant restaurant = restaurantService.getRestaurantById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));

        // Get spaces for capacity calculation
        List<Space> spaces = spaceService.getActiveSpacesByRestaurantId(request.getRestaurantId());
        int totalMaxCapacity = spaces.stream()
                .mapToInt(Space::getMaxCapacity)
                .sum();

        // Get reservations for the period
        List<Reservation> confirmedReservations = reservationRepository
                .findByRestaurantIdAndReservationDateBetweenAndStatus(
                        restaurantId, request.getStartDate(), request.getEndDate(),
                        ReservationStatus.CONFIRMED);

        long cancelledCount = reservationRepository.countByRestaurantIdAndReservationDateBetweenAndStatus(
                restaurantId, request.getStartDate(), request.getEndDate(),
                ReservationStatus.CANCELLED);

        // Build daily breakdown
        List<DailyOccupancy> dailyBreakdown = buildDailyBreakdown(
                confirmedReservations, spaces, request.getStartDate(), request.getEndDate(),
                request.getGranularity() == ReportGranularity.HOURLY);

        // Calculate summary
        OccupancySummary summary = calculateSummary(
                confirmedReservations, cancelledCount, totalMaxCapacity, dailyBreakdown);

        // Generate insights
        OccupancyInsights insights = generateInsights(dailyBreakdown, confirmedReservations);

        return OccupancyReportResponse.builder()
                .restaurantId(request.getRestaurantId())
                .restaurantName(restaurant.getName())
                .period(OccupancyReportResponse.ReportPeriod.builder()
                        .startDate(request.getStartDate())
                        .endDate(request.getEndDate())
                        .build())
                .summary(summary)
                .dailyBreakdown(dailyBreakdown)
                .insights(insights)
                .build();
    }

    /**
     * Build daily occupancy breakdown.
     */
    private List<DailyOccupancy> buildDailyBreakdown(List<Reservation> reservations,
                                                      List<Space> spaces,
                                                      LocalDate startDate,
                                                      LocalDate endDate,
                                                      boolean includeHourly) {
        // Group reservations by date
        Map<LocalDate, List<Reservation>> byDate = reservations.stream()
                .collect(Collectors.groupingBy(Reservation::getReservationDate));

        // Calculate total capacity per space for percentage calculations
        Map<UUID, Integer> spaceCapacities = spaces.stream()
                .collect(Collectors.toMap(Space::getId, Space::getMaxCapacity));
        int totalCapacity = spaceCapacities.values().stream().mapToInt(Integer::intValue).sum();

        List<DailyOccupancy> dailyList = new ArrayList<>();

        // Iterate through each day in the range
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<Reservation> dayReservations = byDate.getOrDefault(date, Collections.emptyList());

            long totalReservations = dayReservations.size();
            long totalGuests = dayReservations.stream()
                    .mapToInt(Reservation::getPartySize)
                    .sum();

            BigDecimal utilizationPercentage = totalCapacity > 0
                    ? BigDecimal.valueOf(totalGuests * 100.0 / totalCapacity)
                        .setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // Find peak hour
            Map<String, Long> hourlyGuests = dayReservations.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.getStartTime().substring(0, 2) + ":00",
                            Collectors.summingLong(Reservation::getPartySize)));

            String peakHour = hourlyGuests.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            BigDecimal peakHourUtilization = BigDecimal.ZERO;
            if (peakHour != null && totalCapacity > 0) {
                long peakGuests = hourlyGuests.get(peakHour);
                peakHourUtilization = BigDecimal.valueOf(peakGuests * 100.0 / totalCapacity)
                        .setScale(1, RoundingMode.HALF_UP);
            }

            // Build space breakdown
            List<SpaceOccupancy> spaceBreakdown = buildSpaceBreakdown(dayReservations, spaces);

            // Build hourly breakdown if requested
            List<HourlyOccupancy> hourlyBreakdown = null;
            if (includeHourly) {
                hourlyBreakdown = buildHourlyBreakdown(dayReservations, totalCapacity);
            }

            DailyOccupancy daily = DailyOccupancy.builder()
                    .date(date)
                    .dayOfWeek(date.getDayOfWeek())
                    .totalReservations(totalReservations)
                    .totalGuests(totalGuests)
                    .utilizationPercentage(utilizationPercentage)
                    .peakHour(peakHour)
                    .peakHourUtilization(peakHourUtilization)
                    .spaceBreakdown(spaceBreakdown)
                    .hourlyBreakdown(hourlyBreakdown)
                    .build();

            dailyList.add(daily);
        }

        return dailyList;
    }

    /**
     * Build space-level occupancy breakdown for a day.
     */
    private List<SpaceOccupancy> buildSpaceBreakdown(List<Reservation> dayReservations,
                                                      List<Space> spaces) {
        Map<UUID, List<Reservation>> bySpace = dayReservations.stream()
                .collect(Collectors.groupingBy(Reservation::getSpaceId));

        return spaces.stream()
                .map(space -> {
                    List<Reservation> spaceReservations = bySpace.getOrDefault(
                            space.getId(), Collections.emptyList());

                    long reservationCount = spaceReservations.size();
                    long guestCount = spaceReservations.stream()
                            .mapToInt(Reservation::getPartySize)
                            .sum();

                    BigDecimal utilization = space.getMaxCapacity() > 0
                            ? BigDecimal.valueOf(guestCount * 100.0 / space.getMaxCapacity())
                                .setScale(1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    return SpaceOccupancy.builder()
                            .spaceId(space.getId())
                            .spaceName(space.getName())
                            .maxCapacity(space.getMaxCapacity())
                            .reservations(reservationCount)
                            .guests(guestCount)
                            .utilizationPercentage(utilization)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Build hourly occupancy breakdown for a day.
     */
    private List<HourlyOccupancy> buildHourlyBreakdown(List<Reservation> dayReservations,
                                                        int totalCapacity) {
        Map<String, List<Reservation>> byHour = dayReservations.stream()
                .collect(Collectors.groupingBy(r -> r.getStartTime().substring(0, 2) + ":00"));

        return byHour.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String hour = entry.getKey();
                    List<Reservation> hourReservations = entry.getValue();

                    long reservationCount = hourReservations.size();
                    long guestCount = hourReservations.stream()
                            .mapToInt(Reservation::getPartySize)
                            .sum();

                    BigDecimal utilization = totalCapacity > 0
                            ? BigDecimal.valueOf(guestCount * 100.0 / totalCapacity)
                                .setScale(1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    return HourlyOccupancy.builder()
                            .hour(hour)
                            .reservations(reservationCount)
                            .guests(guestCount)
                            .utilizationPercentage(utilization)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate summary statistics.
     */
    private OccupancySummary calculateSummary(List<Reservation> reservations,
                                               long cancelledCount,
                                               int totalCapacity,
                                               List<DailyOccupancy> dailyBreakdown) {
        long totalReservations = reservations.size();
        long totalGuests = reservations.stream()
                .mapToInt(Reservation::getPartySize)
                .sum();

        BigDecimal avgPartySize = totalReservations > 0
                ? BigDecimal.valueOf((double) totalGuests / totalReservations)
                    .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal avgUtilization = dailyBreakdown.isEmpty()
                ? BigDecimal.ZERO
                : dailyBreakdown.stream()
                    .map(DailyOccupancy::getUtilizationPercentage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(dailyBreakdown.size()), 1, RoundingMode.HALF_UP);

        long totalOperatingSlots = dailyBreakdown.size() * 12L; // Approximate slots per day
        long totalBookedSlots = dailyBreakdown.stream()
                .mapToLong(DailyOccupancy::getTotalReservations)
                .sum();

        BigDecimal cancellationRate = (totalReservations + cancelledCount) > 0
                ? BigDecimal.valueOf(cancelledCount * 100.0 / (totalReservations + cancelledCount))
                    .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return OccupancySummary.builder()
                .totalReservations(totalReservations)
                .totalGuests(totalGuests)
                .averagePartySize(avgPartySize)
                .averageUtilizationPercentage(avgUtilization)
                .totalOperatingSlots(totalOperatingSlots)
                .totalBookedSlots(totalBookedSlots)
                .cancelledReservations(cancelledCount)
                .cancellationRate(cancellationRate)
                .build();
    }

    /**
     * Generate insights and recommendations.
     */
    private OccupancyInsights generateInsights(List<DailyOccupancy> dailyBreakdown,
                                                List<Reservation> reservations) {
        // Group by day of week
        Map<DayOfWeek, List<DailyOccupancy>> byDayOfWeek = dailyBreakdown.stream()
                .collect(Collectors.groupingBy(DailyOccupancy::getDayOfWeek));

        // Find busiest and slowest days
        Map.Entry<DayOfWeek, BigDecimal> busiestDay = byDayOfWeek.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(DailyOccupancy::getUtilizationPercentage)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(BigDecimal.valueOf(e.getValue().size()), 1, RoundingMode.HALF_UP)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        Map.Entry<DayOfWeek, BigDecimal> slowestDay = byDayOfWeek.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(DailyOccupancy::getUtilizationPercentage)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(BigDecimal.valueOf(e.getValue().size()), 1, RoundingMode.HALF_UP)))
                .entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .orElse(null);

        // Group by hour
        Map<String, Long> hourlyGuests = reservations.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getStartTime().substring(0, 2) + ":00",
                        Collectors.summingLong(Reservation::getPartySize)));

        String busiestHour = hourlyGuests.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        String slowestHour = hourlyGuests.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        // Generate recommendations
        List<String> recommendations = generateRecommendations(
                busiestDay, slowestDay, busiestHour, slowestHour);

        return OccupancyInsights.builder()
                .busiestDay(busiestDay != null ? busiestDay.getKey() : null)
                .busiestDayAverageUtilization(busiestDay != null ? busiestDay.getValue() : null)
                .slowestDay(slowestDay != null ? slowestDay.getKey() : null)
                .slowestDayAverageUtilization(slowestDay != null ? slowestDay.getValue() : null)
                .busiestHour(busiestHour)
                .busiestHourAverageUtilization(busiestHour != null
                        ? BigDecimal.valueOf(hourlyGuests.get(busiestHour)) : null)
                .slowestHour(slowestHour)
                .slowestHourAverageUtilization(slowestHour != null
                        ? BigDecimal.valueOf(hourlyGuests.get(slowestHour)) : null)
                .recommendations(recommendations)
                .build();
    }

    /**
     * Generate recommendations based on the data.
     */
    private List<String> generateRecommendations(Map.Entry<DayOfWeek, BigDecimal> busiestDay,
                                                  Map.Entry<DayOfWeek, BigDecimal> slowestDay,
                                                  String busiestHour,
                                                  String slowestHour) {
        List<String> recommendations = new ArrayList<>();

        if (slowestDay != null && slowestDay.getValue().compareTo(BigDecimal.valueOf(50)) < 0) {
            recommendations.add(String.format(
                    "Consider running promotions on %ss to increase utilization",
                    slowestDay.getKey().toString().toLowerCase()));
        }

        if (busiestDay != null && busiestDay.getValue().compareTo(BigDecimal.valueOf(85)) > 0) {
            recommendations.add(String.format(
                    "%s is frequently at high capacity - consider extending hours or adding space",
                    busiestHour));
        }

        if (slowestHour != null) {
            recommendations.add(String.format(
                    "The %s time slot has low utilization - consider happy hour specials",
                    slowestHour));
        }

        return recommendations;
    }
}

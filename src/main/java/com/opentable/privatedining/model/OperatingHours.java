package com.opentable.privatedining.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Operating hours for a specific day of the week.
 * Embedded within Restaurant document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperatingHours {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Day of week (0 = Sunday, 6 = Saturday).
     */
    private Integer dayOfWeek;

    /**
     * Opening time in HH:mm format.
     */
    private String openTime;

    /**
     * Closing time in HH:mm format.
     */
    private String closeTime;

    /**
     * Whether the restaurant is closed on this day.
     */
    @Builder.Default
    private Boolean isClosed = false;

    /**
     * Get opening time as LocalTime.
     */
    public LocalTime getOpenTimeAsLocalTime() {
        if (openTime == null || openTime.isEmpty()) {
            return null;
        }
        return LocalTime.parse(openTime, TIME_FORMATTER);
    }

    /**
     * Get closing time as LocalTime.
     */
    public LocalTime getCloseTimeAsLocalTime() {
        if (closeTime == null || closeTime.isEmpty()) {
            return null;
        }
        return LocalTime.parse(closeTime, TIME_FORMATTER);
    }
}

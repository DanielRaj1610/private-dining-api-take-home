package com.opentable.privatedining.model.enums;

/**
 * Granularity level for occupancy reports.
 */
public enum ReportGranularity {
    /**
     * Report aggregated by day.
     */
    DAILY,

    /**
     * Report aggregated by hour within each day.
     */
    HOURLY
}

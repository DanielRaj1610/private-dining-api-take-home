# ADR-003: Reporting Granularity

## Status
**Accepted**

## Date
2024-01-14

## Context

Restaurant owners need visibility into occupancy trends to plan availability efficiently. The reporting API must provide actionable insights while being performant for various date ranges.

**Key Requirements**:
- Accept date range as input
- Provide detailed breakdown of occupancy
- Support different analysis needs (daily trends, hourly peaks)
- Generate actionable recommendations

**Options Considered**:

1. **Fixed Daily Only**
   - One aggregation level
   - Simple but limited insights

2. **Fixed Hourly Only**
   - Granular data
   - May be overwhelming for long date ranges

3. **Configurable Granularity**
   - User chooses DAILY or HOURLY
   - Flexibility with complexity trade-off

## Decision

**Use Configurable Granularity with Multi-Level Breakdown**

Response structure:
```json
{
  "summary": { /* aggregate statistics */ },
  "dailyBreakdown": [
    {
      "date": "2024-01-15",
      "totalReservations": 8,
      "totalGuests": 52,
      "utilizationPercentage": 65.0,
      "peakHour": "19:00",
      "spaceBreakdown": [...],
      "hourlyBreakdown": [...] // Only if granularity=HOURLY
    }
  ],
  "insights": {
    "busiestDay": "SATURDAY",
    "slowestDay": "MONDAY",
    "recommendations": [...]
  }
}
```

Granularity options:
- `DAILY`: Summary per day with space breakdown
- `HOURLY`: Includes hourly breakdown within each day

## Consequences

### Positive
- Owners can analyze at preferred level of detail
- Daily view for trends, hourly for operational planning
- Insights help drive business decisions
- Space-level breakdown shows which rooms are underutilized

### Negative
- HOURLY reports for long date ranges may be large
- More complex aggregation queries
- Insight generation requires additional computation

### Mitigations
- Reasonable date range limits (max 90 days)
- Efficient MongoDB aggregation pipelines
- Caching for frequently requested reports (future enhancement)

## Utilization Calculation

```
Daily Utilization % = (Total Guests / Total Max Capacity) × 100
```

This simplified model provides a quick health metric. More sophisticated time-weighted utilization could be added as an enhancement.

## References
- `ReportingService.generateOccupancyReport()` implementation
- `OccupancyReportResponse` DTO structure
- `ReportGranularity` enum

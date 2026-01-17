# Assumptions

This document outlines all assumptions made during the implementation of the Private Dining Reservation System. These assumptions were made to fill gaps in the requirements and ensure a coherent, production-ready system.

## Table of Contents
1. [Technology Stack Assumptions](#1-technology-stack-assumptions)
2. [Business Logic Assumptions](#2-business-logic-assumptions)
3. [API Design Assumptions](#3-api-design-assumptions)
4. [Reporting Assumptions](#4-reporting-assumptions)
5. [Performance & Scalability Assumptions](#5-performance--scalability-assumptions)
6. [Data Model Assumptions](#6-data-model-assumptions)
7. [Out of Scope](#7-out-of-scope)

---

## 1. Technology Stack Assumptions

### 1.1 MongoDB as Primary Database
**Assumption**: MongoDB is the required database as specified in the starter project.

**Rationale**: The starter project was pre-configured with MongoDB and Spring Data MongoDB dependencies. This influenced the concurrency strategy.

**Impact**:
- Used MongoDB `findAndModify` atomic operations for capacity management (prevents race conditions)
- Implemented `SlotCapacity` collection to track booked capacity per time slot
- Used MongoDB aggregation pipelines for reporting queries
- Atomic capacity reservation ensures no overbooking even under concurrent load

### 1.2 Java 17+ Runtime
**Assumption**: The application will run on Java 17 or higher.

**Rationale**: Spring Boot 3.2 requires Java 17 minimum. Modern Java features (records, pattern matching) improve code quality.

### 1.3 Containerized Deployment
**Assumption**: The application will be deployed using Docker containers.

**Rationale**: Docker Compose provided for local development ensures consistent environments and simplifies setup.

---

## 2. Business Logic Assumptions

### 2.1 Operating Hours

**Assumption**: Operating hours are defined per restaurant, per day of week.

**Details**:
- Each restaurant has 7 operating hour entries (Sunday=0 through Saturday=6)
- Default hours: 9:00 AM - 10:00 PM
- Some days can be marked as closed (`isClosed: true`)
- Reservations must **start AND end** within operating hours

**Example**: If a restaurant closes at 10:00 PM and slot duration is 90 minutes, the last valid start time is 8:30 PM.

### 2.2 Time Slot Management

**Assumption**: Time slots are configurable per space, not globally.

**Details**:
- Each space has its own `slotDurationMinutes` (default: 60 minutes)
- Each space has a `bufferMinutes` between reservations (default: 15 minutes)
- Start times must align to slot boundaries (e.g., on the hour or half-hour)
- End time is automatically calculated: `endTime = startTime + slotDurationMinutes`

**Rationale**: Different spaces may have different turnover requirements (e.g., a wine cellar event vs. quick business lunch).

### 2.3 Flexible Capacity Model

**Assumption**: Multiple concurrent reservations are allowed in the same space at the same time, as long as the total party size doesn't exceed max capacity.

**Formula**: `SUM(party_sizes of overlapping confirmed reservations) ≤ max_capacity`

**Example**:
- Space "Garden Room" has `maxCapacity: 20`
- Existing reservation at 6 PM: 8 guests
- New request at 6 PM for 10 guests: **ALLOWED** (8 + 10 = 18 ≤ 20)
- New request at 6 PM for 15 guests: **REJECTED** (8 + 15 = 23 > 20)

**Rationale**: This allows restaurants to maximize space utilization while preventing overcrowding.

### 2.4 Minimum Capacity is Informational Only

**Assumption**: The `minCapacity` field is for informational purposes only and does not block small parties.

**Rationale**: Rejecting a party of 2 for a space with `minCapacity: 4` would be bad UX. The field helps staff suggest appropriate spaces.

### 2.5 Advance Booking Limit

**Assumption**: Reservations can only be made up to 90 days in advance.

**Rationale**: Prevents speculative far-future bookings that may never be fulfilled. Aligns with typical restaurant booking practices.

### 2.6 Reservation Cancellation

**Assumption**: Cancelled reservations remain in the database with status `CANCELLED`.

**Details**:
- Cancellation stores: timestamp, reason (optional)
- Cancelled reservations are excluded from capacity calculations
- Allows for historical analytics and audit trails

---

## 3. API Design Assumptions

### 3.1 URL Path Versioning

**Assumption**: API versioning uses URL path prefix `/api/v1/`.

**Rationale**: Most explicit and widely adopted approach. Clear in URLs, easy to route, and well-supported by tools.

### 3.2 Pagination Defaults

**Assumption**: List endpoints default to 20 items per page.

**Parameters**:
- `page`: 0-indexed page number (default: 0)
- `size`: items per page (default: 20)
- `sortBy`: field to sort by
- `sortDir`: `asc` or `desc`

### 3.3 ID Formats

**Assumption**:
- Restaurant and Reservation IDs use MongoDB ObjectId (24-character hex string)
- Space IDs use UUID (36-character UUID string)

**Rationale**: Space UUIDs allow for pre-generation and easier cross-system integration.

### 3.4 Time Format

**Assumption**: Times are represented as strings in `HH:mm` format (24-hour).

**Examples**: `"09:00"`, `"18:30"`, `"22:00"`

**Rationale**: Simple, unambiguous, and doesn't require timezone handling for local times.

### 3.5 Date Format

**Assumption**: Dates use ISO-8601 format (`YYYY-MM-DD`).

**Examples**: `"2024-02-15"`, `"2024-12-25"`

---

## 4. Reporting Assumptions

### 4.1 Reporting Granularity

**Assumption**: Occupancy reports support two granularity levels:
- **DAILY**: One entry per day with daily totals
- **HOURLY**: Includes hourly breakdown within each day

### 4.2 Utilization Calculation

**Assumption**: Utilization percentage is calculated as:

```
Daily Utilization % = (Total Guests for Day / Total Max Capacity of All Spaces) × 100
```

**Note**: This is a simplified model. A more sophisticated approach would consider time-weighted capacity across operating hours.

### 4.3 Only Confirmed Reservations

**Assumption**: Reports only include `CONFIRMED` status reservations.

**Rationale**: Cancelled and no-show reservations should not inflate occupancy metrics.

### 4.4 Insights Generation

**Assumption**: The system generates actionable recommendations based on:
- Busiest/slowest days of week
- Peak/off-peak hours
- Utilization thresholds (<50% = low, >85% = high)

**Example Recommendations**:
- "Consider running promotions on Mondays to increase utilization"
- "19:00 slot is frequently at capacity - consider extending hours"

---

## 5. Performance & Scalability Assumptions

### 5.1 Concurrency Strategy

**Assumption**: Atomic capacity management using MongoDB `findAndModify` prevents overbooking.

**Implementation**:
- `SlotCapacity` collection tracks booked capacity per space/date/time slot
- `findAndModify` with condition `bookedCapacity <= (maxCapacity - partySize)` ensures atomic reservation
- If condition fails, request is rejected with `CAPACITY_EXCEEDED` (409)
- Capacity released on reservation cancellation

**Verified**:
- Load tested with 10 concurrent users booking the same slot
- Result: Exactly 3 bookings succeed (9 capacity ÷ 3 party size), 7 rejected
- Zero overbooking, zero server errors

**Trade-off**: Chose `findAndModify` over distributed locks for simplicity. Additional `slot_capacities` collection adds minimal storage overhead (~100 bytes per slot).

### 5.2 Index Strategy

**Assumption**: The following indexes are critical for performance:

| Index | Purpose |
|-------|---------|
| `spaceId + reservationDate + status` | Availability queries |
| `reservationDate + startTime + endTime` | Overlap detection |
| `restaurantId + reservationDate` | Reporting queries |
| `customerEmail` | Customer lookup |

### 5.3 Query Patterns

**Assumption**: Most queries are filtered by:
1. Space + Date (availability checks)
2. Restaurant + Date Range (reporting)
3. Customer Email (lookup)

---

## 6. Data Model Assumptions

### 6.1 Separate Spaces Collection

**Assumption**: Spaces are stored in a separate MongoDB collection, not embedded in restaurants.

**Rationale**:
- Spaces need individual queries (by ID, by capacity)
- Space updates shouldn't require restaurant document updates
- Better query performance for space-specific operations

### 6.2 Operating Hours Embedded

**Assumption**: Operating hours are embedded within the restaurant document.

**Rationale**:
- Always queried together with restaurant
- Limited to 7 entries per restaurant
- No need for separate queries

### 6.3 Reservation Status Values

**Assumption**: Reservations have the following statuses:

| Status | Description |
|--------|-------------|
| `CONFIRMED` | Active reservation, counts toward capacity |
| `CANCELLED` | Cancelled by customer or restaurant |
| `COMPLETED` | Past reservation that was fulfilled |
| `NO_SHOW` | Customer didn't show up |

---

## 7. Out of Scope

The following features were explicitly considered out of scope for this implementation:

### 7.1 Authentication & Authorization
- No user authentication (JWT, OAuth)
- No role-based access control
- API is open/unauthenticated

### 7.2 Payment Processing
- No payment capture or processing
- No deposit handling
- No cancellation fees

### 7.3 Notification System
- No email confirmations
- No SMS reminders
- No webhook notifications

### 7.4 Multi-tenancy
- Single-tenant deployment assumed
- No restaurant-level isolation

### 7.5 Timezone Handling
- All times assumed to be in restaurant's local timezone
- No cross-timezone booking support

### 7.6 Recurring Reservations
- No support for weekly/monthly recurring bookings
- Each reservation is standalone

### 7.7 Waitlist Management
- No waitlist when capacity is full
- No automatic promotion from waitlist

### 7.8 Table/Seating Management
- No specific table assignments
- Space treated as single unit

---

## Summary

These assumptions were made to deliver a complete, functional system within the project scope. Each assumption is documented with rationale and impact to enable informed future modifications.

For questions about specific assumptions or to discuss alternative approaches, please refer to the Architecture Decision Records (ADRs) in `/docs/adr/`.

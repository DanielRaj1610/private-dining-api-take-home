# Future Improvements

## Overview

This document outlines proposed enhancements beyond the current implementation scope, demonstrating forward-thinking and system evolution planning.

---

## Improvement Roadmap

| Phase   | Focus Area                           | Timeline Estimate |
| ------- | ------------------------------------ | ----------------- |
| Phase 1 | Core Enhancements - Quick wins       | Near-term         |
| Phase 2 | Enhanced Features - Pricing, recurring | Medium-term      |
| Phase 3 | Scale & Security - Auth, multi-tenancy | Medium-term      |
| Phase 4 | Advanced Analytics - ML, dashboards  | Long-term         |

---

## Phase 1: Core Enhancements

### 1.1 Waitlist Management

**Problem**: Customers leave when capacity is full, losing potential business.

**Solution**: When capacity is exceeded, customers can join a waitlist and receive notifications when spots become available.

**Proposed API**:
```
POST /api/v1/waitlist
{
  "spaceId": "uuid",
  "reservationDate": "2024-02-15",
  "startTime": "18:00",
  "partySize": 6,
  "customerEmail": "customer@example.com"
}
```

**Features**:
- Auto-add to waitlist when capacity exceeded
- Priority queue (first-come-first-served)
- Auto-release after timeout (e.g., 24 hours)
- SMS/Email notification when spot opens

**Implementation Notes**:
- New `waitlist` collection in MongoDB
- Background job to process waitlist when cancellations occur
- Integration with notification service

### 1.2 Reservation Modifications

**Current Limitation**: Cancel and rebook required for any changes.

**Proposed API**:
```
PATCH /api/v1/reservations/{id}
{
  "partySize": 10,        // Changed from 8
  "startTime": "19:00"    // Changed from 18:00
}
```

**Business Rules**:
- Re-validate capacity for changes
- Track modification history
- Limit modifications (e.g., max 3 per booking)
- Release old capacity, reserve new capacity atomically

**Database Addition**:
```javascript
// Embedded in reservation document
"modificationHistory": [
  {
    "modifiedAt": ISODate("2024-02-10T10:00:00Z"),
    "changes": { "partySize": { "from": 8, "to": 10 } }
  }
]
```

### 1.3 Email/SMS Notifications

| Event              | Notification                           |
| ------------------ | -------------------------------------- |
| Booking Confirmed  | Confirmation email with details        |
| 24h Before         | Reminder with directions               |
| Cancellation       | Confirmation of cancellation           |
| Waitlist Available | Spot opened notification               |

**Technology Options**:
- SendGrid / AWS SES for email
- Twilio for SMS
- Spring Events for async processing

### 1.4 Report Export (UC-08)

**Current**: Reports return JSON via API only.

**Proposed**: Export occupancy reports in multiple formats.

**API**:
```
GET /api/v1/reports/occupancy/export?format=csv&restaurantId=X&startDate=Y&endDate=Z
```

**Formats**:

| Format | Use Case                              | Library           |
| ------ | ------------------------------------- | ----------------- |
| CSV    | Spreadsheet analysis, Excel import    | OpenCSV           |
| PDF    | Management presentations, printing    | OpenPDF / iText   |
| Excel  | Advanced data manipulation            | Apache POI        |

**Implementation Notes**:
- Consider async generation for large date ranges
- Store generated files in cloud storage (S3)
- Return download URL instead of file content for large reports

---

## Phase 2: Enhanced Features

### 2.1 Dynamic Pricing

**Concept**: Adjust pricing based on demand patterns.

```
Base Price: $100/hour

Peak Hours (Fri-Sat 6-9pm):    +25%  = $125/hour
Off-Peak (Mon-Thu lunch):      -20%  = $80/hour
Holidays:                      +50%  = $150/hour
```

**Database Addition**:
```javascript
// pricing_rules collection
{
  "_id": ObjectId("..."),
  "spaceId": "uuid",
  "ruleType": "PEAK",         // PEAK, OFF_PEAK, HOLIDAY
  "multiplier": 1.25,
  "startTime": "18:00",
  "endTime": "21:00",
  "daysOfWeek": [5, 6],       // Friday, Saturday
  "effectiveFrom": "2024-01-01",
  "effectiveTo": "2024-12-31"
}
```

### 2.2 Recurring Reservations

**Use Cases**:
- Weekly team dinners
- Monthly board meetings
- Annual celebrations

**Proposed API**:
```
POST /api/v1/reservations/recurring
{
  "spaceId": "uuid",
  "startDate": "2024-02-01",
  "endDate": "2024-12-31",
  "recurrencePattern": "WEEKLY",
  "dayOfWeek": "THURSDAY",
  "startTime": "18:00",
  "partySize": 12,
  "customerEmail": "organizer@company.com"
}
```

**Implementation Considerations**:
- Generate individual reservation documents for each occurrence
- Link with `recurringGroupId` for batch operations
- Support bulk cancellation of future occurrences

### 2.3 Deposit & Payments

**Flow**:
```
Booking Created → Deposit Required → Paid (via Stripe) → Confirmed
```

**Timeout Handling**:
- If deposit not paid within 30 minutes → Auto-cancel and release capacity

**Integration Options**:
- Stripe for payment processing
- Square for POS integration
- PayPal as alternative

---

## Phase 3: Scale & Security

### 3.1 Authentication & Authorization

**Technology**: OAuth2 / JWT

**Role-Based Access**:

| Role     | Permissions                                    |
| -------- | ---------------------------------------------- |
| CUSTOMER | Book reservations, view/cancel own bookings    |
| MANAGER  | Access reports, manage spaces, view all bookings |
| ADMIN    | Full system access, user management            |

**Implementation Approach**:
- Use Spring Security framework with JWT token-based authentication
- Implement role-based access control (RBAC) with method-level security annotations
- Create custom filters to validate JWT tokens and extract user roles
- Support API key authentication for B2B integrations using separate authentication provider
- Store user roles and permissions in the database linked to user accounts

### 3.2 Multi-Tenancy

Support multiple restaurant chains on a shared platform.

**Data Isolation Options**:

| Strategy          | Pros                        | Cons                      |
| ----------------- | --------------------------- | ------------------------- |
| Schema-per-tenant | Strong isolation            | Management overhead       |
| Row-level security| Simpler management          | Query complexity          |
| Database-per-tenant | Complete isolation        | Highest cost              |

**Recommended**: Row-level security with `tenantId` field for cost-effective scaling.

### 3.3 API Rate Limiting

**Implementation Approach**:
- Use Spring Cloud Gateway with Redis-backed rate limiting for distributed tracking
- Define rate limit configurations: booking endpoints limited to 10 requests/minute per user, reporting endpoints to 5 requests/minute
- Apply rate limiters using declarative annotations or programmatic configuration
- Implement different limits for authenticated users (higher limits) vs. anonymous users (lower limits)
- Return HTTP 429 (Too Many Requests) with Retry-After header when limits exceeded

### 3.4 Caching Strategy

| Data Type         | Cache Strategy          | TTL      |
| ----------------- | ----------------------- | -------- |
| Restaurant info   | Redis cache             | 1 hour   |
| Space details     | Redis cache             | 30 min   |
| Availability      | Short-lived cache       | 30 sec   |
| Reports           | Pre-computed cache      | 5 min    |

---

## Phase 4: Advanced Analytics

### 4.1 Predictive Analytics

**Pipeline**: Historical Data → ML Model → Predictions

**Prediction Types**:

| Prediction       | Input Data                    | Output                        |
| ---------------- | ----------------------------- | ----------------------------- |
| Demand Forecast  | Historical bookings, calendar | Expected bookings per slot    |
| No-Show Risk     | Customer history, booking lead time | Risk score (0-100)      |
| Revenue Forecast | Pricing rules, demand forecast | Projected revenue            |

**Technology Options**:
- Python ML pipeline with scikit-learn
- AWS SageMaker for managed ML
- Export data to analytics warehouse (Snowflake/BigQuery)

### 4.2 Real-Time Dashboard

**Dashboard Widgets**:
- Today's bookings (count vs. capacity)
- Live occupancy percentage
- Weekly booking trends
- Revenue trend charts
- Cancellation rate monitoring

**Technology**:
- WebSocket for real-time updates
- React/Vue dashboard frontend
- Grafana for operational metrics

### 4.3 Integration Ecosystem

| Integration     | Purpose                    | Priority |
| --------------- | -------------------------- | -------- |
| Google Calendar | Sync reservations          | High     |
| Stripe/Square   | Payment processing         | High     |
| Twilio          | SMS notifications          | Medium   |
| SendGrid        | Email notifications        | Medium   |
| Slack           | Staff notifications        | Low      |
| POS Systems     | Order management           | Low      |

---


## Technical Debt Backlog

| Item                              | Priority | Effort | Impact       |
| --------------------------------- | -------- | ------ | ------------ |
| Increase test coverage to 90%    | High     | Medium | Quality      |
| Add request tracing (Jaeger)     | Medium   | Low    | Observability|
| Implement circuit breakers       | Medium   | Medium | Resilience   |
| Database query optimization      | High     | Medium | Performance  |
| API documentation improvements   | Low      | Low    | DX           |
| Containerize with Kubernetes     | Medium   | High   | Scalability  |

---

## Migration Considerations

### Database Schema Evolution

For features like waitlist and pricing rules, use MongoDB's flexible schema:

```javascript
// Add fields incrementally - no migration needed
db.reservations.updateMany(
  { modificationHistory: { $exists: false } },
  { $set: { modificationHistory: [] } }
)
```

### API Versioning Strategy

Current: `/api/v1/`

When breaking changes are needed:
1. Introduce `/api/v2/` endpoints
2. Maintain `/api/v1/` for backward compatibility
3. Set deprecation timeline (e.g., 6 months)
4. Migrate clients to v2

---

## Related Documents

- [Scalability Analysis](./SCALABILITY.md)
- [System Design](./SYSTEM_DESIGN.md)
- [ADR-004: API Versioning](./adr/004-api-versioning.md)
- [Use Cases on Confluence](https://danielstanlee.atlassian.net/wiki/external/MzgxNThkMzUwNjRkNGUxNjkzMjI3OTQzYWI5ODQwYjI)

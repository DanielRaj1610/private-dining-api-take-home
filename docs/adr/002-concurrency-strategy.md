# ADR-001: Concurrency Strategy for Reservation Booking

## Status
**Superseded** - Updated 2026-01-16

## Date
- Original: 2024-01-14
- Updated: 2026-01-16

## Context

The Private Dining Reservation System needs to handle concurrent booking requests for the same time slot. Multiple customers may attempt to book the last available capacity simultaneously, creating a race condition.

**Key Requirements**:
- Prevent overbooking (total party size exceeding max capacity)
- Support flexible capacity model (multiple reservations per slot allowed)
- Provide good user experience (fast response, clear errors)
- Work with MongoDB (no native pessimistic locking)

**Problem Identified (2026-01-16)**:
Load testing revealed that the original optimistic locking approach using `@Version` on the Reservation entity was **insufficient**. The issue:
1. Multiple requests read the current capacity (all see the same value)
2. All pass the capacity validation check
3. All create new documents (no version conflict since they're new documents)
4. Result: Overbooking occurs (e.g., 30 guests booked in a 9-capacity space)

The `@Version` annotation only prevents conflicts when **updating the same document**, not when **inserting new documents** that share a logical constraint.

## Decision

**Use Atomic Capacity Counter with MongoDB `findAndModify`**

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Reservation Request                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  1. Validate Request (operating hours, party size, date)        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. ATOMIC: SlotCapacityService.tryReserveCapacity()            │
│     - Uses findAndModify with condition:                        │
│       bookedCapacity <= (maxCapacity - partySize)               │
│     - Atomically increments bookedCapacity if condition met     │
│     - Returns false if insufficient capacity                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │                   │
              (success)            (failure)
                    │                   │
                    ▼                   ▼
┌───────────────────────┐   ┌───────────────────────┐
│  3. Create Reservation │   │  Return 409 Conflict  │
│     Document           │   │  CAPACITY_EXCEEDED    │
└───────────────────────┘   └───────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. If save fails: Release capacity via releaseCapacity()       │
└─────────────────────────────────────────────────────────────────┘
```

### Implementation Details

**New Model: `SlotCapacity`**
```java
@Document(collection = "slot_capacities")
public class SlotCapacity {
    @Id
    private String id;  // Format: "{spaceId}:{date}:{startTime}"

    private UUID spaceId;
    private LocalDate date;
    private String startTime;
    private String endTime;
    private Integer bookedCapacity;
    private Integer maxCapacity;

    @Version
    private Long version;
}
```

**Atomic Capacity Reservation:**
```java
public boolean tryReserveCapacity(Space space, LocalDate date,
                                   String startTime, String endTime,
                                   int partySize) {
    // Ensure slot document exists (upsert)
    ensureSlotExists(slotId, space.getId(), date, startTime, endTime, maxCapacity);

    // Atomic increment only if capacity allows
    Query query = new Query(Criteria.where("_id").is(slotId)
            .and("bookedCapacity").lte(maxCapacity - partySize));

    Update update = new Update().inc("bookedCapacity", partySize);

    SlotCapacity result = mongoTemplate.findAndModify(
            query, update,
            FindAndModifyOptions.options().returnNew(true),
            SlotCapacity.class
    );

    return result != null;  // null means condition not met
}
```

**Capacity Release on Cancellation:**
```java
public void releaseCapacity(UUID spaceId, LocalDate date,
                            String startTime, int partySize) {
    Query query = new Query(Criteria.where("_id").is(slotId));
    Update update = new Update().inc("bookedCapacity", -partySize);
    mongoTemplate.findAndModify(query, update, SlotCapacity.class);
}
```

### Key Components

| Component | Purpose |
|-----------|---------|
| `SlotCapacity` | Tracks booked capacity per space/date/time slot |
| `SlotCapacityService` | Atomic capacity operations via `findAndModify` |
| `ReservationService` | Orchestrates validation → capacity → save |

## Consequences

### Positive
- **Guaranteed consistency**: Atomic operations prevent race conditions
- **No overbooking**: Mathematically impossible to exceed capacity
- **Proven under load**: Validated with k6 load tests (10 concurrent users)
- **Simple recovery**: Capacity release on cancellation or save failure
- **No external dependencies**: Uses native MongoDB operations

### Negative
- **Additional collection**: `slot_capacities` adds storage overhead
- **Eventual consistency for reads**: Availability API reads from reservations, not slot_capacities
- **Cleanup needed**: Old slot capacity records should be cleaned up periodically

### Trade-offs
- **Write performance**: Slight overhead from dual writes (slot + reservation)
- **Read consistency**: Availability reads are eventually consistent with capacity counter
- **Storage**: ~100 bytes per slot × slots per day × days = minimal overhead

## Load Test Results

Race condition test with 10 concurrent users booking the same slot:

| Metric | Expected | Actual | Result |
|--------|----------|--------|--------|
| Max Capacity | 9 | 9 | - |
| Party Size | 3 | 3 | - |
| Max Successful Bookings | 3 | 3 | ✅ |
| Capacity Exceeded Errors | 7 | 7 | ✅ |
| Server Errors | 0 | 0 | ✅ |
| Total Guests Booked | ≤9 | 9 | ✅ |

```bash
# Run race condition test
./load-tests/run-test.sh race-condition-test
```

## Alternatives Considered (and Rejected)

### 1. Optimistic Locking with @Version Only
- **Problem**: Version field on Reservation doesn't prevent new document race conditions
- **Result**: Overbooking occurred in load tests

### 2. MongoDB Transactions
- **Problem**: Requires replica set (not available in embedded MongoDB for dev)
- **Complexity**: Higher operational overhead

### 3. Distributed Locking (Redis)
- **Problem**: Additional infrastructure dependency
- **Complexity**: Network partition handling, lock expiration

### 4. Pessimistic Locking
- **Problem**: MongoDB doesn't support row-level locking
- **Workaround**: Would require external lock service

## Migration Notes

When upgrading from the previous optimistic-locking-only approach:

1. Deploy the new `SlotCapacity` model and service
2. The system will auto-create slot capacity documents on first booking
3. Existing reservations don't need migration (slots created on-demand)
4. Optional: Run a one-time sync to pre-populate slot_capacities from existing reservations

## References

- [MongoDB findAndModify](https://www.mongodb.com/docs/manual/reference/method/db.collection.findAndModify/)
- [Spring Data MongoDB - Atomic Operations](https://docs.spring.io/spring-data/mongodb/reference/mongodb/template-update.html)
- `SlotCapacityService.tryReserveCapacity()` implementation
- `ReservationService.doCreateReservation()` implementation
- Load tests: `load-tests/race-condition-test.js`

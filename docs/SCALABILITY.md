# Scalability

## Current Design

The system is designed to handle high-traffic scenarios with the following characteristics:

### Concurrency Handling

- **Strategy**: Atomic capacity management using MongoDB `findAndModify`
- **Implementation**: `SlotCapacityService.tryReserveCapacity()` performs atomic check-and-update
- **Suitable for**: High concurrency (hundreds of concurrent requests per second)
- **Trade-off**: Additional `slot_capacities` collection adds minimal storage overhead (~100 bytes per slot)

**How It Works**:
```
findAndModify:
  Query: { "_id": slotId, "bookedCapacity": { $lte: maxCapacity - partySize } }
  Update: { $inc: { "bookedCapacity": partySize } }
```

This atomic operation guarantees:
- No overbooking under any load condition
- No distributed locks required
- No retry storms under contention
- Immediate rejection when capacity is insufficient

### Database Performance

- **Indexes**: Compound indexes on critical query paths
  - `reservations`: spaceId + date + status, date + time range, restaurantId + date
  - `slot_capacities`: _id (compound key), spaceId + date
- **Aggregations**: Server-side aggregation for reports
- **Connection Pooling**: Spring Data MongoDB default pooling

### Stateless Design

- No server-side session state
- Horizontal scaling ready with load balancer
- Atomic operations work across multiple API instances

## Load Test Results

### Race Condition Test (10 Concurrent Users)

| Metric | Expected | Actual | Result |
|--------|----------|--------|--------|
| Max Capacity | 9 | 9 | - |
| Party Size | 3 | 3 | - |
| Max Successful Bookings | 3 | 3 | Pass |
| Capacity Exceeded Errors | 7 | 7 | Pass |
| Server Errors | 0 | 0 | Pass |
| Total Guests Booked | <=9 | 9 | Pass |

### Edge Case Tests

| Test | Description | Result |
|------|-------------|--------|
| Concurrent Cancellation | 5 VUs cancel same reservation | Only 1 succeeds |
| Partial Capacity | Fill large, then small party | Correctly accommodates |
| Book-Cancel Cycles | Rapid state changes | Capacity properly tracked |
| Boundary Conditions | Exact capacity limits | No overbooking |

Run tests:
```bash
./load-tests/run-test.sh race-condition-test
./load-tests/run-test.sh edge-case-test
```

## Bottleneck Analysis

| Component | Bottleneck Risk | Mitigation |
|-----------|-----------------|------------|
| Concurrent Bookings | **Low** | Atomic `findAndModify` prevents race conditions |
| Report Generation | Low | Pre-computed for common ranges |
| Database Queries | Low | Proper indexing on all query paths |
| Capacity Tracking | **Very Low** | Dedicated `slot_capacities` collection with compound key |

## Scaling Recommendations

### Phase 1: Current (Single Instance)
- Atomic capacity management handles concurrent load
- MongoDB indexes optimize query performance
- Suitable for: 100-500 reservations/minute

### Phase 2: Horizontal Scaling
- Deploy multiple API instances behind load balancer
- **No code changes needed** - atomic operations work across instances
- MongoDB replica set for high availability and read scaling
- Suitable for: 500-2000 reservations/minute

### Phase 3: Advanced (High Volume)
- Redis cache for availability data (read-heavy optimization)
- Message queue for async operations (notifications, analytics)
- Database sharding by restaurant or region
- Suitable for: 2000+ reservations/minute

## Performance Characteristics

| Operation | Expected Latency | Throughput |
|-----------|------------------|------------|
| Create Reservation | <200ms | 100+ req/s |
| Check Availability | <100ms | 500+ req/s |
| Cancel Reservation | <150ms | 200+ req/s |
| Generate Report | <500ms | 50 req/s |

## MongoDB-Specific Scalability

### Why `findAndModify` Scales Well

1. **Single Document Operation**: MongoDB guarantees atomicity for single-document operations
2. **No Locks**: Unlike distributed locks, no lock contention or timeout issues
3. **Immediate Response**: Either succeeds or fails immediately, no retry loops
4. **Consistent**: Works correctly even with multiple API instances

### Index Strategy for Scale

```javascript
// Reservations - for availability queries
db.reservations.createIndex({ "spaceId": 1, "reservationDate": 1, "status": 1 })

// Reservations - for overlap detection
db.reservations.createIndex({ "reservationDate": 1, "startTime": 1, "endTime": 1 })

// SlotCapacities - primary key is compound
db.slot_capacities.createIndex({ "spaceId": 1, "date": 1 })
```

## References

- [ADR-002: Concurrency Strategy](adr/002-concurrency-strategy.md)
- [Load Tests README](../load-tests/README.md)
- [Capacity Model on Confluence](https://danielstanlee.atlassian.net/wiki/external/YmQ2ZTZiZWQxMTc5NGM4MGI0NmZlYWZiN2ZlMjU0ZTA)

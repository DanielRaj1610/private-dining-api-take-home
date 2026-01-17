# Load Tests for Private Dining API

Performance and stress tests using [k6](https://k6.io/).

## Prerequisites

1. Install k6:
   ```bash
   # macOS
   brew install k6

   # Linux
   sudo gpg -k
   sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
   echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
   sudo apt-get update
   sudo apt-get install k6

   # Windows
   choco install k6
   ```

2. Ensure the API is running:
   ```bash
   # Using Docker Compose
   docker-compose up -d

   # Or run directly
   ./mvnw spring-boot:run
   ```

## Dynamic Test Data

**All tests now dynamically fetch test data from the API** - no hardcoded IDs required!

Each test:
1. **Setup**: Fetches restaurants and spaces from the API at startup
2. **Execution**: Uses the fetched IDs for all operations
3. **Teardown**: Automatically cancels all reservations created during the test

This means:
- Tests work with any seed data
- No need to update IDs when data changes
- Automatic cleanup after each test run

## Available Tests

| Test | Description | Duration |
|------|-------------|----------|
| `quick-benchmark.js` | Single user baseline test (50 iterations) | ~1 minute |
| `race-condition-test.js` | Concurrent booking stress test | ~20 seconds |
| `concurrent-booking-test.js` | Multi-scenario stress test | ~3 minutes |
| `mixed-operations-test.js` | Realistic mixed traffic | ~2 minutes |
| `benchmark.js` | Comprehensive phased benchmark | ~4.5 minutes |
| `edge-case-test.js` | Edge case and boundary testing | ~2.5 minutes |

## Shared Utilities

All tests use `test-utils.js` which provides:

- `fetchTestData()` - Fetches restaurant and space data from the API
- `getFutureDate(daysAhead)` - Generates valid future dates (skips closed days)
- `getAvailableSlot(spaceId, date)` - Finds available time slots
- `getFullyAvailableSlot(spaceId, date, maxCapacity)` - Finds slots with full capacity (for race condition tests)
- `createReservation(params)` - Creates a reservation
- `cancelReservation(id, reason)` - Cancels a reservation
- `cleanupTestReservations(spaceId, date, emailPattern)` - API-based cleanup by email pattern
- `getBaseUrl()` - Returns the API base URL

## Running Tests

### Using the Test Runner Script (Recommended)

The `run-test.sh` script handles test execution, report generation, and cleanup automatically:

```bash
# Run quick benchmark with automatic cleanup
./load-tests/run-test.sh quick-benchmark

# Run race condition test
./load-tests/run-test.sh race-condition-test

# Run full benchmark
./load-tests/run-test.sh benchmark

# Pass additional k6 options
./load-tests/run-test.sh race-condition-test -e PARTY_SIZE=2
```

The script will:
1. Run the k6 test
2. Save logs to `load-tests/results/<test-name>_<timestamp>.log`
3. Save JSON metrics to `load-tests/results/<test-name>_<timestamp>.json`
4. Generate a summary report at `load-tests/results/<test-name>_<timestamp>_summary.txt`
5. Clean up test reservations from the database

### Running Tests Directly with k6

#### Quick Benchmark (Recommended First)
Single user test to measure baseline latency:
```bash
k6 run load-tests/quick-benchmark.js
```

### Race Condition Test
Tests atomic capacity management by having 10 users simultaneously book the same slot:
```bash
k6 run load-tests/race-condition-test.js
```

**Expected behavior**:
- Space capacity divided by party size = max successful bookings
- Example: 9 capacity / 3 party size = exactly 3 succeed, 7 get 409

### Concurrent Booking Test
Multi-scenario stress test with burst, sustained, and ramping loads:
```bash
k6 run load-tests/concurrent-booking-test.js
```

### Edge Case Test
Tests boundary conditions and potential race conditions:
- Concurrent cancellation of the same reservation
- Partial capacity fits (small party after larger ones)
- Rapid book-cancel cycles
- Exact capacity boundary conditions

```bash
k6 run load-tests/edge-case-test.js
```

### Mixed Operations Test
Simulates realistic traffic with different request rates:
- Availability checks: 20/second
- Reservations: 5/second
- Reports: 1/second

```bash
k6 run load-tests/mixed-operations-test.js
```

### Full Benchmark
Comprehensive test with multiple phases:
1. Baseline (1 user) - measure raw latency
2. Ramp up (0→10 users) - gradual increase
3. Steady (10 users) - sustained load
4. Peak (10→20 users) - increase to peak
5. Peak hold (20 users) - sustained peak
6. Concurrent stress (10 users same slot) - stress test

```bash
k6 run load-tests/benchmark.js
```

Results are saved to `load-tests/results.json`.

## Configuration

Override default settings with environment variables:
```bash
# Custom base URL
k6 run -e BASE_URL=http://localhost:8080/api/v1 load-tests/quick-benchmark.js

# Custom party size for race condition test
k6 run -e PARTY_SIZE=2 load-tests/race-condition-test.js
```

## Test Data Requirements

Tests automatically fetch data from the API. Ensure your database has:

1. **At least one restaurant** with operating hours defined
2. **At least one active space** for that restaurant
3. **Operating hours** that include some weekdays (tests skip Sunday/Monday by default)

The tests will use whatever data is available and log what they find during setup.

## Performance Thresholds

Default thresholds (can be adjusted in test files):

| Metric | Threshold |
|--------|-----------|
| HTTP request duration (p95) | < 1000ms |
| HTTP request duration (p99) | < 2000ms |
| Availability check (p95) | < 200ms |
| Reservation create (p95) | < 500ms |
| Report generate (p95) | < 2000ms |
| HTTP failure rate | < 10% |

## Interpreting Results

### Key Metrics

- **reservation_success** / **booking_success**: Number of successful reservations
- **reservation_failed** / **booking_error**: Number of failed reservations (server errors)
- **capacity_exceeded** / **booking_capacity_exceeded**: Number of rejections due to capacity limits
- **http_req_duration**: Overall request latency

### Expected Behavior

1. **Race Condition Test**: Most requests should fail with capacity exceeded (409) - this confirms atomic capacity management is working
2. **Concurrent Booking Test**: Success rate depends on capacity; failures should be 409 (capacity) not 500 (server error)
3. **Mixed Operations**: All operations should stay within thresholds under normal load
4. **Edge Case Test**: All edge cases should pass with proper capacity tracking

## Automatic Cleanup

Tests handle cleanup in two ways:

### 1. API-Based Cleanup (quick-benchmark, race-condition-test)
Tests that use a fixed date can perform API-based cleanup in their `teardown()` function:
- Fetches reservations from the API by space and date
- Filters by email pattern (e.g., `bench-*@test.com`, `race*@test.com`)
- Cancels matching reservations via API

### 2. Shell Script Cleanup (run-test.sh)
The `run-test.sh` script performs MongoDB-based cleanup after every test:
- Deletes reservations matching test customer name patterns
- Cleans up slot capacity records
- Works regardless of which test was run

### Test Email Patterns
Each test uses a distinct email pattern for identification:
| Test | Email Pattern |
|------|---------------|
| quick-benchmark | `bench-*@test.com` |
| race-condition-test | `race*@test.com` |
| concurrent-booking-test | `stress_*@test.com` |
| mixed-operations-test | `mixed_*@test.com` |
| benchmark | `bench_*@test.com` |
| edge-case-test | `*@test.com` (various patterns) |

### Manual Cleanup
If cleanup fails or you need manual cleanup:
```bash
# Connect to MongoDB
docker exec -it private-dining-mongodb mongosh private_dining

# Delete test reservations by email pattern
db.reservations.deleteMany({
  customerEmail: { $regex: /@test\.com$/ },
  status: "CONFIRMED"
})

# Or delete by customer name pattern
db.reservations.deleteMany({
  customerName: { $regex: /^(Benchmark|Load Test|Race|Mixed Test|Stress Test|Edge Test)/ }
})

# Cancel instead of delete (preserves history)
db.reservations.updateMany(
  { customerEmail: { $regex: /@test\.com$/ }, status: "CONFIRMED" },
  { $set: { status: "CANCELLED", cancelledAt: new Date() } }
)
```

### k6 VU Isolation Note
Due to k6's Virtual User (VU) isolation, arrays cannot be shared between VUs during test execution. This means tests with multiple VUs cannot track reservation IDs in memory. The API-based cleanup and shell script cleanup work around this limitation.

## Troubleshooting

### Tests fail with "Could not fetch test data from API"
- Ensure the API is running and accessible at the BASE_URL
- Check Docker containers are healthy: `docker-compose ps`
- Verify seed data exists: check that restaurants and spaces are in the database

### All reservations fail with 400 errors
- Check operating hours - tests generate dates for open days only
- Verify spaces have valid time slots configured
- Check the API logs for validation error details

### High failure rate during concurrent tests
- This is expected behavior - atomic capacity management prevents overbooking
- Check that failures are 409 (capacity) not 500 (server error)
- Review the metrics: `booking_capacity_exceeded` should account for most failures

### Cleanup reports failures
- Some reservations may have already been cancelled by the test logic
- 400 status on cleanup means "already cancelled" - counted as success
- Only non-200/400 responses are counted as cleanup failures

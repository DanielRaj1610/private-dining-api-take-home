# Postman Collection - Private Dining Reservation System (MongoDB)

This folder contains Postman collection and environment files for testing the Private Dining Reservation System API.

## Files

| File | Description |
|------|-------------|
| `Private_Dining_API.postman_collection.json` | Main collection with all API tests |
| `Private_Dining_Local.postman_environment.json` | Environment variables for local testing |

## Quick Start

1. **Import Collection**: In Postman, click `Import` and select `Private_Dining_API.postman_collection.json`
2. **Import Environment**: Click `Import` again and select `Private_Dining_Local.postman_environment.json`
3. **Select Environment**: In the top-right dropdown, select "Private Dining - Local (MongoDB)"
4. **Run Tests**: Execute the collection in order, starting with "1. Setup & Health Check"

## Test Sections

### 1. Setup & Health Check
- **1.1** Health endpoint verification (API and MongoDB status)
- **1.2** List restaurants and auto-populate variables
- **1.3** Get restaurant details
- **1.4** List spaces by restaurant
- **1.5** Get availability and find bookable slot

### 2. Happy Path - Reservation Lifecycle
- **2.1** Create reservation
- **2.2** Get reservation by ID
- **2.3** Verify capacity reduced
- **2.4** Cancel reservation
- **2.5** Verify capacity restored

### 3. Validation Tests
- **3.1** Reject - Before opening hours (06:00)
- **3.2.1** Reject - End time crosses midnight (23:00)
- **3.2.2** Reject - Early morning (00:30)
- **3.3** Reject - Invalid time slot (18:47)
- **3.4.1** Reject - Party size exceeds space max
- **3.4.2** Reject - Party size over 100
- **3.5** Reject - Party size zero
- **3.6** Reject - Past date
- **3.7** Reject - Beyond 90 days advance booking
- **3.8** Reject - Missing required fields
- **3.9** Reject - Invalid email format
- **3.10** Reject - Non-existent space
- **3.11** Reject - Non-existent reservation
- **3.12** Reject - Cancel already cancelled

### 4. Reporting
- **4.1** Occupancy report - Daily granularity
- **4.2** Occupancy report - Hourly granularity
- **4.3** Occupancy report - With space filter
- **4.4** Reject - Non-existent restaurant

### 5. Restaurant API
- **5.1** List all restaurants (paginated)
- **5.2** Get single restaurant
- **5.3** Get restaurant spaces
- **5.4** Get non-existent restaurant (404)

### 6. Advanced Reservation Scenarios
Tests the flexible capacity model with multiple bookings in the same time slot:

- **6.1** List all reservations (paginated)
- **6.2** Setup - Get fresh availability for testDate2
- **6.3** Create first booking in slot (party of 4)
- **6.4** Create second booking in SAME slot (party of 3) - Verifies flexible capacity
- **6.5** Verify combined capacity usage (7 guests total)
- **6.6** Get slot capacity endpoint
- **6.7** Delete reservation (with capacity release)
- **6.8** Verify capacity restored after delete
- **6.9** Cleanup - Cancel remaining reservation
- **6.10** Reject - Delete non-existent reservation

## MongoDB-Specific Notes

### ID Formats
- **Restaurant IDs**: MongoDB ObjectIds (24-character hex strings)
  - Example: `507f1f77bcf86cd799439011`
- **Space IDs**: UUIDs
  - Example: `550e8400-e29b-41d4-a716-446655440000`
- **Reservation IDs**: MongoDB ObjectIds
  - Example: `507f1f77bcf86cd799439012`

### API Endpoint Differences from PostgreSQL Version
| Feature | MongoDB Version | PostgreSQL Version |
|---------|-----------------|-------------------|
| Availability | `GET /api/v1/availability/spaces/{spaceId}` | `GET /api/v1/spaces/{spaceId}/availability` |
| Cancel | `POST /api/v1/reservations/{id}/cancel` | `PATCH /api/v1/reservations/{id}/cancel` |
| Space ID format | UUID | Integer |
| Restaurant ID format | ObjectId | Integer |

## Running the Collection

### Using Postman GUI
1. Click on the collection name
2. Click "Run" button
3. Select tests to run (or run all)
4. Click "Run Private Dining..." button

### Using Newman (CLI)
```bash
# Install Newman
npm install -g newman

# Run collection
newman run Private_Dining_API.postman_collection.json \
  -e Private_Dining_Local.postman_environment.json \
  --reporters cli,json \
  --reporter-json-export results.json
```

## Collection Variables

| Variable | Description | Auto-populated |
|----------|-------------|----------------|
| `baseUrl` | API base URL | No (default: `http://localhost:8080/api/v1`) |
| `healthUrl` | Health endpoint URL | No (default: `http://localhost:8080/actuator/health`) |
| `restaurantId` | MongoDB ObjectId | Yes (from test 1.2) |
| `spaceId` | Space UUID | Yes (from test 1.2) |
| `maxCapacity` | Space max capacity | Yes (from test 1.4) |
| `minCapacity` | Space min capacity | Yes (from test 1.4) |
| `testDate` | Primary test date (YYYY-MM-DD) | Yes (from test 1.2) |
| `testDate2` | Secondary test date for Section 6 | Yes (from test 1.2) |
| `availableStartTime` | Available slot start time | Yes (from test 1.5) |
| `availableEndTime` | Available slot end time | Yes (from test 1.5) |
| `reservationId` | Primary reservation ID | Yes (from test 2.1) |
| `reservationId2` | First booking ID (Section 6) | Yes (from test 6.3) |
| `reservationId3` | Second booking ID (Section 6) | Yes (from test 6.4) |
| `firstBookingStartTime` | Start time from first booking response | Yes (from test 6.3) |
| `slotEndTime` | End time for capacity endpoint | Yes (from test 6.5) |
| `capacityAfterBooking` | Capacity after booking (for comparison) | Yes |
| `bookedCapacityBeforeDelete` | Booked capacity before delete (for comparison) | Yes |

Variables are automatically populated during test execution. Run tests in sequence using Collection Runner for proper variable propagation.

## Expected Results

When running against a properly seeded database:
- **42 requests** across 6 sections
- **102 tests** should pass
- Setup tests (Section 1) populate variables for subsequent tests
- Validation tests (Section 3) verify correct error responses
- Section 6 tests the flexible capacity model with concurrent bookings
- All tests clean up after themselves (cancel/delete reservations)

## Troubleshooting

### "Restaurant not found" errors
Ensure the database is seeded. Run:
```bash
docker compose -f docker-compose.db.yml up -d
```

### "Space not found" errors
Run the setup tests first to populate the `spaceId` variable.

### "Outside operating hours" on validation tests
The test uses 06:00 and 23:30 which are intentionally outside default hours (09:00-22:00).

### Tests fail on Sunday/Monday
The collection automatically skips Sunday and Monday (often closed days). If your restaurant has different hours, adjust the pre-request scripts.

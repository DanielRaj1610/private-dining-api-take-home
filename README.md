# Private Dining Reservation System

A robust Java/Spring Boot API for managing private dining reservations with intelligent availability management, flexible capacity model, and occupancy analytics.

## Features

### Feature 1: Reservation Management (POST API)
- **Operating Windows**: Enforce reservations within defined hours (e.g., 9:00 AM - 10:00 PM) per restaurant/day
- **Slot Optimization**: Time-block intervals aligned to configurable slot durations per space
- **Flexible Capacity Management**: Concurrent reservations allowed if total party size ≤ max capacity
- **Atomic Concurrency Control**: MongoDB `findAndModify` ensures no overbooking under concurrent load

### Feature 2: Occupancy Reporting (GET API)
- **Date Range Analytics**: Query occupancy data for any date range
- **Multi-level Granularity**: Daily and hourly breakdowns
- **Space-level Insights**: Per-space utilization metrics
- **Actionable Recommendations**: AI-generated insights for optimizing restaurant availability

## Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 17+ |
| Framework | Spring Boot | 3.2.0 |
| Database | MongoDB | 7.0 |
| Build Tool | Maven | 3.9+ |
| API Docs | SpringDoc OpenAPI | 2.3.0 |
| Containerization | Docker | Latest |

## Prerequisites

- Java 17 or higher
- Maven 3.9+ (or use included `./mvnw`)
- Docker & Docker Compose (for MongoDB)

## Quick Start

### Option 1: Using Docker (Recommended)

```bash
# Start MongoDB with seed data
docker compose -f docker-compose.db.yml up -d

# Run the application
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Option 2: Full Docker Stack

```bash
# Start everything (MongoDB + App)
docker compose up -d

# Access the application at http://localhost:8080
```

### Option 3: Embedded MongoDB (Development)

```bash
# Uses embedded Flapdoodle MongoDB
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## API Documentation

Once running, access Swagger UI at:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

## API Endpoints

### Reservations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/reservations` | List all reservations (paginated) |
| GET | `/api/v1/reservations/{id}` | Get reservation by ID |
| POST | `/api/v1/reservations` | Create new reservation |
| POST | `/api/v1/reservations/{id}/cancel` | Cancel a reservation |
| DELETE | `/api/v1/reservations/{id}` | Delete a reservation |

### Restaurants
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/restaurants` | List all restaurants (paginated) |
| GET | `/api/v1/restaurants/{id}` | Get restaurant by ID |
| POST | `/api/v1/restaurants` | Create new restaurant |
| GET | `/api/v1/restaurants/{id}/spaces` | Get spaces for a restaurant |

### Availability
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/availability/{spaceId}` | Get availability for a space on a date |

### Reports
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/reports/occupancy` | Generate occupancy report for date range |

## Sample API Calls

### Create Reservation
```bash
curl -X POST http://localhost:8080/api/v1/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "spaceId": "2bcce39a-091a-4e14-81bf-d7141c6cfe43",
    "reservationDate": "2026-02-15",
    "startTime": "18:00",
    "partySize": 8,
    "customerName": "John Smith",
    "customerEmail": "john.smith@example.com",
    "customerPhone": "+1-555-123-4567",
    "specialRequests": "Anniversary dinner"
  }'
```

### Get Occupancy Report
```bash
curl "http://localhost:8080/api/v1/reports/occupancy?\
restaurantId=69675fbd4664c65bfd4f87fe&\
startDate=2026-01-01&\
endDate=2026-01-31&\
granularity=DAILY"
```

## Project Structure

```
src/main/java/com/opentable/privatedining/
├── config/           # Configuration classes
├── controller/       # REST controllers
├── dto/              # Data transfer objects
│   ├── request/      # Request DTOs
│   └── response/     # Response DTOs
├── exception/        # Custom exceptions & handlers
├── mapper/           # Entity-DTO mappers
├── model/            # Domain entities
│   └── enums/        # Enumeration types
├── repository/       # MongoDB repositories
├── service/          # Business logic
├── util/             # Utility classes
└── validation/       # Validation logic
```

## Database Setup

The Docker setup automatically seeds the database with:
- **60 restaurants** with operating hours
- **208 private dining spaces** (2-5 per restaurant)
- **12,000+ reservations** (past 30 days + future 90 days)

### MongoDB Indexes (Auto-created)
- Reservation space-date-status index (for availability queries)
- Reservation date-time range index (for overlap detection)
- Restaurant-date index (for reporting)
- Customer email index (for lookups)

## Configuration

### Application Profiles

| Profile | Database | Use Case |
|---------|----------|----------|
| `dev` | Embedded Flapdoodle | Unit testing, quick development |
| `local` | Docker MongoDB (localhost:27017) | Local development with seeded data |
| `docker` | Container MongoDB | Production-like Docker deployment |

### Key Configuration Properties

```yaml
app:
  mongo:
    create-indexes: true  # Auto-create indexes on startup

spring:
  data:
    mongodb:
      uri: mongodb://admin:admin@localhost:27017/private_dining?authSource=admin
```

## Running Tests

```bash
# Run all tests
./mvnw test

# Run with coverage
./mvnw test jacoco:report
```

## Load Testing

Load tests are provided using [k6](https://k6.io/) to validate performance and concurrency handling.

### Prerequisites

```bash
# Install k6 on macOS
brew install k6

# Install k6 on Linux
sudo apt-get install k6
```

### Available Load Tests

| Test | Description | Duration |
|------|-------------|----------|
| `quick-benchmark.js` | Single-user baseline (50 iterations) | ~1 min |
| `race-condition-test.js` | Concurrent booking stress test (10 VUs) | <1 sec |
| `concurrent-booking-test.js` | Multi-scenario stress test | ~2 min |
| `mixed-operations-test.js` | Realistic mixed traffic | ~2 min |
| `benchmark.js` | Comprehensive phased benchmark | ~4 min |
| `edge-case-test.js` | Edge case and boundary testing | ~2.5 min |

### Running Load Tests

```bash
# Using the test runner script (recommended)
./load-tests/run-test.sh quick-benchmark
./load-tests/run-test.sh race-condition-test

# Or run directly with k6
k6 run load-tests/quick-benchmark.js
```

The test runner script automatically:
- Saves logs to `load-tests/results/`
- Generates JSON metrics reports
- Cleans up test data from the database

### Concurrency Verification

The race condition test validates that the system prevents overbooking:

```bash
./load-tests/run-test.sh race-condition-test
```

Expected results for a 9-capacity space with 10 concurrent requests (party size 3):
- **Successful bookings**: 3 (9 ÷ 3 = 3 max)
- **Capacity exceeded**: 7 (rejected with 409)
- **Server errors**: 0

See [ADR-002: Concurrency Strategy](docs/adr/002-concurrency-strategy.md) for implementation details.

## Architecture Decisions

Key architectural decisions are documented in `/docs/adr/`:

- [ADR-001: Space Storage Strategy](docs/adr/001-space-storage-strategy.md) - Separate collection with Binary UUID for spaces
- [ADR-002: Concurrency Strategy](docs/adr/002-concurrency-strategy.md) - Atomic capacity management with MongoDB findAndModify
- [ADR-003: Time Slot Management](docs/adr/003-time-slot-management.md) - Per-space configurable slots
- [ADR-004: API Versioning](docs/adr/004-api-versioning.md) - URL path versioning
- [ADR-005: Error Handling](docs/adr/005-error-handling.md) - Standardized error responses
- [ADR-006: Reporting Granularity](docs/adr/006-reporting-granularity.md) - Multi-level occupancy metrics

## Documentation

- [ASSUMPTIONS.md](ASSUMPTIONS.md) - All assumptions made during implementation
- [docs/](docs/) - Technical documentation

## AI Tooling Disclosure

This project was developed with assistance from Claude AI (Anthropic) as a complementary tool for:
- Boiler plate code generation and review
- Documentation formatting




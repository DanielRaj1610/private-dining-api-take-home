# System Design

## Overview

The Private Dining Reservation System is a RESTful API built with Spring Boot and MongoDB, designed to manage private dining space reservations with intelligent availability management and occupancy analytics.

## Architecture Style

**Layered Architecture** with clear separation of concerns:
- API Layer (Controllers, Request Validation, Response Mapping)
- Service Layer (Business Logic, Validation, Orchestration)
- Repository Layer (Data Access, MongoDB Queries, Aggregations)
- Data Layer (MongoDB Collections)

For detailed architecture diagrams, see [System Architecture on Confluence](https://danielstanlee.atlassian.net/wiki/external/ZWU5ZWZjMTkzMjQ0NGZjZTliYjM4ZDIyNjQwZjY4MWM).

## Component Overview

### Controllers
| Controller | Responsibility |
|------------|----------------|
| `ReservationController` | CRUD operations for reservations, cancellation |
| `RestaurantController` | Restaurant and space management |
| `AvailabilityController` | Space availability queries |
| `ReportingController` | Occupancy reports generation |

### Services
| Service | Responsibility |
|---------|----------------|
| `ReservationService` | Reservation business logic with concurrency handling |
| `AvailabilityService` | Capacity calculations, time slot generation |
| `SlotCapacityService` | Atomic capacity management via MongoDB `findAndModify` |
| `RestaurantService` | Restaurant and space data management |
| `SpaceService` | Space-specific queries and validation |
| `ReportingService` | Aggregation queries, insight generation |

### Cross-Cutting Concerns
| Component | Responsibility |
|-----------|----------------|
| `GlobalExceptionHandler` | Centralized error handling with trace IDs |
| `ReservationValidator` | Business rule validation |
| `MongoIndexConfig` | Database index management |

## Key Design Patterns

### 1. Atomic Capacity Management
Uses MongoDB `findAndModify` with conditional updates to prevent race conditions during concurrent bookings. The `SlotCapacityService` atomically checks and reserves capacity in a single operation.

For detailed capacity model visualization, see [Capacity Model on Confluence](https://danielstanlee.atlassian.net/wiki/external/YmQ2ZTZiZWQxMTc5NGM4MGI0NmZlYWZiN2ZlMjU0ZTA). For architectural decision details, see [ADR-002](adr/002-concurrency-strategy.md).

### 2. Builder Pattern
Used extensively for DTOs and domain objects for clean object construction.

### 3. Repository Pattern
Spring Data MongoDB repositories with custom queries and aggregation pipelines.

### 4. Optimistic Locking
`@Version` annotation on entities provides additional safety for document updates.

## For Detailed Diagrams

See [System Architecture on Confluence](https://danielstanlee.atlassian.net/wiki/external/ZWU5ZWZjMTkzMjQ0NGZjZTliYjM4ZDIyNjQwZjY4MWM) for complete diagrams with Mermaid.

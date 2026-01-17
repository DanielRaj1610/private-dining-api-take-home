# Database Design

## Overview

The system uses MongoDB as the primary database, chosen for its flexibility with document schemas and native support for the starter project requirements.

## Collections

### restaurants
Stores restaurant information including operating hours.

**Key Fields**:
- `_id`: ObjectId
- `name`, `address`, `city`: Basic info
- `timezone`: Restaurant's timezone
- `operatingHours`: Embedded array (7 days)
- `isActive`: Boolean

### spaces
Stores private dining spaces (separate collection for query performance).

**Key Fields**:
- `_id`: UUID (not ObjectId)
- `restaurantId`: Reference to restaurant
- `name`, `description`: Space info
- `minCapacity`, `maxCapacity`: Capacity limits
- `slotDurationMinutes`, `bufferMinutes`: Time configuration
- `isActive`: Boolean

### reservations
Stores all reservation records.

**Key Fields**:
- `_id`: ObjectId
- `restaurantId`: Reference to restaurant
- `spaceId`: Reference to space (UUID)
- `reservationDate`, `startTime`, `endTime`: Timing
- `partySize`: Number of guests
- `customerName`, `customerEmail`, `customerPhone`: Customer info
- `status`: CONFIRMED, CANCELLED, COMPLETED, NO_SHOW
- `version`: For optimistic locking

## Indexes

| Collection | Index | Purpose |
|------------|-------|---------|
| reservations | `spaceId, reservationDate, status` | Availability queries |
| reservations | `reservationDate, startTime, endTime` | Overlap detection |
| reservations | `restaurantId, reservationDate` | Reporting |
| reservations | `customerEmail` | Customer lookup |
| spaces | `restaurantId, isActive` | Active spaces query |

## Design Decisions

1. **Spaces in Separate Collection**: Enables direct space queries without loading restaurant documents.

2. **Operating Hours Embedded**: Always accessed with restaurant, limited size (7 entries).

3. **UUID for Space IDs**: Allows pre-generation and easier cross-system integration.

## For Detailed ERD

See [Database Schema on Confluence](https://danielstanlee.atlassian.net/wiki/external/MTU5NWU5NjNlM2U1NDcxMmEwYTkyNmU5ODQwODFmM2Q) for detailed entity relationship diagram with Mermaid ERD.

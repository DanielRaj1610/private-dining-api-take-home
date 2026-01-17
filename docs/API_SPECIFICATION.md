# API Specification

## Base URL

```
http://localhost:8080/api/v1
```

## Authentication

**Note**: Authentication is out of scope for this assignment. All endpoints are open.

## Endpoints Summary

### Reservations

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/reservations` | List reservations (paginated) |
| GET | `/reservations/{id}` | Get reservation by ID |
| POST | `/reservations` | Create reservation |
| POST | `/reservations/{id}/cancel` | Cancel reservation |
| DELETE | `/reservations/{id}` | Delete reservation |

### Restaurants

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/restaurants` | List restaurants (paginated) |
| GET | `/restaurants/{id}` | Get restaurant by ID |
| POST | `/restaurants` | Create restaurant |
| GET | `/restaurants/{id}/spaces` | Get spaces for restaurant |
| POST | `/restaurants/{id}/spaces` | Add space to restaurant |

### Availability

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/availability/{spaceId}` | Get availability for date |

### Reports

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/reports/occupancy` | Generate occupancy report |

## Pagination

List endpoints support pagination:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | 0 | Page number (0-indexed) |
| `size` | int | 20 | Items per page |
| `sortBy` | string | varies | Field to sort by |
| `sortDir` | string | varies | `asc` or `desc` |

## Error Responses

All errors return a standardized format. See [ADR-005](adr/005-error-handling.md).

## Interactive Documentation

For full API documentation with examples:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

## Detailed Examples

See [API Specification on Confluence](https://danielstanlee.atlassian.net/wiki/external/NjFjMmRjOTkzNjVkNDA0NTgxMTQ4N2NkOTYwZjZjNzU) for detailed request/response examples with diagrams.

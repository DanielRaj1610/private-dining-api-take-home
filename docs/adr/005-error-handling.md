# ADR-005: Error Handling

## Status
**Accepted**

## Date
2024-01-14

## Context

Consistent error handling improves API usability and debugging. Clients need clear, actionable error messages while operators need diagnostic information.

**Requirements**:
- Consistent error response format across all endpoints
- Clear error codes for programmatic handling
- Human-readable messages for debugging
- Trace IDs for log correlation
- Appropriate HTTP status codes

## Decision

**Use Standardized Error Response Format with Global Exception Handler**

### Error Response Structure

```json
{
  "status": 409,
  "error": "CAPACITY_EXCEEDED",
  "message": "Insufficient capacity. Space 'Garden Room' has 20 max capacity. Current bookings: 15 guests. Your party of 8 would exceed capacity.",
  "availableCapacity": 5,
  "timestamp": "2024-02-01T10:30:00Z",
  "path": "/api/v1/reservations",
  "traceId": "abc12345"
}
```

### HTTP Status Code Mapping

| Code | Use Case |
|------|----------|
| 200 | Successful GET, successful cancellation |
| 201 | Successful POST (resource created) |
| 204 | Successful DELETE |
| 400 | Validation errors, invalid input |
| 404 | Resource not found |
| 409 | Business rule conflict (capacity, already cancelled) |
| 500 | Unexpected server errors |

### Custom Exceptions

| Exception | HTTP Code | Use Case |
|-----------|-----------|----------|
| `SpaceNotFoundException` | 404 | Space not found |
| `RestaurantNotFoundException` | 404 | Restaurant not found |
| `ReservationNotFoundException` | 404 | Reservation not found |
| `OutsideOperatingHoursException` | 400 | Time outside operating hours |
| `InvalidTimeSlotException` | 400 | Start time misaligned |
| `CapacityExceededException` | 409 | Overbooking attempt |
| `ConcurrentModificationException` | 409 | Optimistic lock failure |

## Consequences

### Positive
- Clients can programmatically handle specific error codes
- Trace IDs enable request correlation in logs
- Consistent format reduces client-side parsing complexity
- Detailed messages help developers debug issues

### Negative
- Custom exceptions for each error case
- Need to maintain error code documentation
- Some errors may need localization (future)

### Mitigations
- Central `GlobalExceptionHandler` for consistency
- Error codes documented in API spec
- Generic fallback for unexpected exceptions

## Implementation

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CapacityExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleCapacityExceeded(
            CapacityExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiErrorResponse.builder()
                .status(409)
                .error("CAPACITY_EXCEEDED")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(generateTraceId())
                .timestamp(LocalDateTime.now())
                .build());
    }
}
```

## References
- `GlobalExceptionHandler` class
- `ApiErrorResponse` DTO
- Custom exception classes in `exception/` package

# ADR-002: Time Slot Management

## Status
**Accepted**

## Date
2024-01-14

## Context

The system needs to manage reservation time slots to optimize table turnover and enforce consistent booking windows. The challenge is balancing flexibility (different spaces may have different needs) with simplicity.

**Key Requirements**:
- Enforce consistent time boundaries
- Maximize space utilization
- Allow buffer time between reservations
- Support different slot durations for different spaces

**Options Considered**:

1. **Global Fixed Slots**
   - Same slot duration for all spaces
   - Simple but inflexible

2. **Restaurant-level Configuration**
   - All spaces in a restaurant have same duration
   - Medium flexibility

3. **Per-Space Configuration**
   - Each space has its own slot duration and buffer
   - Maximum flexibility

## Decision

**Use Per-Space Configurable Time Slots**

Each space has:
- `slotDurationMinutes`: Duration of each reservation (default: 60)
- `bufferMinutes`: Gap between reservations (default: 15)

Time slot rules:
- Start times must align to slot boundaries
- End time is automatically calculated: `startTime + slotDurationMinutes`
- Reservations must start AND end within operating hours

```java
// Validation in ReservationValidator
public void validateTimeSlotAlignment(LocalTime startTime, int slotDurationMinutes) {
    int totalMinutes = startTime.getHour() * 60 + startTime.getMinute();
    if (totalMinutes % slotDurationMinutes != 0) {
        throw new InvalidTimeSlotException(startTime, slotDurationMinutes);
    }
}
```

## Consequences

### Positive
- Different spaces can have different turnover requirements
- Wine cellar can have 2-hour slots, lunch space can have 1-hour slots
- Buffer time prevents rushed transitions
- Clear validation errors for misaligned times

### Negative
- More complex configuration per space
- Customers need to know valid start times
- API complexity for availability queries

### Mitigations
- Availability endpoint returns valid time slots
- Default values work for most cases
- Documentation clearly explains slot rules

## References
- `Space.slotDurationMinutes` and `Space.bufferMinutes` fields
- `ReservationValidator.validateTimeSlotAlignment()` implementation
- `AvailabilityService.generateTimeSlots()` implementation

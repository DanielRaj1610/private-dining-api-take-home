# ADR-004: API Versioning

## Status
**Accepted**

## Date
2024-01-14

## Context

APIs evolve over time. Breaking changes need to be managed without disrupting existing clients. A versioning strategy is needed from day one to enable future evolution.

**Options Considered**:

1. **URL Path Versioning** (`/api/v1/...`)
   - Version in URL path
   - Most explicit and visible

2. **Header Versioning** (`Accept: application/vnd.api+json;version=1`)
   - Version in request header
   - Clean URLs but less discoverable

3. **Query Parameter** (`?version=1`)
   - Version as query param
   - Can be accidentally omitted

4. **No Versioning**
   - Rely on backward compatibility
   - Risky for long-term maintenance

## Decision

**Use URL Path Versioning**

All endpoints are prefixed with `/api/v1/`:
```
GET /api/v1/reservations
POST /api/v1/reservations
GET /api/v1/reports/occupancy
```

## Consequences

### Positive
- Explicit and self-documenting
- Easy to route at load balancer/gateway level
- Clear in logs and debugging
- Well-supported by Swagger/OpenAPI
- Easy for clients to understand and implement

### Negative
- URLs are longer
- Major version changes require new routes
- Cannot version individual endpoints differently

### Mitigations
- Use semantic versioning for major breaking changes only
- Deprecation period before removing old versions
- Document version differences clearly

## Future Considerations

When v2 is needed:
1. Create new v2 controllers
2. Deprecate v1 endpoints with warning headers
3. Maintain v1 for deprecation period (e.g., 6 months)
4. Remove v1 after migration period

## References
- `@RequestMapping("/api/v1/...")` on all controllers
- Spring Boot configuration for consistent path prefix

# Documentation Index

This directory contains technical documentation for the Private Dining Reservation System.

## Quick Links

| Document | Description |
|----------|-------------|
| [REQUIREMENTS_CHECKLIST.md](REQUIREMENTS_CHECKLIST.md) | **Mapping of requirements to implementation** |
| [SYSTEM_DESIGN.md](SYSTEM_DESIGN.md) | High-level architecture and component overview |
| [API_SPECIFICATION.md](API_SPECIFICATION.md) | API endpoints and request/response formats |
| [DATABASE_DESIGN.md](DATABASE_DESIGN.md) | MongoDB schema and indexing strategy |
| [SCALABILITY.md](SCALABILITY.md) | Performance considerations and scaling strategy |
| [FUTURE_IMPROVEMENTS.md](FUTURE_IMPROVEMENTS.md) | Roadmap and enhancement proposals |

## Architecture Decision Records (ADRs)

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-001](adr/001-space-storage-strategy.md) | Space Storage Strategy (Separate Collection with Binary UUID) | Accepted |
| [ADR-002](adr/002-concurrency-strategy.md) | Concurrency Strategy (Atomic Capacity Management) | **Superseded** (Updated) |
| [ADR-003](adr/003-time-slot-management.md) | Time Slot Management | Accepted |
| [ADR-004](adr/004-api-versioning.md) | API Versioning | Accepted |
| [ADR-005](adr/005-error-handling.md) | Error Handling | Accepted |
| [ADR-006](adr/006-reporting-granularity.md) | Reporting Granularity | Accepted |

## Confluence Documentation

For detailed diagrams and visual documentation, see the published Confluence pages:

| Document | Description |
|----------|-------------|
| [System Architecture](https://danielstanlee.atlassian.net/wiki/external/ZWU5ZWZjMTkzMjQ0NGZjZTliYjM4ZDIyNjQwZjY4MWM) | Detailed system architecture with component and flow diagrams |
| [Sequence Diagrams](https://danielstanlee.atlassian.net/wiki/external/Zjc3OTdkYTY5ODI1NDE0YTlhMmQ0MjcyY2U2ZTQyY2U) | Booking, cancellation, and reporting flow diagrams |
| [Database Schema](https://danielstanlee.atlassian.net/wiki/external/MTU5NWU5NjNlM2U1NDcxMmEwYTkyNmU5ODQwODFmM2Q) | Entity relationship diagram (ERD) with Mermaid |
| [Capacity Model](https://danielstanlee.atlassian.net/wiki/external/YmQ2ZTZiZWQxMTc5NGM4MGI0NmZlYWZiN2ZlMjU0ZTA) | Atomic capacity management visualization |
| [Use Cases](https://danielstanlee.atlassian.net/wiki/external/MzgxNThkMzUwNjRkNGUxNjkzMjI3OTQzYWI5ODQwYjI) | Use case matrix and user stories |
| [API Specification](https://danielstanlee.atlassian.net/wiki/external/NjFjMmRjOTkzNjVkNDA0NTgxMTQ4N2NkOTYwZjZjNzU) | Detailed API request/response examples |

## External Resources

- **Swagger UI**: http://localhost:8080/swagger-ui.html (when running)
- **OpenAPI Spec**: http://localhost:8080/api-docs

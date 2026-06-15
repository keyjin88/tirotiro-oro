# Test Coverage Plan: Equipment Warehouse MVP

## Coverage Goal

The MVP must maintain at least 60% line coverage for production Java code through the JaCoCo Maven `check` goal. The coverage gate is configured in `pom.xml` and runs during `mvn verify`; the build must fail when covered line ratio drops below `0.60`.

## Coverage Gate Scope

The gate covers production code under `src/main/java`.

The following code may be excluded from the coverage gate when it does not contain business behavior:

- generated code;
- application bootstrap classes;
- framework-only configuration classes;
- configuration DTOs and metadata holders.

Coverage exclusions must stay narrow. Domain services, controllers, repositories, validators, authorization checks, and booking or availability rules are always in scope.

## Unit Tests

Unit tests should be the first line of coverage for deterministic domain and service behavior. They should run during `mvn test` without PostgreSQL, Docker, browser automation, or external services.

Required unit coverage areas:

- interval overlap detection using `existing.starts_at < requested.ends_at` and `existing.ends_at > requested.starts_at`;
- quantity availability for tracked stock;
- unit availability for individually tracked inventory units;
- `BookingLine` rules, including positive quantities and quantity `1` for unit-specific lines;
- service-level authorization, including role checks and `EQUIPMENT_CREATE`;
- soft delete invariants for equipment items and units.

## Integration Tests

Integration tests should run during `mvn verify` through Maven Failsafe. They should use Testcontainers PostgreSQL so database behavior matches the production database as closely as possible.

Required integration coverage areas:

- Liquibase migrations apply cleanly to PostgreSQL;
- repository mappings and constraints for users, permissions, equipment, bookings, and audit records;
- successful booking creation;
- insufficient stock rejection;
- overlapping inventory unit rejection;
- cancellation releases availability;
- concurrent booking attempts with deterministic locking behavior.

## Web And Security Tests

Web and security tests should use focused Spring MVC tests where possible, with full-context tests reserved for cross-cutting behavior. These tests should validate server-rendered HTML routes, form handling, CSRF, and authorization without depending on browser automation.

Required web and security coverage areas:

- login and logout;
- admin-only route restrictions;
- `EQUIPMENT_CREATE` access for equipment creation;
- user versus admin booking visibility;
- CSRF protection for mutating requests;
- MVC form validation and error rendering.

## UI Smoke Tests

UI smoke tests are not the primary coverage source and should stay small. Their purpose is to catch broken user journeys after the server-rendered UI exists.

Required smoke flows:

- catalog loads and displays equipment rows;
- booking creation flow reaches confirmation or validation feedback;
- admin equipment creation page submits valid data;
- calendar period update refreshes the displayed availability.

## Coverage Ownership

Coverage work should start with the riskiest business areas: availability calculations, booking creation, booking modification, cancellation, concurrency, and authorization. Controllers should be covered with focused MVC tests that verify request handling and security boundaries rather than duplicating service logic.

Every implementation phase that adds production behavior should add or update tests in the same change. If a phase temporarily cannot meet the 60% gate because the behavior is still skeletal, the blocker must be documented before merging and resolved before the MVP is considered complete.

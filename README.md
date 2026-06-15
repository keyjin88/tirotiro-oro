# Tirotiro Oro Equipment Warehouse

Spring Boot MVP for equipment inventory, availability, bookings, and administrative warehouse workflows.

## Local Commands

Run the full verification suite, including integration tests and the JaCoCo coverage gate:

```sh
mvn verify
```

Build and run the application with PostgreSQL:

```sh
docker compose up --build
```

The application listens on `http://localhost:8080`. The compose stack creates a local PostgreSQL database named `equipment` with user `equipment_app` and password `change-me`.

Stop the stack:

```sh
docker compose down
```

# DatabaseMapper Web Backend — CLAUDE.md

## Project Overview

This is the backend component of **DatabaseMapper** — a web application that automates
database-to-model mapping for QA automation workflows. It provides REST API endpoints
that allow the frontend to manage mappings and return data from the database as JSON,
ready to be consumed by the Java library used in automated tests.

## Problem Being Solved

- **BAs** manually document DB-to-model mappings in Excel with inconsistent styles
- **QA testers** write repetitive SQL queries and monotonous assertion code by hand

This backend centralizes and formalizes the mapping process, and exposes the results
via API so the Java library can hydrate model objects automatically in tests.

## Target Users

- Business Analysts — create and manage mappings via the web UI
- QA Automation Testers — consume mappings indirectly through the Java library
- Technical Leads — inspect service-to-database dependencies

## Tech Stack

| Layer        | Technology                        |
|--------------|-----------------------------------|
| Language     | Java 21 (Temurin LTS)             |
| Framework    | Spring Boot 3.5.11                |
| Build tool   | Maven Wrapper (./mvnw)            |
| Database     | PostgreSQL (app data storage)     |
| Migrations   | Flyway                            |
| ORM          | Spring Data JPA + Hibernate       |
| Boilerplate  | Lombok                            |
| Dev tools    | Spring Boot DevTools              |

## Project Structure

```
databasemapper-web-backend/
├── src/
│   ├── main/
│   │   ├── java/com/databasemapper/
│   │   │   ├── DatabasemapperWebBackendApplication.java  # Entry point
│   │   │   ├── controller/    # REST controllers (@RestController)
│   │   │   ├── service/       # Business logic (@Service)
│   │   │   ├── repository/    # DB access (@Repository, JPA)
│   │   │   ├── model/         # JPA entities
│   │   │   ├── dto/           # Request/Response DTOs
│   │   │   └── config/        # Configuration classes
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── application-local.yaml  # Local dev overrides (gitignored)
│   │       └── db/migration/           # Flyway migration scripts
│   └── test/
│       └── java/com/databasemapper/
├── docker-compose.yml
├── pom.xml
└── CLAUDE.md
```

## Database Schema

Five tables, applied via Flyway migrations V1–V7.

```
databases    (database_id, database_type, database_host, database_name)
services     (service_id, database_id, service_name, service_base_url, swagger_endpoint, last_fetched_swagger JSONB, committed_swagger JSONB)
connections  (connection_id, database_id, port, username, password, connection_string, connection_mode, is_active)
endpoints    (endpoint_id, service_id, http_method, endpoint_path)
mappings     (mapping_id, endpoint_id, response_model_name, mapping_data JSONB)
```

### Key relationships and design decisions

- `databases` is independent — it exists on its own, not owned by a service
- `services.database_id` → `databases` — many services can share one database (real-world scenario)
- `connections.database_id` → `databases` — multiple connections per database (e.g. different users/ports)
- `connections.is_active` — marks which connection is currently used for a given database
- `connections.connection_mode` — enum: `PARAMETERS` or `CONNECTION_STRING`; determines which fields are filled
- `connections.username` and `connections.password` — nullable (not needed in CONNECTION_STRING mode)
- `services.swagger_endpoint` — nullable; stores the path (e.g. `/v3/api-docs`); combined with `service_base_url` to fetch the OpenAPI spec
- `services.last_fetched_swagger` — JSONB; updated every time `GET /api/v1/services/{id}/swagger` is called; represents the latest live spec
- `services.committed_swagger` — JSONB; updated only on explicit `POST /api/v1/services/{id}/swagger/commit`; the stable snapshot mappings are anchored to
- `mappings.mapping_data` — JSONB; stores flexible mapping metadata (field↔column, primary keys, filter conditions, etc.)
- `mappings.response_model_name` — human-readable label shown in UI (e.g. `UserResponse`)

### Flyway conventions
- One table per migration file
- Files located in `src/main/resources/db/migration/`
- Naming: `V{n}__{description}.sql` (double underscore)
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate validates schema only, Flyway owns all DDL

### Local PostgreSQL via Docker
```bash
docker compose up -d    # start PostgreSQL container
docker compose down     # stop (data preserved)
docker compose down -v  # stop and delete all data
```

Container name: `databasemapper-db-postgres`
Database name: `databasemapper-db`
User: `dev`
Port: `5432`

## Key API Responsibilities

- **Database management** — CRUD for database records (`/api/v1/databases`)
- **Service management** — CRUD for services (`/api/v1/services`)
- **Connection management** — CRUD for database connections (`/api/v1/connections`)
- **Endpoint management** — CRUD for service endpoints (`/api/v1/endpoints`)
- **Mapping management** — CRUD for mappings (`/api/v1/mappings`)
- **Swagger/OpenAPI import** — fetch and parse OpenAPI spec from a service's swagger_endpoint
- **Mapping export** — return mappings as compact metadata files
- **Model hydration API** — return data from target DB as model objects (JSON)

## Development Conventions

### Package Structure
Follow layered architecture: `controller → service → repository`.
Controllers must not contain business logic. Services must not directly use `EntityManager`.

### Naming
- Controllers: `*Controller.java` (e.g., `DatabaseController`)
- Services: `*Service.java` + `*ServiceImpl.java` (e.g., `DatabaseService`, `DatabaseServiceImpl`)
- Repositories: `*Repository.java` extending `JpaRepository` (e.g., `DatabaseRepository`)
- Entities: singular noun matching table concept (e.g., `Database`, `Service`, `Connection`)
- DTOs: `*Request.java` / `*Response.java` (e.g., `DatabaseRequest`, `DatabaseResponse`)

### REST Conventions
- Use standard HTTP verbs: GET, POST, PUT, DELETE
- Resource URLs in plural kebab-case: `/api/v1/databases`, `/api/v1/connections`
- Plural form is used even when creating a single resource — this is standard REST convention
- Return `ResponseEntity<T>` from controllers
- Validation via `@Valid` + Bean Validation annotations on DTOs

### Configuration
- Use `application.yaml` (not `.properties`)
- Never commit credentials — use `application-local.yaml` (gitignored) or env variables
- Profile `local` for development, `prod` for production

### Error Handling
- Use a global `@ControllerAdvice` for exception handling
- Return consistent error response structure: `{ timestamp, status, error, message }`

### Lombok
Use Lombok to reduce boilerplate:
- `@Getter` / `@Setter` on DTOs
- `@Builder` where appropriate
- `@RequiredArgsConstructor` for constructor injection in services/controllers
- **Do not** use `@Data` on JPA entities (can cause issues with `equals`/`hashCode`)

## Multi-Database Support

The app must connect to **target databases** (PostgreSQL, MySQL, Oracle, SQL Server)
at runtime based on user-provided connection details — not just its own storage DB.
Use dynamic `DataSource` creation (not Spring's auto-configured single datasource)
for target DB connections.

## Running Locally

```bash
# Start PostgreSQL first
docker compose up -d

# Start the app
./mvnw spring-boot:run

# Run tests
./mvnw test

# Build JAR
./mvnw clean package
```

## Related Components

| Component                          | Description                                             |
|------------------------------------|---------------------------------------------------------|
| `databasemapper-web-frontend`      | React + Vite frontend that calls this API               |
| `databasemapper-library`           | Java Maven library consumed by QA BE automation testers |
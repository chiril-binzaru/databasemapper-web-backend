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
| Framework    | Spring Boot 3.5.x                 |
| Build tool   | Maven                             |
| Database     | PostgreSQL (app data storage)     |
| ORM          | Spring Data JPA + Hibernate       |
| API docs     | SpringDoc OpenAPI (Swagger UI)    |
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
│   │       ├── application.yml
│   │       └── application-local.yml  # Local dev overrides (gitignored)
│   └── test/
│       └── java/com/databasemapper/
├── pom.xml
└── CLAUDE.md
```

## Key API Responsibilities

- **Database connection management** — store and validate connections to target DBs
- **Schema import** — connect to target DB and read its table/column structure
- **Swagger/OpenAPI import** — parse specs to discover model fields
- **Mapping management** — CRUD for DB column ↔ model field mappings
- **Mapping export** — return mappings as compact metadata files
- **Model hydration API** — return data from target DB as model objects (JSON)

## Development Conventions

### Package Structure
Follow layered architecture: `controller → service → repository`.
Controllers must not contain business logic. Services must not directly use `EntityManager`.

### Naming
- Controllers: `*Controller.java` (e.g., `MappingController`)
- Services: `*Service.java` + `*ServiceImpl.java`
- Repositories: `*Repository.java` (extend `JpaRepository`)
- Entities: singular noun (e.g., `Mapping`, `DatabaseConnection`)
- DTOs: `*Request.java` / `*Response.java`

### REST Conventions
- Use standard HTTP verbs: GET, POST, PUT, DELETE
- Resource URLs in plural kebab-case: `/api/v1/mappings`, `/api/v1/database-connections`
- Return `ResponseEntity<T>` from controllers
- Validation via `@Valid` + Bean Validation annotations on DTOs

### Configuration
- Use `application.yml` (not `.properties`)
- Never commit credentials — use `application-local.yml` (gitignored) or env variables
- Profile `local` for development, `prod` for production

### Error Handling
- Use a global `@ControllerAdvice` for exception handling
- Return consistent error response structure: `{ timestamp, status, error, message }`

### Lombok
Use Lombok to reduce boilerplate:
- `@Data` or `@Getter`/`@Setter` on DTOs
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
# Start the app (requires PostgreSQL running locally or in Docker)
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

---
name: add-rest-endpoint
description: Add a REST endpoint to the ApplyPilot Spring Boot backend following this project's recipe (request/response DTO records, service, thin controller, Bean Validation, error handling, unit and integration tests). Use when asked to add, expose or extend an API endpoint in backend/.
---

# Add a REST endpoint

Follow these steps in order. Work in small steps: after each numbered step the code must
compile, and each step that changes behavior gets its own conventional commit.

Reference implementation: `auth/AuthController`, `auth/AuthService`, `auth/dto/*`,
`AuthServiceTest` and `AuthFlowIT`. Read them first and match their style.

## 1. Decide the contract

Write down, before any code: HTTP method and path (`/api/<feature>/...`, plural nouns),
request body, success status and response body, error cases with their status, and whether
the endpoint is public or needs a token. If any of this is unclear, ask.

## 2. Place the code (package by feature)

Everything for a feature lives in `backend/src/main/java/com/applypilot/<feature>/`:
entity, repository, service, controller, exceptions, and a `dto/` subpackage.
Create a new feature package rather than adding unrelated code to an existing one.
Cross-cutting code only goes in `common/`.

## 3. Database change (only if needed)

Add a new Flyway file `backend/src/main/resources/db/migration/V<next>__<description>.sql`.
Never edit a migration that has already been applied. Then update the JPA entity so it
matches the schema exactly (Hibernate runs with `ddl-auto: validate`).

## 4. DTOs

- Java records in `<feature>/dto`, named `<Action><Thing>Request` and `<Thing>Response`.
- Bean Validation annotations on request components (`@NotBlank`, `@Size`, `@Email`, ...).
- Responses get a static `from(Entity)` factory. Never return or accept a JPA entity.
- Override `toString()` on any request that carries a secret.

## 5. Service

- `@Service` class with constructor injection and `private final` fields.
- `@Transactional` on methods that write, `@Transactional(readOnly = true)` on reads.
- Business rules live here, not in the controller.
- For expected failures throw a subclass of `common.error.ApiException` that fixes the HTTP
  status and a stable `CODE` (see `EmailAlreadyUsedException`). Do not catch exceptions in
  controllers; `GlobalExceptionHandler` turns them into the shared `ApiError` JSON.
- Resources owned by a user must be looked up by the caller's id, so one user can never
  read or change another user's data.

## 6. Controller

- `@RestController` + `@RequestMapping("/api/<feature>")`; methods only validate, delegate
  and return a DTO.
- `@Valid @RequestBody` on request bodies. `@ResponseStatus(HttpStatus.CREATED)` for creates,
  `NO_CONTENT` for deletes.
- Get the caller with `@AuthenticationPrincipal Jwt jwt` and
  `UUID.fromString(jwt.getSubject())`. Never take the user id from the request.
- Endpoints require a token by default. Only if the endpoint must be public, add its matcher
  to `auth/SecurityConfig` and say so explicitly in the commit message.

## 7. Tests

- Unit test the service: `<Service>Test` with `@ExtendWith(MockitoExtension.class)`, mocked
  collaborators, one test per rule and per failure case. Runs with `./mvnw test`.
- Integration test the endpoint: `<Feature>FlowIT` (the `IT` suffix matters) with
  `@SpringBootTest`, `@AutoConfigureMockMvc` and `@Import(TestcontainersConfiguration.class)`.
  Cover the happy path, a validation error (400 with `fieldErrors`), the request without a
  token (401) and, for owned resources, access to another user's resource.
- Never weaken or delete a failing test to make the build pass. Fix the code or ask.

## 8. Verify and commit

1. Run `./mvnw verify` from `backend/` (Docker must be running; on this machine set
   `JAVA_HOME` to JDK 21 first, see `CLAUDE.local.md`). Report the real result.
2. Start the app and check the endpoint appears at `http://localhost:8080/swagger-ui.html`.
3. Commit in small steps, for example `feat(<feature>): add <endpoint>` and
   `test(<feature>): ...`. No AI attribution lines.
4. If the frontend will call the endpoint, add the matching TypeScript interface next to the
   Angular service that uses it.

---
name: add-rest-endpoint
description: Add a REST endpoint to the ApplyPilot Spring Boot backend following this project's recipe (request/response DTO records, service, thin controller, Bean Validation, error handling, unit and integration tests). Use when asked to add, expose or extend an API endpoint in backend/.
---

# Add a REST endpoint

Follow these steps in order. Work in small steps: after each numbered step the code must
compile, and each step that changes behavior gets its own conventional commit.

Reference implementations. Read the one closest to your task first and match its style:

- Public endpoint: `auth/AuthController`, `auth/AuthService`, `AuthServiceTest`, `AuthFlowIT`.
- Resource owned by a user (most endpoints): `application/ApplicationController`,
  `application/ApplicationService`, `ApplicationServiceTest`, `ApplicationFlowIT`.
- File upload: `cv/CvController`, `cv/CvService`, `CvFlowIT`, `CvUploadHttpIT`.

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
- Optional text: web forms send `""` for fields left empty, so accept it and store null
  (see `cleanToNull` in `ApplicationService`). A `@Pattern` on an optional field must also
  match the empty string.
- Every value that reaches the database needs a bound the database can hold: `@Size` on
  strings, a sane range on dates and numbers. Strip the NUL character (`\u0000`) from text;
  PostgreSQL rejects it and the request would end in a 500.
- URLs that will be rendered as links must be restricted to `http`/`https`.

## 5. Service

- `@Service` class with constructor injection and `private final` fields.
- `@Transactional` on methods that write, `@Transactional(readOnly = true)` on reads.
- Business rules live here, not in the controller.
- For expected failures throw a subclass of `common.error.ApiException` that fixes the HTTP
  status and a stable `CODE` (see `EmailAlreadyUsedException`). Do not catch exceptions in
  controllers; `GlobalExceptionHandler` turns them into the shared `ApiError` JSON.
- Resources owned by a user are always loaded with the caller's id in the query
  (`findByIdAndUserId`, never plain `findById`). When the row is not the caller's, answer
  404 with the same error as "does not exist": a 403 would confirm that the id is real.
- A write that reads several rows and then changes them (reordering, counters, "one per
  user") is not safe when two requests overlap. Take a lock first, in the same transaction:
  `JobApplicationRepository.lockBoard(userId)` for anything that changes a user's board,
  `@Lock(PESSIMISTIC_WRITE)` on the finder for a single row. Let the database enforce
  uniqueness with a constraint and translate its violation into a 409.
- Keep transactions short. Parsing files, disk or network I/O do not belong inside one; do
  that work first and wrap only the row changes (`TransactionTemplate`, see `CvService`).
- Files and rows cannot change atomically. Write the new file, then commit the row, and
  only then delete the old file; on failure delete the new file.
- Never trust what the client says about a file: validate size and the real content
  (signature bytes), generate storage names on the server, and treat the original file
  name as display text only.

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
  token (401) and, for owned resources, a second user trying to read, change and delete
  the first user's resource (404 each time, and the resource unchanged afterwards).
- MockMvc does not go through the servlet container. Anything the container does
  (multipart parsing, upload size limits) needs one test over real HTTP: `*HttpIT` with
  `webEnvironment = RANDOM_PORT`, see `CvUploadHttpIT`.
- If the endpoint takes a lock, add a test that sends overlapping requests from several
  threads and checks that all succeed and the data is consistent (see
  `simultaneousMovesNeverCorruptTheColumnOrFail`). Check once that it fails without the lock.
- Never weaken or delete a failing test to make the build pass. Fix the code or ask.

## 8. Verify and commit

1. Run `./mvnw verify` from `backend/` (Docker must be running; on this machine set
   `JAVA_HOME` to JDK 21 first, see `CLAUDE.local.md`). Report the real result.
2. Start the app and check the endpoint appears at `http://localhost:8080/swagger-ui.html`.
3. Ask the `code-reviewer` subagent to review the diff and act on its findings; use the
   `test-writer` subagent for classes that still lack tests.
4. Commit in small steps, for example `feat(<feature>): add <endpoint>` and
   `test(<feature>): ...`. No AI attribution lines.
5. If the frontend will call the endpoint, add the matching TypeScript interface next to the
   Angular service that uses it.

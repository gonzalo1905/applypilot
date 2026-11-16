# Learning log

A running record of what each stage built, why it was designed that way, and which
Claude Code concept was practiced. Each stage ends with three interview-style questions.

## Stage 0 - Setup

### What was built

- A monorepo with `backend/` (Spring Boot 4.1, Java 21, Maven wrapper) and `frontend/`
  (Angular 22, standalone components, strict TypeScript, SCSS).
- `docker-compose.yml` running PostgreSQL 16 with a named volume and a healthcheck.
- `.gitignore`, `.gitattributes`, and `.env.example` (the real `.env` is git-ignored).
- `README.md` (what it is, stack, how to run), `CLAUDE.md` (project memory for Claude Code)
  and this learning log.

### Why these decisions

- **Monorepo.** Backend and frontend evolve together and share one history, one README and one
  set of conventions. A single clone is enough to run the whole product, which matters for a
  portfolio reviewer.
- **Maven wrapper (`mvnw`).** The repository pins its own Maven version, so nobody needs Maven
  installed and everybody builds with the same one.
- **PostgreSQL in Docker Compose.** The database is disposable and reproducible: the same engine
  and version as production, with no local installation. The healthcheck lets tools wait until
  the database really accepts connections.
- **`.env` + `.env.example`.** Configuration lives outside the code (12-factor style). The
  example file documents every variable; the real file never reaches git, so secrets cannot leak
  through history.
- **`.gitattributes`.** Windows and Unix use different line endings. Normalizing them avoids
  noisy diffs and keeps `mvnw` runnable inside Linux containers.
- **Spring Boot 4.1 on Java 21.** Java 21 is an LTS release (records, pattern matching, virtual
  threads). The project was first scaffolded on Spring Boot 3.5 and moved to 4.1 before any
  feature code existed, because 3.x is no longer the current line and Spring Initializr has
  stopped offering it. Upgrading an empty skeleton costs one commit; upgrading later would mean
  touching every feature. Visible differences in 4.x: modular starters
  (`spring-boot-starter-webmvc` instead of `-web`, one test starter per technology), Spring
  Framework 7, Spring Security 7 and Jackson 3.

### Claude Code concepts practiced

- **Plan mode.** Before any file was written, Claude inspected the environment in read-only
  mode (tool versions, git identity, the empty GitHub repository) and proposed a plan with its
  design decisions. Work started only after approval. Reviewing a plan costs minutes; undoing
  the wrong implementation costs hours. In the CLI you switch to plan mode by pressing
  `Shift+Tab` until the mode indicator shows it.
- **`CLAUDE.md`.** A Markdown file at the repository root that Claude Code loads automatically
  at the start of every session. It is the project's memory: stack, structure, commands,
  conventions and rules. Without it, every session starts from zero and you repeat yourself;
  with it, Claude follows the conventions by default. Keep it short and specific, because every
  line is read in every session. Machine-specific notes go in `CLAUDE.local.md`, which is
  git-ignored.

### Interview questions

1. Why keep configuration in environment variables instead of committing an `application.yml`
   with real values? What exactly is the risk of committing a secret and deleting it later?
2. What problem does the Maven wrapper solve, and what is the equivalent idea in the frontend
   (hint: `package-lock.json` and the `packageManager` field)?
3. What are the trade-offs of a monorepo versus two repositories for a backend and a frontend
   owned by the same team?

## Stage 1 - Base (authentication and the first protected page)

### What was built

- **Backend** (package by feature: `auth`, `user`, `common`):
  - `User` entity (UUID id, unique email, BCrypt password hash, full name, creation time), its
    Spring Data repository and the Flyway migration `V1__create_users_table.sql`.
  - `POST /api/auth/register`, `POST /api/auth/login` and `GET /api/users/me`.
  - Stateless Spring Security with JWTs, CORS for the Angular dev origin, Bean Validation,
    one error JSON shape for every failure, and Swagger UI at `/swagger-ui.html`.
  - Tests: 9 unit tests (`AuthServiceTest`, `JwtServiceTest`) and 8 integration tests
    (`AuthFlowIT`, `UserRepositoryIT`) against a real PostgreSQL started by Testcontainers.
- **Frontend**: login and register pages, an `AuthService` that keeps the session in a signal
  and in `localStorage`, an HTTP interceptor, route guards, and a protected `/board` page with
  five empty Kanban columns, the user's name and a logout button. 15 unit tests.
- **Claude Code**: the project skill `.claude/skills/add-rest-endpoint/SKILL.md`.

### Why these decisions

- **Package by feature.** Everything about authentication sits in `auth/`, everything about
  users in `user/`. A feature can be read, changed or deleted in one place, instead of being
  spread over `controller/`, `service/` and `repository/` packages that grow without limit.
- **DTO records, never entities, at the API boundary.** `UserResponse` has no password hash
  field, so the hash cannot leak by accident, and the database schema can change without
  breaking API clients.
- **JWT through Spring Security's resource-server support.** The framework's own filter
  (`BearerTokenAuthenticationFilter`) reads the `Authorization: Bearer` header, the `JwtDecoder`
  bean verifies the HMAC-SHA256 signature, the expiry and the issuer, and the result becomes
  the request's `Authentication`. The alternative, a hand-written filter with the `jjwt`
  library, does the same steps manually; using the built-in path means less security code to
  get wrong. The token's subject is the user id, so `/api/users/me` never takes an id from
  the request.
- **Stateless, so CSRF protection is off.** CSRF abuses credentials the browser attaches
  automatically (cookies). This API has no session cookie; the token is added explicitly by
  the Angular interceptor, so there is nothing for a forged request to ride on.
- **BCrypt for passwords.** It is slow on purpose and salts every hash. BCrypt only reads the
  first 72 bytes, so longer passwords are rejected rather than silently truncated.
- **Login errors do not reveal which emails exist.** Unknown email and wrong password return
  the same 401 and the same message.
- **The database enforces email uniqueness.** The service checks first to give a friendly 409,
  but two simultaneous requests can both pass that check; the unique constraint is the real
  guarantee, and the service translates its violation into the same 409.
- **Flyway owns the schema.** Hibernate runs with `ddl-auto: validate`: it never changes
  tables, it only fails fast if an entity and the schema disagree. Every schema change is a
  versioned, reviewable SQL file.
- **One error shape.** `GlobalExceptionHandler` turns validation errors, business errors and
  unexpected exceptions into the same `ApiError` JSON. Security filters run before Spring MVC,
  so their 401/403 are forwarded to the same handler through `RestAuthenticationEntryPoint`.
- **Unit tests vs integration tests.** `*Test` classes run in the `test` phase with mocks and
  no Spring context (fast feedback). `*IT` classes run in `verify` through the Failsafe plugin
  with the full application and a real PostgreSQL in Docker (real confidence). If Docker is
  not running, `./mvnw verify` fails loudly instead of skipping them.
- **Frontend session in `localStorage`.** Simple, and it survives a reload. The trade-off is
  that an XSS bug could read the token; an httpOnly cookie avoids that but brings CSRF back.
  The interceptor only attaches the token to this API's URLs, never to other hosts, and logs
  the user out on a 401.
- **Guards are UX, not security.** The Angular route guard only decides what to show. The
  backend still rejects every request without a valid token.

### Claude Code concepts practiced

- **Small steps and reviewing diffs.** Each step was written, built, tested and committed on
  its own (entity and migration, error handling, security configuration, endpoints, tests,
  API docs, then the frontend in three steps). Small commits make review realistic: run
  `git log --oneline` and then `git show <hash>` on any commit and you can read the whole
  change in a minute. When something breaks, the cause is in the last small step, not
  somewhere in a thousand-line change. Habit to build: read the diff before accepting it.
- **A first custom skill.** A skill is a folder with a `SKILL.md` file: a short description
  (which Claude Code always sees) plus instructions that are loaded only when the task matches.
  `CLAUDE.md` holds rules that apply to every task; a skill holds a recipe for one recurring
  task, so it costs no context until it is needed. Project skills live in `.claude/skills/`
  and are shared through git. Invoke this one by typing `/add-rest-endpoint` followed by what
  you want (for example `/add-rest-endpoint PATCH /api/users/me to update the full name`), or
  just describe the task and Claude will pick the skill up from its description.

### Interview questions

1. Walk through what happens, step by step, when the browser sends `GET /api/users/me` with a
   bearer token: which component reads the header, what exactly is verified, and how does the
   controller know which user is calling?
2. Why is it safe to disable CSRF protection in this API, and what would have to change if the
   token were stored in a cookie instead of `localStorage`?
3. The register endpoint checks `existsByEmail` before saving. Why is that check not enough on
   its own, and what guarantees uniqueness when two requests arrive at the same time?

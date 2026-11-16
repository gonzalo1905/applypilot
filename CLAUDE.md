# ApplyPilot

AI-assisted job-application tracker (Kanban board + LLM helpers). Portfolio project built in
stages; see `docs/LEARNING.md` for what each stage added and why.

## Stack

- Backend: Java 21, Spring Boot 3.5, Spring Security (stateless JWT), Spring Data JPA, Flyway, PostgreSQL 16
- Frontend: Angular 22, standalone components, signals, TypeScript strict, SCSS (no UI library)
- Tests: JUnit 5 + Mockito, Testcontainers (PostgreSQL), Vitest
- Infra: Docker Compose for local PostgreSQL

## Structure

- `backend/` Spring Boot API, base package `com.applypilot`
- `frontend/` Angular SPA, source in `frontend/src/app`
- `docs/` documentation; `docs/LEARNING.md` is the learning log (add a section per stage)
- `.claude/skills/` project skills (recipes for recurring tasks)

## Commands

- Database: `docker compose up -d` (repo root)
- Backend run: `cd backend && ./mvnw spring-boot:run` (Windows: `.\mvnw.cmd`)
- Backend tests: `cd backend && ./mvnw verify` (integration tests need Docker running)
- Frontend run: `cd frontend && npm start` (http://localhost:4200)
- Frontend checks: `cd frontend && npm test -- --watch=false` and `npm run build`
- Maven needs JDK 21. If the default `java` is older, set `JAVA_HOME` to a JDK 21 for that
  shell only (machine-specific path: see the git-ignored `CLAUDE.local.md`). Never change
  global or system environment variables.

## Backend conventions

- Package by feature (`auth`, `user`, `common`, ...), not by layer.
- Controllers are thin: validate input, call a service, return a DTO. Never expose JPA entities.
- DTOs are Java records with Bean Validation annotations; suffix `Request` / `Response`.
- Constructor injection only (no field `@Autowired`); dependencies are `private final`.
- Errors go through the global exception handler in `common` so every error has the same JSON shape.
- Schema changes only through new Flyway migrations (`V<n>__description.sql`); never edit an applied one.
- Configuration via environment variables with local defaults in `application.yml`.

## Frontend conventions

- Standalone components, `inject()` for dependencies, signals for state, new control flow (`@if`, `@for`).
- Feature folders under `src/app` (`auth`, `board`, `core`); functional guards and interceptors.
- Styling with SCSS and the CSS custom properties defined in `src/styles.scss`; support light and dark.

## Rules

- Never commit secrets. Real values live in the git-ignored `.env`; keep `.env.example` in sync.
- Small conventional commits (`feat:`, `fix:`, `chore:`, `test:`, `docs:`), one per working step.
- Commits are authored by the repository owner only: no AI attribution, no `Co-Authored-By`
  or "Generated with" lines in commits, code or docs.
- Do not push; the owner reviews and pushes.
- Work only inside this repository. Do not read or modify other folders on this machine.
- Code, commits and docs in English.
- Run the relevant tests before committing and report real results, including failures.

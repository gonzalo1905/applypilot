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

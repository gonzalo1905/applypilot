# ApplyPilot

[![CI](https://github.com/gonzalo1905/applypilot/actions/workflows/ci.yml/badge.svg)](https://github.com/gonzalo1905/applypilot/actions/workflows/ci.yml)

ApplyPilot is an AI-assisted job-application tracker. It keeps every application on a
Kanban board (Saved, Applied, Interview, Offer, Rejected) and, in later stages, uses an
LLM to help tailor CVs and prepare for interviews.

> Status: early development. This README covers local setup; a fuller one comes at launch.

## Stack

| Layer    | Technology                                                    |
|----------|---------------------------------------------------------------|
| Backend  | Java 21, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA, Flyway |
| Database | PostgreSQL 16 (Docker Compose)                                |
| Frontend | Angular 22 (standalone components, signals), TypeScript strict, SCSS |
| Testing  | JUnit 5, Mockito, Testcontainers, Vitest                      |

## Project structure

```
backend/             Spring Boot REST API (Maven wrapper included)
frontend/            Angular single-page application
docs/                Project documentation and learning log
docker-compose.yml   Local infrastructure (PostgreSQL)
.env.example         Template for local environment variables
CLAUDE.md            Project conventions for Claude Code
.claude/skills/      Project skills (reusable recipes for Claude Code)
.claude/agents/      Project subagents (code reviewer, test writer)
```

## Prerequisites

- JDK 21 (`java -version` must report 21; otherwise point `JAVA_HOME` at a JDK 21 install)
- Node.js 22.22+ and npm
- Docker with Docker Compose

## Run locally

1. Create your local environment file (git-ignored) and adjust it if needed:

   ```bash
   cp .env.example .env
   ```

2. Start PostgreSQL:

   ```bash
   docker compose up -d
   ```

3. Start the backend on http://localhost:8080:

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

   On Windows PowerShell use `.\mvnw.cmd spring-boot:run`. If your default JDK is not 21,
   point `JAVA_HOME` at a JDK 21 install for the current shell first:

   ```powershell
   $env:JAVA_HOME = 'C:\path\to\jdk-21'
   ```

4. Start the frontend on http://localhost:4200:

   ```bash
   cd frontend
   npm install
   npm start
   ```

## What works today

- Register, log in and log out; the session survives a page reload.
- Kanban board: add, edit and delete applications, and drag cards inside and between the five
  columns. Each user only ever sees their own cards.
- Profile page: upload, replace, download and delete a CV (PDF, up to 5 MB). Its text is
  extracted and stored for the AI features of later stages.
- API documentation at http://localhost:8080/swagger-ui.html once the backend is running.

| Method | Path                          | Auth   | Description                             |
|--------|-------------------------------|--------|-----------------------------------------|
| POST   | `/api/auth/register`          | public | Create an account, returns a JWT        |
| POST   | `/api/auth/login`             | public | Log in, returns a JWT                   |
| GET    | `/api/users/me`               | bearer | Profile of the logged-in user           |
| GET    | `/api/applications`           | bearer | The user's applications                 |
| POST   | `/api/applications`           | bearer | Create an application                   |
| GET    | `/api/applications/{id}`      | bearer | One application                         |
| PUT    | `/api/applications/{id}`      | bearer | Edit its details                        |
| PATCH  | `/api/applications/{id}/move` | bearer | Change its column and position          |
| DELETE | `/api/applications/{id}`      | bearer | Delete it                               |
| POST   | `/api/users/me/cv`            | bearer | Upload or replace the CV (multipart)    |
| GET    | `/api/users/me/cv`            | bearer | CV metadata                             |
| GET    | `/api/users/me/cv/file`       | bearer | Download the PDF                        |
| DELETE | `/api/users/me/cv`            | bearer | Delete the CV                           |

Uploaded CVs are stored in `backend/storage/` (git-ignored; change it with `STORAGE_DIR`).

## Tests

```bash
cd backend && ./mvnw test       # unit tests only (fast, no Docker)
cd backend && ./mvnw verify     # unit + integration tests (Docker must be running)
cd frontend && npm test         # unit tests
cd frontend && npm run build    # production build
cd frontend && npm run lint     # ESLint (angular-eslint)
cd frontend && npm run format:check
```

### Continuous integration

Every push and pull request to `main` runs `.github/workflows/ci.yml`: the backend's
`mvnw verify` (with Testcontainers) and the frontend's lint, formatting check, tests and
production build. Tests that call the real AI provider never run in CI.

### Agent evals (real model, opt-in)

The evals run the agents on a labelled dataset (`backend/src/test/resources/evals/cases.json`)
and write a report to `backend/target/evals/`. They spend AI quota, so they only run when
asked. A full run takes about 15 minutes on Groq's free tier, because it waits to stay under
the tokens-per-minute limit.

```bash
cd backend && ./mvnw verify -Devals=true -Dit.test=AgentEvalsIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false
# fewer cases: add -Devals.cases=3, or pick some: -Devals.only=en-backend-strong,es-injection-cv
```

The latest baseline is in `docs/evals/`. To run the evals on GitHub, add the repository
secret `GROQ_API_KEY` (Settings > Secrets and variables > Actions > New repository secret),
then open Actions > "Agent evals" > "Run workflow". The report appears in the run's
summary and as a downloadable artifact.

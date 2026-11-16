# ApplyPilot

ApplyPilot is an AI-assisted job-application tracker. It keeps every application on a
Kanban board (Saved, Applied, Interview, Offer, Rejected) and, in later stages, uses an
LLM to help tailor CVs and prepare for interviews.

> Status: early development. This README covers local setup; a fuller one comes at launch.

## Stack

| Layer    | Technology                                                    |
|----------|---------------------------------------------------------------|
| Backend  | Java 21, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA, Flyway |
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

## Tests

```bash
cd backend && ./mvnw verify     # unit + integration tests (Docker must be running)
cd frontend && npm test         # unit tests
cd frontend && npm run build    # production build
```

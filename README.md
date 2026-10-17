# Gym Management App

Owner / Staff / Member portals with QR attendance. Java 21 + Spring Boot 3.5, PostgreSQL 16, React + TypeScript (Vite), Docker.
Built from *Gym App Implementation Plan, Phases 1–8*.

```
backend/    Spring Boot API, Flyway migrations (V1–V5), unit + Testcontainers integration tests
frontend/   React app (mobile-first), vitest tests
e2e/        Playwright lifecycle test (runs against a live stack)
deploy/     TLS nginx config      scripts/  backup, restore-test, dev cert
docs/       API.md, DEPLOYMENT, BACKUP_RESTORE, RUNBOOK, SECURITY, PILOT_CHECKLIST
```

## Quick start (Docker)
```
cp .env.example .env     # set POSTGRES_PASSWORD, APP_QR_ENC_KEY, APP_BOOTSTRAP_OWNER_PASSWORD
docker compose up --build
# http://localhost:8080   (sign in as the bootstrap owner; you must set a new password)
```
Sample data: set `SPRING_PROFILES_ACTIVE=demo` (see docs/DEPLOYMENT.md).

## Develop
```
# database
docker run -d --name gympg -e POSTGRES_DB=gym -e POSTGRES_USER=gym -e POSTGRES_PASSWORD=gym -p 5432:5432 postgres:16-alpine
# backend (dev profile)
cd backend && DB_PASSWORD=gym SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# frontend (proxies /api to :8080)
cd frontend && npm install && npm run dev      # http://localhost:5173
```
## Tests
```
cd backend  && ./mvnw verify          # needs Docker (Testcontainers)
cd frontend && npm install && npm test && npm run typecheck
cd e2e      && npm install && npx playwright install chromium && E2E_OWNER_PASSWORD=... npx playwright test
```

## What was and was not verified (honest status)
The build environment had no access to Maven Central / npm and no Docker, so:

**Actually run and passing**
- All 5 Flyway migrations on a real PostgreSQL 16, including the overlap exclusion constraint, partial unique QR index and append-only audit trigger.
- Every dashboard/report SQL file against seeded data on PostgreSQL 16.
- Core pure logic (money, CSV, membership status, dues) via a standalone javac harness.
- Syntax-level parse of all Java sources, and of all TypeScript/TSX (no syntax errors).

**Written but NOT executed** — expect to fix small compile/type errors on first run:
- Java semantic compilation, all JUnit unit tests and Testcontainers integration tests.
- The React app (type-check, vitest, browser behaviour), Docker image builds, compose stack, backup/restore scripts, Playwright e2e.

Run the commands above in that order; treat the first failures as the to-do list.

## Decisions and deviations
QR: SHA-256 hash for lookup + AES-GCM encrypted copy (plan's Option A). JdbcClient instead of JPA. BCrypt instead of Argon2.
Receipts are HTML/JSON. No email/SMS (owner shares links). Single gym per deployment. See docs/PILOT_CHECKLIST.md.

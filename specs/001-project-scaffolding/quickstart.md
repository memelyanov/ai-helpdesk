# Quickstart & Validation: Project Scaffolding

**Feature**: [Project Scaffolding](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-08-13

How to run the scaffold and prove it satisfies the spec. Each section below maps to a user story
and its acceptance scenarios, so working through this document top to bottom is a full manual
validation pass.

> This is a validation guide, not an implementation guide. Commands and expected outcomes only —
> the code that makes them work is produced by `/speckit-tasks` and the implementation phase.

## Prerequisites

| Tool | Required | Check |
|---|---|---|
| JDK 17 | yes | `java -version` |
| Node.js `^20.19.0 \|\| ^22.12.0 \|\| >=24.0.0` | yes | `node -v` |
| Docker + Compose V2+ | yes | `docker compose version` |
| Maven | no — the wrapper covers it | — |

Node 22.0–22.11 will **not** work; Angular 21's engine range excludes them.

## Setup

```bash
cp .env.example .env
```

`.env` holds the local database credentials and is git-ignored. No other configuration is needed,
and **no OpenAI API key is required** — nothing in this feature calls an external service.

---

## US1 — Database environment

### Start

```bash
docker compose up -d
```

**Expected**: the container reaches a healthy state within roughly 30 seconds and `localhost:5432`
accepts connections. Verify with:

```bash
docker compose ps
```

### Verify vector capability (FR-002, scenario US1-2)

```bash
docker compose exec db psql -U aihelpdesk -d aihelpdesk -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"
```

**Expected**: exactly one row, `vector` with version `0.8.6`. Zero rows means the init script did
not run — almost always because the volume already existed; see Reset below.

### Verify persistence (FR-003, SC-004, scenario US1-3)

```bash
docker compose exec db psql -U aihelpdesk -d aihelpdesk -c "CREATE TABLE IF NOT EXISTS smoke(id int); INSERT INTO smoke VALUES (1);"
docker compose down
docker compose up -d
docker compose exec db psql -U aihelpdesk -d aihelpdesk -c "SELECT count(*) FROM smoke;"
```

**Expected**: `1`. Drop the table afterwards — it is a probe, not schema:

```bash
docker compose exec db psql -U aihelpdesk -d aihelpdesk -c "DROP TABLE smoke;"
```

### Stop, and reset

```bash
docker compose down
```

Stops the environment and releases port 5432, **keeping** the data (scenario US1-4).

```bash
docker compose down -v
```

Destroys the volume too. This is the only way a changed `db/init/*.sql` takes effect, because the
image runs init scripts solely when the data directory is empty.

---

## US2 — Backend service

### Start

```bash
backend/mvnw spring-boot:run
```

**Expected**: the service starts and listens on `http://localhost:8080`.

### Verify health with the database up (FR-006, scenario US2-2)

```bash
curl -i http://localhost:8080/actuator/health
```

**Expected**: `200 OK`, `"status":"UP"`, and a `db` component with `"status":"UP"` and
`"database":"PostgreSQL"`. Full contract: [contracts/health-api.md](contracts/health-api.md).

### Verify health with the database down (FR-007, scenario US2-3)

This is the scenario most likely to be got wrong, so test it deliberately:

```bash
docker compose down
backend/mvnw spring-boot:run
curl -i http://localhost:8080/actuator/health
```

**Expected**: the service **starts normally** — no crash, no restart loop — and returns
`503 Service Unavailable` with `"status":"DOWN"` and a `db` component `DOWN` carrying an error
message. A backend that fails to boot here fails FR-007.

Then, without restarting the backend:

```bash
docker compose up -d
curl -i http://localhost:8080/actuator/health
```

**Expected**: back to `200 UP` on its own. This covers the "database restarted underneath a
running backend" edge case.

### Run the test suite (FR-008, SC-003)

```bash
backend/mvnw test
```

**Expected**: all tests pass, zero failures, zero skips, with no database running — the suite has
no Docker dependency by design (research Decision 8). At minimum it contains a context-loads test
and a health contract test covering both cases above.

---

## US3 — Frontend application

### Start

```bash
cd frontend
npm install
npm start
```

**Expected**: the dev server builds and serves `http://localhost:4200`.

### Verify the placeholder page (FR-011, scenario US3-2)

Open `http://localhost:4200`.

**Expected**: a page that names the application (AI Helpdesk). Open the browser console:
**zero errors**. The page makes no network calls to the backend — the frontend is deliberately
unwired at this stage, so it renders identically whether or not the backend is running.

### Run the test suite (FR-012, SC-003)

```bash
cd frontend
npm test
```

**Expected**: Vitest runs and all tests pass, including one asserting the placeholder renders.

---

## US4 — Whole stack, from the documentation alone

The real test of this story is a person, not a command: hand the repository to a developer who has
not seen it and have them follow the README only.

**Expected outcome** (SC-001, SC-007): all three parts running in under 15 minutes, on the first
attempt, without editing any file the documentation did not tell them to edit.

Full stack, in the recommended order:

```bash
docker compose up -d
backend/mvnw spring-boot:run          # in a second terminal
cd frontend && npm start              # in a third terminal
```

Then confirm all three: `docker compose ps`, `curl http://localhost:8080/actuator/health`,
and `http://localhost:4200` in a browser.

---

## Independence check (FR-013, SC-005)

Three separate single-part startups, each from a fully stopped state:

| Start only | Expected |
|---|---|
| `docker compose up -d` | Healthy container, `psql` connects |
| `backend/mvnw spring-boot:run` | Serves on 8080; health `503` with `db` `DOWN` |
| `npm start` in `frontend/` | Serves on 4200; placeholder renders, console clean |

---

## Secret check (FR-009, SC-006)

```bash
git ls-files | grep -E "(^|/)\.env$"
```

**Expected**: no output — `.env` is ignored, only `.env.example` is tracked. Also confirm no
password literal appears in `docker-compose.yml` or `application.yml`; both must read from the
environment.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Compose fails with a port conflict on 5432 | A local PostgreSQL is already running | Stop it, or change the host-side port mapping and the docs together |
| `vector` extension query returns no rows | Volume pre-dated the init script | `docker compose down -v`, then `up -d` |
| Backend health `503` while the database is up | Wrong credentials, or `.env` not created | Confirm `.env` exists and matches compose |
| `npm start` fails on an engine check | Node 22.0–22.11 | Upgrade to ≥22.12 |
| Backend fails to *start* with the database down | FR-007 violated — likely JPA or a startup migration was added | Remove it; see research Decisions 3 and 4 |

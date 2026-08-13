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
| Azure OpenAI credentials | **no** | see below |

Node 22.0–22.11 will **not** work; Angular 21's engine range excludes them.

## Shell conventions (FR-027)

Commands are written for **PowerShell 7+**, the primary development platform. Where a command
differs under bash/POSIX, both forms are given side by side. Commands shown once are identical in
both shells — that covers everything `docker`, `npm` and `git` related.

Two substitutions account for most of the difference:

| PowerShell | bash / POSIX | Why |
|---|---|---|
| `backend\mvnw.cmd` | `backend/mvnw` | The Maven wrapper ships as two scripts; Windows needs the `.cmd` one |
| `Select-String` | `grep` | `grep` does not exist in PowerShell |

Environment variables are `$env:NAME` in PowerShell and `$NAME` in bash. That matters in exactly one
command below — the key-leak check.

## Setup

```powershell
Copy-Item .env.example .env
```

```bash
cp .env.example .env
```

`.env` holds the local database credentials and is git-ignored.

**Azure OpenAI credentials are optional here.** Every part starts, serves and tests green without
them (SC-009); the backend simply reports the provider as unconfigured. If you do have them, these
four variables are read from the environment — the names are fixed by constitution v1.3.0:

```bash
AZURE_OPEN_AI_KEY
AZURE_OPEN_AI_ENDPOINT
AZURE_OPEN_AI_DEPLOYMENT_NAME
AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME   # may be unset in this feature
```

Nothing in normal operation contacts Azure. The only thing that does is the opt-in check in the
US2 section below, and only when you run it.

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

```powershell
backend\mvnw.cmd spring-boot:run
```

```bash
backend/mvnw spring-boot:run
```

**Expected**: the service starts and listens on `http://localhost:8080`.

### Verify health with the database up (FR-006, scenario US2-2)

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health | Select-Object StatusCode, Content
```

```bash
curl -i http://localhost:8080/actuator/health
```

`curl.exe` ships with Windows 11 and works too, but PowerShell 7 no longer aliases `curl`, so bare
`curl` resolves to the real binary rather than `Invoke-WebRequest` — the two accept different flags.
`Invoke-WebRequest` is used here to keep the PowerShell column unambiguous.

**Expected**: `200 OK`, `"status":"UP"`, and a `db` component with `"status":"UP"` and
`"database":"PostgreSQL"`. Full contract: [contracts/health-api.md](contracts/health-api.md).

### Verify health with the database down (FR-007, scenario US2-3)

This is the scenario most likely to be got wrong, so test it deliberately:

```powershell
docker compose down
backend\mvnw.cmd spring-boot:run
Invoke-WebRequest http://localhost:8080/actuator/health -SkipHttpErrorCheck | Select-Object StatusCode, Content
```

```bash
docker compose down
backend/mvnw spring-boot:run
curl -i http://localhost:8080/actuator/health
```

**`-SkipHttpErrorCheck` is required here.** Without it `Invoke-WebRequest` throws on any non-2xx
status, so the expected `503` would surface as a PowerShell error rather than a readable response —
easily misread as the backend having crashed, which is the exact opposite of what this step proves.

**Expected**: the service **starts normally** — no crash, no restart loop — and returns
`503 Service Unavailable` with `"status":"DOWN"` and a `db` component `DOWN` whose `details.error`
names the underlying connection exception (FR-007). A backend that fails to boot here fails FR-007.

Then, without restarting the backend:

```powershell
docker compose up -d
Invoke-WebRequest http://localhost:8080/actuator/health | Select-Object StatusCode, Content
```

```bash
docker compose up -d
curl -i http://localhost:8080/actuator/health
```

**Expected**: back to `200 UP` on its own, within 30 seconds of the database accepting connections
(FR-025), with no manual restart. This covers the "database restarted underneath a running backend"
edge case.

### Verify Azure OpenAI configuration status (FR-020, scenarios US2-6 and US2-7)

With credentials present in the environment:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health | ConvertTo-Json -Depth 6
```

```bash
curl -s http://localhost:8080/actuator/health
```

**Expected**: an `azureOpenAi` component with `"status":"UP"`. **No request is made to Azure** —
this reports configuration only, so it costs nothing and stays fast. `UP` here means *configured*,
not *working*; wrong credentials still show `UP`. Proving they work is the next step.

With the variables unset, in the same shell the backend runs in:

**Expected**: `azureOpenAi` reports `"status":"UNKNOWN"` with a `missing` list naming each absent
setting — and the **overall status stays `UP` with HTTP 200**. A developer with no Azure access
still has a healthy service. Full contract: [contracts/health-api.md](contracts/health-api.md).

Confirm the key never leaks:

```powershell
if (-not $env:AZURE_OPEN_AI_KEY) { "SKIPPED - key not set" }
else { (Invoke-WebRequest http://localhost:8080/actuator/health).Content |
        Select-String -SimpleMatch $env:AZURE_OPEN_AI_KEY }
```

```bash
test -n "$AZURE_OPEN_AI_KEY" && curl -s http://localhost:8080/actuator/health | grep -F "$AZURE_OPEN_AI_KEY"
```

**Expected**: no output (or `SKIPPED` if you have no key set).

**The guard is not optional.** With the key unset, the pattern is the empty string —
`Select-String` and `grep` both match *every* line against it, and the check appears to fail
loudly while actually having tested nothing. `-SimpleMatch` / `-F` matter too: a raw key can contain
regex metacharacters, which would otherwise be interpreted rather than matched literally.

### Verify Azure connectivity, on demand (FR-022, SC-008, scenario US2-8)

```powershell
backend\mvnw.cmd test -Pverify-ai
```

```bash
backend/mvnw test -Pverify-ai
```

**Expected**: passes, having made **exactly one** completion request against the chat deployment.
This is the only command in the repository that contacts Azure.

- Wrong credentials → fails with Azure's own error, not a generic message
- Incomplete configuration → fails immediately naming what is missing, without making a request
- `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` unset → reported as missing (FR-023) but does not fail
  the chat check; nothing consumes embeddings yet

It never runs during startup, during the health check, or in `mvnw test`.

### Run the test suite (FR-008, SC-003, SC-009)

```powershell
backend\mvnw.cmd test
```

```bash
backend/mvnw test
```

**Expected**: all tests pass, zero failures, zero skips — **with no database running and no Azure
credentials set**. The default suite has no Docker dependency and makes no external call by design
(research Decision 11); constitution v1.3.0 requires exactly this. It contains a context-loads
test, health contract tests for the database up and down cases, and unit tests over
configuration-completeness including the partial case.

---

## US3 — Frontend application

### One-time setup (prerequisite, not a start command)

```bash
cd frontend
npm install
```

Run once per checkout. Per SC-002 this is a **prerequisite**, not part of the start command — which
is why the frontend still counts as starting with exactly one command below.

### Start

```bash
npm start
```

**Expected**: the dev server builds and serves `http://localhost:4200`. Identical in both shells.

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

Full stack, in the recommended order (after `npm install` has been run once):

```powershell
docker compose up -d
backend\mvnw.cmd spring-boot:run       # in a second terminal
cd frontend && npm start               # in a third terminal
```

```bash
docker compose up -d
backend/mvnw spring-boot:run          # in a second terminal
cd frontend && npm start              # in a third terminal
```

Then confirm all three: `docker compose ps`, the health check above, and `http://localhost:4200` in
a browser.

---

## Independence check (FR-013, SC-005)

Three separate single-part startups, each from a fully stopped state:

| Start only | Expected |
|---|---|
| `docker compose up -d` | Healthy container, `psql` connects |
| `backend\mvnw.cmd spring-boot:run` (bash: `backend/mvnw …`) | Serves on 8080; health `503` with `db` `DOWN` |
| `npm start` in `frontend/` | Serves on 4200; placeholder renders, console clean |

---

## Secret check (FR-009, SC-006)

```powershell
git ls-files | Select-String -Pattern '(^|/)\.env$'
```

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
| Backend fails to *start* with the database down | FR-007 violated — likely JPA or a startup migration was added | Remove it; see research Decisions 6 and 7 |
| Backend fails to *start* with Azure variables unset | FR-019 violated — `spring.ai.model.chat`/`embedding` are not defaulted to `none`, so auto-config tried to build a client without a key | Restore the `none` defaults; see [contracts/ai-provider.md](contracts/ai-provider.md) |
| Overall health is `503` purely because Azure is unconfigured | The indicator is returning `DOWN` instead of `UNKNOWN` | `UNKNOWN` is required so overall stays `UP`; see research Decision 4 |
| `azureOpenAi` reports `UP` but ingestion later fails to authenticate | Expected — the indicator makes no network call, so it cannot see invalid credentials | Run the verification above to test them for real |
| `mvnw test` tries to reach Azure | The `azure` tag is not excluded in the default Surefire config | Restore `<excludedGroups>azure</excludedGroups>`; the live call belongs only to `-Pverify-ai` |
| PowerShell: `backend/mvnw` is "not recognized" | That is the POSIX wrapper script | Use `backend\mvnw.cmd` — see Shell conventions |
| PowerShell: the database-down step throws instead of printing `503` | `Invoke-WebRequest` treats non-2xx as a terminating error | Add `-SkipHttpErrorCheck`; the `503` is the expected result here, not a failure |
| The key-leak check prints the whole health payload | `AZURE_OPEN_AI_KEY` is unset, so the search pattern is empty and matches every line | Expected when you have no key — the guarded form prints `SKIPPED` instead |
| PowerShell: `grep` is "not recognized" | `grep` has no PowerShell equivalent by that name | Use `Select-String` — see Shell conventions |

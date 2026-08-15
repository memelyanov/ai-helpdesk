# AI Helpdesk

An AI helpdesk chatbot that answers natural-language questions about a company's internal
documents (`.txt` / `.pdf`), grounded in the documents themselves and citing its sources.

**Status: runnable skeleton.** A three-part scaffold now exists and runs — a containerised
PostgreSQL database with the `vector` extension, a Spring Boot backend exposing a health check,
and an Angular placeholder frontend that polls that health check and shows whether the backend is
reachable — but no PoC functionality (document upload, chunking, embedding, retrieval, answer
generation) is implemented yet. **Azure OpenAI credentials are not required** to run the scaffold;
the backend starts and serves with them absent, reporting the provider as unconfigured. See
[Setup](#setup) below.

Proof of concept for Jira ticket
[EPMGDPL-3139](https://jiraeu.epam.com/browse/EPMGDPL-3139) — *[Week 1] Java General Task 2 —
PoC Concept Definition*.

## The problem

A company accumulates a large set of process documents — onboarding guides, security policies,
expense rules, release procedures. They exist, but they are effectively unfindable: keyword
search collapses on vocabulary mismatch. A user asks *"how do I get money back for a taxi to the
airport?"* while the document says *"Business Travel Expense Reimbursement Policy"* — zero
keyword overlap, zero results. So people ask colleagues instead, and a self-service question
becomes an interruption.

## The approach

Retrieval-Augmented Generation (RAG). The user stops searching for *a document* and starts asking
for *an answer*.

**Ingest** — document → parse (Apache Tika) → chunk → Azure OpenAI embeddings → vector database.

**Ask** — question → embedding → vector similarity search → top-K chunks → Azure OpenAI chat
model → answer + source citations.

Matching happens on *meaning* rather than exact words, and every answer is traceable back to the
document it came from. When retrieval finds nothing relevant, the assistant says so rather than
inventing an answer.

## Planned stack

| Layer | Choice |
|---|---|
| Language / runtime | Java 17, Spring Boot 3 |
| RAG orchestration | Spring AI (alt: LangChain4j) |
| LLM & embeddings | Azure OpenAI — a chat deployment (`gpt-4o-mini` class) and a separate embedding deployment (`text-embedding-3-small`) |
| Vector database | pgvector on PostgreSQL (alt: Chroma / Qdrant) |
| Document parsing | Apache Tika |
| API | Spring Web REST — `POST /documents`, `POST /chat` |
| Frontend | Angular 21 — chat view + document upload view |
| Infra | PostgreSQL/pgvector in Docker; backend and frontend run locally |

## Current contents

| Path | Contents |
|---|---|
| `backend/` | Spring Boot 3 service — health check, Azure OpenAI configuration status, CORS-open for the frontend's origin; no PoC endpoints yet |
| `frontend/` | Angular 21 placeholder application — polls the backend health check and shows a connection-status indicator (healthy / degraded / unreachable) |
| `db/init/` | Database init scripts — enable the `vector` extension, then create the `documents` and `chunks` tables (see [Database schema](#database-schema-dbinitsql) below) |
| `docker-compose.yml` | The database service (backend and frontend run on the host, not in containers) |
| `.env.example` | Committed template for `.env` — database credentials and the four Azure OpenAI variables |
| [`docs/poc-concept.md`](docs/poc-concept.md) | Full PoC concept: problem, scenario, architecture, tooling, risks, success criteria, presentation outline |
| [`sample-data/`](sample-data/README.md) | The synthetic corpus, the evaluation set, and the PDF build script |
| [`specs/001-project-scaffolding/`](specs/001-project-scaffolding/spec.md) | Spec, plan and contracts for this scaffold, including [`quickstart.md`](specs/001-project-scaffolding/quickstart.md), a full manual validation pass |
| [`specs/002-frontend-health-wire/`](specs/002-frontend-health-wire/spec.md) | Spec, plan and contracts for the frontend's connection-status indicator, including its own [`quickstart.md`](specs/002-frontend-health-wire/quickstart.md) |
| [`specs/003-document-vector-schema/`](specs/003-document-vector-schema/spec.md) | Spec, plan and contracts for the `documents`/`chunks` database schema, including its own [`quickstart.md`](specs/003-document-vector-schema/quickstart.md) |
| `.specify/` | speckit workflow templates |

## Setup

This is the single place onboarding documentation lives for this repository; if
[`quickstart.md`](specs/001-project-scaffolding/quickstart.md) ever disagrees with this section,
this section governs.

### Shell conventions

Commands below are written for **PowerShell 7+**, the primary development platform. Where a
command differs under bash/POSIX, both forms are given. Commands shown once (`docker`, `npm`,
`git`) are identical in both shells.

| PowerShell | bash / POSIX | Why |
|---|---|---|
| `backend\mvnw.cmd` | `backend/mvnw` | The Maven wrapper ships as two scripts; Windows needs the `.cmd` one |
| `Select-String` | `grep` | `grep` does not exist in PowerShell |

### Prerequisites

| Tool | Minimum | Notes |
|---|---|---|
| JDK | 17 | `java -version` |
| Node.js | `^20.19.0 \|\| ^22.12.0 \|\| >=24.0.0` | `node -v` — Node **22.0–22.11 will not work**; Angular 21 rejects that range |
| Docker + Compose V2+ | — | `docker compose version` |
| Maven | not required | the backend ships its own wrapper (`mvnw` / `mvnw.cmd`) |
| Azure OpenAI credentials | **not required** | every part starts, serves and tests green without them; see [Optional: Azure OpenAI](#optional-azure-openai) |

One-time setup, run once per checkout (a prerequisite, not a start command):

```powershell
Copy-Item .env.example .env
cd frontend; npm install; cd ..
```

```bash
cp .env.example .env
cd frontend && npm install && cd ..
```

`.env` holds local database credentials and is git-ignored — it is never committed.

### Start, stop and verify each part

Recommended order below is a convenience, **not mandatory** — each part starts and serves
independently of the others. Database → backend → frontend simply gives a green backend health
check on the first request instead of a `503` to re-check.

Or start backend and frontend together, each in its own window, with one script:

```powershell
.\start-dev.ps1            # backend + frontend
.\start-dev.ps1 -IncludeDb # database + backend + frontend
```

| Part | Start | Stop | Address | Verify it's working |
|---|---|---|---|---|
| Database | `docker compose up -d` | `docker compose down` | `localhost:5432` | `docker compose ps` shows `healthy` |
| Backend | `backend\mvnw.cmd spring-boot:run` (bash: `backend/mvnw spring-boot:run`) | `Ctrl+C` | `http://localhost:8080` | `Invoke-WebRequest http://localhost:8080/actuator/health` (bash: `curl -i http://localhost:8080/actuator/health`) returns `200` and `"status":"UP"` |
| Frontend | `npm start` (in `frontend/`) | `Ctrl+C` | `http://localhost:4200` | Open the address — within 5s a connection-status indicator shows the backend as healthy (or unreachable/degraded, matching whatever state the backend is actually in), with a clean browser console |

The indicator polls `GET /actuator/health` directly from the browser, which only works because the
backend explicitly allows that cross-origin request (`management.endpoints.web.cors.*` in
`backend/src/main/resources/application.yml`, restricted to `http://localhost:4200`) — see
[`specs/002-frontend-health-wire/contracts/frontend-health-consumption.md`](specs/002-frontend-health-wire/contracts/frontend-health-consumption.md).

Full command-by-command walkthrough, including the database-down and Azure-unconfigured health
cases: [`specs/001-project-scaffolding/quickstart.md`](specs/001-project-scaffolding/quickstart.md)
and, for the indicator itself,
[`specs/002-frontend-health-wire/quickstart.md`](specs/002-frontend-health-wire/quickstart.md).

### Database schema (`db/init/*.sql`)

**Short answer: nothing to run by hand — but only on a database that has never been started
before.** `db/init/` holds two scripts, applied in filename order:

1. `01-init-vector.sql` — enables the `vector` extension.
2. `02-documents-and-chunks.sql` — creates the `documents` and `chunks` tables (feature 003).

These are not migrations and there is no separate "apply the schema" command. They are picked up
automatically by the official PostgreSQL Docker image's own startup mechanism: anything mounted at
`/docker-entrypoint-initdb.d/` — which `docker-compose.yml` points at `./db/init` — runs once,
**only when the container's data directory is empty**. On a first-ever `docker compose up -d` for
this repository (no `db-data` volume yet), both scripts run automatically and you get a fully
formed schema for free.

**The trap**: that "only when empty" rule means an *existing* database — one you already brought up
before this schema existed, or before pulling this change — will **not** pick up the new script on
a plain `docker compose up -d`. Postgres sees a non-empty data directory and skips
`/docker-entrypoint-initdb.d/` entirely. If `\d documents` (below) comes back empty, this is why.

To force the scripts to (re-)run, recreate the volume from scratch:

```bash
docker compose down -v   # destroys all stored data — see Reset below
docker compose up -d
```

To verify the schema is actually present:

```bash
docker compose exec db psql -U aihelpdesk -d aihelpdesk -c "\d documents"
docker compose exec db psql -U aihelpdesk -d aihelpdesk -c "\d chunks"
```

Full column-by-column detail, plus a per-table walkthrough with sample inserts, is in
[`specs/003-document-vector-schema/quickstart.md`](specs/003-document-vector-schema/quickstart.md).

### Reset (destructive)

```bash
docker compose down -v
```

**This discards all stored database data** — distinct from the ordinary `docker compose down`
stop command above, which keeps it. It is also the *only* way a changed `db/init/*.sql` script
takes effect: the database image only runs init scripts when its data directory is empty (see
[Database schema](#database-schema-dbinitsql) above).

### Run the test suites

```powershell
backend\mvnw.cmd test
cd frontend; npm test; cd ..
```

```bash
backend/mvnw test
cd frontend && npm test && cd ..
```

Both pass with **no database running and no Azure credentials set** — neither is required by
either suite.

### Optional: Azure OpenAI

Nothing in normal operation contacts Azure. If you have credentials, set these four variables
(names fixed by the constitution, do not rename) before starting the backend:

```text
AZURE_OPEN_AI_KEY
AZURE_OPEN_AI_ENDPOINT
AZURE_OPEN_AI_DEPLOYMENT_NAME
AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME   # may be unset — nothing consumes embeddings yet
```

To verify they actually work (the *only* command in the repository that contacts Azure, making
exactly one request):

```powershell
backend\mvnw.cmd test -Pverify-ai
```

```bash
backend/mvnw test -Pverify-ai
```

### Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Compose fails with a port conflict on 5432 | A local PostgreSQL is already running | Stop it, or change the host-side port mapping and this doc together |
| `vector` extension query returns no rows, or `\d documents` / `\d chunks` shows nothing | Volume pre-dated one of the init scripts — see [Database schema](#database-schema-dbinitsql) | `docker compose down -v`, then `up -d` |
| Backend health `503` while the database is up | Wrong credentials, or `.env` not created | Confirm `.env` exists and matches `docker-compose.yml` |
| `npm start` fails on an engine check | Node 22.0–22.11 | Upgrade to ≥22.12 |
| `npm start` / dev server fails with "Port 4200 is already in use" | Another instance is already running | Stop it first — the dev server does not silently move to another port |
| PowerShell: `backend/mvnw` is "not recognized" | That is the POSIX wrapper script | Use `backend\mvnw.cmd` |
| PowerShell: `grep` is "not recognized" | `grep` has no PowerShell equivalent by that name | Use `Select-String` |
| `mvnw test` tries to reach Azure | Should never happen by default — see `-Pverify-ai` above | File a bug; the default suite must never contact Azure |
| Frontend indicator stuck on "checking" past 5s with the backend confirmed running | CORS not applied, or the backend was started before the CORS config was added | Confirm `management.endpoints.web.cors.allowed-origins` is present in `application.yml` and restart the backend |

## Sample data

There is no real corpus to test against, so the repository carries a synthetic one: 16 documents
(7 `.txt`, 9 `.pdf`) written as internal process documentation for a fictional company —
travel and expenses, security, incident response, releases, leave, IT support, onboarding,
benefits, conduct, facilities. The PDFs are generated from Markdown sources by
[`sample-data/build-pdfs.py`](sample-data/build-pdfs.py).

`sample-data/evaluation-questions.csv` is built to test the actual claim. Most questions are
flagged `tests_vocabulary_mismatch=yes` — they deliberately avoid the source document's own
wording, so a passing score means semantic retrieval works rather than keyword luck. Five of the
sixteen documents are distractors that answer no question, so a wrong answer is always available.
One row is a negative test with an empty expected source: the correct behaviour there is refusing
to answer.

See [`sample-data/README.md`](sample-data/README.md) for the document list and the
question-to-document vocabulary gaps.

## Success criteria

1. A `.txt` and a `.pdf` can be uploaded and are searchable immediately afterwards.
2. The correct source document appears in the top-K retrieved chunks for **≥ 80%** of the
   evaluation questions.
3. Answers cite the source document they came from.
4. A question with no coverage produces an explicit "not found in documentation" answer.
5. End-to-end response time under ~5 seconds for a typical question.

## Out of scope for the PoC

Authentication and per-user document permissions, visual design polish beyond a functional chat
interface, real confidential data, OCR of scanned PDFs, and multi-turn conversation memory.

## Next steps

See [`docs/poc-concept.md`](docs/poc-concept.md) §10 for the implementation plan.

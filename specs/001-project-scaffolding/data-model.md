# Phase 1 Data Model: Project Scaffolding

**Date**: 2026-08-13 | **Plan**: [plan.md](plan.md)

## Scope note

**This feature defines no domain entities.** There are no documents, chunks, embeddings, queries
or answers yet — FR-016 excludes all of them. Creating tables for entities that no code reads or
writes would be speculative schema, and it would have to be rewritten once the ingestion feature
establishes what a chunk actually needs to store.

What this feature does define is the **initial state of the database**: the capabilities that must
be present and verifiable so that the ingestion feature can create its schema without touching
infrastructure. That state is described below.

## Database initialisation state

### Instance

| Property | Value | Source |
|---|---|---|
| Engine | PostgreSQL 18 | `pgvector/pgvector:pg18` |
| Extension bundle | pgvector 0.8.6 (pre-compiled in the image) | image tag |
| Host port | `5432` | FR-004 (fixed, documented port) |
| Database name | `aihelpdesk` | convention |
| Application user | `aihelpdesk` | convention |
| Password | supplied via `.env`, defaulted for local development only | FR-009 |

### Extensions

| Extension | Required state | Created by | Verification |
|---|---|---|---|
| `vector` | installed in the target database | `db/init/01-init-vector.sql` | `SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';` returns one row (FR-002) |

`CREATE EXTENSION IF NOT EXISTS vector;` is idempotent, so re-running the init script against an
already-initialised database is harmless — though in practice it will not re-run at all, because
the image only executes `/docker-entrypoint-initdb.d/` scripts when the data directory is empty.

### Tables

**None.** The schema is intentionally empty beyond the extension. The first tables arrive with
ingestion; if Spring AI's pgvector store is adopted then, it manages its own `vector_store` table
and this feature must not pre-create it.

### Persistence

| Property | Value |
|---|---|
| Mechanism | Named Docker volume mounted at the container's data directory |
| Lifecycle | Survives `docker compose down`; destroyed only by `docker compose down -v` |
| Requirement served | FR-003 / SC-004 — 100% of stored data survives a stop/start cycle |

The distinction between `down` and `down -v` is the whole of FR-003's testable behaviour, and it
is also the trap behind the "stale volume" edge case: because init scripts only run on an empty
data directory, a developer who changes `01-init-vector.sql` sees no effect until they run
`down -v`. The quickstart states this explicitly rather than leaving it to be discovered.

## Backend-side representation

The backend holds **no persistent model** in this feature. Its only interaction with the database
is a connection-liveness probe performed by Actuator's `db` health indicator, which issues a
validation query and maps the outcome to a health component. No entity classes, no repositories,
no migrations.

## Bound configuration (not persisted)

The one structured value this feature introduces is the Azure OpenAI configuration, held in memory
and never written anywhere. It is included here because its shape determines the health
contribution and the verification, and because the ingestion feature inherits it unchanged.

| Field | Source variable | Blank permitted | Part of "complete"? |
|---|---|---|---|
| `apiKey` | `AZURE_OPEN_AI_KEY` | yes | ✅ required |
| `endpoint` | `AZURE_OPEN_AI_ENDPOINT` | yes | ✅ required |
| `chatDeploymentName` | `AZURE_OPEN_AI_DEPLOYMENT_NAME` | yes | ✅ required |
| `embeddingDeploymentName` | `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` | yes | ❌ excluded (FR-023) |

**Completeness rule** (FR-021): complete only when `apiKey`, `endpoint` and `chatDeploymentName`
are all present and non-blank. Any other combination — including three of four present — is
incomplete. There is no partially-usable state.

`embeddingDeploymentName` is excluded from the rule on purpose: nothing consumes embeddings in this
feature, and requiring it would make the scaffold unrunnable in the target environment, where that
deployment does not yet exist.

**Handling rules**: `apiKey` is a secret. It MUST NOT appear in health responses, logs, error
messages or test output — constitution v1.3.0 requires this and a test asserts it. The other three
values are not secrets but are still supplied by environment rather than committed.

### Deferred: embedding vector shape

The ingestion feature will store vectors whose dimensionality is fixed by the embedding model
(`text-embedding-3-small` produces 1536-dimension vectors). That column is **not** created here:
the dimension is a property of a model chosen by whoever provisions the Azure deployment, and
committing a `vector(n)` column before the deployment exists would be guessing. Principle V's
constraint — the same embedding deployment must serve ingestion and query, and changing it
invalidates the index — makes that guess expensive to get wrong.

## Validation rules

There are no user-supplied values to validate — this feature accepts no input. The only
constraints that apply are environmental, and each maps to a requirement:

| Constraint | Requirement | How it fails if violated |
|---|---|---|
| Port 5432 available on the host | FR-004 | Compose reports a port binding conflict naming 5432 |
| `vector` extension present after startup | FR-002 | The verification query returns zero rows |
| Data survives restart | FR-003 | A row written before restart is absent afterwards |
| No secret committed | FR-009 | `.env` is tracked by git, or a password literal appears in `docker-compose.yml` / `application.yml` |

## State transitions

The only stateful lifecycle in this feature belongs to the database environment:

```text
absent ──compose up──> initialising ──init scripts run──> ready
                                                            │
                          ┌─────────────────────────────────┤
                          │                                 │
                    compose down                      compose down -v
                          │                                 │
                          ▼                                 ▼
                       stopped                            absent
                    (volume kept)                     (volume destroyed)
                          │
                     compose up
                          │
                          ▼
                    ready (init scripts SKIPPED — data dir non-empty)
```

The `stopped → ready` path skipping the init scripts is the behaviour that makes FR-003 work and
the stale-volume edge case possible. Both follow from the same mechanism.

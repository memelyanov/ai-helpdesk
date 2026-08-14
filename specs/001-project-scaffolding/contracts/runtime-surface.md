# Contract: Runtime Surface

**Feature**: [Project Scaffolding](../spec.md) | **Serves**: FR-001, FR-004, FR-005, FR-009, FR-010, FR-013, FR-014 | **Date**: 2026-08-13 (revised)

The commands, ports and startup guarantees each of the three parts must honour. This is the
contract the quickstart verifies and that FR-014's documentation must match.

## Ports

Fixed and documented. A single developer on one machine is assumed, so no allocation strategy is
needed.

| Part | Port | Address |
|---|---|---|
| Database | `5432` | `localhost:5432` |
| Backend | `8080` | `http://localhost:8080` |
| Frontend | `4200` | `http://localhost:4200` |

**Failure behaviour**: if a port is occupied, startup fails with a message naming the port. It must
not silently pick another port — a frontend that quietly moves to 4201 breaks the documentation
FR-014 requires to be accurate.

## Commands

Each part starts with exactly one command (SC-002). One-time dependency installation is a
**prerequisite**, not a start command, and is excluded from that count per SC-002 — it runs once per
checkout, not on every start.

| Part | One-time setup | Required before |
|---|---|---|
| Database | none | — |
| Backend | none — the wrapper resolves dependencies on first run | — |
| Frontend | `npm install` (in `frontend/`) | first `npm start` or `npm test` |

| Part | Start | Stop | Test |
|---|---|---|---|
| Database | `docker compose up -d` | `docker compose down` | verification query, see quickstart |
| Backend | `backend/mvnw spring-boot:run` | `Ctrl+C` | `backend/mvnw test` |
| Frontend | `npm start` (in `frontend/`) | `Ctrl+C` | `npm test` (in `frontend/`) |

Two additional documented commands, neither part of normal operation:

| Command | Purpose |
|---|---|
| `docker compose down -v` | Destroys the volume. The documented reset, and the only way a changed init script takes effect. |
| `backend/mvnw test -Pverify-ai` | Opt-in Azure connectivity check. Makes exactly one real request. Never runs in the default suite. |

The backend ships the **Maven wrapper**, so `mvnw` works without a globally installed Maven even
though this machine has 3.9.16. The frontend uses plain `npm` scripts, so no global Angular CLI
install is required.

Commands above are written in their POSIX form. On the primary platform (Windows/PowerShell) the
wrapper is `backend\mvnw.cmd`; FR-027 governs how the setup documentation presents both forms, and
[quickstart.md](../quickstart.md) carries them.

## Startup independence (FR-013 / SC-005 / SC-009)

| Started alone | Result |
|---|---|
| Database only | Fully functional; accepts connections |
| Backend only | Starts and serves; health `503` with `db` `DOWN` |
| Frontend only | Starts and serves the placeholder page; no network calls, no console errors |

**With no Azure credentials in the environment at all** (SC-009): every row above is unchanged,
both test suites still pass, and the backend additionally reports `azureOpenAi: UNKNOWN` while
staying overall `UP`. Absence of AI credentials degrades nothing in this feature.

The backend is the only part whose observable output changes when a dependency is absent, and every
such change is specified rather than incidental — see [health-api.md](health-api.md).

## Configuration contract (FR-009, FR-018)

| Value | Source | Committed? |
|---|---|---|
| Database name, user | `.env`, template in `.env.example` | template only |
| Database password | `.env` | ❌ never |
| Backend datasource URL | `application.yml`, env vars with local defaults | non-secret defaults only |
| `AZURE_OPEN_AI_KEY` | environment | ❌ **never** |
| `AZURE_OPEN_AI_ENDPOINT` | environment | placeholder only |
| `AZURE_OPEN_AI_DEPLOYMENT_NAME` | environment | placeholder only |
| `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` | environment | placeholder only; **may be unset** (FR-023) |

Variable names are fixed by constitution v1.3.0 and MUST NOT be renamed. `.env` is git-ignored;
`.env.example` is committed and contains no real secret. Full property mapping and binding
semantics: [ai-provider.md](ai-provider.md).

Every AI variable binds with an empty default, so absence is a configuration state rather than a
startup failure (FR-019).

## Version-control hygiene (FR-015)

Excluded from git: `backend/target/`, `frontend/node_modules/`, `frontend/.angular/`,
`frontend/dist/`, `.env`, and IDE directories.

## Prerequisites the documentation must state (FR-014)

| Tool | Minimum | Verified present here |
|---|---|---|
| JDK | 17 | 17.0.12 |
| Node.js | `^20.19.0 \|\| ^22.12.0 \|\| >=24.0.0` | 22.22.2 |
| Docker with Compose V2+ | — | 29.6.2 / Compose v5.3.1 |
| Azure OpenAI credentials | **not required** | key, endpoint, chat deployment set; embedding deployment unset |

Maven is **not** a prerequisite — the wrapper covers it. The Node range is not a formality: Angular
21 rejects Node 22.0 through 22.11, so "Node 22" alone is not a sufficient instruction.

## Recommended start order

Database → backend → frontend. Any order works (that is what FR-013 guarantees); this order simply
gives a green health check on the first request rather than a 503 to re-check.

# Contract: Runtime Surface

**Feature**: [Project Scaffolding](../spec.md) | **Serves**: FR-001, FR-004, FR-005, FR-010, FR-013, FR-014 | **Date**: 2026-08-13

The commands, ports and startup guarantees each of the three parts must honour. This is the
contract the quickstart verifies and that FR-014's documentation must match.

## Ports

Fixed and documented, per FR-004/FR-005/FR-010. A single developer on one machine is assumed, so
no allocation strategy is needed.

| Part | Port | Address |
|---|---|---|
| Database | `5432` | `localhost:5432` |
| Backend | `8080` | `http://localhost:8080` |
| Frontend | `4200` | `http://localhost:4200` |

**Failure behaviour**: if a port is occupied, startup fails with a message naming the port. It
must not silently pick another port — a frontend that quietly moves to 4201 breaks the
documentation that FR-014 requires to be accurate.

## Commands

Each part starts with exactly one command (SC-002), run from the repository root or the part's own
directory as shown.

| Part | Start | Stop | Test |
|---|---|---|---|
| Database | `docker compose up -d` | `docker compose down` | verification query, see quickstart |
| Backend | `backend/mvnw spring-boot:run` | `Ctrl+C` | `backend/mvnw test` |
| Frontend | `npm start` (in `frontend/`) | `Ctrl+C` | `npm test` (in `frontend/`) |

`docker compose down -v` additionally destroys the volume. It is the documented reset, and the
only way a changed init script takes effect.

The backend ships the **Maven wrapper**, so `mvnw` works without a globally installed Maven even
though this machine has 3.9.16. The frontend uses plain `npm` scripts, so no global Angular CLI
install is required either.

## Startup independence (FR-013 / SC-005)

| Started alone | Result |
|---|---|
| Database only | Fully functional; accepts connections |
| Backend only | Starts and serves; `/actuator/health` reports `503` with `db` `DOWN` |
| Frontend only | Starts and serves the placeholder page; no network calls, no console errors |

The backend is the only part whose *observable output* changes when another part is absent, and
that change is specified rather than incidental — see [health-api.md](health-api.md) Case B.

## Configuration contract (FR-009)

| Value | Source | Committed? |
|---|---|---|
| Database name, user | `.env` with a default in `.env.example` | template only |
| Database password | `.env` | ❌ never |
| Backend datasource URL | `application.yml`, reading env vars with local defaults | non-secret defaults only |
| OpenAI API key | not used in this feature | ❌ not present |

`.env` is git-ignored; `.env.example` is committed and contains no real secret. The backend reads
configuration through environment variables so that the same build runs against a different
database without an edit.

## Version-control hygiene (FR-015)

Excluded from git: `backend/target/`, `frontend/node_modules/`, `frontend/.angular/`,
`frontend/dist/`, `.env`, and IDE directories.

## Prerequisites the documentation must state (FR-014)

| Tool | Minimum | Verified present here |
|---|---|---|
| JDK | 17 | 17.0.12 |
| Node.js | `^20.19.0 \|\| ^22.12.0 \|\| >=24.0.0` | 22.22.2 |
| Docker with Compose V2+ | — | 29.6.2 / Compose v5.3.1 |

Maven is **not** a prerequisite — the wrapper covers it. The Node range is not a formality:
Angular 21 rejects Node 22.0 through 22.11, so "Node 22" alone is not a sufficient instruction.

## Recommended start order

Database → backend → frontend. Any order works (that is what FR-013 guarantees); this order
simply gives a developer a green health check on the first request rather than a 503 they then
have to re-check.

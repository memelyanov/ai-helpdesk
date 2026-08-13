# Implementation Plan: Project Scaffolding

**Branch**: `main` (no feature branch created — no `before_specify` hook is registered) | **Date**: 2026-08-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-project-scaffolding/spec.md`

## Summary

Stand up a runnable, empty three-part skeleton: a containerised PostgreSQL database with the
`vector` extension enabled, a Spring Boot backend exposing a health check that reports database
reachability, and an Angular frontend serving a placeholder page. Each part starts with one
command, each ships a green test suite, and no PoC behaviour (upload, chunking, embedding,
retrieval, answer generation) is implemented.

The technical approach is deliberately minimal and driven by two constraints from the spec:
FR-007 (the backend must boot with the database down) and SC-005 (each part must start alone).
Together these rule out anything that opens a database connection during backend startup — so the
scaffold uses no JPA and no startup migrations. The `vector` extension is installed by the
container's own init script, and the backend talks to the database through a lazily-initialised
connection pool that only connects when the health check asks it to.

## Technical Context

All versions below were resolved against live registries on 2026-08-13 and against the toolchain
actually installed on this machine — none are assumed. See [research.md](research.md) for the
lookups and the reasoning.

**Language/Version**: Java 17 (installed: 17.0.12 LTS, `JAVA_HOME=C:\Program Files\Java\jdk-17\`);
TypeScript via the Angular 21 toolchain; Node.js 22.22.2 (satisfies Angular 21's
`^20.19.0 || ^22.12.0 || >=24.0.0` engine range)

**Primary Dependencies**: Spring Boot 3.5.16 (last 3.x release — the constitution mandates Spring
Boot 3); Spring Boot Actuator; `spring-boot-starter-jdbc`; PostgreSQL JDBC driver; Angular
21.2.x. **Spring AI is deliberately not added in this feature** — see the research decision on
deferring it.

**Storage**: PostgreSQL 18 via `pgvector/pgvector:pg18` (pgvector 0.8.6, image published
2026-07-29), with the `vector` extension created by an init script. Named Docker volume for
persistence.

**Testing**: JUnit 5 + Spring Boot Test (`spring-boot-starter-test`) for the backend, run with
`mvnw test`. Vitest for the frontend — confirmed as the Angular 21 `ng new` default
(`testRunner` default is `vitest`, alternative `karma`), run with `npm test`. Testcontainers is
**not** used in this feature; see research.

**Target Platform**: Local developer machine (Windows 11 here; the setup is OS-neutral).
Docker Desktop 29.6.2 with Compose v5.3.1 provides the database only.

**Project Type**: Web application — separate backend service and frontend SPA, plus container
infrastructure for the database.

**Performance Goals**: None for this feature beyond startup responsiveness. The PoC's ~5s
end-to-end answer target belongs to the retrieval and chat features, which do not exist yet.

**Constraints**:
- Backend MUST boot with the database unavailable (FR-007) — no eager DB access at startup.
- No OpenAI API key required to run anything in this feature.
- Fixed local ports: database `5432`, backend `8080`, frontend `4200`.
- No secrets in version control (FR-009).

**Scale/Scope**: Single developer, one machine, three processes. Roughly a dozen source files
across the two applications plus compose and init SQL.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` v1.2.1.

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` exists and was written before this plan; this plan precedes any code. FR-017 carries the README update, so living docs stay truthful. |
| II | Test-Driven Development (Mandatory) | ✅ PASS | Both test suites are scaffold deliverables, not follow-ups (FR-008, FR-012). Each part's first real test — health check, placeholder render — is written before its implementation. Contract tests for `/actuator/health` are specified in `contracts/`. |
| III | Grounded Answers (RAG-First) | ⏭️ N/A — deferred | No answer generation exists in this feature. Nothing here can violate or satisfy it. Binds from the chat feature onward. |
| IV | No Hallucination (Context Adherence) | ⏭️ N/A — deferred | Same as III — no LLM call is made. |
| V | Semantic Understanding | ⏭️ N/A — deferred | No retrieval. The scaffold does prepare for it: FR-002 requires the `vector` extension enabled and verifiable now, so the ingestion feature inherits a ready store. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ✅ PASS | The vector store is self-hosted PostgreSQL from the first commit. No managed vector service, no fine-tuning path introduced. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ⏭️ N/A — deferred | No retrieval to measure. The evaluation set already exists in `sample-data/`; this feature does not run it. |

**Technology Stack compliance**: Java 17 ✅, Spring Boot 3 ✅ (3.5.16), PostgreSQL + pgvector ✅,
Angular 21 ✅ (21.2.x), Docker Compose for the database only ✅. Spring AI, OpenAI models and
Apache Tika are mandated by the constitution but belong to features that use them; omitting them
from a scaffold is scope, not deviation — and adding the OpenAI starter now would actively break
FR-007 by requiring an API key at startup (see research).

**A note on newer majors**: Spring Boot 4.1.0 and Spring AI 2.0.0 are released and available; the
latest Angular is 22.1.4. This plan stays on Spring Boot 3.5.16, Spring AI 1.x (when introduced)
and Angular 21 because the constitution's Technology Stack table names those versions and freezes
them: *"No dependency is eligible for replacement without a constitution amendment."* Moving to
Spring Boot 4 / Spring AI 2 / Angular 22 is a defensible choice, but it is an amendment decision,
not a planning decision. Flagged for the user in research.md.

**Post-Phase 1 re-check**: ✅ No change. The design introduces no new dependency beyond those
listed, adds no data model that outlives this feature (the `vector` extension is required by
Principle V's future needs), and the deferred principles remain untouched rather than pre-empted.

**Gate result**: PASS — no violations, no justifications required.

## Project Structure

### Documentation (this feature)

```text
specs/001-project-scaffolding/
├── plan.md              # This file
├── research.md          # Phase 0 output — version resolution and design decisions
├── data-model.md        # Phase 1 output — database initialisation state
├── quickstart.md        # Phase 1 output — how to run and verify all three parts
├── contracts/
│   ├── health-api.md    # Backend health check contract
│   └── runtime-surface.md  # Ports, commands and startup contract for all three parts
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # Phase 2 output — created by /speckit-tasks, NOT by this command
```

### Source Code (repository root)

```text
docker-compose.yml            # Database service only (backend and frontend run on the host)
.env.example                  # Local DB credentials template; .env is git-ignored

db/
└── init/
    └── 01-init-vector.sql    # CREATE EXTENSION IF NOT EXISTS vector

backend/
├── pom.xml                   # Spring Boot 3.5.16, Java 17
├── mvnw / mvnw.cmd           # Maven wrapper — no global Maven required
└── src/
    ├── main/
    │   ├── java/com/epam/aihelpdesk/
    │   │   └── AiHelpdeskApplication.java
    │   └── resources/
    │       └── application.yml     # Datasource + actuator config, env-var driven
    └── test/
        └── java/com/epam/aihelpdesk/
            ├── AiHelpdeskApplicationTests.java   # Context loads
            └── HealthEndpointTest.java           # Health contract test

frontend/
├── package.json              # Angular 21.2.x, Vitest
├── angular.json
└── src/
    ├── index.html
    ├── main.ts
    └── app/
        ├── app.ts            # Placeholder component
        ├── app.html
        └── app.spec.ts       # Placeholder render test
```

**Structure Decision**: Web application layout — `backend/` and `frontend/` as sibling roots at
the repository root, with `docker-compose.yml` and `db/init/` alongside them. This matches the
spec's requirement that each part start independently (FR-013): each directory owns its own build
tooling, dependency manifest and test command, and nothing in one directory is needed to build
another. The existing `docs/`, `sample-data/` and `specs/` directories are untouched.

The backend package is `com.epam.aihelpdesk` under a single module — no `models/services/api`
subdivision yet, because this feature creates no models and no services. Those directories arrive
with the features that need them rather than as empty placeholders.

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty.

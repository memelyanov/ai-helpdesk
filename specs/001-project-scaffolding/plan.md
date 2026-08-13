# Implementation Plan: Project Scaffolding

**Branch**: `main` (no feature branch created — no `before_specify` hook is registered) | **Date**: 2026-08-13 (revised) | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-project-scaffolding/spec.md`

> **Revision note**: regenerated after the 2026-08-13 clarification session (Azure OpenAI) and
> constitution **v1.3.0**, which ratified the provider switch. The previous revision excluded
> Spring AI from this feature; that decision is reversed and re-argued in
> [research.md](research.md) Decision 2.

## Summary

Stand up a runnable, empty three-part skeleton: a containerised PostgreSQL database with the
`vector` extension enabled, a Spring Boot backend that exposes a health check reporting database
reachability and Azure OpenAI configuration status, and an Angular frontend serving a placeholder
page. Each part starts with one command, each ships a green test suite, and no PoC behaviour
(upload, chunking, embedding, retrieval, answer generation) is implemented.

Three requirements shape the technical approach more than anything else:

- **FR-007** — the backend boots with the database down. This rules out JPA and startup
  migrations, so the `vector` extension is installed by the container's own init script and the
  backend uses a lazily-initialised JDBC pool.
- **FR-019** — the backend boots with Azure credentials absent. Verified against the artifact:
  Spring AI's Azure auto-configuration builds an `OpenAIClientBuilder` from key and endpoint and
  fails startup without them. The starter is therefore added with its auto-configuration gate
  (`spring.ai.model.chat` / `spring.ai.model.embedding`) defaulted to `none`.
- **FR-020/FR-022** — configuration status is reported for free; connectivity is proven
  deliberately. Health reports whether Azure is configured without any network call; a single
  real request lives in an opt-in Maven profile.

The result: a developer with no database and no Azure credentials can still start all three parts
and run both suites green.

## Technical Context

All versions and property names were resolved against live registries and downloaded artifacts on
2026-08-13, and against the toolchain installed on this machine. See [research.md](research.md).

**Language/Version**: Java 17 (installed: 17.0.12 LTS); TypeScript via the Angular 21 toolchain;
Node.js 22.22.2 (satisfies Angular 21's `^20.19.0 || ^22.12.0 || >=24.0.0`)

**Primary Dependencies**: Spring Boot 3.5.16 (last 3.x — the constitution mandates Spring Boot 3);
`spring-boot-starter-web`, `spring-boot-starter-actuator`, `spring-boot-starter-jdbc`;
`org.postgresql:postgresql`; `spring-ai-bom:1.1.8` managing
`spring-ai-starter-model-azure-openai:1.1.8` (present but inert by default); Angular 21.2.x

**Storage**: PostgreSQL 18 via `pgvector/pgvector:pg18` (pgvector 0.8.6, published 2026-07-29),
`vector` extension created by an init script, named Docker volume for persistence

**AI Provider**: Azure OpenAI. Four environment variables bound to Spring AI's canonical property
names (mapping verified against `spring-configuration-metadata.json`, see research Decision 3).
No model bean is created at runtime in this feature.

**Testing**: JUnit 5 + Spring Boot Test, run with `backend/mvnw test`; the live Azure check is a
`@Tag("azure")` test excluded by default and run via `backend/mvnw test -Pverify-ai`. Vitest for
the frontend — confirmed as the Angular 21 `ng new` default — run with `npm test`. No
Testcontainers, no live external calls in the default suites.

**Target Platform**: Local developer machine (Windows 11 here; the setup is OS-neutral). Docker
Desktop 29.6.2 with Compose v5.3.1 provides the database only.

**Project Type**: Web application — separate backend service and frontend SPA, plus container
infrastructure for the database.

**Performance Goals**: None beyond startup responsiveness. The PoC's ~5s answer target belongs to
the retrieval and chat features. The health endpoint must stay free of network I/O to the AI
provider (FR-020), which is a design constraint rather than a latency target.

**Constraints**:
- Backend MUST boot with the database unavailable (FR-007) and with AI credentials absent (FR-019)
- Health MUST NOT contact Azure (FR-020); an unconfigured provider MUST NOT change overall status
- Exactly one Azure request from the opt-in verification (SC-008)
- Environment variable names are fixed by the constitution and MUST NOT be renamed (FR-018)
- Fixed local ports: database `5432`, backend `8080`, frontend `4200`
- No secrets in version control (FR-009)

**Scale/Scope**: Single developer, one machine, three processes. Roughly fifteen source files
across the two applications plus compose, init SQL and environment templates.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.3.0**.

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` precedes this plan; clarifications are recorded in it; the constitution was amended *before* planning rather than after. FR-017 keeps the README truthful. |
| II | Test-Driven Development (Mandatory) | ✅ PASS | Both suites are scaffold deliverables (FR-008, FR-012). v1.3.0's new clause — tests MUST NOT require live AI credentials — is satisfied by design: the live call is isolated in the opt-in `verify-ai` profile (research Decision 11). |
| III | Grounded Answers (RAG-First) | ⏭️ N/A — deferred | No answer generation. Nothing here can violate or satisfy it. |
| IV | No Hallucination (Context Adherence) | ⏭️ N/A — deferred | No LLM call serves a user; the single verification request has no user-facing output. |
| V | Semantic Understanding | ⏭️ N/A — deferred | No retrieval. The scaffold prepares for it: `vector` is enabled now (FR-002) and the embedding deployment name is bound now (FR-023), so ingestion inherits both. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ✅ PASS | Vector store is self-hosted PostgreSQL from the first commit. Inference is Azure OpenAI as v1.3.0 requires. No fine-tuning path introduced. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ⏭️ N/A — deferred | No retrieval to measure. |

**AI Provider Configuration compliance** (new in v1.3.0): all four mandated variable names used
verbatim and not renamed ✅; chat and embedding treated as two distinct deployments ✅; models
addressed by deployment name, never by hardcoded model identifier ✅; application starts when
variables are absent or partial, reporting unconfigured ✅ (FR-019/FR-021); `.env.example`
committed with non-secret placeholders, no key committed ✅.

**Technology Stack compliance**: Java 17 ✅, Spring Boot 3 ✅ (3.5.16), Spring AI ✅ (1.1.8),
Azure OpenAI ✅, PostgreSQL + pgvector ✅, Angular 21 ✅ (21.2.x), Docker Compose for the database
only ✅. Apache Tika is mandated but belongs to the ingestion feature that parses documents;
omitting it from a scaffold that parses nothing is scope, not deviation.

**Governance note**: v1.3.0 amended the provider row but left Spring Boot 3 and Angular 21 in
place, while Spring Boot 4.1.0, Spring AI 2.0.0 and Angular 22.1.4 are all released. This plan
complies with the constitution as written. The case for a second amendment is in research
Decisions 1 and 10 and is the user's to make.

**Post-Phase 1 re-check**: ✅ No change. The design adds one dependency (the Spring AI starter,
deliberately inert), creates no persistent data model, introduces no runtime AI bean, and leaves
the deferred principles untouched rather than pre-empted.

**Gate result**: PASS — no violations, no justifications required.

## Project Structure

### Documentation (this feature)

```text
specs/001-project-scaffolding/
├── plan.md              # This file
├── research.md          # Phase 0 — 11 decisions, versions and property names verified
├── data-model.md        # Phase 1 — database init state and bound configuration
├── quickstart.md        # Phase 1 — how to run and verify all three parts
├── contracts/
│   ├── health-api.md       # Health endpoint: db and azureOpenAi components
│   ├── ai-provider.md      # Configuration binding and the on-demand verification
│   └── runtime-surface.md  # Ports, commands, startup guarantees
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 — created by /speckit-tasks, NOT by this command
```

### Source Code (repository root)

```text
docker-compose.yml            # Database service only (backend and frontend run on the host)
.env.example                  # DB credentials + the four Azure variables; .env is git-ignored

db/
└── init/
    └── 01-init-vector.sql    # CREATE EXTENSION IF NOT EXISTS vector

backend/
├── pom.xml                   # Spring Boot 3.5.16, Java 17, spring-ai-bom 1.1.8,
│                             # surefire excludes the "azure" tag; verify-ai profile selects it
├── mvnw / mvnw.cmd           # Maven wrapper — no global Maven required
└── src/
    ├── main/
    │   ├── java/com/epam/aihelpdesk/
    │   │   ├── AiHelpdeskApplication.java
    │   │   └── health/
    │   │       ├── AzureOpenAiProperties.java        # binds the four values
    │   │       └── AzureOpenAiConfigHealthIndicator.java  # UP / UNKNOWN, no network I/O
    │   └── resources/
    │       └── application.yml   # datasource, actuator, Azure binding,
    │                             # spring.ai.model.chat/embedding = none
    └── test/
        └── java/com/epam/aihelpdesk/
            ├── AiHelpdeskApplicationTests.java        # context loads, no DB, no credentials
            ├── HealthEndpointTest.java                # db UP and DOWN cases
            ├── AzureOpenAiConfigHealthIndicatorTest.java  # complete / absent / partial
            └── AzureOpenAiConnectivityIT.java         # @Tag("azure") — opt-in, one real call

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

**Structure Decision**: Web application layout — `backend/` and `frontend/` as sibling roots at the
repository root, with `docker-compose.yml` and `db/init/` alongside them. This matches FR-013:
each directory owns its build tooling, dependency manifest and test command, and nothing in one is
needed to build another. The existing `docs/`, `sample-data/` and `specs/` directories are
untouched.

The backend uses a single module under `com.epam.aihelpdesk`, with one `health` package rather
than a full `models/services/api` subdivision — this feature creates no models and no services.
Those directories arrive with the features that need them rather than as empty placeholders.

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty.

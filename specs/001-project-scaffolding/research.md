# Phase 0 Research: Project Scaffolding

**Date**: 2026-08-13 | **Plan**: [plan.md](plan.md)

Every version below was resolved against a live registry or the local toolchain on 2026-08-13.
Nothing here is recalled from training data.

## Installed toolchain (verified on this machine)

| Tool | Version found | Verdict |
|---|---|---|
| Java | 17.0.12 LTS, `JAVA_HOME=C:\Program Files\Java\jdk-17\` | ✅ Matches the mandated Java 17 |
| Maven | 3.9.16 | ✅ Present, but the project will still ship a wrapper |
| Node.js | 22.22.2 | ✅ Satisfies Angular 21's engine range `^20.19.0 \|\| ^22.12.0 \|\| >=24.0.0` |
| npm | 10.9.7 | ✅ Satisfies `>=8.0.0` |
| Docker | 29.6.2 | ✅ |
| Docker Compose | v5.3.1 | ✅ Compose V2+ syntax; the obsolete top-level `version:` key must be omitted |

No tooling needs to be installed. Node 22.22.2 is comfortably above the `^22.12.0` floor, so no
Node upgrade is required — worth stating because Angular 21 rejects Node 22.0–22.11.

## Decision 1: Stay on Spring Boot 3.5.16, not Spring Boot 4

- **Decision**: `spring-boot-starter-parent:3.5.16` — the newest 3.x release.
- **Rationale**: The constitution's Technology Stack table mandates "Java 17, Spring Boot 3" and
  states that no dependency may be replaced without a constitution amendment. 3.5.16 is the most
  current release that honours that.
- **Alternatives considered**: Spring Boot **4.1.0** is released and its Java baseline is 17
  (verified in its POM: `java.version = 17`), so it would run on this machine. It was rejected
  **only** because adopting it is a governance decision, not a planning one.
- **⚠️ Flagged for the user**: Spring Boot 3.5.x will reach end of open-source support well
  before this PoC would reach production. If the intent is to build on a current baseline, amend
  the constitution's stack table to Spring Boot 4 *now*, while the codebase is empty and the
  migration costs nothing. Doing it after ingestion and retrieval exist will cost considerably
  more. This plan does not make that call.

## Decision 2: Do not add Spring AI in this feature

- **Decision**: The scaffold's `pom.xml` contains no `spring-ai-*` dependency.
- **Rationale**: Two reasons, one hard and one soft.
  1. **Hard**: `spring-ai-starter-model-openai` auto-configuration requires
     `spring.ai.openai.api-key` and fails application startup when it is absent. That directly
     violates FR-007 (backend boots with dependencies unavailable) and the spec's assumption that
     no API key is needed to run the scaffold.
  2. **Soft**: FR-016 excludes all PoC behaviour. A dependency that is present but unused is
     dead weight that still has to be version-managed.
- **When it arrives**: the ingestion feature. Artifact names were verified to exist on Maven
  Central so the later feature does not have to guess them:
  `org.springframework.ai:spring-ai-starter-model-openai` and
  `org.springframework.ai:spring-ai-starter-vector-store-pgvector`.
- **Version to use then**: Spring AI **1.1.8** (newest 1.x, the line that pairs with Spring Boot
  3.5). Spring AI **2.0.0** exists but targets the Spring Boot 4 generation — so Decision 1 and
  this one move together, or not at all.
- **Alternatives considered**: adding the starters now with a dummy API key. Rejected — it makes
  startup depend on a fake secret and would be a lie in `application.yml`.

## Decision 3: Enable `vector` via a container init script, not a startup migration

- **Decision**: `db/init/01-init-vector.sql` runs `CREATE EXTENSION IF NOT EXISTS vector;`,
  mounted into the image's `/docker-entrypoint-initdb.d/`. No Flyway or Liquibase in this feature.
- **Rationale**: FR-007 requires the backend to start when the database is down. Flyway runs
  during application startup and fails hard on an unreachable database, which would make FR-007
  unachievable without disabling Flyway anyway. Putting extension setup in the database's own
  bootstrap keeps the requirement satisfiable and puts the responsibility where it belongs — the
  extension is a property of the database, not of the application.
- **Consequence to know about**: `/docker-entrypoint-initdb.d/` scripts run **only when the data
  directory is empty**. Editing the init script later has no effect until the volume is removed.
  The quickstart documents the volume-reset command for exactly this reason, and it is why the
  spec lists "a stale database volume exists" as an edge case.
- **Alternatives considered**: Flyway with `spring.flyway.enabled` toggled off locally (rejected —
  a config flag that must be wrong for the app to boot is a trap); a custom entrypoint (rejected —
  the official image already supports init scripts).
- **Migrations later**: when the ingestion feature adds tables, Flyway becomes appropriate, and
  FR-007 can be revisited then as a deliberate trade rather than an accident.

## Decision 4: No JPA in the scaffold — plain JDBC starter only

- **Decision**: `spring-boot-starter-jdbc` + `org.postgresql:postgresql`, not
  `spring-boot-starter-data-jpa`.
- **Rationale**: JPA triggers Hibernate schema validation during startup, which opens a
  connection and fails when the database is down — again breaking FR-007. The JDBC starter
  creates a HikariCP pool that initialises lazily on first `getConnection()`, so the application
  context starts cleanly with no database present and only touches it when the health indicator
  probes it. This is precisely the behaviour acceptance scenario US2-3 describes.
- **Alternatives considered**: JPA with `spring.jpa.hibernate.ddl-auto=none` and deferred
  datasource initialisation. Rejected as more configuration to get the same result, in a feature
  that persists no entities at all.

## Decision 5: Health check via Actuator, with details exposed

- **Decision**: `spring-boot-starter-actuator`, exposing `/actuator/health` with
  `management.endpoint.health.show-details: always` and the built-in `db` health indicator.
- **Rationale**: FR-006 asks for a health check reporting both service status and database
  reachability. Actuator's `DataSourceHealthIndicator` already does the database half, and
  `show-details: always` is what makes the database component *visible* rather than collapsed
  into a bare status. Writing a custom endpoint would reimplement this with no gain.
- **Response semantics** (documented in [contracts/health-api.md](contracts/health-api.md)):
  database up → HTTP 200 `{"status":"UP"}` with a `db` component `UP`; database down → HTTP 503
  with overall `DOWN` and a `db` component `DOWN` carrying the connection error. The 503 is
  correct and intentional — the service honestly reports that it cannot serve its purpose — and
  the contract test asserts both cases.
- **Security note**: `show-details: always` leaks connection error text to unauthenticated
  callers. Acceptable here because the PoC is explicitly local-only and unauthenticated; it must
  be revisited before any deployment, and is recorded as such in the contract.

## Decision 6: pgvector image `pgvector/pgvector:pg18`

- **Decision**: pin `pgvector/pgvector:pg18` (pgvector 0.8.6 on PostgreSQL 18).
- **Rationale**: Verified as a current tag on Docker Hub, published 2026-07-29. The official
  pgvector image ships the extension already compiled, so the init script only has to `CREATE
  EXTENSION` — no build step, no custom Dockerfile.
- **Alternatives considered**: `pg17` (equally current, one major behind — no reason to prefer it
  for a greenfield PoC); the plain `postgres` image plus a build step to compile pgvector
  (rejected — slower, more moving parts, no benefit); the fully-pinned `0.8.6-pg18` tag. The
  latter is the stricter choice and worth adopting if reproducibility matters more than picking
  up patch updates.

## Decision 7: Angular 21.2.x with Vitest

- **Decision**: `ng new` from the Angular **21.2.x** CLI, keeping the CLI's own defaults.
- **Rationale**: The constitution mandates Angular 21. Latest 21 line verified as
  `@angular/core@21.2.20`, `@angular/cli@21.2.21`.
- **Test runner — verified, not assumed**: the Angular 21 `ng-new` schema was downloaded and
  inspected. `testRunner` has **default `vitest`** with `karma` as the alternative. So a default
  `ng new` on Angular 21 produces a Vitest project, and `npm test` is the command. The plan does
  not override this — accepting the framework default is the lowest-maintenance choice and it is
  what the CLI's own documentation and future schematics assume.
- **Other schema defaults noticed**: `fileNameStyleGuide` defaults to `2025` (so components
  generate as `app.ts` / `app.html` / `app.spec.ts`, not the older `app.component.ts`), and
  `zoneless` is an available option. The project structure in the plan reflects the 2025 naming.
- **⚠️ Flagged for the user**: Angular **22.1.4** is the current release. The same governance
  point as Decision 1 applies — staying on 21 is a constitution obligation, not a technical
  preference, and if both are to be updated it is cheapest to do it in the same amendment now.

## Decision 8: No Testcontainers in this feature

- **Decision**: Backend tests run against no database. The health contract test covers the
  database-down path with the real indicator, and the database-up path via a mocked/overridden
  datasource health contribution.
- **Rationale**: Testcontainers would make the backend test suite require a running Docker daemon,
  which contradicts SC-005 (each part verifiable alone) and SC-003 (a clean checkout runs the
  suite green). The database's own behaviour is verified through the quickstart's manual checks in
  US1, not through the backend suite.
- **When it arrives**: the ingestion feature, where real SQL against a real pgvector instance is
  the thing under test. That is the right moment to accept a Docker dependency in the test suite.

## Open questions

None. No `NEEDS CLARIFICATION` markers were carried in from the spec, and none arose here.

Two items are **flagged for user decision but do not block implementation** — the scaffold works
either way, and both are constitution amendments rather than plan changes:

1. Spring Boot 3.5.16 + Spring AI 1.1.8 versus Spring Boot 4.1.0 + Spring AI 2.0.0 (Decisions 1, 2)
2. Angular 21.2.x versus Angular 22.1.4 (Decision 7)

The empty codebase is the cheapest possible moment to change either answer.

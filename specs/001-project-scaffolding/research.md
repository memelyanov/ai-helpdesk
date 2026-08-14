# Phase 0 Research: Project Scaffolding

**Date**: 2026-08-13 (revised) | **Plan**: [plan.md](plan.md)

Every version and property name below was resolved against a live registry, a downloaded artifact,
or the local toolchain on 2026-08-13. Nothing here is recalled from training data.

> **Revision note**: this document was regenerated after `/speckit-clarify` selected Azure OpenAI
> and constitution v1.3.0 ratified it. The previous revision's Decision 2 said "do not add Spring
> AI in this feature" — that is now **reversed**, and the reasoning behind the reversal is set out
> in Decision 2 below.

## Installed toolchain (verified on this machine)

| Tool | Version found | Verdict |
|---|---|---|
| Java | 17.0.12 LTS, `JAVA_HOME=C:\Program Files\Java\jdk-17\` | ✅ Matches the mandated Java 17 |
| Maven | 3.9.16 | ✅ Present; the project still ships a wrapper |
| Node.js | 22.22.2 | ✅ Satisfies Angular 21's `^20.19.0 \|\| ^22.12.0 \|\| >=24.0.0` |
| npm | 10.9.7 | ✅ Satisfies `>=8.0.0` |
| Docker | 29.6.2 | ✅ |
| Docker Compose | v5.3.1 | ✅ Compose V2+; omit the obsolete top-level `version:` key |

Angular 21 rejects Node 22.0–22.11, so "Node 22" alone is not a sufficient prerequisite to
document. 22.22.2 is comfortably above the floor.

## Environment variables (verified present)

| Variable | State on this machine |
|---|---|
| `AZURE_OPEN_AI_KEY` | ✅ set (32 chars) |
| `AZURE_OPEN_AI_ENDPOINT` | ✅ set (29 chars) |
| `AZURE_OPEN_AI_DEPLOYMENT_NAME` | ✅ set (6 chars) |
| `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` | ❌ **not set** |
| `OPENAI_API_KEY` | ❌ not set |

Two consequences. The direct-OpenAI path the constitution previously mandated was not runnable
here at all, which is part of why the amendment was the right call. And the embedding deployment
does not exist yet — harmless for this feature (FR-023 permits it unset) but a hard blocker for
ingestion, so it is recorded as a follow-up rather than discovered later.

## Decision 1: Stay on Spring Boot 3.5.16, not Spring Boot 4

- **Decision**: `spring-boot-starter-parent:3.5.16` — the newest 3.x release.
- **Rationale**: The constitution mandates "Java 17, Spring Boot 3" and forbids substituting a
  dependency without an amendment. 3.5.16 is the most current release honouring that.
- **Alternatives considered**: Spring Boot **4.1.0** is released and its Java baseline is 17
  (verified in its POM), so it would run here. Rejected only because adopting it is a governance
  decision.
- **⚠️ Still flagged**: v1.3.0 amended the provider but left the Spring Boot 3 / Angular 21 rows
  untouched. Spring Boot 3.5.x heads for end of support well before this reaches production, and
  the codebase is still empty — the cheapest possible moment to move. Unchanged from the previous
  revision; raising it again because the amendment opportunity was there and was not taken.

## Decision 2: Add the Spring AI Azure OpenAI starter — with auto-configuration off by default

**This reverses the previous revision, which excluded Spring AI entirely.**

- **Decision**: add `spring-ai-starter-model-azure-openai:1.1.8`, managed by
  `spring-ai-bom:1.1.8`, and set `spring.ai.model.chat=none` / `spring.ai.model.embedding=none` /
  `spring.ai.model.image=none` / `spring.ai.model.audio.transcription=none` as the shipped defaults
  — **all four**, not just the two this feature discusses; see the implementation note below and
  [ai-provider.md](contracts/ai-provider.md) for why the other two are equally load-bearing.
- **Why the reversal**: the previous revision excluded Spring AI on the grounds that the OpenAI
  starter fails startup without an API key. The clarification session changed the requirement —
  FR-018 now mandates binding provider configuration, and FR-022 mandates an on-demand credential
  check. The old rationale addressed a requirement that no longer exists.
- **Why auto-config must be disabled anyway** — the original concern was real, and I verified it
  rather than assuming. Extracting `spring-ai-autoconfigure-model-azure-openai-1.1.8.jar` shows
  `AzureOpenAiChatAutoConfiguration` carries `@ConditionalOnProperty` on **`spring.ai.model.chat`**
  with value **`azure-openai`**, and delegates client construction to
  `AzureOpenAiClientBuilderConfiguration`, which builds an `OpenAIClientBuilder` from the key and
  endpoint. With auto-config active and credentials absent, that bean fails and **the application
  does not start** — a direct FR-019 violation. Defaulting the gate to `none` keeps the starter on
  the classpath and inert.
- **Consequence**: no `ChatModel` or `EmbeddingModel` bean exists at runtime in this feature. The
  verification (Decision 5) constructs what it needs explicitly. The ingestion feature turns the
  starter on by flipping two properties — no dependency change, which is the payoff for including
  the starter now rather than the bare library.
- **Alternatives considered**:
  - `spring-ai-azure-openai` (the library alone, no auto-config jar) — equally safe, but makes
    ingestion a dependency change rather than a property change. Verified to exist as a separate
    artifact at 1.1.8, alongside the autoconfigure and starter artifacts.
  - Starter with auto-config left on, plus dummy credentials — rejected: it makes startup depend
    on a fake secret and puts a lie in `application.yml`.
  - An `EnvironmentPostProcessor` flipping the gate based on whether credentials are present —
    rejected as machinery serving no requirement; nothing in this feature needs the beans.
- **Version**: Spring AI **1.1.8** is the newest 1.x, the line pairing with Spring Boot 3.5.
  Spring AI 2.0.0 targets the Spring Boot 4 generation, so Decisions 1 and 2 move together or not
  at all.

## Decision 3: Configuration property mapping (verified against the artifact)

- **Decision**: bind the four environment variables to Spring AI's own property names, each with
  an empty default so absence is not a startup failure:

  | Environment variable | Spring property |
  |---|---|
  | `AZURE_OPEN_AI_KEY` | `spring.ai.azure.openai.api-key` |
  | `AZURE_OPEN_AI_ENDPOINT` | `spring.ai.azure.openai.endpoint` |
  | `AZURE_OPEN_AI_DEPLOYMENT_NAME` | `spring.ai.azure.openai.chat.options.deployment-name` |
  | `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` | `spring.ai.azure.openai.embedding.options.deployment-name` |

- **Rationale**: all four property names were read from
  `META-INF/spring-configuration-metadata.json` inside the autoconfigure jar — they are not
  guesses. Binding to Spring AI's canonical names means the ingestion feature inherits working
  configuration the moment it enables auto-config, with nothing to rename.
- **Constitutional constraint honoured**: the environment variable names are fixed by the
  constitution's AI Provider Configuration table and MUST NOT be renamed. The indirection lives on
  the Spring side, which is where it belongs.
- **Alternatives considered**: a bespoke `@ConfigurationProperties` record with our own names
  (rejected — guarantees a rename when ingestion switches to Spring AI's beans).

## Decision 4: Configuration health without a network call

- **Decision**: a custom health indicator contributing an `azureOpenAi` component that reports
  **UP** when key, endpoint and chat deployment name are all non-blank, and **UNKNOWN** when the
  configuration is absent or partial. It performs no network I/O.
- **Rationale**: FR-020 requires configuration status to be reported without contacting the
  provider and without dragging overall health down. Spring Boot's default status aggregation
  orders severity `DOWN, OUT_OF_SERVICE, UP, UNKNOWN` and takes the most severe present, so an
  `UNKNOWN` component alongside an `UP` database yields an overall **UP** and HTTP **200** — only
  `DOWN` and `OUT_OF_SERVICE` map to 503. `UNKNOWN` is therefore the precise status for "not
  configured, and that is not an error here".
- **Why not DOWN for unconfigured**: it would make the whole service report 503 for any developer
  without Azure credentials, contradicting SC-009 and the spec's promise that the scaffold is
  fully usable without AI access.
- **Partial configuration reports UNKNOWN, not UP** — FR-021. An endpoint without a key is not a
  working configuration, and reporting it as configured would hide the fault until ingestion.
- **Alternatives considered**: a live call inside the health endpoint (rejected — every poll costs
  a billed request and inherits Azure's latency); a cached live call (rejected — a cache to reason
  about, and stale results across an outage, for no requirement).

## Decision 5: On-demand verification as a tagged test behind a Maven profile

- **Decision**: a JUnit test tagged `@Tag("azure")`, excluded from the default Surefire run, run
  explicitly with `backend/mvnw test -Pverify-ai`. It constructs an `AzureOpenAiChatModel` from
  the bound properties, issues **one** minimal completion against the chat deployment, and reports
  success or the provider's own error. It also asserts the embedding deployment name is present,
  reporting it as missing when unset (FR-023).
- **Rationale**: FR-022 requires an explicitly invoked check that never runs during startup, the
  health check, or the default test suite. A tagged test satisfies all three exclusions with no
  new runtime surface — no extra endpoint to secure, no profile-activated runner to accidentally
  ship. It also exercises the exact Spring AI code path ingestion will use, so a pass here is
  evidence about the real integration rather than about a bespoke HTTP call.
- **Why a Maven profile rather than `-Dgroups=azure`**: the default build sets
  `<excludedGroups>azure</excludedGroups>`, so a bare `-Dgroups` would still be filtered out. A
  `verify-ai` profile that clears the exclusion and selects the tag gives one clean documented
  command, satisfying SC-008's "one command".
- **Cost**: exactly one completion request, with a minimal prompt and a low token cap (SC-008).
- **Alternatives considered**: a dedicated actuator endpoint (rejected — a permanently exposed,
  unauthenticated, billable endpoint in a PoC with no auth); a `CommandLineRunner` behind a
  profile (rejected — a runner that fires on startup under the wrong profile is exactly the
  accident FR-022 forbids).

## Decision 6: Enable `vector` via a container init script, not a startup migration

- **Decision**: `db/init/01-init-vector.sql` runs `CREATE EXTENSION IF NOT EXISTS vector;`, mounted
  into `/docker-entrypoint-initdb.d/`. No Flyway or Liquibase in this feature.
- **Rationale**: FR-007 requires the backend to start with the database down. Flyway runs during
  startup and fails hard on an unreachable database. Extension setup is a property of the
  database, not of the application, so it belongs in the database's own bootstrap.
- **Consequence to know about**: init scripts run **only when the data directory is empty**.
  Editing the script later has no effect until the volume is removed — which is why the quickstart
  documents `down -v` and why the spec lists a stale volume as an edge case.
- **Alternatives considered**: Flyway disabled by a local flag (rejected — a config flag that must
  be wrong for the app to boot is a trap); a custom entrypoint (rejected — the official image
  already supports init scripts).

## Decision 7: No JPA in the scaffold — plain JDBC starter only

- **Decision**: `spring-boot-starter-jdbc` + `org.postgresql:postgresql`, not
  `spring-boot-starter-data-jpa`.
- **Rationale**: JPA triggers Hibernate schema validation during startup, which opens a connection
  and fails when the database is down — breaking FR-007. The JDBC starter's HikariCP pool
  initialises lazily on first `getConnection()`, so the context starts cleanly with no database
  and only touches it when the health indicator probes. That is exactly acceptance scenario US2-3.
- **Alternatives considered**: JPA with `ddl-auto=none` plus deferred datasource initialisation —
  more configuration for the same result, in a feature that persists no entities.

## Decision 8: Database health via Actuator, with details exposed

- **Decision**: `spring-boot-starter-actuator`, `/actuator/health`,
  `management.endpoint.health.show-details: always`, built-in `db` indicator, and only `health`
  exposed over HTTP.
- **Rationale**: Actuator's `DataSourceHealthIndicator` already implements the database half of
  FR-006; `show-details: always` is what makes components visible rather than collapsed into a
  bare status. A custom endpoint would reimplement this for nothing.
- **Semantics**: database up → 200 `UP`; database down → 503 `DOWN` with the connection error.
  Contract and both assertions: [contracts/health-api.md](contracts/health-api.md).
- **Security note**: `show-details: always` exposes connection error text and configuration status
  to unauthenticated callers. Acceptable for an explicitly local, unauthenticated PoC; recorded in
  the contract as something that must change before any deployment.

## Decision 9: pgvector image `pgvector/pgvector:pg18`

- **Decision**: pin `pgvector/pgvector:pg18` (pgvector 0.8.6 on PostgreSQL 18).
- **Rationale**: verified as a current Docker Hub tag, published 2026-07-29. The image ships the
  extension pre-compiled, so the init script only has to `CREATE EXTENSION` — no build step.
- **Alternatives considered**: `pg17` (one major behind, no reason to prefer for greenfield); the
  plain `postgres` image plus a compile step (slower, more moving parts); the fully-pinned
  `0.8.6-pg18` tag — stricter, and worth adopting if exact reproducibility outranks picking up
  patch updates.

## Decision 10: Angular 21.2.x with Vitest

- **Decision**: `ng new` from the Angular **21.2.x** CLI, keeping the CLI's defaults.
- **Rationale**: the constitution mandates Angular 21. Latest 21 line verified as
  `@angular/core@21.2.20`, `@angular/cli@21.2.21`.
- **Test runner — verified, not assumed**: the Angular 21 `ng-new` schema was downloaded and
  inspected. `testRunner` defaults to **`vitest`** (alternative `karma`), so a default `ng new`
  produces a Vitest project and `npm test` is the command. Not overridden — accepting the
  framework default is the lowest-maintenance choice.
- **Other observed defaults**: `fileNameStyleGuide` defaults to `2025`, so components generate as
  `app.ts` / `app.html` / `app.spec.ts` rather than the older `app.component.ts`. The plan's
  structure reflects this. `zoneless` is available as an option.
- **⚠️ Still flagged**: Angular **22.1.4** is current. Same governance point as Decision 1.

## Decision 11: No Testcontainers, and no live Azure call, in the default test suite

- **Decision**: the default backend suite runs against no database and contacts no external
  service.
- **Rationale**: Testcontainers would make the suite require a Docker daemon, and a live Azure
  call would require credentials and cost money — both contradict SC-003 (clean checkout runs
  green), SC-005 (each part verifiable alone) and SC-009 (everything passes with zero AI
  credentials). Constitution v1.3.0's Principle II now states this obligation directly: tests MUST
  NOT require live AI provider credentials to pass.
- **How the paths are still covered**: the database-down health path uses the real indicator with
  no database present; the database-up path overrides the health contribution; the Azure path is
  covered by unit tests over the configuration-completeness logic, with the real call confined to
  the opt-in `verify-ai` profile.
- **When this changes**: the ingestion feature, where real SQL against real pgvector is the thing
  under test, is the right moment to accept a Docker dependency.

## Open questions

None. All five clarifications from the 2026-08-13 session are resolved and reflected above, and
the constitution amendment they depended on is ratified (v1.3.0).

Two items remain **flagged for user decision but do not block implementation**:

1. Spring Boot 3.5.16 + Spring AI 1.1.8 versus Spring Boot 4.1.0 + Spring AI 2.0.0 (Decisions 1, 2)
2. Angular 21.2.x versus Angular 22.1.4 (Decision 10)

One item is an **external prerequisite, not a code task**: provision the Azure embedding
deployment and set `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME`. This feature runs without it; the
ingestion feature cannot start without it.

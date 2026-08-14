# Tasks: Project Scaffolding

**Input**: Design documents from `/specs/001-project-scaffolding/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: **Mandatory, not optional.** FR-008 and FR-012 make a runnable test suite a deliverable
of each application, and constitution v1.3.0 Principle II mandates test-driven development —
failing test first, minimum code to pass, then refactor. Test tasks therefore precede the
implementation tasks they cover within each story, and each says what it must fail against before
the implementation exists.

**Organization**: Tasks are grouped by user story so each can be implemented and verified
independently — which for this feature is not a convention but a requirement (FR-013).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: `[US1]`–`[US4]`, mapping to the user stories in [spec.md](spec.md)
- Every task names the exact file it touches, or the exact command it runs

## Path Conventions

Web application layout per [plan.md](plan.md) — `backend/` and `frontend/` as sibling roots at the
repository root, with `docker-compose.yml` and `db/init/` alongside them. `docs/`, `sample-data/`
and `specs/` are untouched by this feature.

Commands appear in PowerShell form (the primary development platform per spec Assumptions), with
the bash form alongside where they differ, as FR-027 requires.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: The two repository-root files every other phase depends on for hygiene and
configuration. Both must exist before anything is installed or started, because `npm install`
without a `.gitignore` tracks `node_modules/`, and `docker compose up` without `.env` has no
credentials to read.

- [X] T001 [P] Create `.gitignore` at the repository root excluding `backend/target/`,
      `frontend/node_modules/`, `frontend/.angular/`, `frontend/dist/`, `.env`, and IDE
      directories (FR-015)
- [X] T002 [P] Create `.env.example` at the repository root with `POSTGRES_DB`, `POSTGRES_USER`
      and `POSTGRES_PASSWORD` carrying non-secret local-development placeholders, and all four
      `AZURE_OPEN_AI_*` variable names documented with non-secret placeholders. The names are
      fixed by constitution v1.3.0 and MUST NOT be renamed (FR-009, FR-018, SC-006)
- [X] T003 Copy `.env.example` to `.env` and confirm `git status` does not list it — proving T001
      covers it before any credential is written (FR-009, SC-006)

---

## Phase 2: Foundational (Blocking Prerequisites)

**This phase is intentionally empty, and that is a finding rather than an omission.**

FR-013 requires each of the three parts to be startable independently of the others, and
[runtime-surface.md](contracts/runtime-surface.md) discharges it: no part needs another to build,
test or run. Research Decision 11 extends the same property to the test suites — the backend suite
runs against no database and contacts no external service, so US2 does not wait on US1 even for
verification.

Introducing a foundational phase here would manufacture a dependency the design deliberately
avoids. After Phase 1, **US1, US2 and US3 can be worked in any order or in parallel**.

One soft ordering remains, and it is a sequencing convenience rather than a block: T023's manual
"database reachable" health check (scenario US2-2) needs a running database, so it reads more
naturally after US1. Every automated test in US2 passes without one.

**Checkpoint**: After Phase 1, all three application stories are unblocked simultaneously.

---

## Phase 3: User Story 1 - Start the database environment (Priority: P1) 🎯 MVP

**Goal**: One documented command brings up PostgreSQL 18 with the `vector` extension enabled on a
fixed local port, storing data on a named volume that survives a stop/start cycle.

**Independent Test**: On a clean checkout run the start command, connect with `psql`, confirm
`vector` is installed, write a row, restart the environment, confirm the row survived. No backend
or frontend involvement.

**Verification approach**: this story ships no automated test suite — it has no application code to
test. Its acceptance scenarios are verified by the documented `psql` probes below, which are the
executable form of US1's acceptance criteria.

### Implementation for User Story 1

- [X] T004 [P] [US1] Create `db/init/01-init-vector.sql` containing
      `CREATE EXTENSION IF NOT EXISTS vector;` — idempotent, and the only schema this feature
      creates (FR-002, data-model.md)
- [X] T005 [US1] Create `docker-compose.yml` at the repository root: a single `db` service on
      `pgvector/pgvector:pg18`, host port mapping `5432:5432`, `POSTGRES_*` values read from `.env`
      with **no password literal in the file**, a named volume mounted at the container data
      directory, `./db/init` mounted read-only at `/docker-entrypoint-initdb.d/`, a healthcheck, and
      **no top-level `version:` key** (obsolete under Compose V2+). Backend and frontend are not
      containerised (FR-001, FR-003, FR-004, FR-009, research Decisions 6 and 9)

### Verification for User Story 1

- [X] T006 [US1] Run `docker compose up -d`, then confirm the extension query
      `SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';` returns exactly one
      row reporting `vector` (scenario US1-2, FR-002)
- [X] T007 [US1] Write a probe row, run `docker compose down` then `docker compose up -d`, confirm
      the row survived, then drop the probe table (scenario US1-3, FR-003, SC-004)
- [X] T008 [US1] Confirm `docker compose down` shuts down cleanly and releases port 5432
      (scenario US1-4, FR-001)
- [X] T009 [US1] Confirm `docker compose down -v` discards the stored state and that the init
      script runs again on the next `up -d` — the behaviour behind the stale-volume edge case
      (FR-024, data-model.md state transitions)

**Checkpoint**: US1 is complete and independently verifiable. This alone is a usable increment —
the ingestion feature's vector store now exists.

---

## Phase 4: User Story 2 - Run the backend service (Priority: P2)

**Goal**: One documented command starts a Spring Boot service on port 8080 that boots with the
database down *and* with Azure credentials absent, reports both states through `/actuator/health`,
and ships a suite that runs green on a clean checkout with neither available.

**Independent Test**: Start the backend and request the health check; with the database up it
reports a healthy service and a reachable database, with it down the service still answers and
reports the failure. Separately, `mvnw test` passes. No frontend involvement.

### Project skeleton (prerequisite for writing any test)

A test cannot be written before there is a project to run it in. These three tasks create the
harness; they implement none of the behaviour under test.

- [X] T010 [US2] Initialize the backend Maven project at `backend/`: `pom.xml` with
      `spring-boot-starter-parent:3.5.16`, Java 17, and dependencies `spring-boot-starter-web`,
      `spring-boot-starter-actuator`, `spring-boot-starter-jdbc`, `org.postgresql:postgresql`
      (runtime), plus `spring-ai-bom:1.1.8` in `dependencyManagement` managing
      `spring-ai-starter-model-azure-openai`. **No `spring-boot-starter-data-jpa`** — Hibernate
      schema validation opens a connection at startup and would break FR-007. Ship `mvnw`,
      `mvnw.cmd` and `.mvn/wrapper/` so no global Maven install is required (FR-005, research
      Decisions 1, 2, 7)
- [X] T011 [US2] Configure Surefire in `backend/pom.xml` with `<excludedGroups>azure</excludedGroups>`
      in the default build, and add a `verify-ai` profile that clears the exclusion and sets
      `<groups>azure</groups>`. A bare `-Dgroups=azure` would still be filtered out by the default
      exclusion — the profile is what makes SC-008's single command work (FR-022, research
      Decision 5)
- [X] T012 [US2] Create `backend/src/main/java/com/epam/aihelpdesk/AiHelpdeskApplication.java` —
      a bare `@SpringBootApplication` entry point, serving on port 8080 (FR-005)

### Tests for User Story 2 ⚠️

> **Write these FIRST and confirm each fails** before writing T017–T019. T013 in particular should
> fail with `IllegalArgumentException: Endpoint must not be empty` thrown during context refresh —
> that failure is the direct evidence for FR-019 and for research Decision 2, and watching it
> happen is worth more than reading about it.

- [X] T013 [P] [US2] `backend/src/test/java/com/epam/aihelpdesk/AiHelpdeskApplicationTests.java` —
      the application context loads with **no database reachable and every `AZURE_OPEN_AI_*`
      variable unset**. Fails until T017 defaults `spring.ai.model.chat` and
      `spring.ai.model.embedding` to `none` (FR-007, FR-019, SC-009)
- [X] T014 [P] [US2] `backend/src/test/java/com/epam/aihelpdesk/AzureOpenAiConfigHealthIndicatorTest.java`
      — four cases over the completeness rule: all three required values present → `UP`; all absent
      → `UNKNOWN` with `missing` naming `api-key`, `endpoint` and `chat-deployment-name`; endpoint
      present with the key blank → `UNKNOWN`, **never `UP`** (this is the half-set environment
      FR-021 forbids from masquerading as working); embedding deployment name unset while the other
      three are set → still `UP`, since FR-023 excludes it from the rule. Assert the indicator
      never returns `DOWN` (FR-020, FR-021, FR-023, ai-provider.md)
- [X] T015 [P] [US2] `backend/src/test/java/com/epam/aihelpdesk/HealthEndpointTest.java` — the three
      cases of [health-api.md](contracts/health-api.md):
      **Case A** (database reachable) → HTTP 200, `$.status` `UP`, `$.components.db.status` `UP`;
      **Case B** (database unreachable) → HTTP 503, `$.status` `DOWN`, `$.components.db.status`
      `DOWN`, and `$.components.db.details.error` present, non-empty and **naming the underlying
      exception type**;
      **Case C** (Azure unconfigured, database reachable) → HTTP 200 with overall status still
      `UP` and `$.components.azureOpenAi.status` `UNKNOWN`.
      Assert only the fields the contract guarantees, and **do not assert the exception's verbatim
      message** — it is environment-dependent (FR-006, FR-007, FR-020, health-api.md)
- [X] T016 [P] [US2] `backend/src/test/java/com/epam/aihelpdesk/HealthResponseSecretTest.java` —
      with a known sentinel value bound as the API key, no part of the `/actuator/health` payload
      contains it in whole or in part. Constitution v1.3.0 requires a key never to appear in a
      response; this is the test that enforces it (FR-009, data-model.md handling rules)

### Implementation for User Story 2

- [X] T017 [US2] Create `backend/src/main/resources/application.yml`: datasource URL, user and
      password from environment with working local defaults; `management.endpoints.web.exposure.include: health`
      (health only — no other actuator endpoint); `management.endpoint.health.show-details: always`;
      the four `spring.ai.azure.openai.*` properties each bound with an **empty** default
      (`${VAR:}`); and `spring.ai.model.chat: none` plus `spring.ai.model.embedding: none`.
      **The `none` gate is load-bearing** — without it the Azure auto-configuration builds a client
      at startup and the application fails to boot with credentials absent. Turns T013 green
      (FR-009, FR-018, FR-019, research Decisions 2, 3, 8)
- [X] T018 [US2] Create `backend/src/main/java/com/epam/aihelpdesk/health/AzureOpenAiProperties.java`
      — binds `apiKey`, `endpoint`, `chatDeploymentName` and `embeddingDeploymentName` from the
      Spring property names above, and exposes the FR-021 completeness rule (complete only when the
      first three are all present and non-blank) plus the list of missing setting names. Reads
      **our** bound values, not Spring AI's `AzureOpenAiChatProperties`, whose deployment name
      carries a non-blank library default and could never report as missing (FR-018, FR-021,
      FR-023, data-model.md, ai-provider.md)
- [X] T019 [US2] Create `backend/src/main/java/com/epam/aihelpdesk/health/AzureOpenAiConfigHealthIndicator.java`
      — contributes an `azureOpenAi` component: `UP` when configuration is complete, `UNKNOWN` when
      absent or partial. **Performs no network I/O and never returns `DOWN`** — `DOWN` would push
      the whole service to 503 for any developer without Azure credentials, which SC-009 forbids.
      Details carry booleans and the names of missing settings only, never a value. Turns T014,
      T015 Case C and T016 green (FR-020, FR-021, health-api.md, research Decision 4)
- [X] T020 [US2] Create `backend/src/test/java/com/epam/aihelpdesk/AzureOpenAiConnectivityIT.java`
      — tagged `@Tag("azure")`, therefore excluded from the default suite by T011. When
      configuration is incomplete it fails immediately naming what is missing, **without making a
      request**. Otherwise it issues **exactly one** minimal completion against the chat deployment
      with a low token cap, and on failure reports Azure's own status and message rather than a
      generic "verification failed". It reports `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` as missing
      when unset without failing the chat check — nothing consumes embeddings yet (FR-022, FR-023,
      SC-008, ai-provider.md)

### Verification for User Story 2

- [X] T021 [US2] With the database stopped and every `AZURE_OPEN_AI_*` variable unset in the shell,
      run `backend\mvnw.cmd test` (bash: `backend/mvnw test`) — all tests pass, **zero failures and
      zero skips** (FR-008, SC-003, SC-009)
- [X] T022 [US2] Confirm that run made no request to Azure and that `AzureOpenAiConnectivityIT` did
      not execute, then run `backend\mvnw.cmd test -Pverify-ai` with credentials present and confirm
      it makes exactly one request (FR-022, SC-008, scenario US2-8)
- [ ] T023 [US2] Manual health pass: with the database **up**, `/actuator/health` returns 200 `UP`
      with `db` `UP` (scenario US2-2); with the database **down**, the service still starts — no
      crash, no restart loop — and returns 503 `DOWN` with `db` `DOWN` carrying the connection error
      text (scenario US2-3, FR-007)
- [ ] T024 [US2] Without restarting the backend left running from T023, run `docker compose up -d`
      and confirm health returns to 200 `UP` **within 30 seconds** of the database accepting
      connections (FR-025, Edge Cases)

**Checkpoint**: US1 and US2 both work, together and independently.

---

## Phase 5: User Story 3 - Run the frontend application (Priority: P3)

**Goal**: One documented command serves an Angular placeholder page on port 4200 that names the
application, makes no backend calls, and ships a passing Vitest suite.

**Independent Test**: Run the start command, open `http://localhost:4200`, see the placeholder
render with a clean console. Separately run `npm test`. Requires neither backend nor database.

### Implementation for User Story 3

- [X] T025 [US3] Generate the Angular application into `frontend/` with the Angular **21.2.x** CLI,
      keeping CLI defaults — `testRunner` defaults to **Vitest** and `fileNameStyleGuide: 2025`
      produces `app.ts` / `app.html` / `app.spec.ts` rather than the older `app.component.*` naming.
      `npm install` is the one-time prerequisite; `npm start` is the single start command
      (FR-010, SC-002, research Decision 10)

### Test for User Story 3 ⚠️

- [X] T026 [US3] Update `frontend/src/app/app.spec.ts` to assert the placeholder renders and names
      the application. **Confirm it fails** against the CLI's generated default template before
      writing T027 (FR-012)

- [X] T027 [US3] Replace `frontend/src/app/app.html` with a placeholder page naming the application
      (AI Helpdesk) and stating that PoC functionality is not yet implemented. It **makes no HTTP
      call to the backend** — the frontend is deliberately unwired at this stage, so it renders
      identically whether or not the backend is running. Turns T026 green (FR-011, FR-016)

### Verification for User Story 3

- [X] T028 [US3] Confirm the dev server binds port 4200 and, when 4200 is occupied, **fails with a
      message naming the port rather than moving to 4201**. See the risk note below — the Angular
      CLI's default behaviour may need to be pinned explicitly for this to hold (FR-010, FR-026)
- [X] T029 [US3] Run `npm test` in `frontend/` — all tests pass. Then `npm start` and confirm
      `http://localhost:4200` renders the placeholder with **zero browser console errors**
      (scenario US3-2, FR-011, FR-012, SC-003)

**Checkpoint**: all three parts exist, run and test independently.

---

## Phase 6: User Story 4 - Bring up the whole stack from the documentation alone (Priority: P3)

**Goal**: `README.md` carries everything a developer who has never seen the repository needs to
reach three running parts, in commands that execute as written on the primary platform.

**Independent Test**: hand the repository to a developer who has not worked on it; they follow the
setup documentation only and reach a state where all three parts are running.

**Depends on US1, US2 and US3** — this is the one story that genuinely cannot be verified before
the others exist, since it documents them.

### Implementation for User Story 4

- [X] T030 [US4] Add the setup section to `README.md`: prerequisites with exact version ranges —
      including Node's `^20.19.0 || ^22.12.0 || >=24.0.0` and the explicit exclusion of 22.0–22.11,
      because "Node 22" alone is an actively misleading instruction — the one-time `npm install`
      marked as a **prerequisite rather than a start command**, the start and stop command for each
      of the three parts, the local address each serves on, and the recommended start order
      (FR-014, SC-002)
- [X] T031 [US4] Document `docker compose down -v` in `README.md` as a **destructive** reset that
      discards stored data, explicitly distinguished from the `docker compose down` stop command of
      FR-001, and note that a changed `db/init/*.sql` takes effect only after it (FR-024)
- [X] T032 [US4] State in `README.md` which shell the commands are written for (PowerShell 7+, the
      primary development platform), and give both PowerShell and bash forms wherever they differ —
      at minimum `backend\mvnw.cmd` vs `backend/mvnw`, and `Select-String` vs `grep`. Every command
      MUST execute as written on the primary platform (FR-027, spec Assumptions)
- [X] T033 [US4] Update the status section of `README.md` — currently "**Status: concept phase.** No
      application code exists yet" — to state accurately that a runnable skeleton now exists without
      PoC functionality, and that **Azure OpenAI credentials are not required** to run it. Update
      the stack table if any version differs from what was actually installed (FR-017, SC-009,
      constitution Principle I)

### Verification for User Story 4

- [ ] T034 [US4] Walk `README.md` end to end and confirm all three parts come up simultaneously
      from the documentation alone (scenario US4-2). **This is a proxy, not the real test**: SC-001
      (under 15 minutes, asking no questions) and SC-007 (first attempt, no unsanctioned file edits)
      cannot be assessed by the person who wrote the documentation. Recruiting a developer who has
      not seen the repository is the actual acceptance step

**Checkpoint**: all four user stories complete.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T035 [P] Run the full [quickstart.md](quickstart.md) validation pass top to bottom — it is
      written as a complete manual acceptance sweep and covers scenarios these tasks reference only
      by number
- [X] T036 [P] Secret check: `git ls-files | Select-String -Pattern '(^|/)\.env$'` (bash:
      `git ls-files | grep -E "(^|/)\.env$"`) returns nothing, and no password literal appears in
      `docker-compose.yml` or `backend/src/main/resources/application.yml` — both must read from the
      environment (FR-009, SC-006)
- [X] T037 [P] Independence check: three separate single-part startups, each from a fully stopped
      state, matching the table in [runtime-surface.md](contracts/runtime-surface.md) (FR-013,
      SC-005)
- [X] T038 [P] Port-conflict check: occupy 5432, 8080 and 4200 in turn and confirm each part fails
      with a message naming the occupied port rather than binding an alternative (FR-026)
- [X] T039 Confirm no PoC behaviour was introduced anywhere in `backend/` or `frontend/` — no
      document upload, parsing, chunking, embedding, retrieval or answer generation, and no stub of
      `POST /documents` or `POST /chat` (FR-016, health-api.md "Not in this contract")
- [ ] T040 Assemble the PR: Constitution Compliance checklist against the seven principles, a link
      to [spec.md](spec.md), proof of both suites passing, and the committed-credential check.
      Note in the PR that Principles III, IV, V and VII are deferred rather than satisfied — this
      feature has no retrieval to measure (constitution Governance → Compliance Review)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: no dependencies — start immediately
- **Phase 2 (Foundational)**: intentionally empty, blocks nothing
- **Phase 3 (US1)** / **Phase 4 (US2)** / **Phase 5 (US3)**: each depends only on Phase 1, and on
  nothing from each other
- **Phase 6 (US4)**: depends on US1, US2 and US3 — it documents all three
- **Phase 7 (Polish)**: depends on everything

### User Story Dependencies

- **US1 (P1)**: independent. The MVP.
- **US2 (P2)**: independent for implementation and for its entire automated suite. Only T023's
  manual database-up check wants US1 running first, and only for convenience.
- **US3 (P3)**: fully independent — neither backend nor database.
- **US4 (P3)**: the one real dependency in this feature. Documents the other three.

### Within User Story 2

T010 → T012 (harness) → T013–T016 (tests, must fail) → T017 → T018 → T019 (implementation) →
T020 → T021–T024 (verification). T011 must precede T020, or the tagged connectivity test runs in
the default suite and breaks SC-003 and SC-009 for anyone without credentials.

### Parallel Opportunities

- T001 and T002 in parallel
- **Whole stories in parallel**: after Phase 1, US1, US2 and US3 can be worked simultaneously by
  three people with no coordination — this is FR-013 paying off during construction as well as at
  runtime
- Within US2: T013, T014, T015 and T016 are four separate test files with no ordering between them
- Within Phase 7: T035–T038 are independent checks

---

## Parallel Example: User Story 2 tests

```bash
# Four independent test files, no ordering between them — all four must fail before T017 starts:
Task: "AiHelpdeskApplicationTests.java — context loads with no DB and no credentials"
Task: "AzureOpenAiConfigHealthIndicatorTest.java — complete / absent / partial / embedding-unset"
Task: "HealthEndpointTest.java — health-api.md Cases A, B and C"
Task: "HealthResponseSecretTest.java — the API key never appears in the payload"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 — three setup tasks
2. Phase 3 — US1: `docker-compose.yml` and the init script
3. **STOP and VALIDATE**: T006–T009
4. The vector store the ingestion feature needs now exists, on its own

### Incremental Delivery

Phase 1 → US1 (database, MVP) → US2 (backend + both health contracts) → US3 (frontend) → US4
(documentation) → Phase 7. Each story adds a runnable part without touching the previous ones —
there is no integration step between them, because there is deliberately no integration.

### Parallel Team Strategy

With three developers: everyone agrees Phase 1, then A takes US1, B takes US2, C takes US3. No
shared files, no merge surface between the three. Whoever finishes first writes US4.

---

## Risks & Open Items

Recorded here so they are decided rather than discovered mid-implementation.

1. **FR-026 versus the Angular CLI (T028).** The CLI's dev server has historically offered to use a
   different port when the requested one is busy. FR-026 forbids binding an alternative, because the
   documented address of FR-014 would then be wrong. If the default behaviour does not fail cleanly,
   pin it explicitly in `angular.json` rather than relaxing the requirement.
2. **FR-025's 30-second recovery window (T024)** depends on HikariCP pool timing that no task
   configures. If the observed recovery is slower, tune the pool rather than amend the requirement —
   or amend it deliberately, with the measured number.
3. **SC-001 and SC-007 cannot be self-assessed (T034).** Both describe a developer who did not build
   the scaffold. Budget for a second person, or record explicitly that the criteria are accepted
   unverified.
4. **The embedding deployment does not exist yet.** `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` is
   unset in the target environment. FR-023 permits that here and T020 reports it as missing, but it
   is a hard blocker for the ingestion feature — provisioning it is a task for that feature, and the
   lead time is external.
5. **104 checklist items remain open** across the four checklists in [checklists/](checklists/).
   None is a known defect — the four genuine ones were fixed and are marked `[x]`. They are the
   "answer or record as intentional" kind, and the highest-value clusters are `health.md` CHK003 /
   CHK004 / CHK026 (unspecified state combinations a developer meets on day one) and `devex.md`
   CHK001–CHK005 (SC-001 and SC-007 read measurable but define neither boundaries nor method).
6. **Spring Boot 3.5.16 / Angular 21.2.x are the constitution's mandate, not the newest releases.**
   Spring Boot 4.1.0, Spring AI 2.0.0 and Angular 22.1.4 are all out. Research Decisions 1, 2 and 10
   flag this; changing it requires a constitution amendment, and the codebase is empty right now —
   the cheapest moment it will ever be.

---

## Notes

- `[P]` = different files, no dependency on an incomplete task
- Commit after each task or logical group; the constitution requires tests passing before code is
  committed, so a commit that carries T017 must carry a green T013
- Verify each test fails before implementing against it — T013 in particular, whose failure mode is
  the evidence for FR-019
- Stop at any checkpoint to validate a story independently; that independence is a requirement here,
  not a convenience

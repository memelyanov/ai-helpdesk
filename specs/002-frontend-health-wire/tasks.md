---

description: "Task list template for feature implementation"
---

# Tasks: Frontend Health Wire

**Input**: Design documents from `/specs/002-frontend-health-wire/`

**Prerequisites**: [plan.md](plan.md) (required), [spec.md](spec.md) (required for user stories),
[research.md](research.md), [data-model.md](data-model.md), [contracts/frontend-health-consumption.md](contracts/frontend-health-consumption.md),
[quickstart.md](quickstart.md)

**Tests**: Included and sequenced before their implementation. Constitution Principle II
(Test-Driven Development) is **mandatory** project-wide, not optional for this feature — every
behavior below has a failing test written first.

**Organization**: Tasks are grouped by user story (spec.md priorities P1/P2/P3) so each story is
independently implementable, testable, and demoable.

> **A note on "Phase" numbering**: the six phases below (Setup, Foundational, User Story 1–3,
> Polish) are this document's own numbering and are unrelated to `plan.md`'s "Phase 0"/"Phase
> 1"/"Phase 2", which name the `/speckit-plan` workflow's Research/Design/Tasks-generation stages,
> not implementation steps.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- File paths are exact and relative to the repository root

## Path Conventions

Web app layout inherited from `001-project-scaffolding`: `backend/src/`, `frontend/src/`. This
feature adds one new frontend module, `frontend/src/app/connection-status/`, and a two-line
backend configuration change plus one backend test — no new top-level directory (see plan.md
Project Structure).

---

## Phase 1: Setup

**Purpose**: Establish a clean, known-good baseline before any TDD work begins.

- [X] T001 Confirm the existing scaffold is green before changing anything: run `backend/mvnw test` (from `backend/`) and `npm test` (from `frontend/`); both MUST pass with zero failures on the current `main` before Phase 2 starts

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The plumbing every user story needs regardless of which indicator state it's about —
without this, no state (healthy, degraded, or unreachable) can ever be observed in a browser.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Write a failing test in `backend/src/test/java/com/epam/aihelpdesk/HealthEndpointCorsTest.java` asserting that a `GET /actuator/health` request carrying `Origin: http://localhost:4200` receives an `Access-Control-Allow-Origin: http://localhost:4200` response header (per `contracts/frontend-health-consumption.md`'s CORS requirement, FR-008)
- [X] T003 Add `management.endpoints.web.cors.allowed-origins: http://localhost:4200` and `management.endpoints.web.cors.allowed-methods: GET` to `backend/src/main/resources/application.yml` (research.md Decision 3); run `backend/mvnw test` and confirm T002 now passes — depends on T002
- [X] T004 [P] Add `provideHttpClient()` to the providers array in `frontend/src/app/app.config.ts` (research.md Decision 1)
- [X] T005 [P] Create `frontend/src/app/connection-status/connection-status.ts` with the `ConnectionStatus` union type (`'checking' | 'healthy' | 'degraded' | 'unreachable'`) and the `ConnectionStatusInfo` interface (`state: ConnectionStatus`, `lastCheckedAt: Date`) per data-model.md — type declarations only, no `classify()` logic yet

**Checkpoint**: CORS is open for the frontend's origin, `HttpClient` is available for injection, and the state shape both later phases build on is defined.

---

## Phase 3: User Story 1 - See whether the backend is up, at a glance (Priority: P1) 🎯 MVP

**Goal**: On page load, the frontend automatically checks the backend's health endpoint and shows
a visible "healthy" indicator when the backend responds `200`/`status: "UP"`.

**Independent Test**: With the database, backend, and frontend all running, open the frontend in a
browser and observe a "connected/healthy" indicator on the page within 5 seconds, with no manual
verification step and no console errors (quickstart.md step 2).

### Tests for User Story 1 ⚠️

> Write these tests FIRST; confirm they FAIL before implementing anything below.

- [X] T006 [P] [US1] Write failing tests in `frontend/src/app/connection-status/connection-status.spec.ts`: (a) `classify()` maps a response body with `status: "UP"` (use the captured fixture from `contracts/frontend-health-consumption.md#reference-fixture-captured-2026-08-14`) to `'healthy'`; (b) `classify()` maps a malformed body — missing `status`, `status` not a string, or a body that isn't valid JSON — to `'unreachable'` (FR-009, spec Edge Cases)
- [X] T007 [P] [US1] Write failing tests in `frontend/src/app/connection-status/health.service.spec.ts` using `provideHttpClientTesting()`/`HttpTestingController`: (a) the service's status signal starts at `'checking'`, and becomes `'healthy'` after the first request is flushed with the 200/UP fixture; (b) a `200` response flushed with a malformed/missing-`status` body → status becomes `'unreachable'` (FR-009 exercised end-to-end through the service, not just the pure function)
- [X] T008 [P] [US1] Write a failing test in `frontend/src/app/connection-status/connection-status.component.spec.ts`: the component renders visibly different content/markup for a `'checking'` status than for a `'healthy'` status

### Implementation for User Story 1

- [X] T009 [US1] In `frontend/src/app/connection-status/connection-status.ts`, implement `classify(body: unknown): ConnectionStatus`: `status: "UP"` → `'healthy'`; anything else (including a missing/malformed `status` field or non-object body) → `'unreachable'` as the default branch — makes both T006(a) and T006(b) pass; depends on T005
- [X] T010 [US1] Create `frontend/src/app/connection-status/health.service.ts`: `timer(0, 10000)` → `switchMap` to `http.get('http://localhost:8080/actuator/health')` → `timeout(3000)` → `catchError` mapping any thrown error to `'unreachable'` → `map(classify)` on a successful response, exposed as `readonly status: Signal<ConnectionStatus>` via `toSignal(..., { initialValue: 'checking' })` — makes T007(a) and T007(b) pass; depends on T004, T007, T009
- [X] T011 [US1] Create `frontend/src/app/connection-status/connection-status.component.ts` (+ `.html`, `.css`): a standalone component that injects `HealthService` and renders `status()` — distinct visible content for `'checking'` and `'healthy'` at minimum (an `'unreachable'`/`'degraded'` fallback rendering is acceptable here; getting those visually distinct from each other is US2/US3's job) — makes T008 pass; depends on T008, T010
- [X] T012 [US1] Import `ConnectionStatusComponent` into `frontend/src/app/app.ts` and add `<app-connection-status />` to `frontend/src/app/app.html` so the indicator appears on the existing placeholder page — depends on T011

**Checkpoint**: User Story 1 is fully functional and independently testable — quickstart.md step 2 passes.

---

## Phase 4: User Story 2 - See when the backend cannot be reached at all (Priority: P2)

**Goal**: When the backend is stopped, unreachable, or a request to it hangs, the indicator shows a
distinct "unreachable" state — and returns to "healthy" on its own, without a page reload, once the
backend comes back.

**Independent Test**: With the frontend running and the backend stopped, open the frontend and
observe an "unreachable" indicator; then start the backend and, without reloading the page, observe
the indicator change to healthy within the refresh window (quickstart.md step 3).

### Tests for User Story 2 ⚠️

- [X] T013 [P] [US2] Add failing tests to `frontend/src/app/connection-status/health.service.spec.ts`: (a) `req.error(...)` on the flushed request → status becomes `'unreachable'`; (b) under `vi.useFakeTimers()`, a request left unflushed past 3000ms → status becomes `'unreachable'` (FR-007); (c) still under fake timers, an `'unreachable'` result followed by a successful flush at the next 10s poll tick → status returns to `'healthy'` without recreating the service (FR-006, spec Story 2 Scenario 3)
- [X] T014 [P] [US2] Add a failing test to `frontend/src/app/connection-status/connection-status.component.spec.ts`: the component renders content/markup for `'unreachable'` that is visually distinct (different CSS class and/or text) from both `'healthy'` and `'checking'`

### Implementation for User Story 2

- [X] T015 [US2] Adjust the operator composition in `frontend/src/app/connection-status/health.service.ts` as needed so all three cases in T013 pass — in particular, confirm `catchError` sits inside the per-tick `switchMap` (catching that one request's failure) rather than wrapping the outer `timer`, so one failed/timed-out check does not permanently end the polling stream — depends on T013
- [X] T016 [US2] Add distinct CSS/markup for the `'unreachable'` state in `frontend/src/app/connection-status/connection-status.component.html` and `.css` — makes T014 pass; depends on T014

**Checkpoint**: User Stories 1 AND 2 both work independently — quickstart.md step 3 passes.

---

## Phase 5: User Story 3 - See when the backend is up but reporting a problem (Priority: P3)

**Goal**: When the backend is reachable but its own health check reports a problem (HTTP 503,
`status: "DOWN"`), the indicator shows a "degraded" state, distinct from both "healthy" and
"unreachable" — while an unconfigured AI provider alone (still `status: "UP"`) continues to show
"healthy".

**Independent Test**: With the backend running and its database stopped, open the frontend and
observe a "degraded" indicator distinct from both the healthy state and the unreachable state
(quickstart.md step 4).

### Tests for User Story 3 ⚠️

- [X] T017 [P] [US3] Add failing tests to `frontend/src/app/connection-status/connection-status.spec.ts`: (a) `classify()` maps a body with `status: "DOWN"` to `'degraded'`; (b) `classify()` maps a body with `status: "UP"` and `components.azureOpenAi.status: "UNKNOWN"` (the captured-fixture shape) to `'healthy'` — spec Story 3 Scenario 2
- [X] T018 [P] [US3] Add a failing test to `frontend/src/app/connection-status/health.service.spec.ts`: a `503` response with body `status: "DOWN"` flushed via `HttpTestingController` → status becomes `'degraded'`
- [X] T019 [P] [US3] Add a failing test to `frontend/src/app/connection-status/connection-status.component.spec.ts`: the component renders content/markup for `'degraded'` that is visually distinct from both `'healthy'` and `'unreachable'`

### Implementation for User Story 3

- [X] T020 [US3] Add the `status: "DOWN"` → `'degraded'` branch to `classify()` in `frontend/src/app/connection-status/connection-status.ts` (the `'UP'`-with-`UNKNOWN`-Azure case already resolves to `'healthy'` via the existing default-field-only read from Decision 4 — T017(b) should pass without further change) — makes T017 and T018 pass; depends on T017, T018. **Also required an unplanned fix to `health.service.ts`'s `catchError`**: `HttpClient` surfaces a `503` as an error, not a `next` emission, so the blanket `catchError` was misclassifying the degraded case as `unreachable` (violating FR-003) until it was taught to distinguish a reachable-but-non-2xx `HttpErrorResponse` (`status !== 0`) from a genuine network failure (`status === 0`) and classify the former's body instead of discarding it.
- [X] T021 [US3] Add distinct CSS/markup for the `'degraded'` state in `frontend/src/app/connection-status/connection-status.component.html` and `.css` — makes T019 pass; depends on T019

**Checkpoint**: All three user stories are independently functional — quickstart.md steps 4 and 5 pass; all three indicator states are pairwise visually distinct.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verification against the spec's success criteria and constitution obligations, across
all three stories together.

- [X] T022 [P] Verify FR-011 (no secret value in the indicator or its logging): inspect `frontend/src/app/connection-status/*.ts` and confirm no `console.*` call logs the full response body or any `details` field — only `classify()`'s single-field read exists
- [X] T023 [P] Run `backend/mvnw test` (full suite, including T002/T003's new CORS test) and confirm zero failures, zero skips
- [X] T024 [P] Run `npm test` in `frontend/` (full suite, including all `connection-status/*.spec.ts` files) and confirm zero failures, zero skips
- [ ] T025 **Partially blocked in this environment** — Execute `specs/002-frontend-health-wire/quickstart.md` end-to-end against the real running stack (all 6 steps) and confirm SC-001 through SC-005 each hold. The backend (`backend/mvnw.cmd spring-boot:run`) cannot bind a real socket in this sandbox: Tomcat's NIO selector fails with `java.io.IOException: Unable to establish loopback connection` / `SocketException: Invalid argument: connect` even after trying the legacy Windows selector provider and forcing IPv4 — an OS-level loopback-networking restriction of this sandbox, not a defect in the feature (confirmed: `backend/mvnw test`'s MockMvc-based tests, which never bind a real socket, all pass). What *was* verified live: with `npm start` serving the frontend and no backend reachable, `http://localhost:4200` correctly rendered "Backend unreachable", and the browser console showed exactly one line — `net::ERR_CONNECTION_REFUSED` (the browser-native line SC-004 explicitly excludes) — with zero application-code `console.error` output, live-confirming Story 2 Scenario 1 and part of SC-004. Steps 2/4/5/6 (which need the backend reachable) are unverified live and rely on T023/T024's automated coverage instead; **a developer on an unrestricted machine should still run all 6 quickstart.md steps before considering this feature done**
- [X] T026 Review `specs/001-project-scaffolding/contracts/health-api.md`'s Consumers section and `specs/002-frontend-health-wire/contracts/frontend-health-consumption.md` against what was actually shipped; correct either document if implementation diverged (Constitution Principle I — living documents must stay truthful). **Checked, no divergence found**: the endpoint URL, the 10s/3s timing, the status-only field read, the UP/DOWN/unreachable classification table, the CORS origin/method, and the reference fixture all match the shipped `health.service.ts`/`connection-status.ts`/`application.yml` exactly. Neither document claims anything about *how* a non-2xx response is distinguished from a network failure at the `HttpClient` level (T020's `HttpErrorResponse.status !== 0` check) — that's an implementation detail of *how* the documented classification table is achieved, not a behavior the contract promises, so it correctly stays out of both documents.
- [X] T027 Update root `README.md`'s frontend description/quick-check (and, if worth surfacing, the ports/commands table) to mention the connection-status indicator and its dependency on the backend CORS setting added in T003, so the document stays truthful about current frontend behavior (Constitution Principle I — README.md is explicitly named as a living document that MUST stay truthful)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup completion (T001) — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational (T002–T005) completion
- **User Story 2 (Phase 4)**: Depends on Foundational completion; builds directly on User Story 1's `health.service.ts` and `connection-status.component.*` (T010, T011) — not on US1 being "done" in the checkpoint sense, but on those specific files existing
- **User Story 3 (Phase 5)**: Depends on Foundational completion; builds directly on the same two files US2 extended (T015, T016)
- **Polish (Phase 6)**: Depends on all three user stories being complete

### User Story Dependencies

Unlike a typical spec-kit feature where stories touch disjoint files, this feature's three stories
are three **states of the same one classification function and the same two files**
(`connection-status.ts`, `health.service.ts`, `connection-status.component.*`) — see plan.md Summary
and research.md Decision 5. They are still independently *testable and demoable* in priority order
(each checkpoint above is a real, working increment a developer can use), but they are **not**
independently *implementable in parallel by different people on the same files*: US2 literally adds
branches/tests next to what US1 wrote, and US3 does the same to what US2 left. Implement in strict
priority order: US1 → US2 → US3.

### Within Each User Story

- Tests MUST be written and FAIL before their corresponding implementation task (Constitution
  Principle II)
- `connection-status.ts` (pure classification) before `health.service.ts` (HTTP + polling) before
  `connection-status.component.*` (rendering) — each depends on the one before it
- Story complete (checkpoint reached) before moving to the next priority

### Parallel Opportunities

- T002 (backend CORS test), T004 (frontend HttpClient), and T005 (frontend type file) touch three
  unrelated files and can run in parallel once Setup (T001) is done
- Within each story's Tests sub-phase, tasks touching different spec files (e.g., T006/T007/T008,
  or T013/T014, or T017/T018/T019) can run in parallel; tasks touching the *same* spec file (e.g.,
  T017's two sub-cases) are combined into one task rather than split, to avoid two parallel edits to
  one file
- T022, T023, T024 in Polish are independent (static inspection vs. two separate test-runner
  invocations) and can run in parallel

---

## Parallel Example: Foundational Phase

```bash
# After T001 (baseline check) completes, launch together:
Task: "Write failing CORS test in backend/src/test/java/com/epam/aihelpdesk/HealthEndpointCorsTest.java"
Task: "Add provideHttpClient() to frontend/src/app/app.config.ts"
Task: "Create ConnectionStatus/ConnectionStatusInfo types in frontend/src/app/connection-status/connection-status.ts"
```

## Parallel Example: User Story 1 Tests

```bash
# All three touch different spec files — launch together, then implement T009→T010→T011→T012 in order:
Task: "Failing classify() UP→healthy test in connection-status.spec.ts"
Task: "Failing HealthService checking→healthy test in health.service.spec.ts"
Task: "Failing component checking-vs-healthy rendering test in connection-status.component.spec.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002–T005) — CRITICAL, blocks everything else
3. Complete Phase 3: User Story 1 (T006–T012)
4. **STOP and VALIDATE**: run quickstart.md step 2 against the real stack
5. This alone is a demoable improvement over today: a developer opening the frontend now sees
   *something* rather than nothing, for the one case (healthy) that matters most

### Incremental Delivery

1. Setup + Foundational → CORS open, HttpClient wired, state type defined
2. Add User Story 1 → validate independently → the MVP: "is the backend up?" answered visibly
3. Add User Story 2 → validate independently → "…or is it just unreachable?" now distinct
4. Add User Story 3 → validate independently → "…or is it up but unhappy?" now distinct too
5. Polish → prove the success criteria (SC-001…SC-005) and constitution obligations, not just the
   acceptance scenarios

### Note on team parallelism

Because all three stories converge on the same three files (see "User Story Dependencies" above),
this feature does not offer the usual "different developers take different stories in parallel"
opportunity. The real parallelism is *within* the Foundational phase and *within* each story's test
sub-phase, both called out above.

---

## Notes

- [P] tasks = different files, no dependency on an incomplete task — not "different story"
- [Story] label maps a task to its user story for traceability, per the rule that Setup/
  Foundational/Polish tasks carry no story label
- Tests are written first and MUST fail before the implementation task that follows makes them pass
- Commit after each task or logical group
- Stop at any checkpoint (end of Phase 3, 4, or 5) to demo that story's increment independently
- Avoid: skipping the "write it failing first" step, editing the same spec file from two "parallel"
  tasks at once, reordering stories ahead of their priority

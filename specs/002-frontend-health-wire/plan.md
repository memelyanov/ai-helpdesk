# Implementation Plan: Frontend Health Wire

**Branch**: `main` (no feature branch created — no `before_specify`/`before_plan` hook is registered, consistent with [001-project-scaffolding](../001-project-scaffolding/plan.md)) | **Date**: 2026-08-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-frontend-health-wire/spec.md`

## Summary

Wire the existing Angular placeholder page to the existing `GET /actuator/health` endpoint so a
developer can see, without leaving the browser, whether the backend is healthy, degraded, or
unreachable. The frontend polls the endpoint on a fixed interval starting at page load, classifies
each outcome into one of three states, and renders a small always-visible indicator. The only
backend change is enabling CORS for that one endpoint from the frontend's origin — the health
response shape itself, defined in
[001-project-scaffolding/contracts/health-api.md](../001-project-scaffolding/contracts/health-api.md),
is unchanged.

Two requirements shape the approach more than anything else:

- **FR-003** — degraded and unreachable must stay visually and logically distinct, not collapsed
  into one "not working" bucket. This rules out a boolean "connected" flag; the classification is a
  three-state (plus a transient "checking" state before the first response) union, not a boolean.
- **FR-006/FR-007** — the indicator must self-correct without a page reload, and a hung request
  must not block that self-correction. This is why polling has both an interval (how often to
  check) and a per-request timeout (how long one check is allowed to take) as two separate numbers.

## Technical Context

**Language/Version**: TypeScript via the Angular 21.2.x toolchain (already scaffolded); Java 17 /
Spring Boot 3.5.16 for the one backend change (already scaffolded, see
[001-project-scaffolding/plan.md](../001-project-scaffolding/plan.md))

**Primary Dependencies**: `@angular/common/http` (`HttpClient`, added to `app.config.ts` — not yet
present) and `rxjs` (already a transitive Angular dependency, no new package). No new backend
dependency: CORS is a `spring-boot-starter-actuator` configuration property, not a library addition.

**Storage**: N/A — the connection status is in-memory, client-side, unpersisted (see spec Key
Entities: "Connection Status").

**Testing**: Vitest (already scaffolded) with `provideHttpClientTesting()` /
`HttpTestingController` to simulate the 200/503/network-error/timeout cases without a real backend,
and fake timers to test polling and timeout behavior without real waiting. One new backend test
(JUnit 5 + Spring Boot Test, already scaffolded) asserting the CORS header on the health endpoint.

**Target Platform**: Same local developer machine as 001-project-scaffolding — browser talking to
`http://localhost:8080` from a page served at `http://localhost:4200`.

**Project Type**: Web application — this feature adds one small module to each of the two existing
projects (`backend/`, `frontend/`); no new project or service is introduced.

**Performance Goals**: None beyond the spec's own SC-001 (indicator settles within 5s of page load)
and SC-002 (indicator reflects a backend state change within 15s, no reload). These are met by
construction (see Research Decision 2), not by a separate performance-tuning effort.

**Constraints**:
- At least three distinguishable indicator states; degraded and unreachable MUST NOT collapse
  (FR-003)
- A hung request MUST NOT block the indicator from eventually showing unreachable (FR-007)
- No secret value in the indicator or its logging (FR-011) — trivially satisfied: the frontend
  reads only the `status` field, never `details`
- The health response contract itself (`contracts/health-api.md`) is not modified by this feature
- Fixed local addresses inherited from 001: backend `http://localhost:8080`, frontend
  `http://localhost:4200`

**Scale/Scope**: One developer, one browser tab, one polled endpoint. Eight new frontend source
files (`connection-status.ts`, `health.service.ts`, `connection-status.component.ts/.html/.css`,
and their three `.spec.ts` files — see Project Structure below) plus a small edit to `app.config.ts`
and `app.html`, and a two-line backend configuration addition plus one new backend test.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.3.0**.

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` precedes this plan. This feature changes no shipped behaviour that a living document currently describes incorrectly, except `contracts/health-api.md`'s "no CORS configuration and no client contract" note, which Phase 1 updates in place (see Project Structure). |
| II | Test-Driven Development (Mandatory) | ✅ PASS | Every new behavior (classification of the three states, timeout handling, polling, CORS header) gets a test before/with the code; no live network call is required for any test to pass. |
| III | Grounded Answers (RAG-First) | ⏭️ N/A — deferred | No answer generation touched. |
| IV | No Hallucination (Context Adherence) | ⏭️ N/A — deferred | No LLM call anywhere in this feature. |
| V | Semantic Understanding | ⏭️ N/A — deferred | No retrieval touched. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ⏭️ N/A — unaffected | No vector or inference code touched; the only network call this feature adds is the frontend calling its own backend's health endpoint. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ⏭️ N/A — deferred | No retrieval to measure. |

**Technology Stack compliance**: Angular 21 ✅ (existing scaffold, no version change), Spring Boot 3
✅ (existing scaffold, no version change). No dependency substitution, so the "no replacement
without a constitution amendment" rule is not engaged.

**Post-Phase 1 re-check**: ✅ No change. Design adds one new frontend module and one backend
configuration property; no new external dependency, no persistent data model, no principle
pre-empted or violated.

**Gate result**: PASS — no violations, no justifications required.

## Project Structure

> **A note on "Phase" numbering**: this document's "Phase 0"/"Phase 1"/"Phase 2" (below, and in the
> Constitution Check gate above) name the standard `/speckit-plan` workflow stages — Research,
> Design, and the follow-on `/speckit-tasks` command respectively. `tasks.md`'s six *implementation*
> phases (Setup, Foundational, User Story 1–3, Polish) are numbered independently and mean something
> different; a cross-reference to "Phase 1" only makes sense within the document it appears in.

### Documentation (this feature)

```text
specs/002-frontend-health-wire/
├── plan.md              # This file
├── research.md          # Phase 0 — polling/timeout numbers, CORS mechanism, state model
├── data-model.md        # Phase 1 — the Connection Status state machine
├── quickstart.md        # Phase 1 — how to see and verify the indicator
├── contracts/
│   └── frontend-health-consumption.md  # What the frontend assumes of GET /actuator/health
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 — created by /speckit-tasks, NOT by this command
```

**Amendment to a prior feature's artifact**: `specs/001-project-scaffolding/contracts/health-api.md`
currently states, in its **Consumers** section, "None in this feature. The frontend makes no
backend calls, so there is no CORS configuration and no client contract to agree." That statement
is no longer true once this feature ships. Phase 1 updates that section in place — a short pointer
to this feature's contract, not a rewrite of 001's design — because Constitution Principle I treats
a living document that contradicts shipped behaviour as a defect, not stale text to leave alone.

### Source Code (repository root)

```text
backend/
└── src/
    ├── main/resources/
    │   └── application.yml    # + management.endpoints.web.cors.* (new lines only)
    └── test/java/com/epam/aihelpdesk/
        └── HealthEndpointCorsTest.java   # new: asserts the CORS header on /actuator/health

frontend/
└── src/app/
    ├── app.config.ts                      # + provideHttpClient()
    ├── app.ts                             # + imports ConnectionStatusComponent
    ├── app.html                           # + <app-connection-status />
    └── connection-status/
        ├── connection-status.ts           # ConnectionStatus type + classification function
        ├── connection-status.spec.ts      # classification unit tests (pure function, no HTTP)
        ├── health.service.ts              # polls the endpoint, exposes a signal
        ├── health.service.spec.ts         # HttpTestingController + fake timers
        ├── connection-status.component.ts # renders the signal as the indicator
        ├── connection-status.component.html
        ├── connection-status.component.css
        └── connection-status.component.spec.ts
```

**Structure Decision**: Both changes land inside the existing `backend/` and `frontend/` roots from
001-project-scaffolding — no new top-level directory. The frontend addition is a single cohesive
`connection-status/` folder (type + service + component, each with its own spec) rather than
spreading across `services/`/`components/` subdirectories the placeholder app doesn't have yet;
those generic subdivisions arrive with the feature that needs enough of them to justify the split,
per the same reasoning 001's plan used for the backend's `health/` package.

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty.

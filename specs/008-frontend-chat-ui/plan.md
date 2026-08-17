# Implementation Plan: Frontend Chat UI

**Branch**: `main` (no feature branch created — no `before_plan` hook is registered, consistent with
every feature since [002-frontend-health-wire](../002-frontend-health-wire/plan.md)) | **Date**:
2026-08-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/008-frontend-chat-ui/spec.md`

## Summary

Turn the static mockup in `docs/rag_chatbot.html` into a live Angular application wired to the four
backend resources already shipped (features 004–007): `POST /chat` for grounded, cited answers,
`GET /documents` for the sidebar, `POST /documents` for upload, `GET /documents/{id}/content` for
download, and `DELETE /documents/{id}` for deletion. Two new signal-based Angular services
(`ChatService`, `DocumentsService`) hold all client-side state; a small set of new components
re-implements the mockup's sidebar and chat panel against that state instead of hardcoded markup.
The only backend change is enabling CORS for `/documents/**` and `/chat` from the frontend's origin
— the same category of change 002 already made for `/actuator/health` — no REST contract changes.

Three requirements shape the approach more than anything else:

- **FR-002/SC-002** — a citation badge is a direct, unaltered mirror of what `POST /chat` returned,
  never something the UI infers or reconstructs from `answer`'s text. This is why `ChatMessage` and
  `Citation` (data-model.md) are simple 1:1 shapes over the backend response, not a re-parsed one.
- **FR-007/FR-011/FR-014/FR-017** — every failure path must resolve to a specific, pre-written,
  human-readable message and leave the user with a next action, never a raw backend code/string and
  never a stuck UI. This is why Decision 5 (research.md) is a closed lookup table with an explicit
  fallback arm, not ad-hoc error handling scattered per call site.
- **FR-020** — document-scoped chat filtering is explicitly out of scope (Clarifications session);
  `ChatService.ask()` always sends `documentIds: null`, with no UI state anywhere that could
  populate it otherwise.

## Technical Context

**Language/Version**: TypeScript via the Angular 21.2.x toolchain (already scaffolded); Java 17 /
Spring Boot 3.5.16 for the one backend CORS change (already scaffolded, see
[001-project-scaffolding/plan.md](../001-project-scaffolding/plan.md))

**Primary Dependencies**: `@angular/common/http` (`HttpClient`, already added to `app.config.ts` by
002) and `rxjs`/`@angular/core` signals (already present) — no new frontend package. No new backend
dependency: the CORS change is a `WebMvcConfigurer`/`CorsRegistry` bean, the same category of
configuration 002 already added for the actuator endpoint (research.md Decision 1).

**Storage**: N/A — all client-side state (conversation, document list, upload/delete/download UI
state) is in-memory, signal-based, and unpersisted beyond the current page load (spec.md
Assumptions; data-model.md).

**Testing**: Vitest (already scaffolded) with `provideHttpClientTesting()` / `HttpTestingController`
for every service (mirroring `health.service.spec.ts` exactly) and `TestBed` + DOM assertions for
every component (mirroring `connection-status.component.spec.ts`) — research.md Decision 8. No new
backend test framework; one small backend test asserting the new CORS headers, mirroring 002's own
CORS test for `/actuator/health`.

**Target Platform**: Same local developer machine as every prior frontend feature — browser at
`http://localhost:4200` talking to the backend at `http://localhost:8080`.

**Project Type**: Web application — this feature adds two new feature areas (`chat/`, `documents/`)
plus a small shared area to the existing `frontend/` project, replaces the placeholder markup in
`app.html`, and makes one small, additive change to `backend/`'s CORS configuration; no new project
or service is introduced.

**Performance Goals**: None beyond the spec's own SC-001 (continuous flow, no manual refresh at any
step) and the Clarifications session's explicit choice of *no* client-side timeout on a pending chat
request — the UI's only obligation is to not add its own latency on top of whatever the backend
takes.

**Constraints**:
- A citation badge MUST reproduce the backend's `sources` entry exactly — document, page label, and
  relevance score — never inventing, dropping, or reordering one (FR-002, SC-002).
- No `documentIds` value is ever sent from any UI state; every `POST /chat` call omits/nulls it
  (FR-020).
- No raw backend `error` code or `message` string is ever rendered to the user (FR-007/FR-011/
  FR-014/FR-017) — every failure maps through Decision 5's closed lookup table.
- Chat history and document-list state are session-local only — no persistence, no cross-tab sync
  (FR-019, spec.md Assumptions).
- The backend CORS allowance (Decision 1) is a hard prerequisite — without it, every HTTP call this
  feature makes fails at the browser level regardless of frontend correctness.

**Scale/Scope**: One developer, one browser tab, four backend resources. Two new services
(`ChatService`, `DocumentsService`), roughly seven to nine new components across `chat/` and
`documents/` (see Project Structure), a small `shared/` module (error-message mapping, download
helper), each with a matching `.spec.ts`; one small backend CORS configuration addition plus one new
backend test.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.4.1**.

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` (with its Clarifications session) precedes this plan; both were validated against the spec quality checklist before planning began. |
| II | Test-Driven Development (Mandatory) | ✅ PASS | Every new service and component gets a `.spec.ts` before/with its implementation (research.md Decision 8), using the codebase's already-proven `HttpTestingController`/`TestBed` pattern; no test requires live Azure credentials or a running backend. |
| III | Grounded Answers (RAG-First) | ✅ PASS — UI-level reinforcement | This feature generates no answers itself, but FR-002/FR-003 require it to render the backend's already-grounded `ChatResponse` verbatim — the UI layer's obligation under this principle is to never let a citation drift from what was actually retrieved, which `data-model.md`'s direct 1:1 `Citation` mapping (no re-derivation) guarantees. |
| IV | No Hallucination (Context Adherence) | ✅ PASS — UI-level reinforcement | FR-003 requires the fixed "documentation does not cover this" wording to render exactly as the backend sends it, with no client-side rewording or embellishment; SC-002 makes "never invented client-side" an explicit, testable outcome for this feature's own component tests. |
| V | Semantic Understanding | ⏭️ N/A — deferred | No retrieval or embedding logic exists in this feature; it consumes `POST /chat`'s already-computed result. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ⏭️ N/A — unaffected | No vector or inference code touched; the frontend calls only its own backend, which is already the sole caller of Azure OpenAI. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ⏭️ N/A — deferred | No retrieval behavior to measure; this feature cannot change retrieval accuracy, only how its result is displayed. |

**Technology Stack compliance**: Angular 21 ✅ (existing scaffold, no version change), Spring Boot 3
✅ (existing scaffold, no version change, CORS-only addition). No dependency substitution — research
Decision 2 explicitly rejects adding a state-management library, and Decision 8 explicitly rejects
adding a new testing library — so the "no replacement without a constitution amendment" rule is not
engaged.

**Post-Phase 1 re-check**: ✅ No change. Design adds two new frontend services, a handful of
presentational components, and one backend CORS configuration bean — no new external dependency, no
persistent data model, no principle pre-empted or violated. The Phase 1 artifacts (data-model.md,
contracts/frontend-service-contract.md, quickstart.md) introduce no shape or requirement that
contradicts anything evaluated above.

**Gate result**: PASS — no violations, no justifications required.

## Project Structure

### Documentation (this feature)

```text
specs/008-frontend-chat-ui/
├── plan.md                              # This file (/speckit-plan command output)
├── research.md                          # Phase 0 output — 8 decisions
├── data-model.md                        # Phase 1 output — client-side types + service state shapes
├── quickstart.md                        # Phase 1 output — per-story manual validation + `npm test`
├── contracts/
│   └── frontend-service-contract.md     # Phase 1 output — ChatService/DocumentsService contract
├── checklists/
│   └── requirements.md                  # Spec quality checklist (from /speckit-specify + /speckit-clarify)
└── tasks.md                             # Phase 2 output (/speckit-tasks command — NOT created by /speckit-plan)
```

### Source Code (repository root)

This is the existing web application layout (`backend/` + `frontend/`, established by
001-project-scaffolding) — no new top-level project. New/changed paths only:

```text
backend/
└── src/main/java/com/epam/aihelpdesk/
    └── config/
        └── WebCorsConfig.java            # NEW — CORS for /documents/**, /chat (research Decision 1)
    (src/test/... — one new test asserting the CORS headers, mirroring 002's actuator CORS test)

frontend/
└── src/app/
    ├── app.ts / app.html / app.css       # CHANGED — shell now composes the sidebar + chat panel
    │                                       (replacing the "PoC functionality is not yet implemented"
    │                                       placeholder), still hosting the existing
    │                                       <app-connection-status /> inside the sidebar header
    │
    ├── chat/
    │   ├── chat-message.ts               # ChatMessage/Citation types + ChatResponse→Citation mapping
    │   ├── chat-message.spec.ts
    │   ├── chat.service.ts               # ChatService (messages, pending, ask()) — data-model.md
    │   ├── chat.service.spec.ts
    │   └── chat-view/
    │       ├── chat-view.component.ts/.html/.css        # composes message list + input
    │       ├── chat-view.component.spec.ts
    │       ├── message-bubble.component.ts/.html/.css   # one ChatMessage, incl. citation badges
    │       ├── message-bubble.component.spec.ts
    │       ├── chat-input.component.ts/.html/.css       # textarea + send button + FR-004/FR-005 validation
    │       └── chat-input.component.spec.ts
    │
    ├── documents/
    │   ├── document.ts                   # DocumentSummary type + contentType→icon mapping
    │   ├── document.spec.ts
    │   ├── documents.service.ts          # DocumentsService (documents, uploading, uploadError, upload/remove/refresh)
    │   ├── documents.service.spec.ts
    │   └── document-sidebar/
    │       ├── document-sidebar.component.ts/.html/.css  # header + list + empty state + upload control
    │       ├── document-sidebar.component.spec.ts
    │       ├── document-item.component.ts/.html/.css     # one row: icon, filename, hover download/delete,
    │       │                                                inline confirm (research Decision 4)
    │       └── document-item.component.spec.ts
    │
    └── shared/
        ├── api-error.ts                  # Decision 5's closed error-code → message lookup (per contract)
        ├── api-error.spec.ts
        ├── file-download.ts              # Decision 3's downloadDocument() helper
        └── file-download.spec.ts
```

**Structure Decision**: Follows the existing `frontend/src/app/<feature>/` convention
`connection-status/` already established (feature module = types + service + component(s), each
co-located with its `.spec.ts`) — extended with one level of nesting (`chat-view/`,
`document-sidebar/`) since each of `chat/` and `documents/` now has more than one component, unlike
002's single-component `connection-status/`. `shared/` is new, holding the two small
cross-cutting pieces (error mapping, download helper) neither feature module owns exclusively. The
backend gets exactly one new file (`WebCorsConfig.java`) in a new `config/` package, since no
existing package is a natural home for a concern that spans both `ingestion` and `chat`.

## Complexity Tracking

*No entries — the Constitution Check above recorded no violations requiring justification.*

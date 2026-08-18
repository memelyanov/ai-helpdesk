# Implementation Plan: Chat Trace Dialog

**Branch**: `main` (no feature branch created — no `before_plan` hook is registered, consistent with
every feature since [002-frontend-health-wire](../002-frontend-health-wire/plan.md)) | **Date**:
2026-08-17 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-chat-trace-dialog/spec.md`

## Summary

Give the chat UI a way to show the diagnostic trace feature 009 already returns from `POST /chat`.
`ChatService` requests it by default (`includeTrace: true`, FR-011) and carries whatever
`ChatResponse.trace` comes back straight onto its `ChatMessage`, unmodified. `MessageBubbleComponent`
shows a small "View diagnostic trace" control under an assistant response's sources whenever that
message has trace data, opening a new `TraceDialogComponent` that walks through every recorded stage
in order — including the ones that never ran, when the pipeline stopped early (User Story 2). A new
`TraceToggleComponent`, self-contained the same way `ConnectionStatusComponent` already is, lets the
user turn trace collection off (or back on) from the chat header. No backend change: this feature is a
pure consumer of the `POST /chat` contract feature 009 already shipped and merged.

Three things shape the approach more than anything else:

- **FR-002/FR-011** — the response's own `trace` field, not any client-side flag, is what decides
  whether a message gets a trace control. `includeTrace` only decides what's *requested*; whether a
  given `ChatMessage` ends up with one is still just "does it have `trace` data," the same pass-through
  discipline 008 already established for `sources`/`Citation`.
- **FR-004/FR-012 (009)** — the dialog renders exactly what the API returned: full passage text, the
  exact prompt, the raw response, never summarized or reworded client-side. This is a UI feature with
  nothing of its own to get wrong about *content* — its job is presentation, not interpretation.
- **Testing under jsdom** — the project's pinned jsdom (28.1.0) does not implement
  `HTMLDialogElement.prototype.showModal`/`close` (verified directly against the frontend's own
  `node_modules/jsdom` before writing this plan). `TraceDialogComponent` is designed so every close
  path degrades to a component-level fallback when the native methods are absent (research Decision
  1), so `.spec.ts` tests exercise FR-006 without needing a real browser, while production code still
  gets the native modal's free focus trap, Escape-to-close, and stacking behavior.

## Technical Context

**Language/Version**: TypeScript via the Angular 21.2.x toolchain (already scaffolded, unchanged since
[008-frontend-chat-ui](../008-frontend-chat-ui/plan.md)) — no backend change, so no Java involved.

**Primary Dependencies**: None new. The native `<dialog>` element and the `Clipboard` API
(`navigator.clipboard.writeText`) are both browser platform features, not npm packages — both used
behind a feature-detection guard (research Decisions 1 and 6) rather than a polyfill dependency.

**Storage**: N/A — trace data and the trace-collection toggle are in-memory only, scoped to the current
page load, same as every other piece of `ChatService` state (spec.md Assumptions).

**Testing**: Vitest + `TestBed`, the same pattern 008 established (`HttpTestingController` for
`ChatService`, DOM assertions via `fixture.nativeElement` for components) — no new test tooling. The
one adjustment: `TraceDialogComponent`'s tests drive its fallback close path rather than the real
`HTMLDialogElement.showModal`/`close`, since jsdom 28.1.0 has neither (research Decision 1); native
Escape-key and backdrop-click dismissal are covered by quickstart.md's manual validation instead, not
by an automated test.

**Target Platform**: Same local developer setup as every prior frontend feature — browser at
`http://localhost:4200` talking to the backend at `http://localhost:8080`.

**Project Type**: Web application — frontend-only addition to the existing `frontend/` project's
`chat/` feature area; no new project, no backend change.

**Performance Goals**: None beyond not adding latency to the existing chat flow — opening the trace
dialog is a pure client-side render of data the response already carried, with no additional network
call (SC-001: reachable in one click).

**Constraints**:
- `TraceDialogComponent` renders `ChatResponse.trace` verbatim — no summarizing, truncating, or
  rewording of passage text, the assembled prompt, or the raw model response (FR-004; 009's own
  FR-012/FR-016 "trace never changes what's shown" carried through to this layer).
- No backend change of any kind — this feature's only prerequisite is that feature 009 (already merged
  to `main`) is deployed, since it is the sole source of `trace` data.
- Trace collection defaults to on (FR-011, per Clarifications) — every `POST /chat` call sends
  `includeTrace: true` unless the user has turned the control off.
- The trace control and dialog never alter `answer`, `sources`, or the rest of the conversation
  (FR-007; mirrors 009's own FR-016 non-goal at the UI layer).

**Scale/Scope**: One developer, one browser tab, zero new backend endpoints. Two new components
(`TraceDialogComponent`, `TraceToggleComponent`), two changed components
(`MessageBubbleComponent`, `App`), one changed service (`ChatService`), one changed type file
(`chat-message.ts`) — each with a matching `.spec.ts`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.4.1**.

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md`, with its Clarifications session, precedes this plan; the spec quality checklist passed before `/speckit-clarify` and again after. |
| II | Test-Driven Development (Mandatory) | ✅ PASS | Every new/changed service and component gets a `.spec.ts` written against the behavior in `data-model.md`/`contracts/`, reusing 008's proven `HttpTestingController`/`TestBed` pattern (research Decision 1's testing note); no test requires a live backend or Azure credentials. |
| III | Grounded Answers (RAG-First) | ⏭️ N/A — UI-level reinforcement | This feature generates nothing; it exposes, verbatim, the retrieval/prompt/response detail 009's pipeline already produced, making that pipeline's grounding *visible* rather than changing it. |
| IV | No Hallucination (Context Adherence) | ⏭️ N/A — UI-level reinforcement | The dialog's `model_response_received` step renders the raw response exactly as returned, including the fixed "not covered" case surfaced upstream in `answer` — no client-side rewording anywhere in this feature. |
| V | Semantic Understanding | ⏭️ N/A — deferred | No retrieval or embedding logic in this feature; it displays 009's already-computed `vector_search_completed`/`results_filtered` steps as-is. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ⏭️ N/A — unaffected | No vector or inference code touched. Worth naming explicitly: the trace surfaces full passage text and the raw model response in the browser (DevTools/Network tab included) whenever trace collection is on — this is not new exposure this feature introduces; it is 009's own deliberate, already-approved opt-in API contract, now reachable one click sooner. FR-011's on-by-default choice makes it reachable *more often* than before, which is exactly the behavior `/speckit-clarify` confirmed. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ⏭️ N/A — unaffected | No retrieval behavior changes; nothing here can move this metric. |

**Technology Stack compliance**: Angular 21 ✅ (existing scaffold, no version change). No dependency
substitution or addition — the "no replacement without a constitution amendment" rule is not engaged.

**Post-Phase 1 re-check**: ✅ No change. Design adds two small frontend components and extends one
existing service and one existing component's template — no new external dependency, no persistent
data model, no principle pre-empted or violated. The Phase 1 artifacts (data-model.md,
contracts/frontend-trace-contract.md, quickstart.md) introduce nothing that contradicts anything
evaluated above.

**Gate result**: PASS — no violations, no justifications required.

## Project Structure

### Documentation (this feature)

```text
specs/010-chat-trace-dialog/
├── plan.md                              # This file (/speckit-plan command output)
├── research.md                          # Phase 0 output — 6 decisions
├── data-model.md                        # Phase 1 output — client-side types + service/component state
├── quickstart.md                        # Phase 1 output — per-story manual validation + `npm test`
├── contracts/
│   └── frontend-trace-contract.md       # Phase 1 output — TraceDialogComponent/TraceToggleComponent/ChatService contract
├── checklists/
│   └── requirements.md                  # Spec quality checklist (from /speckit-specify + /speckit-clarify)
└── tasks.md                             # Phase 2 output (/speckit-tasks command — NOT created by /speckit-plan)
```

### Source Code (repository root)

This is the existing `frontend/` project established by 001-project-scaffolding and extended by
008-frontend-chat-ui — no new project, no backend change. New/changed paths only:

```text
frontend/
└── src/app/
    ├── app.ts / app.html / app.css       # CHANGED — chat-header now also hosts <app-trace-toggle />
    │
    └── chat/
        ├── chat-message.ts               # CHANGED — + ChatTraceStep type; ChatMessage/ChatRequestBody/
        │                                    ChatResponse each gain their trace-related field
        ├── chat-message.spec.ts          # CHANGED
        ├── chat.service.ts               # CHANGED — includeTrace signal + setIncludeTrace(), ask()
        │                                    sends includeTrace, settle() carries response.trace through
        ├── chat.service.spec.ts          # CHANGED
        ├── trace-toggle.component.ts/.html/.css   # NEW — self-contained control (injects ChatService
        │                                             directly, same pattern as ConnectionStatusComponent)
        ├── trace-toggle.component.spec.ts         # NEW
        │
        └── chat-view/
            ├── message-bubble.component.ts/.html/.css   # CHANGED — trace control under sources,
            │                                               owns the open/closed state of its own dialog
            ├── message-bubble.component.spec.ts         # CHANGED
            ├── trace-dialog.component.ts/.html/.css     # NEW — renders steps + not-reached placeholders,
            │                                               copy-to-clipboard, close via button/backdrop/Escape
            └── trace-dialog.component.spec.ts           # NEW
```

**Structure Decision**: Follows the existing `frontend/src/app/<feature>/` convention exactly as 008
extended it — `trace-dialog.component.*` sits inside `chat-view/` alongside `message-bubble.component.*`
and `chat-input.component.*` since it's part of that same message-rendering cluster, while
`trace-toggle.component.*` sits one level up in `chat/` (next to `chat.service.ts`) because it's
rendered from `app.html`'s header, outside the `chat-view` component tree — mirroring why
`connection-status/` lives as its own top-level area rather than nested inside `documents/`. No new
top-level feature folder, no new shared module.

## Complexity Tracking

*No entries — the Constitution Check above recorded no violations requiring justification.*

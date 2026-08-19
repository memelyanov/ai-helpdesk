<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/010-chat-trace-dialog/plan.md](specs/010-chat-trace-dialog/plan.md)

Supporting design artifacts for the active feature:
- [spec.md](specs/010-chat-trace-dialog/spec.md) — three prioritized user stories (inspect a response's diagnostic trace via a dialog opened from a control under its sources panel; understand a short-circuited trace where the pipeline stopped early, with the un-reached stages clearly marked instead of silently missing; turn diagnostic trace collection off when it isn't needed) turning feature 009's opt-in, API-only trace into an actual frontend `TraceDialogComponent`; one Clarifications entry (Session 2026-08-17: trace collection is on by default — opt-out via a visible toggle — reversing the initial opt-in/off-by-default draft)
- [research.md](specs/010-chat-trace-dialog/research.md) — 6 decisions: a native `<dialog>` element with every close path (button/backdrop/Escape) funneled through one method that falls back to a direct emission when `HTMLDialogElement.showModal`/`close` are unavailable (confirmed missing in this repo's pinned jsdom 28.1.0, so component tests exercise the fallback instead of the real native behavior), `ChatService` owning `includeTrace` state with a self-contained `TraceToggleComponent` reading/writing it directly (mirrors `ConnectionStatusComponent`/`HealthService`), `ChatTraceStep.detail` staying a loosely-typed `Record<string, unknown>` with all stage-specific interpretation inside `TraceDialogComponent` (mirrors 009's own `Map<String,Object>` choice), `ChatMessage.trace` as a direct pass-through of `ChatResponse.trace` with no mapping function, client-side derivation of "not reached" stages from a fixed six-stage order positionally zipped against the trace array, and feature-detected `navigator.clipboard.writeText` with a silent no-op fallback for the copy affordance
- [data-model.md](specs/010-chat-trace-dialog/data-model.md) — no persistence; new client-side `ChatTraceStep` type, `ChatMessage`/`ChatRequestBody`/`ChatResponse` each gain one field (`trace` out/in, `includeTrace` in), `ChatService` gains `includeTrace`/`setIncludeTrace()`, and `TraceDialogComponent`'s derived `displayedStages` view model
- [contracts/frontend-trace-contract.md](specs/010-chat-trace-dialog/contracts/frontend-trace-contract.md) — the additive delta on top of feature 008's frontend service contract; `TraceDialogComponent`/`TraceToggleComponent`'s public API and `MessageBubbleComponent`'s extended rendering contract
- [quickstart.md](specs/010-chat-trace-dialog/quickstart.md) — bring-up (existing feature 008/009 prerequisites), per-user-story manual validation including the native Escape/backdrop-dismissal checks the automated suite can't cover under jsdom, `npm test` for the automated suite

Prior features, still the source of truth for their own scope:
- [specs/009-chat-diagnostic-trace/plan.md](specs/009-chat-diagnostic-trace/plan.md) — the backend
  `POST /chat` `includeTrace`/`trace` contract this feature is the first UI consumer of; its
  `ChatTraceStep` stage vocabulary and per-stage `detail` keys
  ([data-model.md](specs/009-chat-diagnostic-trace/data-model.md)) are what this feature's
  `TraceDialogComponent` renders verbatim, never re-derived.
- [specs/008-frontend-chat-ui/plan.md](specs/008-frontend-chat-ui/plan.md) — the Angular chat UI
  (`ChatService`, `MessageBubbleComponent`, the `chat-header`) this feature extends rather than
  replaces; its `ChatMessage`/`Citation` pass-through pattern is what this feature's `ChatMessage.trace`
  field follows.
- [specs/001-project-scaffolding/plan.md](specs/001-project-scaffolding/plan.md) — database, backend,
  frontend skeleton; [contracts/health-api.md](specs/001-project-scaffolding/contracts/health-api.md)
  — the health endpoint response shape.
- [specs/002-frontend-health-wire/plan.md](specs/002-frontend-health-wire/plan.md) — the frontend
  connection-status indicator wired to that health endpoint; this feature reuses
  `<app-connection-status />` as-is inside the new sidebar header, and follows the same
  `HttpTestingController`/signal-based-service pattern its `HealthService` established.
- [specs/003-document-vector-schema/plan.md](specs/003-document-vector-schema/plan.md) — the
  `documents`/`chunks` schema underlying every endpoint this feature calls.
- [specs/004-document-ingestion-endpoint/plan.md](specs/004-document-ingestion-endpoint/plan.md) —
  `POST /documents`; its
  [contracts/ingestion-api-contract.md](specs/004-document-ingestion-endpoint/contracts/ingestion-api-contract.md)
  documents the `4xx`/`503` error vocabulary this feature's upload error-message mapping is built
  from.
- [specs/005-document-listing-download/plan.md](specs/005-document-listing-download/plan.md) —
  `GET /documents` and `GET /documents/{id}/content`, the sidebar list and download endpoints this
  feature wires up directly.
- [specs/006-document-delete/plan.md](specs/006-document-delete/plan.md) —
  `DELETE /documents/{id}`, the deletion endpoint this feature's sidebar delete action calls.
- [specs/007-chat-endpoint/plan.md](specs/007-chat-endpoint/plan.md) — `POST /chat`; this feature's
  `ChatService` is the first real caller of that endpoint, and its `ChatResponse`/`SourceCitation`
  shapes ([data-model.md](specs/007-chat-endpoint/data-model.md)) are what `ChatMessage`/`Citation`
  directly mirror.

Governance: [.specify/memory/constitution.md](.specify/memory/constitution.md) v1.4.1 — seven
principles, Spec-First and TDD first among them; Azure OpenAI is the mandated inference provider;
v1.4.0 added the Code & Documentation Language Standard (English-only); v1.4.1 generalized the Error
Handling & Logging section's status-code wording (a wording fix, no new constraint).

Two constraints that shape this feature's design:
- The trace dialog is read-only and purely presentational — it MUST NOT alter `answer`, `sources`, or
  any other part of the conversation, and every piece of content it shows (passage text, the assembled
  prompt, the raw model response) MUST render exactly as `POST /chat` returned it, never summarized,
  reworded, or re-derived client-side (spec.md FR-004/FR-007).
- Diagnostic trace collection is on by default (`includeTrace: true` sent on every request unless the
  user turns it off via the trace toggle) — reversed from the initial opt-in/off-by-default draft by
  `/speckit-clarify` (spec.md Clarifications, Session 2026-08-17; FR-011/FR-012).
<!-- SPECKIT END -->

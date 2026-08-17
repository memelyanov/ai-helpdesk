<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/008-frontend-chat-ui/plan.md](specs/008-frontend-chat-ui/plan.md)

Supporting design artifacts for the active feature:
- [spec.md](specs/008-frontend-chat-ui/spec.md) — five prioritized user stories (ask a grounded/cited question, browse the live document list, upload, download, delete) turning `docs/rag_chatbot.html`'s static mockup into a live Angular UI; three Clarifications entries (Session 2026-08-16: citation badges show the relevance score as well as document+page; a pending chat request shows an indefinite loading indicator with no client-side timeout or cancel; sidebar rows show filename only, matching the mockup)
- [research.md](specs/008-frontend-chat-ui/research.md) — 8 decisions: a new backend CORS allowance for `/documents/**` and `/chat` (mirroring feature 002's actuator-only CORS, since no other CORS config exists in the codebase), signal-based Angular services with no new state-management dependency, downloads use the filename already in hand rather than parsing `Content-Disposition` (avoids a CORS header exposure), an inline per-row two-step delete confirmation instead of `window.confirm()` (testability), a closed error-code→message lookup table so no raw backend text is ever shown, client-side question-length validation mirroring the backend's fixed 1000-character constant, relevance score rendered as a rounded percentage, and Vitest + `HttpTestingController`/`TestBed` reused as-is with no new test tooling
- [data-model.md](specs/008-frontend-chat-ui/data-model.md) — no persistence; client-side `ChatMessage`/`Citation`/`DocumentSummary` types and the `ChatService`/`DocumentsService` signal-based state shapes, each a direct mirror of an existing backend response shape
- [contracts/frontend-service-contract.md](specs/008-frontend-chat-ui/contracts/frontend-service-contract.md) — the `ChatService`/`DocumentsService` public surface every component is built against, no new REST contract
- [quickstart.md](specs/008-frontend-chat-ui/quickstart.md) — bring-up (`npm start` + backend + the new CORS prerequisite), per-user-story manual validation, `npm test` for the automated suite

Prior features, still the source of truth for their own scope:
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
- A citation badge MUST reproduce feature 007's `POST /chat` response exactly — document, page
  label, and relevance score — never inventing, dropping, or reordering a source (spec.md FR-002,
  SC-002); document-scoped chat filtering is explicitly out of scope, so every request this feature
  sends omits `documentIds` (FR-020).
- No raw backend `error` code or `message` string is ever rendered to the user for any failure
  (chat, upload, download, delete) — every failure maps through a closed, pre-written lookup table
  with an explicit fallback, so the UI can never surface backend internals or be left in an
  unexplained stuck state (spec.md FR-007/FR-011/FR-014/FR-017).
<!-- SPECKIT END -->

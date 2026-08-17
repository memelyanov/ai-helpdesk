<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/009-chat-diagnostic-trace/plan.md](specs/009-chat-diagnostic-trace/plan.md)

Supporting design artifacts for the active feature:
- [spec.md](specs/009-chat-diagnostic-trace/spec.md) — three prioritized user stories (finish the ad hoc backend logging so every chat pipeline step is reliably recorded; expose that same detail via an opt-in `POST /chat` trace; leave default behavior byte-identical) finishing manual logging work already started across `ChatService`/`ChatRetrievalRepository`/`ChatCompletionClient`/`EmbeddingClient`/`AzureOpenAiProperties`; two Clarifications entries (Session 2026-08-17: API-only, no new chat-UI panel in this feature; trace steps carry full raw content — passage text, exact prompt, raw model response — not just summarized metadata)
- [research.md](specs/009-chat-diagnostic-trace/research.md) — 6 decisions: a per-request correlation id via SLF4J MDC (not a parameter threaded through every log call), `ChatTraceStep.detail` as a small per-stage `Map<String, Object>` rather than six typed records, `ChatCompletionClient.complete(...)` returning a new `ChatCompletionResult` record so `ChatService` sees the exact prompt/response text, persistent logs staying at summary level always while full raw content is API-response-only (constitution's "request/response summaries" wording), `ChatService` as the single place that both builds the trace and emits every stage-summary log line (superseding the ad hoc `.forEach` dumps and the misattributed `AzureOpenAiProperties` logger), and a new `ChatServiceTest` unit test (first direct, mocked-collaborator test of `ChatService`)
- [data-model.md](specs/009-chat-diagnostic-trace/data-model.md) — no persistence; `ChatRequest`/`ChatResponse` each gain one optional field (`includeTrace` in, `trace` out), the new in-memory `ChatTraceStep`/`ChatCompletionResult` shapes, and a table of exactly what reaches the persistent log vs. the opt-in trace
- [contracts/chat-diagnostic-trace-contract.md](specs/009-chat-diagnostic-trace/contracts/chat-diagnostic-trace-contract.md) — the additive delta on top of feature 007's `POST /chat` contract, which remains the complete baseline
- [quickstart.md](specs/009-chat-diagnostic-trace/quickstart.md) — bring-up (existing feature 007 prerequisites), per-user-story manual validation including a log-correlation check and a credential-safety check, `mvnw test` for the automated suite

Prior features, still the source of truth for their own scope:
- [specs/008-frontend-chat-ui/plan.md](specs/008-frontend-chat-ui/plan.md) — the Angular chat UI this
  feature's backend-only change does not touch (spec.md Clarifications: API-only, no UI panel);
  `ChatService`'s frontend contract is unchanged by this feature.
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
- Enabling `includeTrace` MUST NOT change `answer`, `sources`, or any other existing `ChatResponse`
  field's value, and its absence/false MUST leave the response byte-identical to feature 007's
  original contract — no `"trace"` key at all, not even `null` (spec.md FR-010/FR-016).
- Full raw content (retrieved passage text, the exact prompt, the raw model response) belongs only in
  the opt-in API response, never in the persistent server log — logs stay at the constitution's
  mandated "summary" level for every request regardless of `includeTrace` (research.md Decision 4).
<!-- SPECKIT END -->

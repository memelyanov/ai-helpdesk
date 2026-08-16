<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/007-chat-endpoint/plan.md](specs/007-chat-endpoint/plan.md)

Supporting design artifacts for the active feature:
- [spec.md](specs/007-chat-endpoint/spec.md) — requirements for `POST /chat`; two Clarifications entries (Session 2026-08-16: max question length is 1000 characters; a citation for a page-less/plain-text source shows a fixed "no page structure" indicator, never a number or `null`)
- [research.md](specs/007-chat-endpoint/research.md) — 9 decisions: a new `chat` package/controller (a distinct resource from `/documents`, not another verb on `DocumentController`), `EmbeddingClient` (feature 004) gains a reused `embedQuery(String)` method with its ingestion-scoped exception translated at the package boundary, a new `ChatCompletionClient` built the same hand-wired way `AzureOpenAiConnectivityIT` already proves works, retrieval reuses feature 003's documented similarity-search query verbatim (top-K=4 in SQL, the 0.5 similarity threshold applied afterward in code, not in the `WHERE` clause), citations are computed from retrieval results grouped by `(documentId, pageNumber)` — never parsed from the model's answer text, the "not covered" outcome is a plain `200` using the constitution's own fixed wording, a new chat-scoped exception hierarchy (`InvalidChatRequestException` → `400`, `ChatProcessingException` → `503`) rather than reusing `ingestion`'s `Document*`/`Ingestion*` classes, three-tier test strategy (`contract` + `db` + `azure`, no new dependency or `pom.xml` profile)
- [data-model.md](specs/007-chat-endpoint/data-model.md) — no new persisted entities (reads feature 003's existing `documents`/`chunks`); `ChatRequest`/`ChatResponse`/`SourceCitation`/`ChatErrorResponse` DTOs, `RetrievedChunk` internal shape, fixed `TOP_K=4`/`SIMILARITY_THRESHOLD=0.5`/`MAX_QUESTION_LENGTH=1000` constants
- [contracts/chat-api-contract.md](specs/007-chat-endpoint/contracts/chat-api-contract.md) — `POST /chat` request/response/error shape
- [quickstart.md](specs/007-chat-endpoint/quickstart.md) — bring-up, per-user-story `curl` validation, the full evaluation-set accuracy check (SC-001)

Prior features, still the source of truth for their own scope:
- [specs/001-project-scaffolding/plan.md](specs/001-project-scaffolding/plan.md) — database, backend,
  frontend skeleton; [contracts/health-api.md](specs/001-project-scaffolding/contracts/health-api.md)
  — the health endpoint response shape.
- [specs/002-frontend-health-wire/plan.md](specs/002-frontend-health-wire/plan.md) — the frontend
  connection-status indicator wired to that health endpoint.
- [specs/003-document-vector-schema/plan.md](specs/003-document-vector-schema/plan.md) — the
  `documents`/`chunks` schema this feature reads from; its
  [contracts/similarity-search-contract.md](specs/003-document-vector-schema/contracts/similarity-search-contract.md)
  defines the exact query shape this feature is the first to actually run.
- [specs/004-document-ingestion-endpoint/plan.md](specs/004-document-ingestion-endpoint/plan.md) —
  `POST /documents` and `EmbeddingClient`, whose embedding-deployment construction this feature
  reuses for query embedding; its
  [contracts/ingestion-api-contract.md](specs/004-document-ingestion-endpoint/contracts/ingestion-api-contract.md)
  documents the `4xx`/`503` error-vocabulary pattern this feature's own `400`/`503` split follows.
- [specs/005-document-listing-download/plan.md](specs/005-document-listing-download/plan.md) —
  `GET /documents` and `GET /documents/{id}/content`; a citation's `documentId` in this feature's
  response is the same identifier a caller passes to the download endpoint.
- [specs/006-document-delete/plan.md](specs/006-document-delete/plan.md) — `DELETE /documents/{id}`;
  established the pattern (reused here) of a new sibling exception hierarchy rather than reusing an
  existing feature's Javadoc-scoped exception classes.

Governance: [.specify/memory/constitution.md](.specify/memory/constitution.md) v1.4.1 — seven
principles, Spec-First and TDD first among them; Azure OpenAI is the mandated inference provider;
v1.4.0 added the Code & Documentation Language Standard (English-only); v1.4.1 generalized the Error
Handling & Logging section's status-code wording (a wording fix, no new constraint).

Two constraints that shape this feature's design:
- The generated answer MUST be built only from retrieved passages that meet the 0.5 similarity
  threshold; when nothing meets it (including an empty corpus or a document filter matching nothing),
  the response MUST be the constitution's fixed "documentation does not cover this" wording with no
  sources — a plain `200`, never confused with the `503 processing_failed`/`provider_unconfigured`
  outcome of an actual system failure.
- Every citation in a successful answer MUST be a document-and-page that genuinely had a retrieved
  passage included in the generation prompt (computed from retrieval, never parsed from the model's
  own text) — a page-less source shows the fixed `"no page structure"` indicator, never a number or
  `null`.
<!-- SPECKIT END -->

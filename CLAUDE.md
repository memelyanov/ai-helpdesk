<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/004-document-ingestion-endpoint/plan.md](specs/004-document-ingestion-endpoint/plan.md)

Supporting design artifacts for the active feature:
- [spec.md](specs/004-document-ingestion-endpoint/spec.md) — requirements, one resolved clarification (parsed documents with no extractable text are stored with zero chunks, not rejected)
- [research.md](specs/004-document-ingestion-endpoint/research.md) — 9 decisions: lean Tika modules (`tika-parser-pdf-module`/`tika-parser-txt-module`, not the standard-package aggregator), page-aware PDF extraction via Tika's `<div class="page">` SAX markers, token-accurate chunking via `jtokkit`'s `cl100k_base` encoding, one batched embedding call per document, transaction opened only after every embedding is in hand, a hand-built `AzureOpenAiEmbeddingModel` gated by its own completeness check (not Spring AI's disabled auto-config bean), `pgvector`'s Java helper over plain JDBC (no JPA), `400` vs `503` status-code split for invalid-input vs processing-failure, four-tier test strategy reusing the `db`/`azure` tag convention
- [data-model.md](specs/004-document-ingestion-endpoint/data-model.md) — no new persisted entities (writes into feature 003's `documents`/`chunks`); request/response DTOs and in-memory pipeline shapes only
- [contracts/ingestion-api-contract.md](specs/004-document-ingestion-endpoint/contracts/ingestion-api-contract.md) — `POST /documents` request/response/error shape, the `4xx`-vs-`503` retry-guidance contract
- [quickstart.md](specs/004-document-ingestion-endpoint/quickstart.md) — bring-up, per-user-story `curl`/`psql` validation, full sample-corpus ingestion

Prior features, still the source of truth for their own scope:
- [specs/001-project-scaffolding/plan.md](specs/001-project-scaffolding/plan.md) — database, backend,
  frontend skeleton; [contracts/health-api.md](specs/001-project-scaffolding/contracts/health-api.md)
  — the health endpoint response shape.
- [specs/002-frontend-health-wire/plan.md](specs/002-frontend-health-wire/plan.md) — the frontend
  connection-status indicator wired to that health endpoint.
- [specs/003-document-vector-schema/plan.md](specs/003-document-vector-schema/plan.md) — the
  `documents`/`chunks` schema this feature writes into; its
  [contracts/](specs/003-document-vector-schema/contracts/) are the guarantees this feature's writes
  must satisfy, unchanged.

Governance: [.specify/memory/constitution.md](.specify/memory/constitution.md) v1.4.0 — seven
principles, Spec-First and TDD first among them; Azure OpenAI is the mandated inference provider;
v1.4.0 added the Code & Documentation Language Standard (English-only).

Two constraints that shape this feature's design:
- All of a document's chunk rows, plus the document row itself, MUST commit in exactly one
  transaction — achieved by never opening the transaction until every chunk already has its
  embedding in hand (FR-008/FR-009).
- The `/documents` response status code itself carries the "retry won't help" vs "retry might help"
  signal (`400` vs `503`) — a caller MUST NOT need to parse the error message to know which (FR-011).
<!-- SPECKIT END -->

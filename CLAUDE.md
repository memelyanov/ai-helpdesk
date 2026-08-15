<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/006-document-delete/plan.md](specs/006-document-delete/plan.md)

Supporting design artifacts for the active feature:
- [spec.md](specs/006-document-delete/spec.md) — requirements for `DELETE /documents/{id}`; one Clarifications entry (Session 2026-08-16: an unexpected server-side failure during deletion MUST leave the document and its chunks fully intact and report a distinct `503`-style outcome, never a partial deletion or a "not found" mix-up)
- [research.md](specs/006-document-delete/research.md) — 7 decisions: the endpoint extends feature 004's existing `DocumentController` (same `/documents` resource), route is the bare `DELETE /documents/{id}` (not `/content`), a malformed/nonexistent/already-deleted id all collapse into the same `404 document_not_found` feature 005 established, one `DELETE ... WHERE id = ?` statement's own affected-row-count decides `204` vs `404` (no prior existence check, no race window), no explicit transaction wrapping needed (a single statement is already atomic and the chunk cascade is feature 003's `ON DELETE CASCADE`, a database-level guarantee), a new sibling `DocumentDeletionException` (→ `503 deletion_failed`) rather than reusing the ingestion-scoped `IngestionProcessingException`, no new dependency and no schema change, two-tier test strategy (`contract` + `db`, no `azure` tier needed)
- [data-model.md](specs/006-document-delete/data-model.md) — no new persisted entities (writes a `DELETE` against feature 003's existing `documents`/`chunks`, cascade unchanged); no new response DTO (`204` has no body), one new `error` row (`deletion_failed`) on the existing shared `DocumentErrorResponse`
- [contracts/document-delete-api-contract.md](specs/006-document-delete/contracts/document-delete-api-contract.md) — `DELETE /documents/{id}` request/response/error shape
- [quickstart.md](specs/006-document-delete/quickstart.md) — bring-up, per-user-story `curl` validation, cascade and cross-document isolation checks

Prior features, still the source of truth for their own scope:
- [specs/001-project-scaffolding/plan.md](specs/001-project-scaffolding/plan.md) — database, backend,
  frontend skeleton; [contracts/health-api.md](specs/001-project-scaffolding/contracts/health-api.md)
  — the health endpoint response shape.
- [specs/002-frontend-health-wire/plan.md](specs/002-frontend-health-wire/plan.md) — the frontend
  connection-status indicator wired to that health endpoint.
- [specs/003-document-vector-schema/plan.md](specs/003-document-vector-schema/plan.md) — the
  `documents`/`chunks` schema this feature writes a `DELETE` against; its
  [contracts/](specs/003-document-vector-schema/contracts/) define the `ON DELETE CASCADE`
  guarantee (FR-011) this feature is the first to actually trigger.
- [specs/004-document-ingestion-endpoint/plan.md](specs/004-document-ingestion-endpoint/plan.md) —
  `POST /documents`, the write side of the same resource this feature adds delete to; its
  [contracts/ingestion-api-contract.md](specs/004-document-ingestion-endpoint/contracts/ingestion-api-contract.md)
  documents the `4xx`/`503` half of the error vocabulary this feature's `404`/`503` joins.
- [specs/005-document-listing-download/plan.md](specs/005-document-listing-download/plan.md) —
  `GET /documents` and `GET /documents/{id}/content`, the read side of the same resource; its
  [contracts/document-query-api-contract.md](specs/005-document-listing-download/contracts/document-query-api-contract.md)
  established the malformed-or-nonexistent-id-both-collapse-to-404 pattern this feature reuses.

Governance: [.specify/memory/constitution.md](.specify/memory/constitution.md) v1.4.1 — seven
principles, Spec-First and TDD first among them; Azure OpenAI is the mandated inference provider;
v1.4.0 added the Code & Documentation Language Standard (English-only); v1.4.1 generalized the Error
Handling & Logging section's status-code wording (a wording fix, no new constraint).

Two constraints that shape this feature's design:
- Deletion of a document and its chunks MUST be all-or-nothing — on an unexpected server-side
  failure, nothing is deleted, and the caller MUST receive a `503 deletion_failed` response that is
  never confused with the `404 document_not_found` "nothing to delete" outcome.
- A malformed, nonexistent, or already-deleted `{id}` on `DELETE /documents/{id}` MUST all resolve
  to the same `404 document_not_found` — a caller never needs to distinguish the three to know the
  document isn't there.
<!-- SPECKIT END -->

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/005-document-listing-download/plan.md](specs/005-document-listing-download/plan.md)

Supporting design artifacts for the active feature:
- [spec.md](specs/005-document-listing-download/spec.md) — requirements for `GET /documents` (list) and `GET /documents/{id}/content` (download); no `[NEEDS CLARIFICATION]` markers, all defaults documented in Assumptions
- [research.md](specs/005-document-listing-download/research.md) — 8 decisions: both endpoints extend feature 004's existing `DocumentController` (same `/documents` resource), one `LEFT JOIN`/`GROUP BY` query for the list (never `INNER JOIN`, or zero-chunk documents would silently vanish), download route `GET /documents/{id}/content` returning raw bytes with a safely-encoded `Content-Disposition`, a malformed or nonexistent id both collapse into one `404 document_not_found`, a new read-only `DocumentQueryRepository` kept separate from the write-only `DocumentRepository`, the shared error surface renamed `Ingestion*` → `Document*` to keep its Javadoc accurate now that it serves three endpoints, no new dependency and no schema change, two-tier test strategy (`contract` + `db`, no `azure` tier needed — neither endpoint calls Azure OpenAI)
- [data-model.md](specs/005-document-listing-download/data-model.md) — no new persisted entities (reads feature 003's `documents`/`chunks`, unchanged); response/internal shapes only (`DocumentSummaryResponse`, `DocumentContent`, renamed `DocumentErrorResponse`)
- [contracts/document-query-api-contract.md](specs/005-document-listing-download/contracts/document-query-api-contract.md) — `GET /documents` and `GET /documents/{id}/content` request/response/error shape
- [quickstart.md](specs/005-document-listing-download/quickstart.md) — bring-up, per-user-story `curl` validation, full sample-corpus listing performance check

Prior features, still the source of truth for their own scope:
- [specs/001-project-scaffolding/plan.md](specs/001-project-scaffolding/plan.md) — database, backend,
  frontend skeleton; [contracts/health-api.md](specs/001-project-scaffolding/contracts/health-api.md)
  — the health endpoint response shape.
- [specs/002-frontend-health-wire/plan.md](specs/002-frontend-health-wire/plan.md) — the frontend
  connection-status indicator wired to that health endpoint.
- [specs/003-document-vector-schema/plan.md](specs/003-document-vector-schema/plan.md) — the
  `documents`/`chunks` schema this feature reads from; its
  [contracts/](specs/003-document-vector-schema/contracts/) are the guarantees this feature's reads
  rely on, unchanged.
- [specs/004-document-ingestion-endpoint/plan.md](specs/004-document-ingestion-endpoint/plan.md) —
  `POST /documents`, the write side of the same resource this feature adds the read side to; its
  [contracts/ingestion-api-contract.md](specs/004-document-ingestion-endpoint/contracts/ingestion-api-contract.md)
  documents the `4xx`/`503` half of the error vocabulary this feature's `404` joins.

Governance: [.specify/memory/constitution.md](.specify/memory/constitution.md) v1.4.1 — seven
principles, Spec-First and TDD first among them; Azure OpenAI is the mandated inference provider;
v1.4.0 added the Code & Documentation Language Standard (English-only); v1.4.1 generalized the Error
Handling & Logging section's status-code wording (a wording fix, no new constraint).

Two constraints that shape this feature's design:
- `GET /documents` MUST use a `LEFT JOIN` between `documents` and `chunks`, never `INNER JOIN` — a
  zero-chunk document (feature 004 FR-015's valid outcome) must still appear in the list.
- A malformed or nonexistent `{id}` on `GET /documents/{id}/content` MUST both resolve to the same
  `404 document_not_found` — a caller never needs to distinguish the two to know the document isn't
  retrievable.
<!-- SPECKIT END -->

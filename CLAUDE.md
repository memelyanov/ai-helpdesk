<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/003-document-vector-schema/plan.md](specs/003-document-vector-schema/plan.md)

Supporting design artifacts for the active feature:
- [spec.md](specs/003-document-vector-schema/spec.md) — requirements, three resolved clarifications (cascade delete, per-document `chunk_id`, independent documents on re-upload)
- [research.md](specs/003-document-vector-schema/research.md) — 9 decisions: init-script DDL (no migration tool), `bytea` for originals, `vector(1536)`, constitution-exact metadata column names, `NULL` page convention, per-document `chunk_id` + surrogate PK, `UUID` document id, no ANN index yet, Testcontainers behind a new `verify-db` profile
- [data-model.md](specs/003-document-vector-schema/data-model.md) — `documents` / `chunks` entities, full DDL, state transitions
- [contracts/document-schema.md](specs/003-document-vector-schema/contracts/document-schema.md), [contracts/chunk-schema.md](specs/003-document-vector-schema/contracts/chunk-schema.md), [contracts/similarity-search-contract.md](specs/003-document-vector-schema/contracts/similarity-search-contract.md) — table guarantees and the query shape a future retrieval feature can rely on
- [quickstart.md](specs/003-document-vector-schema/quickstart.md) — bring-up, stale-volume warning, per-user-story `psql` validation

Prior features, still the source of truth for their own scope:
- [specs/001-project-scaffolding/plan.md](specs/001-project-scaffolding/plan.md) — database, backend,
  frontend skeleton; [contracts/health-api.md](specs/001-project-scaffolding/contracts/health-api.md)
  — the health endpoint response shape.
- [specs/002-frontend-health-wire/plan.md](specs/002-frontend-health-wire/plan.md) — the frontend
  connection-status indicator wired to that health endpoint.

Governance: [.specify/memory/constitution.md](.specify/memory/constitution.md) v1.4.0 — seven
principles, Spec-First and TDD first among them; Azure OpenAI is the mandated inference provider;
v1.4.0 added the Code & Documentation Language Standard (English-only).

Two constraints that shape this feature's design:
- A chunk MUST NOT exist without a valid source document — enforced structurally via
  `ON DELETE CASCADE`, not application-level cleanup (FR-007/FR-011).
- Every similarity-search result MUST carry its source document's identifier with no join required
  — `document_id`/`source_filename`/`page_number` live directly on every `chunks` row (FR-009).
<!-- SPECKIT END -->

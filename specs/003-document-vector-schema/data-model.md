# Phase 1 Data Model: Document & Vector Storage Schema

**Date**: 2026-08-15 | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

This is the first feature to create domain tables (`specs/001-project-scaffolding/data-model.md`
left the schema intentionally empty beyond the `vector` extension). Both tables below are created
by `db/init/02-documents-and-chunks.sql` — see research Decision 1 for why an init script rather
than a migration tool.

## Entity: Document

Represents one uploaded source file, stored in full. Maps to spec.md's "Document" key entity and
satisfies User Story 1.

| Column | Type | Nullable | Default | Requirement |
|---|---|---|---|---|
| `id` | `UUID` | no | `gen_random_uuid()` | FR-002 — stable identifier, distinct from filename (research Decision 7) |
| `filename` | `TEXT` | no | — | FR-004 — original filename, for correct download naming |
| `content_type` | `TEXT` | no | — | FR-004 — original file type (`text/plain` or `application/pdf`) |
| `content` | `BYTEA` | no | — | FR-001/FR-003 — the full original file content (research Decision 2) |
| `uploaded_at` | `TIMESTAMPTZ` | no | `now()` | FR-005 — when the document was uploaded |

**Primary key**: `id`.

**Validation rules**:
- `content_type` MUST be one of `text/plain`, `application/pdf` — the two formats this schema is
  scoped for (spec Assumptions). Enforced with a `CHECK` constraint, which is also what satisfies
  FR-015 (reject any other file type): the constraint rejects the insert outright rather than
  requiring the caller to pre-validate; a future feature that adds a format widens this constraint,
  not works around it.
- `content` MUST NOT be empty (`CHECK (octet_length(content) > 0)`) — an empty row would satisfy
  "the document exists" while failing FR-003's byte-for-byte retrieval expectation the moment
  anyone tries to use it.

**Deletion (FR-014) needs no schema of its own.** `DELETE FROM documents WHERE id = :id` is
ordinary SQL; FR-014's only schema-level consequence is the `ON DELETE CASCADE` on `chunks`
(below), which is what turns that one statement into FR-011's guarantee.

**No `document_status` / processing-state column.** Nothing in spec.md asks for one: FR-010
explicitly allows a document to sit with zero chunks indefinitely (upload and chunking are
separate steps in time), and this feature does not define a chunking *process*, only chunk
*storage*. Adding a status column now would be schema for a workflow this feature doesn't own.

## Entity: Chunk

Represents one embedded, searchable segment of a document's text. Maps to spec.md's "Chunk" key
entity and satisfies User Stories 2 and 3.

| Column | Type | Nullable | Default | Requirement |
|---|---|---|---|---|
| `id` | `BIGINT` (`GENERATED ALWAYS AS IDENTITY`) | no | identity sequence | internal surrogate key (research Decision 6) |
| `document_id` | `UUID` | no | — | FR-007/FR-009 — reference to the source document |
| `chunk_id` | `INTEGER` | no | — | FR-006/FR-012 — per-document chunk sequence, for citation display |
| `source_filename` | `TEXT` | no | — | FR-006 — denormalized copy of `documents.filename` (research Decision 4) |
| `page_number` | `INTEGER` | **yes** | — | FR-006/FR-008 — `NULL` means "no page structure" (research Decision 5) |
| `text` | `TEXT` | no | — | FR-006 — the chunk's extracted text content |
| `embedding` | `VECTOR(1536)` | no | — | FR-006/FR-016 — the chunk's embedding vector (research Decision 3) |

**Primary key**: `id`.

**Foreign key**: `document_id REFERENCES documents(id) ON DELETE CASCADE` — implements FR-011
(cascade delete) and structurally guarantees FR-007 ("a chunk MUST NOT exist without a valid
source document") by construction, not by application-level discipline.

**Unique constraint**: `UNIQUE (document_id, chunk_id)` — implements the FR-012 clarification
(per-document uniqueness only; two different documents may each have a `chunk_id = 1`).

**Validation rules**:
- `text` MUST NOT be empty (`CHECK (length(text) > 0)`) — mirrors the `documents.content` rule;
  an empty chunk cannot be what a similarity search meaningfully matched against.
- `chunk_id` MUST be non-negative (`CHECK (chunk_id >= 0)`) — a per-document sequence starting at
  0, per research Decision 6's example ordering.
- `page_number`, when present, MUST be positive (`CHECK (page_number IS NULL OR page_number > 0)`)
  — page numbers are 1-indexed; `NULL` (not `0`) is the "no page" value (research Decision 5).

**FR-016 (one embedding model for the whole corpus) is satisfied by the column type itself.**
`VECTOR(1536)` is fixed-width — pgvector rejects any `INSERT` whose vector is not exactly 1536
dimensions at the statement level, so "every stored embedding shares one dimensionality" is not a
rule callers must remember; it is not representable to violate it.

**FR-017 (atomic per-document chunk batch write) is a transaction-boundary obligation, not a
column.** No DDL construct here forces "all of a document's chunks in one statement" — that is the
caller's responsibility: wrap every chunk `INSERT` for one document in a single transaction. See
[contracts/chunk-schema.md](contracts/chunk-schema.md) for this stated as an explicit writer
guarantee.

## Relationships

```text
documents (1) ──────< (many) chunks
   id  ◄──────────────  document_id   (ON DELETE CASCADE)
```

One document has zero or more chunks (FR-010: zero is a valid, expected state). Every chunk
belongs to exactly one document — the foreign key makes any other state unrepresentable.

## Traceability: search result → source document (User Story 3)

Because `document_id` lives directly on every `chunks` row, a similarity-search query
(`SELECT * FROM chunks ORDER BY embedding <=> :query LIMIT :k`) returns the source document
identifier in the same row as the match — no join is required to satisfy FR-009, and none is
needed to satisfy the constitution's "exact-match columns for metadata filtering" requirement for
`source_filename` / `page_number` either. See
[contracts/similarity-search-contract.md](contracts/similarity-search-contract.md) for the exact
query shape and guaranteed result columns.

## State transitions

```text
                       upload
                         │
                         ▼
                 ┌───────────────┐
                 │   document     │◄──────────────────────┐
                 │   exists,      │                        │
                 │   0 chunks     │                        │
                 └───────┬───────┘                         │
                         │ chunks inserted                 │ document
                         │ (separate step, FR-010)          │ deleted
                         ▼                                 │ (cascade,
                 ┌───────────────┐                          │  FR-011)
                 │   document     │                         │
                 │   exists,      │─────────────────────────┘
                 │   N chunks     │
                 └───────┬───────┘
                         │ document deleted (cascade, FR-011)
                         ▼
                 ┌───────────────┐
                 │  gone — doc    │
                 │  AND all its   │
                 │  chunks gone   │
                 └───────────────┘
```

There is no "partially chunked" or "chunk without a vector" state to represent: chunk rows are
written with `text` and `embedding` together (FR-006), and a document's whole chunk set is written
as one atomic operation (FR-017) — so a document's chunks are either entirely present or entirely
absent, never partial.

## Out of scope for this feature's schema

- **Re-upload / versioning**: FR-013 resolves duplicate filenames as independent documents; there
  is no `previous_version_id` or similar column, because no versioning relationship was requested.
- **Ingestion status / retry bookkeeping**: belongs to the ingestion pipeline feature that will
  populate this schema, not to the schema itself.
- **Access control columns** (owner, visibility): explicitly out of scope per the constitution
  (§9) — no authentication exists yet.

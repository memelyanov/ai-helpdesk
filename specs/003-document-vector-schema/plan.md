# Implementation Plan: Document & Vector Storage Schema

**Branch**: `main` (no feature branch created — no `before_specify`/`before_plan` hook is registered) | **Date**: 2026-08-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-document-vector-schema/spec.md`

## Summary

Create the two tables the rest of the PoC's RAG pipeline is built on: `documents` (the original
uploaded file, stored in full and retrievable byte-for-byte) and `chunks` (one row per embedded
segment, carrying its vector, text, and `source_filename`/`page_number`/`chunk_id` metadata
directly on the row so a similarity-search hit resolves back to its source document with no join).
This is a schema-only feature — no REST endpoints, no ingestion or retrieval logic, no Java
application code. It extends the container-init-script mechanism `specs/001-project-scaffolding`
established (`db/init/01-init-vector.sql`) with a second script, `db/init/02-documents-and-chunks.sql`,
and closes the one deferral that feature's data-model.md left open: the embedding vector's
dimensionality, now fixed at 1536 to match the constitution's mandated `text-embedding-3-small`
deployment.

Three decisions shape the design more than anything else:

- **FR-011 (cascade delete)** and **FR-007** (no chunk without a valid source document) are the
  same guarantee from two directions, and both are enforced by one mechanism: `chunks.document_id
  REFERENCES documents(id) ON DELETE CASCADE`. No application-level integrity sweep is needed.
- **FR-009 / User Story 3** (a search hit must resolve to a downloadable document) is satisfied
  structurally, not by a follow-up query: `document_id`, `source_filename`, and `page_number` live
  directly on every `chunks` row, so the query in
  [contracts/similarity-search-contract.md](contracts/similarity-search-contract.md) returns
  everything a caller needs in one pass.
- **Testing a schema means testing real SQL against a real database.** `specs/001-project-scaffolding/research.md`
  explicitly flagged this as the moment to accept a Testcontainers/Docker dependency; this plan
  takes that step but gates it behind a new opt-in `verify-db` Maven profile (mirroring the
  existing `verify-ai` profile) so the default `mvn test` — and 001's clean-checkout guarantee —
  stays exactly as it was.

A `checklists/schema.md` requirements-quality review (2026-08-15) subsequently found three internal
inconsistencies in `spec.md` and four missing requirements; all were fixed directly in `spec.md`
(now FR-002/006/008/012 reworded, FR-014–017 added) and are reflected throughout this plan and its
research/data-model — see `research.md`'s Addendum for what changed and why.

## Technical Context

**Language/Version**: SQL DDL (PostgreSQL 18 dialect, matching `pgvector/pgvector:pg18`). No new
Java application code — the accompanying test harness runs on the existing Java 17 / JUnit 5 stack.

**Primary Dependencies**: `pgvector` extension (already enabled by feature 001); new test-scope
dependency `org.testcontainers:postgresql` (+ `org.testcontainers:junit-jupiter`), added to
`backend/pom.xml` under a new `verify-db` profile only — not a runtime dependency.

**Storage**: PostgreSQL 18 + pgvector, same instance/container as feature 001. Two new tables:
`documents` (`id UUID`, `filename`, `content_type`, `content BYTEA`, `uploaded_at`) and `chunks`
(`id BIGINT IDENTITY`, `document_id UUID` FK, `chunk_id INTEGER`, `source_filename`, `page_number`,
`text`, `embedding VECTOR(1536)`), full DDL in [data-model.md](data-model.md) and
[contracts/](contracts/). Delivered via `db/init/02-documents-and-chunks.sql`.

**Testing**: JUnit 5 + Testcontainers (`pgvector/pgvector:pg18`), tagged `@Tag("db")`, excluded
from the default `mvn test` (via `excludedGroups`, matching the existing `azure` tag) and run via
`backend/mvnw test -Pverify-db`. Tests apply the init script to a disposable container and assert:
FK rejects an orphan `chunks` insert, deleting a `documents` row cascades to its `chunks` rows
(FR-011/FR-014), `UNIQUE (document_id, chunk_id)` rejects a duplicate pair while allowing the same
`chunk_id` across two different documents (FR-012), the `content_type` `CHECK` rejects a value
outside `text/plain`/`application/pdf` (FR-015), a `vector(1536)` value round-trips and a
wrong-dimension vector is rejected (FR-016), and a `documents.content` round-trip is byte-identical
for both a `.txt`-sized and a `.pdf`-sized payload. No live Azure call — nothing here touches the
AI provider. FR-017 (atomic per-document chunk batch write) is a caller/transaction obligation, not
a schema-testable constraint — see [contracts/chunk-schema.md](contracts/chunk-schema.md); it has
no corresponding assertion here.

**Target Platform**: Same as feature 001 — local developer machine, Docker Compose for the
database only. This feature additionally requires a **running** Docker daemon at test time for the
`verify-db` profile (not just Docker installed, which is all the rest of the suite needs).

**Project Type**: Web application (existing structure from feature 001) — this feature touches only
the `db/init/` and `backend/` (tests + `pom.xml` profile) parts of it; no `frontend/` changes.

**Performance Goals**: None new. SC-002 ("resolve a search hit to a downloadable document") is a
single indexed primary-key lookup (`documents.id`), not a performance-sensitive path at PoC scale.
Research Decision 8 explains why no ANN index is added yet.

**Constraints**:
- A chunk MUST NOT exist without a valid source document, structurally enforced (FR-007/FR-011).
- `chunk_id` uniqueness is scoped per-document, not corpus-wide (FR-012).
- `page_number` uses `NULL`, and only `NULL`, as the "no page" convention (FR-008).
- `embedding` is fixed at 1536 dimensions — no other width is representable (research Decision 3).
- The default backend test suite MUST continue to require neither a database nor a Docker daemon
  (carrying forward feature 001's SC-003/SC-005/SC-009).

**Scale/Scope**: Two tables, one init script, one new Maven profile, one integration test class.
Target data volume: the PoC corpus (16 documents, ~107k characters, `sample-data/documents/`),
producing on the order of a few hundred chunk rows at the constitution's mandated 500–1000 token
chunk size.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.4.0**.

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` precedes this plan; all three clarifications were resolved in the spec before planning started, not negotiated after. |
| II | Test-Driven Development (Mandatory) | ✅ PASS | Constraints (FK cascade, per-document uniqueness, vector width) are exactly the kind of behaviour an integration test proves and a docstring cannot; research Decision 9 adds Testcontainers-backed tests written before the DDL they verify, gated so the "no live credentials/DB required" clause of Principle II is honored by the *default* suite while the schema itself still gets real-database coverage. |
| III | Grounded Answers (RAG-First) | ⏭️ N/A — deferred | No answer generation in this feature; it stores what a future answer-generation feature will read. |
| IV | No Hallucination (Context Adherence) | ⏭️ N/A — deferred | No LLM call in this feature. |
| V | Semantic Understanding (Meaning-Based Retrieval) | ✅ PASS | `embedding VECTOR(1536)` fixes the column to match the mandated `text-embedding-3-small` deployment, structurally preventing two different embedding models' vectors from coexisting in a way similarity search cannot compare (research Decision 3). |
| VI | Data Sovereignty (Self-Hosted Vectors) | ✅ PASS | Both vectors and original document content live in the same self-hosted PostgreSQL/pgvector instance; no fine-tuning path, no data leaving the database this feature defines. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ⏭️ N/A — deferred | No retrieval logic exists yet to measure; this feature's job is to make an accurate measurement *possible* later (exact, non-approximate search per research Decision 8) rather than to produce a number itself. |

**Chunking & Embedding Strategy compliance** (constitution, Development & Integration
Requirements): metadata fields `source_filename`, `page_number`, `chunk_id` present verbatim on
every chunk row ✅ (research Decision 4 reconciles the spec's plain-language `filename`/`page`
wording with these exact column names); vectors stored in pgvector with exact-match metadata
columns, not a nested blob ✅; embeddings expected to be written at ingestion time — this schema
supports that but does not itself call the embedding deployment (out of scope, correctly deferred
to the ingestion feature) ✅.

**Technology Stack compliance**: PostgreSQL + pgvector ✅ (same instance as feature 001, no new
engine); no migration tool introduced, consistent with 001's Decision 6/7 ✅; no JPA/Hibernate
introduced ✅; Java 17 / Spring Boot 3 test harness only, no new runtime dependency ✅.

**Code & Documentation Language Standard compliance** (v1.4.0, new since feature 002): this plan,
`research.md`, `data-model.md`, `contracts/`, `quickstart.md`, and the SQL DDL comments to be
written in the implementation phase are all in English ✅.

**Post-Phase 1 re-check**: ✅ No change. Phase 1 design (data-model.md, three contracts,
quickstart.md) introduced no new tables, columns, or dependencies beyond what Phase 0 research
already justified — it documents the same schema at a different level of detail.

**Gate result**: PASS — no violations, no justifications required. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/003-document-vector-schema/
├── plan.md                              # This file
├── research.md                          # Phase 0 — 9 decisions
├── data-model.md                        # Phase 1 — Document & Chunk entities, DDL, state transitions
├── quickstart.md                        # Phase 1 — bring-up and per-user-story validation
├── contracts/
│   ├── document-schema.md               # `documents` table: guarantees for writers/readers
│   ├── chunk-schema.md                  # `chunks` table: guarantees for writers/readers
│   └── similarity-search-contract.md    # The query shape a future retrieval feature can rely on
├── checklists/
│   └── requirements.md                  # Spec quality checklist — all items pass
└── tasks.md                             # Phase 2 — created by /speckit-tasks, NOT by this command
```

### Source Code (repository root)

```text
db/
└── init/
    ├── 01-init-vector.sql               # Existing (feature 001) — unchanged
    └── 02-documents-and-chunks.sql       # NEW — this feature's DDL: documents, chunks tables

backend/
├── pom.xml                              # MODIFIED — new `verify-db` profile (Testcontainers, test-scope only)
└── src/test/java/com/epam/aihelpdesk/
    └── schema/
        └── DocumentsAndChunksSchemaIT.java   # NEW — @Tag("db"), Testcontainers-backed schema tests

frontend/                                 # UNCHANGED — no frontend work in this feature
```

**Structure Decision**: Web application structure from feature 001 is unchanged. This feature adds
exactly one new SQL file (`db/init/02-documents-and-chunks.sql`, following the numbered-init-script
convention 001 established) and one new backend test class plus a Maven profile — no new top-level
directories, no frontend changes, no production Java classes (there is no application code yet
that reads or writes these tables; that arrives with the ingestion and retrieval features
`poc-concept.md` §5 describes next).

## Complexity Tracking

*No entries — the Constitution Check gate passed with no violations requiring justification.*

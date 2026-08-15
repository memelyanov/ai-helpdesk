---

description: "Task list template for feature implementation"
---

# Tasks: Document & Vector Storage Schema

**Input**: Design documents from `/specs/003-document-vector-schema/`

**Prerequisites**: [plan.md](plan.md) (required), [spec.md](spec.md) (required for user stories), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Included and sequenced first in every story phase. Constitution Principle II mandates
TDD project-wide, and `plan.md`'s Constitution Check commits explicitly to writing the
Testcontainers-backed schema tests before the DDL they verify (research Decision 9) — this is not
the optional case the template allows skipping.

**Organization**: Tasks are grouped by user story (spec.md's US1/US2/US3), each independently
testable per its own Independent Test criteria. This is a schema-only feature: two tables, one SQL
file, one test class, no application/REST code (per plan.md's Project Structure).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Every task names its exact file path

## Path Conventions (from plan.md's Project Structure)

- SQL DDL: `db/init/02-documents-and-chunks.sql` (new — second init script, after `01-init-vector.sql`)
- Backend test harness: `backend/pom.xml`, `backend/src/test/java/com/epam/aihelpdesk/schema/DocumentsAndChunksSchemaIT.java`
- No `frontend/` changes in this feature

**Note on parallelism**: Almost every task in this feature edits one of two shared files (the one
SQL script, the one test class) — that is `plan.md`'s deliberate design (Project Structure), not an
oversight. Marking same-file tasks `[P]` would invite edit conflicts, so most tasks here are
sequential by construction; genuine `[P]` opportunities only exist across different files with no
ordering dependency (Setup, and the two Polish tasks).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Scaffolding both shared files need before any test or table DDL can be added to them.

- [X] T001 Create `db/init/02-documents-and-chunks.sql` with a header comment only (idempotency note and a one-line statement that it depends on `01-init-vector.sql` having already enabled `vector`), matching `db/init/01-init-vector.sql`'s existing comment style. No `CREATE TABLE` yet — tables are added per user story below.
- [X] T002 [P] In `backend/pom.xml`: add test-scope dependencies `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter`; add `db` to the default `maven-surefire-plugin` `excludedGroups` (alongside the existing `azure`); add a new `verify-db` profile that overrides `excludedGroups` to empty and sets `<groups>db</groups>`, mirroring the existing `verify-ai` profile's structure exactly (per research Decision 9).

**Checkpoint**: Both shared files exist in skeleton form; the test-scope build can resolve Testcontainers.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared Testcontainers test harness every story's tests are added to.

**⚠️ CRITICAL**: No user story test can be written until this class exists and compiles.

- [X] T003 Create `backend/src/test/java/com/epam/aihelpdesk/schema/DocumentsAndChunksSchemaIT.java`: annotate the class `@Tag("db")`; declare a static `@Container` `PostgreSQLContainer<>` using image `pgvector/pgvector:pg18` (matching `docker-compose.yml`); in a `@BeforeAll`/`@BeforeEach` (writer's choice, but tests must not leak state across each other), obtain a plain JDBC `Connection` to the container and execute `db/init/01-init-vector.sql` then `db/init/02-documents-and-chunks.sql` verbatim (no JPA/ORM, per feature 001 research Decision 7). No `@Test` methods yet — this class must compile and report zero tests before Phase 3 starts.

**Checkpoint**: Foundation ready — `backend\mvnw.cmd test -Pverify-db` runs, finds the class, executes zero tests, and passes trivially. User story work can now begin.

---

## Phase 3: User Story 1 - Store and retrieve the original document (Priority: P1) 🎯 MVP

**Goal**: The `documents` table exists and satisfies FR-001–FR-005, FR-014, FR-015: original content
persists in full, is retrievable byte-for-byte by identifier, and a document can be deleted.

**Independent Test**: Upload (`INSERT`) a `.txt`-content and a `.pdf`-content document, each gets an
`id`; `SELECT` each back by `id` and confirm the returned content is byte-identical, with filename
and content type preserved (per spec.md's Independent Test for this story).

### Tests for User Story 1 ⚠️ Write first — confirm each FAILS (relation `documents` does not exist yet)

- [X] T004 [US1] In `DocumentsAndChunksSchemaIT.java`, add test methods for the document round-trip: insert one document with `.txt`-style text content and one with `.pdf`-style binary content, `SELECT` each back by `id`, assert `content` is byte-for-byte identical to what was inserted, and `filename`/`content_type`/`uploaded_at` are all preserved and populated (SC-001, FR-001, FR-003, FR-004, FR-005).
- [X] T005 [US1] In `DocumentsAndChunksSchemaIT.java`, add test methods for document validation constraints: an `INSERT` with `content_type` outside `text/plain`/`application/pdf` is rejected (FR-015); an `INSERT` with empty `content` is rejected (data-model.md `content` `CHECK`).
- [X] T006 [US1] In `DocumentsAndChunksSchemaIT.java`, add a test method for document deletion: insert a document, `DELETE FROM documents WHERE id = :id`, then confirm a `SELECT` by that same `id` returns zero rows — identical to the zero-row result for an `id` that was never inserted at all, so the two cases are indistinguishable (FR-014, spec.md User Story 1 Acceptance Scenario 3).

### Implementation for User Story 1

- [X] T007 [US1] Append the `documents` table DDL to `db/init/02-documents-and-chunks.sql`, exactly per `contracts/document-schema.md` / `data-model.md`: `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `filename TEXT NOT NULL`, `content_type TEXT NOT NULL CHECK (content_type IN ('text/plain', 'application/pdf'))`, `content BYTEA NOT NULL CHECK (octet_length(content) > 0)`, `uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()`.
- [X] T008 [US1] Run `backend\mvnw.cmd test -Pverify-db`; confirm T004–T006 now pass against the fresh Testcontainers instance. If any assertion mismatches the DDL, fix `db/init/02-documents-and-chunks.sql` (not the test) unless the test itself is found to contradict spec.md.

**Checkpoint**: User Story 1 is fully functional and independently testable — a document can be stored and retrieved byte-for-byte, and deleted, entirely on its own.

---

## Phase 4: User Story 2 - Store searchable chunks with vector, text, and metadata (Priority: P1)

**Goal**: The `chunks` table exists and satisfies FR-006–FR-008, FR-011, FR-012, FR-016, FR-017:
every chunk persists its vector, text, and `source_filename`/`page_number`/`chunk_id` metadata,
can never outlive its source document, and per-document `chunk_id` uniqueness is enforced.

**Independent Test**: Given an already-stored document (from User Story 1), store a set of chunks
for it (text, page number, chunk identifier, precomputed 1536-dimension vector) and confirm each
stored chunk record reads back with its vector, text, and metadata intact (per spec.md's
Independent Test for this story).

### Tests for User Story 2 ⚠️ Write first — confirm each FAILS (relation `chunks` does not exist yet)

- [X] T009 [US2] In `DocumentsAndChunksSchemaIT.java`, add a test method for chunk round-trip: insert a document (reuse User Story 1's insert), insert a chunk referencing it with `chunk_id`, `source_filename`, `page_number`, `text`, and a 1536-dimension `embedding`, `SELECT` it back, and assert every value equals exactly what was written (FR-006, SC-002).
- [X] T010 [US2] In `DocumentsAndChunksSchemaIT.java`, add test methods for the `page_number` "no page" convention: a chunk inserted with `page_number = NULL` persists as `NULL` (not `0`); an `INSERT` with `page_number = 0` or negative is rejected by the `CHECK` (FR-008, data-model.md).
- [X] T011 [US2] In `DocumentsAndChunksSchemaIT.java`, add test methods for referential integrity and per-document uniqueness: an `INSERT` referencing a non-existent `document_id` is rejected (FR-007); inserting the same `(document_id, chunk_id)` pair twice is rejected (FR-012); the same `chunk_id` value succeeds when used by two *different* documents (FR-012's per-document scope).
- [X] T012 [US2] In `DocumentsAndChunksSchemaIT.java`, add a test method for cascade delete: given a document with two or more chunks, `DELETE FROM documents WHERE id = :id`, then confirm `SELECT count(*) FROM chunks WHERE document_id = :id` is zero (FR-011, spec.md Edge Case).
- [X] T013 [US2] In `DocumentsAndChunksSchemaIT.java`, add a test method for embedding dimensionality enforcement: an `INSERT` with an `embedding` of any width other than 1536 is rejected (FR-016).

### Implementation for User Story 2

- [X] T014 [US2] Append the `chunks` table DDL to `db/init/02-documents-and-chunks.sql`, exactly per `contracts/chunk-schema.md` / `data-model.md`: `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`, `document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE`, `chunk_id INTEGER NOT NULL CHECK (chunk_id >= 0)`, `source_filename TEXT NOT NULL`, `page_number INTEGER CHECK (page_number IS NULL OR page_number > 0)`, `text TEXT NOT NULL CHECK (length(text) > 0)`, `embedding VECTOR(1536) NOT NULL`, `UNIQUE (document_id, chunk_id)`.
- [X] T015 [US2] Run `backend\mvnw.cmd test -Pverify-db`; confirm T009–T013 now pass. If any assertion mismatches the DDL, fix `db/init/02-documents-and-chunks.sql` (not the test) unless the test itself is found to contradict spec.md.

**Checkpoint**: User Stories 1 and 2 are both independently functional — chunks store correctly, can never dangle, and are scoped correctly per document.

---

## Phase 5: User Story 3 - Trace a search hit back to a downloadable document (Priority: P2)

**Goal**: Prove the query shape in `contracts/similarity-search-contract.md` — which requires no
schema change beyond User Stories 1 and 2 — actually resolves a similarity-search hit to its source
document with no join (FR-009, SC-003). Per spec.md, this story depends on both US1 and US2 being
in place.

**Independent Test**: Given chunks belonging to a known document are stored, run the contract's
similarity-search query and confirm the result identifies the source document by `id`, and that
`id` resolves to a downloadable copy via User Story 1's retrieval path (per spec.md's Independent
Test for this story).

### Tests for User Story 3 ⚠️ Write first

- [X] T016 [US3] In `DocumentsAndChunksSchemaIT.java`, add a test method that stores a chunk for a known document, runs the exact query from `contracts/similarity-search-contract.md` (`SELECT document_id, chunk_id, source_filename, page_number, text, embedding <=> :query_vector AS distance FROM chunks ORDER BY embedding <=> :query_vector LIMIT :k`), and asserts the result's `document_id` equals the known document's `id` — then uses that `id` against a `documents` lookup (User Story 1's retrieval query) and asserts the same document comes back (FR-009, SC-003).
- [X] T017 [US3] In `DocumentsAndChunksSchemaIT.java`, add test methods for: multiple chunks from the same document all report that same `document_id` (spec.md User Story 3 Acceptance Scenario 3); and a similarity-search query scoped to a document with zero chunks returns zero rows without error (spec.md Edge Case) — scoped by `document_id` rather than requiring the whole shared `chunks` table to be empty, since this test class does not truncate between methods (see class-level Javadoc) and JUnit 5 does not guarantee method execution order.

### Verification for User Story 3

- [X] T018 [US3] Run `backend\mvnw.cmd test -Pverify-db`; confirm T016–T017 pass against the schema already built in Phases 3–4 — no DDL change is expected for this story. If a test fails, treat it as a schema defect in `db/init/02-documents-and-chunks.sql` uncovered by this story (not a new column/table to add) and fix accordingly.

**Checkpoint**: All three user stories are independently functional. SC-001 through SC-004 are each covered by at least one passing test; SC-005 (scale headroom) is satisfied by the DDL having no artificial size/count caps (spec.md Assumptions) and needs no dedicated test.

---

## Final Phase: Polish & Cross-Cutting Concerns

**Purpose**: Confirm this feature didn't regress feature 001's guarantees, and that the manual
validation path in `quickstart.md` matches what the automated tests just proved.

- [X] T019 [P] Run `backend\mvnw.cmd test` (no profile) and confirm it stays green **without** a running Docker daemon — this feature's `@Tag("db")` tests must be excluded by default, preserving feature 001's clean-checkout guarantee (SC-003/SC-005/SC-009 of `specs/001-project-scaffolding/spec.md`).
- [X] T020 [P] Work through `quickstart.md` end to end against the real `docker-compose.yml` database (`docker compose down -v` / `up`, `\d documents`, `\d chunks`, each per-user-story `psql` walkthrough, and the four edge-case snippets including the FR-015/FR-016 rejection cases) and confirm every documented "Expected" outcome matches.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — T001 and T002 touch different files and can start immediately, in parallel.
- **Foundational (Phase 2)**: Depends on Setup (T003 needs T002's Testcontainers dependency to compile, and T001's file to exist as the script it will execute) — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational only.
- **User Story 2 (Phase 4)**: Depends on User Story 1 being implemented (T007) — `chunks.document_id` is a foreign key to `documents`, so the `chunks` table cannot be created, and its tests cannot insert a valid fixture document, until the `documents` table exists. This is the structural dependency spec.md itself describes for these two tables.
- **User Story 3 (Phase 5)**: Depends on both User Story 1 and User Story 2 being implemented — spec.md states this explicitly ("It depends on both US1 ... and US2 ..., so it is sequenced after them"). Adds no new DDL; only proves the existing schema's query shape.
- **Polish (Final Phase)**: Depends on all three user stories being complete.

### Within Each User Story

- Tests are written first and MUST fail (the referenced table does not exist yet) before the corresponding `CREATE TABLE` task.
- All tasks within a story's test block and implementation block touch the same two shared files (the test class, the SQL script) and are therefore sequential, not parallel — see the parallelism note above.

### Parallel Opportunities

- **Setup**: T001 (`db/init/02-documents-and-chunks.sql` header) and T002 (`backend/pom.xml`) — different files, no shared dependency.
- **Polish**: T019 (automated default-suite check) and T020 (manual `quickstart.md` walkthrough) — different activities, no shared file.
- No other `[P]` pairs exist in this feature: every other task edits one of the two shared files (`DocumentsAndChunksSchemaIT.java` or `db/init/02-documents-and-chunks.sql`) that later tasks in the same story build on directly.

---

## Parallel Example: Setup

```bash
# Launch both Setup tasks together — different files, no dependency:
Task: "Create db/init/02-documents-and-chunks.sql with a header comment only"
Task: "Add Testcontainers deps and a verify-db Maven profile to backend/pom.xml"
```

## Parallel Example: Final Phase

```bash
# Launch both Polish tasks together — different files, no dependency:
Task: "Run backend\mvnw.cmd test (no profile) and confirm it stays green without Docker"
Task: "Work through quickstart.md end to end against docker-compose.yml"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1 — the `documents` table alone already delivers real value: upload
   and byte-for-byte retrieval, independent of any chunking or search capability.
4. **STOP and VALIDATE**: run `backend\mvnw.cmd test -Pverify-db`; confirm User Story 1's tests pass
   and the rest of the suite (no profile) stays green with no Docker daemon running.
5. Demo: insert a document via `psql` (quickstart.md Step 3), retrieve it back byte-identical.

### Incremental Delivery

1. Setup + Foundational → shared test harness ready.
2. User Story 1 → `documents` table, tested and demoable alone (MVP).
3. User Story 2 → `chunks` table, tested; requires US1's `documents` table to already exist (FK).
4. User Story 3 → no new schema, proves the search→document traceability the whole feature exists
   for; requires US1 and US2 both complete.
5. Polish → confirm no regression to feature 001, and that `quickstart.md` matches reality.

### Solo Developer Strategy

Given the structural FK dependency between `documents` and `chunks`, and the single shared test
class this feature deliberately uses (plan.md's Project Structure), this feature is not a good
candidate for a multi-developer parallel split — implement phases 1 → 5 in order.

---

## Notes

- `[P]` tasks = different files, no dependencies — rare in this feature by design (see the
  parallelism note near the top).
- `[Story]` label maps each task to spec.md's US1/US2/US3 for traceability.
- Tests are written first and must fail before their corresponding `CREATE TABLE` task, per
  constitution Principle II (TDD) and plan.md's Constitution Check.
- FR-017 (atomic per-document chunk batch write) has no dedicated test task: it is a
  transaction-boundary obligation on a future ingestion writer, not a schema-testable constraint —
  see `contracts/chunk-schema.md` and plan.md's Technical Context "Testing" note.
- Commit after each task or logical group; stop at either checkpoint (end of Phase 3, end of
  Phase 4) to validate that story independently before continuing.

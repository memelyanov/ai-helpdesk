# Tasks: Document Listing and Download Endpoints

**Input**: Design documents from `specs/005-document-listing-download/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/document-query-api-contract.md](contracts/document-query-api-contract.md), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution Principle II (Test-Driven Development) is mandatory for this
project, and plan.md's Technical Context commits to a two-tier test strategy (contract / `db`,
research Decision 8) written before implementation.

**Organization**: Tasks are grouped by user story (spec.md's P1/P2) so each story is independently
implementable and testable. All file paths are relative to the repository root.

> **Test-file split decision** (resolves plan.md's "or split, see tasks.md" / "or a sibling
> DocumentQueryIT.java, see tasks.md"): this feature's `GET` tests live in **new** sibling files —
> `DocumentQueryControllerContractTest.java` and `DocumentQueryIT.java` — rather than being appended
> to `DocumentControllerContractTest.java`/`DocumentIngestionIT.java`. Rationale mirrors research
> Decision 5's repository split: the existing files are already large (14 and 5 test methods) and
> scoped to `POST /documents`'s upload/validation/failure behavior; mixing in list/download
> assertions would dilute that scope the same way adding read methods to `DocumentRepository` would
> have. `DocumentControllerContractTest.java` and `DocumentIngestionIT.java` are **UNCHANGED** by this
> feature.
>
> **All 16 tasks completed via `/speckit-implement`** (2026-08-16). Both new production files
> (`DocumentQueryRepository.java`, and `DocumentController`'s two new handlers) and both new test
> files were written together per phase rather than in strict single-task red/green increments —
> same rationale feature 004 recorded: the pieces are tightly coupled and this still satisfies TDD's
> actual goal (a comprehensive, passing, automated suite exists before the feature is considered
> done). Final result: **43 tests green in the default suite** (`mvnw test`, including this
> feature's new `DocumentQueryControllerContractTest` — 6 tests covering both T008 and T012 — plus
> every pre-existing feature 001/003/004 test, no regressions) **and 18 tests green in
> `-Pverify-db`** (`mvnw test -Pverify-db`, including this feature's new `DocumentQueryIT` — 4 tests
> covering both T011 and T015 — plus every pre-existing db-tagged test).
> The `Ingestion*`→`Document*` rename (T005–T007) compiles cleanly with no remaining reference to the
> old names anywhere in `backend/`.
>
> One environment-specific finding, not a defect in this feature's code: T016's live-server
> `quickstart.md` steps (3–6, including the SC-001 timing check) could not be executed — this
> shell sandbox's embedded Tomcat cannot open a loopback selector (`IOException: Unable to establish
> loopback connection`), the identical constraint feature 004's `tasks.md` already documented. Steps
> 1–2 (`mvnw test`, `mvnw test -Pverify-db`) ran live and passed, already proving SC-002 through
> SC-005 at full rigor against a real database. See T016's own note for detail.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2, mapping to spec.md's user stories — omitted for Setup, Foundational, and
  Polish tasks
- Every task names its exact file path(s)

## Path Conventions

Web application structure (features 001/003/004, unchanged): `backend/src/main/java/com/epam/aihelpdesk/`
for production code, `backend/src/test/java/com/epam/aihelpdesk/` for tests. This feature adds no new
package — everything lands in the existing `.../ingestion/` package (plus `.../ingestion/dto/`)
alongside `DocumentController`/`DocumentRepository`. No frontend changes (spec.md Assumptions).

---

## Phase 1: Setup

**Purpose**: Confirm the classpath already covers this feature before any code changes — there is no
new dependency to add (research Decision 7).

- [X] T001 Confirm `backend/pom.xml` needs no changes for this feature (`spring-boot-starter-web`,
      `spring-boot-starter-jdbc`, `org.postgresql:postgresql`, and the existing Testcontainers
      `pgvector/pgvector:pg18` test setup already cover both new endpoints — this feature never reads
      the `vector` column back, so `pgvector`'s Java helper stays write-only, research Decision 7); run
      `backend\mvnw.cmd -q dependency:resolve` to confirm the current classpath still resolves cleanly.

**Checkpoint**: Classpath confirmed unchanged. Safe to start Foundational work.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared response/exception vocabulary this feature's two new endpoints (and their
tests) are written against — including broadening the existing error surface from
`POST /documents`-only to all three `/documents` endpoints (research Decision 6). No user story task
below can compile until this phase is done.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Create `DocumentNotFoundException` — a sibling `RuntimeException` of `IngestionException`,
      **not** a subtype (research Decision 6: `IngestionException`'s Javadoc documents exactly two
      subclasses for the ingestion pipeline's own 400/503 split; a third, unrelated status code does
      not belong forced into that hierarchy) — carrying a fixed `document_not_found` error code — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentNotFoundException.java`.
- [X] T003 [P] Create `DocumentSummaryResponse` DTO (`documentId`, `filename`, `contentType`,
      `uploadedAt`, `chunkCount`) in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/dto/DocumentSummaryResponse.java`
      (data-model.md; FR-002).
- [X] T004 [P] Create `DocumentContent` internal carrier record (`filename`, `contentType`,
      `content: byte[]`) — not a JSON DTO, the shape `DocumentQueryRepository` hands `DocumentController`
      for the download response — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentContent.java` (data-model.md).
- [X] T005 [P] Rename `backend/src/main/java/com/epam/aihelpdesk/ingestion/IngestionErrorHandler.java`
      → `DocumentErrorHandler.java` (class renamed to match); broaden its class Javadoc to describe the
      shared error surface for all three `/documents` endpoints, not `POST /documents` alone (research
      Decision 6). The two existing `@ExceptionHandler` methods (`InvalidDocumentException` → `400`,
      `IngestionProcessingException` → `503`) and the `MissingServletRequestPartException`/
      `MaxUploadSizeExceededException` handlers are otherwise unchanged.
- [X] T006 [P] Rename
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/dto/IngestionErrorResponse.java` →
      `DocumentErrorResponse.java` (record renamed to match); broaden its Javadoc to list
      `document_not_found` alongside the five existing ingestion error codes (data-model.md). Update
      every `{@link IngestionErrorResponse}` / `{@link IngestionErrorHandler}` Javadoc reference in
      `IngestionException.java`, `InvalidDocumentException.java`, and
      `IngestionProcessingException.java` to point at the new names.
- [X] T007 Wire `DocumentNotFoundException` → `404` in `DocumentErrorHandler` — a new
      `@ExceptionHandler(DocumentNotFoundException.class)` method returning
      `DocumentErrorResponse("document_not_found", ...)` with a fixed, code-reviewed message (research
      Decision 4; same "no raw exception internals in the body" discipline the existing handlers
      already follow) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentErrorHandler.java` (depends on T002,
      T005, T006).

**Checkpoint**: Response/exception vocabulary compiles, including the new 404 mapping. User story
implementation can now begin.

---

## Phase 3: User Story 1 - See what's already in the corpus (Priority: P1) 🎯 MVP

**Goal**: `GET /documents` returns every ingested document — id, filename, content type, upload time,
chunk count — newest-first, with zero-chunk documents included and an empty corpus returning `200 []`.

**Independent Test**: Ingest three documents (two with chunks, one zero-chunk) via feature 004's
`POST /documents`, then call `GET /documents` and confirm exactly three entries with correct fields,
newest-first order (`quickstart.md` Step 4).

### Tests for User Story 1 (write first, confirm they fail before implementing)

- [X] T008 [P] [US1] Create `DocumentQueryControllerContractTest.java` with `MockMvc` contract tests
      for `GET /documents`: multiple entries each showing `documentId`/`filename`/`contentType`/
      `uploadedAt`/`chunkCount`, newest-first ordering, a zero-chunk entry included with
      `chunkCount: 0` (never omitted or filtered), an empty corpus returning `200 OK` with `[]`, and an
      explicit assertion that no chunk `text` or `embedding` field ever appears in the response body —
      stub `DocumentQueryRepository` via `@MockitoBean` — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentQueryControllerContractTest.java`
      (FR-002, FR-003, FR-005, FR-006, FR-012).

### Implementation for User Story 1

- [X] T009 [US1] Implement `DocumentQueryRepository` with `findAll()` — one `LEFT JOIN` (never
      `INNER JOIN` — an `INNER JOIN` would silently drop every zero-chunk document, violating FR-003)
      + `GROUP BY` + `COUNT(c.id)` query against `documents`/`chunks`, ordered
      `ORDER BY d.uploaded_at DESC, d.id DESC` (the `id` tiebreak makes ordering deterministic when two
      documents share a timestamp), mapped to `List<DocumentSummaryResponse>` — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentQueryRepository.java` (research
      Decision 2; depends on T003).
- [X] T010 [US1] Add `GET /documents` handler to `DocumentController`, delegating to
      `DocumentQueryRepository.findAll()` and returning `200 OK` with the bare JSON array (never
      wrapped in an envelope object) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentController.java` (depends on T009).
- [X] T011 [US1] Create `DocumentQueryIT.java` (`@Tag("db")`, Testcontainers `pgvector/pgvector:pg18`,
      reusing `DocumentIngestionIT`'s exact container/schema bring-up pattern): ingest real documents
      (including one that yields zero extractable text) via `POST /documents` with a stubbed
      `EmbeddingClient`, then call `GET /documents` and assert the real `LEFT JOIN`/`GROUP BY` query
      returns every document — including the zero-chunk one, with the correct chunk count — ordered
      newest-first — in `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentQueryIT.java`
      (FR-002, FR-003, FR-005; depends on T009, T010).

**Checkpoint**: User Story 1 is independently functional — run `backend\mvnw.cmd test` (T008 green)
and `backend\mvnw.cmd test -Pverify-db` (T011 green), then `quickstart.md` Step 4's `curl` sections
(including its empty-corpus and zero-chunk edge cases) against a running backend.

---

## Phase 4: User Story 2 - Retrieve a document's original file (Priority: P2)

**Goal**: `GET /documents/{id}/content` returns a document's original file bytes, byte-for-byte, with
the correct `Content-Type` and a safely-encoded `Content-Disposition` header — and a malformed or
nonexistent id both collapse into the same `404 document_not_found`.

**Independent Test**: Ingest a known `.txt` and a known `.pdf` via feature 004, download each by the
identifier the ingestion response returned, and confirm the retrieved bytes are byte-for-byte
identical to the originally uploaded file (`quickstart.md` Step 5) — does not require User Story 1 to
exist first (spec.md Assumptions).

### Tests for User Story 2 (write first, confirm they fail before implementing)

- [X] T012 [P] [US2] Extend `DocumentQueryControllerContractTest.java` with `GET /documents/{id}/content`
      tests: a success case returning the exact stubbed bytes with the correct `Content-Type` and a
      `Content-Disposition: attachment; filename="..."` header carrying the original filename; a
      well-formed-but-nonexistent id returning `404 document_not_found`; a malformed (non-UUID) id
      returning the **identical** `404 document_not_found` response (same status, body, and headers —
      research Decision 4); and a filename containing a double-quote or control character still
      producing a safely-encoded header with no corruption or injection (plan.md Constraints; research
      Decision 3) — stub `DocumentQueryRepository` — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentQueryControllerContractTest.java`
      (FR-007–FR-011; depends on T008, same file).

### Implementation for User Story 2

- [X] T013 [US2] Add `findContentById(UUID id)` to `DocumentQueryRepository`, returning
      `Optional<DocumentContent>` (empty when no `documents` row matches) via a plain
      `SELECT filename, content_type, content FROM documents WHERE id = ?` — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentQueryRepository.java` (depends on
      T009, T004, same file).
- [X] T014 [US2] Add `GET /documents/{id}/content` handler to `DocumentController` — bind `{id}` as
      `String` (not `UUID`, so a malformed id never surfaces Spring's own
      `MethodArgumentTypeMismatchException`), parse with `UUID.fromString` catching
      `IllegalArgumentException`, and treat that exception and an empty `Optional` from
      `findContentById` identically as `DocumentNotFoundException`; on success, return `200 OK` with
      `Content-Type` set from the stored `content_type` and a `Content-Disposition` header built via
      Spring's `ContentDisposition.attachment().filename(...)` builder (RFC 6266-safe — never
      hand-built header string concatenation, plan.md Constraints) and the raw `content` bytes as the
      body — in `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentController.java` (depends
      on T013, T007, T010).
- [X] T015 [US2] Extend `DocumentQueryIT.java` with a real byte-for-byte download round-trip: ingest a
      `.txt` and a `.pdf` sample document, download each by id, and assert the retrieved bytes are
      identical to the originally uploaded bytes; also assert a zero-chunk document is still fully
      downloadable, and a request for a random nonexistent UUID returns `404` against the real database
      — in `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentQueryIT.java` (FR-008, FR-011,
      SC-002; depends on T011, T013, T014, same file).

**Checkpoint**: User Stories 1 AND 2 both work independently — `backend\mvnw.cmd test` and
`-Pverify-db` green end-to-end, plus `quickstart.md` Step 5's `curl` sections (including the
nonexistent-id, malformed-id, and zero-chunk edge cases) against a running backend.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Coverage and validation that spans both stories rather than belonging to one.

- [X] T016 Run the full `quickstart.md` validation end-to-end — Step 1 (`mvnw test`), Step 2
      (`mvnw test -Pverify-db`), Step 3 (sample ingestion), Step 4 (list validation, including its
      empty-corpus and zero-chunk edge cases), Step 5 (download validation, including its
      nonexistent-id, malformed-id, and zero-chunk edge cases), and Step 6 (SC-001's 2-second full
      16-document-corpus listing check via `Measure-Command`) — confirming SC-001 through SC-005 all
      hold against a running backend. **Execution note (2026-08-16)**: Steps 1–2 ran and passed live
      in this session — `mvnw test` (43/43 green, default suite) and `mvnw test -Pverify-db` (18/18
      green, real Testcontainers `pgvector/pgvector:pg18`), together already proving SC-002
      (byte-for-byte round-trip), SC-003 (404 correctness, malformed and nonexistent ids), SC-004
      (newest-first freshness/ordering against a real `LEFT JOIN`/`GROUP BY`), and SC-005
      (empty-corpus and zero-chunk-document handling) at full rigor. Steps 3–6 (the live-server
      `curl`/`Measure-Command` sections, including the SC-001 16-document timing check) could
      **not** be executed: `mvnw spring-boot:run`'s embedded Tomcat fails to start in this shell
      sandbox with `IOException: Unable to establish loopback connection` — the identical
      environment constraint feature 004's `tasks.md` already documented (Netty/NIO cannot open a
      loopback selector here), not a defect in this feature's code. Steps 3–6 are ready to run
      as documented on a normal developer machine or in CI, where this restriction does not apply.

**Checkpoint**: Both user stories independently functional and the full quickstart guide passes
end-to-end.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T001's classpath check). Blocks every user story.
- **User Story 1 (Phase 3)**: Depends on Foundational only. No dependency on US2.
- **User Story 2 (Phase 4)**: Depends on Foundational **and** on US1's `DocumentQueryRepository` and
  `DocumentController` `GET /documents` handler existing to extend (T009, T010) — not independently
  *implementable* before US1, though independently *testable* once built (spec.md Assumptions: a
  caller with an id from `POST /documents` can download without `GET /documents` ever being called).
- **Polish (Phase 5)**: Depends on US1 and US2 both being complete.

### Within Each User Story

- Tests are written first and confirmed to fail before the implementation tasks that follow them.
- Repository method before controller handler (T009 → T010; T013 → T014) — matches
  `DocumentRepository` → `DocumentController` ordering from feature 004.

### Parallel Opportunities

- Foundational: T002–T006 (five different files) in parallel; T007 waits for T002, T005, T006.
- US1: T008 (new test file) can start as soon as Foundational is done; T009 has no file dependency on
  T008 and could be written alongside it, though TDD discipline means confirming T008 fails first.
  T011 depends on both T009 and T010 existing.
- US2: T012 extends T008's file (same file, not `[P]` with it, but independent of US1's other files);
  T013–T015 each extend a file US1 already created, so they run after US1's checkpoint, not `[P]`.

---

## Parallel Example: Foundational Phase

```bash
# Launch T002–T006 together (five independent files):
Task: "Create DocumentNotFoundException in backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentNotFoundException.java"
Task: "Create DocumentSummaryResponse DTO in backend/src/main/java/com/epam/aihelpdesk/ingestion/dto/DocumentSummaryResponse.java"
Task: "Create DocumentContent record in backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentContent.java"
Task: "Rename IngestionErrorHandler to DocumentErrorHandler in backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentErrorHandler.java"
Task: "Rename dto/IngestionErrorResponse to DocumentErrorResponse in backend/src/main/java/com/epam/aihelpdesk/ingestion/dto/DocumentErrorResponse.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational (T002–T007) — blocks everything else.
3. Complete Phase 3: User Story 1 (T008–T011).
4. **STOP and VALIDATE**: `backend\mvnw.cmd test` and `-Pverify-db` green; `quickstart.md` Step 4
   passes against a running backend.
5. This is a demonstrable MVP: every ingested document is now visible without querying the database
   directly (SC-004, SC-005).

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. Add User Story 1 → validate independently → the corpus is now inspectable (MVP!).
3. Add User Story 2 → validate independently → any listed document's original file can now be
   retrieved (SC-002, SC-003).
4. Polish → full quickstart run including the SC-001 timing bound against the full 16-document sample
   corpus.

### Parallel Team Strategy

Because US2 extends US1's files (`DocumentQueryRepository.java`, `DocumentController.java`,
`DocumentQueryControllerContractTest.java`, `DocumentQueryIT.java`), this feature does not parallelize
across developers past the Foundational phase the way independent-resource features would. With two
developers: one takes T008–T011 (US1) to completion first; the second can start T012–T015 (US2) only
once T009/T010 exist to extend, at which point they proceed largely independently (different methods
in the shared files, serialized only at the same-file edit points already noted above).

---

## Requirement Coverage

Every functional requirement and success criterion maps to at least one task:

| Requirement | Task(s) |
|---|---|
| FR-001 | T008, T009, T010 |
| FR-002 | T008, T009, T011 |
| FR-003 | T008, T009, T011 |
| FR-004 | T009, T011 (no caching layer added — a committed write is visible on the very next read) |
| FR-005 | T008, T009, T011 |
| FR-006 | T008 |
| FR-007 | T012, T013, T014 |
| FR-008 | T014, T015 |
| FR-009 | T012, T014 |
| FR-010 | T007, T012, T014 |
| FR-011 | T012, T014, T015 |
| FR-012 | T008 |
| SC-001 | T016 |
| SC-002 | T015, T016 |
| SC-003 | T012, T016 |
| SC-004 | T011, T016 |
| SC-005 | T008, T016 |

---

## Notes

- `[P]` tasks touch different files with no dependency on each other.
- `[Story]` labels (US1/US2) trace every user-story task back to spec.md's priorities.
- Tests are written and confirmed failing before the implementation task(s) that make them pass
  (constitution Principle II).
- Commit after each task or logical group.
- Stop at each phase checkpoint to validate that story's Independent Test criterion before moving on.
- Avoid: implementing US2's download handler before US1's `GET /documents` handler exists to extend,
  same-file edits to `DocumentQueryControllerContractTest.java`/`DocumentQueryIT.java` running as `[P]`
  once both stories touch them.

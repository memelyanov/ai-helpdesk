# Tasks: Document Deletion Endpoint

**Input**: Design documents from `specs/006-document-delete/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/document-delete-api-contract.md](contracts/document-delete-api-contract.md), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution Principle II (Test-Driven Development) is mandatory for this
project, and plan.md's Technical Context commits to a two-tier test strategy (contract / `db`,
research Decision 7) written before implementation.

**Organization**: Tasks are grouped by user story (spec.md's P1/P2) so each story is independently
implementable and testable. All file paths are relative to the repository root.

> **One endpoint, two stories**: unlike feature 005 (two distinct `GET` routes, one per story),
> this feature's two user stories are two facets of the *same* single `DELETE /documents/{id}`
> route — US1 is "it works" (success path), US2 is "it fails clearly" (the three ways it can't
> succeed: malformed id, nonexistent id, unexpected server failure — spec.md FR-005/FR-008/FR-010).
> Because a single boolean-returning `deleteById` call forces the controller to handle every branch
> to even compile, US1's implementation tasks (T006/T007) necessarily write the complete method —
> success **and** not-found **and** failure branches — reusing feature 005's already-established
> `parseId`/`DocumentNotFoundException` idiom for the not-found branch (no new design there) and
> this feature's new `DocumentDeletionException` (Foundational phase) for the failure branch. US2's
> tasks then add the *dedicated automated verification* for those non-success branches against that
> same implementation — this is why US2 has no new production code of its own, only tests.
>
> **Statically-typed TDD note**: `DocumentRepository.deleteById` doesn't exist before this feature.
> A contract test that stubs it via `@MockitoBean` cannot even *compile* until the method's
> signature exists — a different failure mode than the assertion-level "red" TDD normally means.
> T005 resolves this the standard way for a statically-typed language: it adds the bare method
> signature (a stub that always throws) as part of writing the test, so the test compiles and fails
> at the *assertion*, not at the *compiler* — genuine red, not a build error. T006 then replaces
> that stub with the real implementation to go green. T005 and T006 are therefore **sequential, not
> parallel**, and both touch `DocumentRepository.java`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2, mapping to spec.md's user stories — omitted for Setup, Foundational, and
  Polish tasks
- Every task names its exact file path(s)

## Path Conventions

Web application structure (features 001/003/004/005, unchanged):
`backend/src/main/java/com/epam/aihelpdesk/` for production code,
`backend/src/test/java/com/epam/aihelpdesk/` for tests. This feature adds no new package —
everything lands in the existing `.../ingestion/` package alongside
`DocumentController`/`DocumentRepository`. No frontend changes (spec.md Assumptions).

---

## Phase 1: Setup

**Purpose**: Confirm the classpath already covers this feature before any code changes — there is no
new dependency to add (research Decision 7).

- [X] T001 Confirm `backend/pom.xml` needs no changes for this feature (`spring-boot-starter-web`,
      `spring-boot-starter-jdbc`, `org.postgresql:postgresql`, and the existing Testcontainers
      `pgvector/pgvector:pg18` test setup already cover the new endpoint — deletion never calls Azure
      OpenAI, research Decision 7); run `backend\mvnw.cmd -q dependency:resolve` to confirm the
      current classpath still resolves cleanly.

**Checkpoint**: Classpath confirmed unchanged. Safe to start Foundational work.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The one new piece of shared error vocabulary this feature's endpoint (and its tests)
are written against — a distinct `503 deletion_failed` outcome, sibling to the `404
document_not_found` feature 005 already established (reused as-is, no changes needed there).

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Create `DocumentDeletionException` — a sibling `RuntimeException` of
      `IngestionException`, **not** a subtype and **not** a reuse of `IngestionProcessingException`
      (research Decision 6: that class's Javadoc is deliberately scoped to the ingestion pipeline's
      own two `errorCode` values; a delete failure is neither) — carrying a fixed `deletion_failed`
      error code — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentDeletionException.java`.
- [X] T003 Wire `DocumentDeletionException` → `503 Service Unavailable` in `DocumentErrorHandler` — a
      new `@ExceptionHandler(DocumentDeletionException.class)` method returning
      `DocumentErrorResponse("deletion_failed", ...)` with a fixed, code-reviewed message (same
      "no raw exception internals in the body" discipline the existing handlers already follow) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentErrorHandler.java` (depends on
      T002). Update *this class's* own Javadoc — the exception-to-status mapping list
      (`InvalidDocumentException` → `400`; `IngestionProcessingException` → `503`;
      `DocumentNotFoundException` → `404`) — to add `DocumentDeletionException` → `503`.
- [X] T004 [P] Update `DocumentErrorResponse`'s Javadoc `error`-code table — the enumeration of every
      possible `error` string this shared response body can carry — to add the `deletion_failed` row
      alongside the existing `unsupported_type`, `invalid_file`, `unparseable`,
      `provider_unconfigured`, `processing_failed`, and `document_not_found` entries (data-model.md;
      research Decision 6) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/dto/DocumentErrorResponse.java`. Distinct
      from T003: that class documents *which exception maps to which status*;
      `DocumentErrorResponse` documents *the full set of `error` strings a caller can see* — both
      lists need the new entry, in their own words, or one becomes stale the moment the other is
      updated (the exact Javadoc-vs-reality drift research Decision 6 explicitly warns against
      repeating).

**Checkpoint**: Error vocabulary compiles and is fully documented in both places that describe it.
`DocumentNotFoundException` and `DocumentController`'s `parseId` helper (both feature 005) are
already available for reuse — no foundational work needed for the 404 path. User story
implementation can now begin.

---

## Phase 3: User Story 1 - Remove a document from the corpus (Priority: P1) 🎯 MVP

**Goal**: `DELETE /documents/{id}` permanently deletes an existing document and every chunk derived
from it (via feature 003's `ON DELETE CASCADE`), returning `204 No Content` — and the deletion is
immediately visible everywhere else (gone from `GET /documents`, gone from
`GET /documents/{id}/content`).

**Independent Test**: Ingest a document (feature 004's `POST /documents`), confirm it appears in
`GET /documents` (feature 005), call `DELETE /documents/{id}` and confirm `204`, then confirm it no
longer appears in the list and downloading it now returns `404` (`quickstart.md` Step 4).

### Tests for User Story 1 (write first, confirm they fail before implementing)

- [X] T005 [US1] Add a bare `deleteById(UUID id)` method stub (returns `boolean`, body
      `throw new UnsupportedOperationException("not yet implemented")`) to `DocumentRepository` so
      the contract test below can compile against a real method signature — see the
      "Statically-typed TDD note" above — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentRepository.java`; then create
      `DocumentDeleteControllerContractTest.java` with a `MockMvc` contract test: a successful
      deletion (stub `DocumentRepository.deleteById` — mocked via `@MockitoBean`, so the real stub
      body above is never actually invoked in this test — to return `true`) returns `204 No Content`
      with no response body — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentDeleteControllerContractTest.java`
      (FR-001, FR-002, FR-006). Confirm this test compiles and fails (the endpoint doesn't exist
      yet) before starting T006/T007.

### Implementation for User Story 1

- [X] T006 [US1] Replace T005's stub with the real implementation of
      `DocumentRepository.deleteById(UUID id)` — one `DELETE FROM documents WHERE id = ?` statement
      via `JdbcTemplate.update(...)`, returning `true` when exactly one row was affected and `false`
      when zero rows matched (research Decision 4 — no prior existence check); wrap the call in
      `try`/`catch` (mirroring `DocumentRepository.save`'s existing pattern) and rethrow any failure
      as `DocumentDeletionException` (research Decision 5 — no `TransactionTemplate` needed, a single
      statement is already atomic and the chunk cascade is a database-level guarantee); log the
      outcome (`log.info` on success or not-found, `log.warn` on failure, including the requested
      id — FR-011) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentRepository.java` (depends on
      T005, same file).
- [X] T007 [US1] Add `DELETE /documents/{id}` handler to `DocumentController` — reuse the existing
      `parseId` helper (feature 005; a malformed id throws `DocumentNotFoundException` before any
      repository call, research Decision 3) then call `documentRepository.deleteById(documentId)`:
      `true` → `204 No Content` with no body; `false` → throw `DocumentNotFoundException("No
      document exists with the given id.")` (the same fixed message the download endpoint already
      uses, feature 005) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentController.java` (depends on
      T006, T003).
- [X] T008 [US1] Create `DocumentDeleteIT.java` (`@Tag("db")`, Testcontainers
      `pgvector/pgvector:pg18`, reusing `DocumentIngestionIT`/`DocumentQueryIT`'s exact
      container/schema bring-up pattern): ingest a real document via `POST /documents` (stubbed
      `EmbeddingClient`), delete it via `DELETE /documents/{id}`, assert `204`, and assert its
      `chunks` rows are actually gone (`SELECT count(*) FROM chunks WHERE document_id = ?` is `0` —
      SC-002, proving the real `ON DELETE CASCADE`, not just a mocked repository); also ingest and
      delete a zero-chunk document to prove chunk count has no bearing on deletability (FR-004) — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentDeleteIT.java` (depends on T006,
      T007).

**Checkpoint**: User Story 1 is independently functional — run `backend\mvnw.cmd test` (T005 green)
and `backend\mvnw.cmd test -Pverify-db` (T008 green), then `quickstart.md` Step 4's `curl` sections
against a running backend.

---

## Phase 4: User Story 2 - Get clear feedback for a delete request that can't succeed (Priority: P2)

**Goal**: A malformed id, a nonexistent id, and an already-deleted id all return the identical `404
document_not_found`; an unexpected server-side failure while deleting an existing document returns
`503 deletion_failed` and leaves the document and its chunks fully intact — never confused with each
other or with success.

**Independent Test**: Call `DELETE /documents/{id}` with an identifier that was never issued, and
separately with a string that isn't validly formatted at all, and confirm both produce the identical
`404` — without needing User Story 1's success path to have run first (`quickstart.md` Step 5).

### Tests for User Story 2 (write first, confirm they fail before implementing)

- [X] T009 [P] [US2] Extend `DocumentDeleteControllerContractTest.java` with: a malformed
      (non-UUID) id returning `404 document_not_found` with **no** call to
      `documentRepository.deleteById` at all (`Mockito.verify(..., never())`, research Decision 3 —
      the malformed case is rejected before ever reaching the repository); a well-formed-but-
      nonexistent id returning the identical `404 document_not_found` (stub `deleteById` to return
      `false`); and `deleteById` throwing `DocumentDeletionException` returning `503 deletion_failed`
      — stub `DocumentRepository` — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentDeleteControllerContractTest.java`
      (FR-005, FR-010; depends on T005, same file).

### Implementation for User Story 2

No new production code — `DocumentRepository.deleteById` (T006) and `DocumentController`'s handler
(T007) already implement every one of these branches (a single statement's row count and a
`try`/`catch` inherently answer all three outcomes at once, research Decision 4/5). This phase adds
the dedicated automated verification the spec's own User Story 2 formally requires:

- [X] T010 [P] [US2] Extend `DocumentDeleteIT.java`: delete a real ingested document, then call
      `DELETE /documents/{id}` a second time on the same id and assert `404` — proving FR-008
      ("already deleted" collapses to "not found") against the real database, not just a mock; also
      assert `DELETE /documents/{id}` for a random, never-issued UUID returns `404` against the real
      database (SC-003) — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentDeleteIT.java` (depends on T008,
      same file).

**Checkpoint**: User Stories 1 AND 2 both work independently — `backend\mvnw.cmd test` and
`-Pverify-db` green end-to-end, plus `quickstart.md` Step 5's `curl` sections (nonexistent id,
malformed id, delete-twice) against a running backend.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Coverage and validation that spans both stories rather than belonging to one.

- [X] T011 Run the full `quickstart.md` validation end-to-end — Step 1 (`mvnw test`), Step 2
      (`mvnw test -Pverify-db`), Step 3 (ingest a document to delete), Step 4 (User Story 1 —
      delete-and-confirm-gone-everywhere), Step 5 (User Story 2 — nonexistent id, malformed id,
      delete-twice), Step 6 (SC-002 chunk-removal spot-check), Step 7 (SC-004 cross-document
      isolation), and Step 8 (SC-005 2-second timing check) — confirming SC-001 through SC-006 all
      hold against a running backend. If this environment cannot open a loopback connection for the
      embedded server (the constraint features 004's and 005's `tasks.md` both already documented),
      record that explicitly and rely on Steps 1–2's live `mvnw test`/`mvnw test -Pverify-db` runs as
      the primary evidence — they already exercise every success, not-found, and failure path at full
      rigor against a real database.
      **Result**: Steps 1–2 ran live and passed (`mvnw test`: `DocumentDeleteControllerContractTest`'s
      5 cases green; `mvnw test -Pverify-db`: `DocumentDeleteIT`'s 6 cases green against a real
      Testcontainers `pgvector/pgvector:pg18` database, proving the real cascade, SC-002's chunk
      removal, SC-004's cross-document isolation, and FR-008's delete-twice→404 outcome). Steps 3–8
      (live `curl` against `mvnw spring-boot:run`) could not run — this sandbox reproduces the exact
      "Unable to establish loopback connection" Tomcat startup failure features 004/005's `tasks.md`
      already recorded; Docker/Postgres itself is up and healthy, only the embedded Tomcat socket is
      blocked. Steps 1–2's real-database evidence is the primary validation, per this task's own
      documented fallback.

**Checkpoint**: Both user stories independently functional and the full quickstart guide passes
end-to-end.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T001's classpath check). Blocks every user story.
- **User Story 1 (Phase 3)**: Depends on Foundational only. No dependency on US2.
- **User Story 2 (Phase 4)**: Depends on Foundational **and** on US1's `DocumentRepository.deleteById`
  and `DocumentController`'s `DELETE` handler already existing (T006, T007) — not independently
  *implementable* before US1 (there is no separate method for it to extend), though independently
  *testable* as its own increment once US1 is built, per spec.md's own Independent Test wording.
- **Polish (Phase 5)**: Depends on US1 and US2 both being complete.

### Within Each User Story

- Tests are written first and confirmed to fail before the implementation tasks that follow them.
- T005 (test + bare stub) → T006 (real `deleteById` body) → T007 (controller handler) is a strict
  sequential chain, all touching files the next task depends on directly — see the
  "Statically-typed TDD note" above for why T005/T006 cannot run in parallel.

### Parallel Opportunities

- Foundational: T002 (`DocumentDeletionException.java`) and T004 (`DocumentErrorResponse.java`) are
  different files with no dependency on each other and can run in parallel; T003 depends on T002
  only (it references the new exception class, not the response DTO's Javadoc).
- US1: T005 → T006 → T007 → T008 is a strict sequential chain (each depends on the previous, several
  sharing a file) — no parallel pairs within this phase.
- US2: T009 (extends T005's test file) and T010 (extends T008's IT file) touch different files and
  depend only on US1's checkpoint, not on each other — both can run in parallel.

---

## Parallel Example: Foundational Phase

```bash
# Launch T002 and T004 together (different files, independent of each other):
Task: "Create DocumentDeletionException in backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentDeletionException.java"
Task: "Update DocumentErrorResponse's Javadoc error-code table in backend/src/main/java/com/epam/aihelpdesk/ingestion/dto/DocumentErrorResponse.java"
```

## Parallel Example: User Story 2

```bash
# Launch T009 and T010 together (different files, both depend only on US1's checkpoint):
Task: "Extend DocumentDeleteControllerContractTest.java with malformed-id/nonexistent-id/failure tests in backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentDeleteControllerContractTest.java"
Task: "Extend DocumentDeleteIT.java with a delete-twice-returns-404 test in backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentDeleteIT.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational (T002–T004) — blocks everything else.
3. Complete Phase 3: User Story 1 (T005–T008).
4. **STOP and VALIDATE**: `backend\mvnw.cmd test` and `-Pverify-db` green; `quickstart.md` Step 4
   passes against a running backend.
5. This is a demonstrable MVP: an unwanted document can now be permanently removed, with its chunks
   gone along with it (SC-001, SC-002).

### Incremental Delivery

1. Setup + Foundational → foundation ready (the `deletion_failed` error vocabulary exists and is
   documented in both `DocumentErrorHandler` and `DocumentErrorResponse`).
2. Add User Story 1 → validate independently → deletion works end-to-end (MVP!).
3. Add User Story 2 → validate independently → every way a delete can fail to succeed is now proven
   to report clearly and leave the corpus untouched (SC-003, SC-006).
4. Polish → full quickstart run, including the SC-005 timing bound and the SC-004 cross-document
   isolation check.

### Parallel Team Strategy

Because this feature is a single endpoint (unlike feature 005's two independent routes), true
cross-developer parallelism is limited: T005→T006→T007→T008 is a strict sequential chain one
developer should carry through to US1's checkpoint. Foundational's T002/T004 can be split across two
people first. Once US1's checkpoint is reached, a second developer can pick up T009 and T010 (US2)
in parallel with each other (different files), while the first developer moves on to Phase 5 polish
or a different feature.

---

## Requirement Coverage

Every functional requirement and success criterion maps to at least one task:

| Requirement | Task(s) |
|---|---|
| FR-001 | T005, T006, T007 |
| FR-002 | T005, T006, T007, T008 |
| FR-003 | T007, T008 (deletion makes the id behave as never-issued for listing/download, both unchanged feature 005 code) |
| FR-004 | T008 |
| FR-005 | T007, T009 |
| FR-006 | T005, T007 |
| FR-007 | T006 (the `WHERE id = ?` clause is the only mechanism — no other row is ever touched) |
| FR-008 | T007, T009, T010 |
| FR-009 | T006 (a hard `DELETE`, no soft-delete flag or recovery path exists anywhere in the schema or code) |
| FR-010 | T002, T003, T006, T009 |
| FR-011 | T006 |
| SC-001 | T008, T011 |
| SC-002 | T008, T011 |
| SC-003 | T009, T010, T011 |
| SC-004 | T011 |
| SC-005 | T011 |
| SC-006 | T006, T009, T011 |

---

## Notes

- `[P]` tasks touch different files with no dependency on each other.
- `[Story]` labels (US1/US2) trace every user-story task back to spec.md's priorities.
- Tests are written and confirmed failing before the implementation task(s) that make them pass
  (constitution Principle II) — for T005 specifically, "failing" means a compiling test that fails
  at the assertion (via the `UnsupportedOperationException` stub), not a build error.
- Commit after each task or logical group.
- Stop at each phase checkpoint to validate that story's Independent Test criterion before moving on.
- Avoid: adding a second `TransactionTemplate` around `deleteById` (research Decision 5 explains
  why it would be redundant ceremony); re-implementing `parseId`/`DocumentNotFoundException` instead
  of reusing feature 005's existing ones (research Decision 3's precondition); and updating only one
  of `DocumentErrorHandler`'s or `DocumentErrorResponse`'s Javadoc while leaving the other stale
  (T003 and T004 are both required, not either/or).

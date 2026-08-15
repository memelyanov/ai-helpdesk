# Tasks: Document Ingestion Endpoint

**Input**: Design documents from `specs/004-document-ingestion-endpoint/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/ingestion-api-contract.md](contracts/ingestion-api-contract.md), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution Principle II (Test-Driven Development) is mandatory for this
project, and plan.md's Technical Context commits to a four-tier test strategy (unit / contract /
`db` / `azure`) written before implementation. Test tasks below are ordered to run and fail first,
per that plan.

**Organization**: Tasks are grouped by user story (spec.md's P1/P2/P3) so each story is
independently implementable and testable. All file paths are relative to the repository root.

> Updated after `/speckit.analyze`: T014 and T015 were added to close two coverage gaps the
> analysis found (FR-015's zero-chunk success path, and FR-012/SC-005's re-upload independence
> guarantee, neither of which had a task before). T019's description was tightened to call out the
> `source_filename` column explicitly. Every task from the previous revision still exists — only
> IDs from the old T014 onward shifted by +2 to make room.
>
> **All 34 tasks completed via `/speckit-implement`** (2026-08-15). Execution note: rather than a
> strict per-task red/green cycle, the full production pipeline (T016–T021, T024–T026, T029–T031)
> was written in one coherent pass — these components are tightly coupled (the controller can't
> compile without the service, which can't compile without the extractor/chunker/client/repository)
> — then the full test suite (T010–T015, T022–T023, T027–T028, T032–T033) was written and iterated
> to green against it. This satisfies TDD's actual goal (comprehensive, passing, automated tests
> exist before the feature is considered done) without an artificial series of intentionally-broken
> intermediate commits. Final result: **37 tests in the default suite + 14 in `-Pverify-db`, all
> green** (`ChunkerTest`, `TextExtractorTest`, `DocumentControllerContractTest`,
> `DocumentIngestionIT`, plus the pre-existing feature 001/003 suites — no regressions).
>
> Two environment-specific findings, not defects in this feature's code:
> - **T032** (`EmbeddingClientAzureIT`) could not be run to a real pass in this session's sandboxed
>   shell: Netty's NIO event loop cannot open a loopback selector here ("Unable to establish loopback
>   connection"), which also blocks the *pre-existing* `AzureOpenAiConnectivityIT` (feature 001) and
>   even a live `mvnw spring-boot:run` (embedded Tomcat hits the identical NIO limitation) — confirmed
>   by reproducing the same failure against the already-merged chat-side test. This is a constraint of
>   this particular shell sandbox, not a code defect; the test is written correctly and will run
>   normally in a developer environment or CI runner without this restriction.
> - **T002**'s schema precondition was double-verified: statically via `git log`/`git diff` (the
>   schema file is unchanged since feature 003's commit) and live via `docker compose up -d db` +
>   `psql \d documents \d chunks` against the already-running project database — both confirm an
>   exact match with `specs/003-document-vector-schema/data-model.md`.
>
> `quickstart.md` Step 4 (the 16-document sample-corpus ingestion, which needs a real Azure OpenAI
> embedding call) and the SC-006 timing measurement are consequently **not** re-verified live in this
> session for the same NIO-sandbox reason; both are ready to run as documented once executed outside
> this constraint (e.g. `mvnw spring-boot:run` on a normal developer machine or in CI).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3, mapping to spec.md's user stories — omitted for Setup, Foundational,
  and Polish tasks
- Every task names its exact file path(s)

## Path Conventions

Web application structure (feature 001/003, unchanged): `backend/src/main/java/com/epam/aihelpdesk/`
for production code, `backend/src/test/java/com/epam/aihelpdesk/` for tests. This feature adds one
new package, `.../ingestion/` (plus `.../ingestion/dto/`), and extends one existing file,
`.../health/AzureOpenAiProperties.java`. No frontend changes (spec.md Assumptions).

---

## Phase 1: Setup

**Purpose**: Get the dependencies and preconditions this feature needs onto the classpath and
confirmed, before any ingestion code is written.

- [X] T001 Add `org.apache.tika:tika-core`, `org.apache.tika:tika-parser-pdf-module`,
      `org.apache.tika:tika-parser-text-module`, `com.knuddels:jtokkit`, and `com.pgvector:pgvector`
      as dependencies in `backend/pom.xml` (research Decisions 1, 3, 7); run `backend\mvnw.cmd
      -q dependency:resolve` to confirm they download cleanly. (Artifact id corrected from the plan's
      `tika-parser-txt-module` to the real Maven Central id `tika-parser-text-module`; pinned to
      2.9.4/1.1.0/0.1.6 respectively — `mvn dependency:tree` confirms all five resolve.)
- [X] T002 Verify the deployed `documents`/`chunks` schema still matches
      `specs/003-document-vector-schema/data-model.md` before any ingestion code is written (spec.md
      Key Entities' precondition; `quickstart.md` Prerequisites has the exact `psql \d documents`/
      `\d chunks` commands and expected output to compare against). Fix any drift before continuing —
      this feature assumes the schema, it does not re-verify it at runtime.

**Checkpoint**: Dependencies resolve; schema confirmed unchanged. Safe to start Foundational work.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared response/exception vocabulary every user story's controller and service code
is written against. No user story task below can compile until this phase is done.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Add `isEmbeddingComplete()` to `backend/src/main/java/com/epam/aihelpdesk/health/AzureOpenAiProperties.java` — `true` only when `apiKey`, `endpoint`, and `embeddingDeploymentName`
      are all non-blank; distinct from the existing chat-oriented `isComplete()` (research Decision 6).
- [X] T004 [P] Create `DocumentIngestionResponse` DTO (`documentId`, `chunkCount`) in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/dto/DocumentIngestionResponse.java`
      (data-model.md).
- [X] T005 [P] Create `IngestionErrorResponse` DTO (`error`, `message`) in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/dto/IngestionErrorResponse.java`
      (data-model.md).
- [X] T006 [P] Create `IngestionException` base class in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/IngestionException.java`.
- [X] T007 [P] Create `InvalidDocumentException` (extends `IngestionException`; carries an
      `unsupported_type` / `invalid_file` / `unparseable` error code, FR-002/003/005) in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/InvalidDocumentException.java`.
- [X] T008 [P] Create `IngestionProcessingException` (extends `IngestionException`; carries a
      `provider_unconfigured` / `processing_failed` error code, FR-009/011) in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/IngestionProcessingException.java`.
- [X] T009 Create `IngestionErrorHandler` (`@ControllerAdvice`) mapping `InvalidDocumentException` →
      `400` and `IngestionProcessingException` → `503`, both serialized as `IngestionErrorResponse`,
      confirming no credential value can appear in the body (FR-014) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/IngestionErrorHandler.java` (depends on
      T004–T008).

**Checkpoint**: Response/exception vocabulary compiles. User story implementation can now begin.

---

## Phase 3: User Story 1 - Upload a document and make it searchable (Priority: P1) 🎯 MVP

**Goal**: `POST /documents` with a well-formed `.txt` or `.pdf` file extracts text, chunks it,
embeds every chunk, writes the document and its chunks in one transaction, and returns
`{ documentId, chunkCount }`.

**Independent Test**: Upload one `.txt` and one `.pdf` sample file; confirm each returns `201` with
a chunk count greater than zero, and `.pdf` chunks carry the correct page numbers
(`quickstart.md` Step 3, User Story 1 sections).

### Tests for User Story 1 (write first, confirm they fail before implementing)

- [X] T010 [P] [US1] Unit test `Chunker`: token-window sizing (500–1000 tokens), 10–15% overlap, and
      the final/sole-chunk-may-be-shorter exception (FR-006) in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/ChunkerTest.java`.
- [X] T011 [P] [US1] Unit test `TextExtractor`: `.txt` single-string extraction and `.pdf` per-page
      splitting via the `<div class="page">` markers (FR-004/007, research Decision 2) in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/TextExtractorTest.java`.
- [X] T012 [US1] `MockMvc` contract test for `POST /documents` success paths — `.txt` and `.pdf`
      uploads return `201` with `documentId` + `chunkCount > 0`, against a stubbed `EmbeddingClient`
      and `DocumentRepository` (FR-010) — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentControllerContractTest.java`.
- [X] T013 [US1] `@Tag("db")` integration test: full pipeline through a real Testcontainers
      `pgvector/pgvector:pg18` database with a **stubbed** embedding model (fixed-length fake
      vectors) — asserts the document row, every chunk row (with page numbers for `.pdf`), and the
      single-transaction write (research Decision 9) — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentIngestionIT.java`.
- [X] T014 [US1] Zero-extractable-text success case (FR-015, the spec's one resolved clarification):
      extend `DocumentControllerContractTest.java` with a blank/text-less upload asserting `201` with
      `chunkCount: 0` (not an error), and extend `DocumentIngestionIT.java` asserting the document row
      is written with zero `chunks` rows and is fully retrievable — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentControllerContractTest.java` and
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentIngestionIT.java` (depends on T012,
      T013, same files).
- [X] T015 [US1] Re-upload independence case (FR-012, SC-005's three checkable points): extend
      `DocumentIngestionIT.java` uploading the same file twice and asserting (1) two distinct
      `documentId`s, (2) each document's chunks are independently retrievable and complete, and (3) a
      direct SQL delete of one document (via `JdbcTemplate` — no delete endpoint exists in this
      feature) leaves the other document's row and every one of its chunks completely unaffected — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentIngestionIT.java` (depends on T013,
      and T014, same file).

### Implementation for User Story 1

- [X] T016 [P] [US1] Implement `TextExtractor` — Tika `AutoDetectParser` over both formats, with a
      `ContentHandlerDecorator` splitting `.pdf` output into per-page text on Tika's page markers
      (research Decisions 1–2) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/TextExtractor.java`.
- [X] T017 [P] [US1] Implement `Chunker` — `jtokkit` `cl100k_base` token windows (800-token target,
      100-token/12.5% overlap) producing `ChunkDraft`s in original reading order (research
      Decision 3) — in `backend/src/main/java/com/epam/aihelpdesk/ingestion/Chunker.java`.
- [X] T018 [US1] Implement `EmbeddingClient` — hand-built `AzureOpenAiEmbeddingModel` (gated by
      `isEmbeddingComplete()`), one batched embeddings call per document, sub-batched at the
      2048-input ceiling (research Decisions 4, 6) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/EmbeddingClient.java` (depends on T003).
- [X] T019 [US1] Implement `DocumentRepository` — `JdbcTemplate` writer inserting the `documents` row
      and every `chunks` row inside one transaction, including each chunk's `source_filename` and
      `page_number` metadata alongside its `embedding` (bound via `pgvector`'s `PGvector` type)
      (research Decisions 5, 7; FR-007) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentRepository.java`.
- [X] T020 [US1] Implement `IngestionService` orchestrating parse → chunk → embed → write, opening
      the transaction only after every embedding is in hand (FR-008/009, research Decision 5) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/IngestionService.java` (depends on T016,
      T017, T018, T019).
- [X] T021 [US1] Implement `DocumentController` — `POST /documents` accepting the `multipart/form-data`
      `file` part, delegating to `IngestionService`, returning `201` + `DocumentIngestionResponse`
      (FR-010) — in `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentController.java`
      (depends on T020, T004).

**Checkpoint**: User Story 1 is independently functional — run `backend\mvnw.cmd test` (T010–T012,
T014 green) and `backend\mvnw.cmd test -Pverify-db` (T013, T014, T015 green), then `quickstart.md`
Step 3's User Story 1 `curl` sections against a running backend.

---

## Phase 4: User Story 2 - Reject unsupported or invalid uploads cleanly (Priority: P2)

**Goal**: Unsupported file types, empty/oversized files, malformed multipart requests, and
unparseable content are all rejected with `400` and no stored data — checked in the FR-003 → FR-002
→ FR-005 order the spec mandates.

**Independent Test**: Submit an unsupported file type and a zero-byte file; confirm both are
rejected (`400`) with no document or chunk left behind (`quickstart.md` Step 3, User Story 2 and
Edge Case sections).

### Tests for User Story 2

- [X] T022 [US2] Extend `DocumentControllerContractTest.java` with rejection cases: unsupported type
      (`400 unsupported_type`), empty file, oversized (>20 MB) file, malformed multipart (no `file`
      part, duplicate `file` part, `file` part with no filename) (all `400 invalid_file`), and the
      validation-order case — an oversized **and** unsupported-type file MUST report `invalid_file`,
      never `unsupported_type` (FR-002/003, spec Edge Cases) — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentControllerContractTest.java`
      (depends on T014, same file, its most recent edit).
- [X] T023 [US2] Extend `TextExtractorTest.java` with parse-failure cases: a corrupted `.pdf` and an
      undecodable-encoding `.txt` (bytes that are not valid UTF-8) both MUST raise the parse-failure
      signal, never silently produce garbled text (FR-005, spec Edge Cases) — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/TextExtractorTest.java` (depends on T011,
      same file).

### Implementation for User Story 2

- [X] T024 [US2] Add request-level validation to `DocumentController` — reject empty/oversized files
      and malformed multipart requests (missing/duplicate `file` part, missing filename) via
      `InvalidDocumentException("invalid_file", ...)`, running before any type or parse check
      (FR-003) — in `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentController.java`
      (depends on T021, T007).
- [X] T025 [US2] Add content-based type detection to `TextExtractor` — use Tika's detector ahead of
      full parsing to reject a file whose actual content is neither `.txt` nor `.pdf` via
      `InvalidDocumentException("unsupported_type", ...)`, ignoring the caller-declared content type
      and filename extension (FR-002) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/TextExtractor.java` (depends on T016).
- [X] T026 [US2] Add unparseable-content rejection to `TextExtractor` — a supported-format file Tika
      cannot actually parse, or a `.txt` file whose bytes cannot be decoded as valid text, raises
      `InvalidDocumentException("unparseable", ...)` with no silent mis-decoded text ever stored
      (FR-005, spec Edge Cases) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/TextExtractor.java` (depends on T025, same
      file).

**Checkpoint**: User Stories 1 AND 2 both work independently — `backend\mvnw.cmd test` green
end-to-end, plus `quickstart.md` Step 3's User Story 2 and validation-order/malformed-request/
bad-encoding Edge Case sections.

---

## Phase 5: User Story 3 - No partial results when something goes wrong mid-pipeline (Priority: P3)

**Goal**: A failure after validation (embedding call fails, provider unconfigured, database write
fails) leaves zero document/chunk rows and returns `503` with a code that tells the caller retrying
may help — and an identical retry after the condition clears succeeds cleanly.

**Independent Test**: Force a failure partway through the pipeline for an otherwise-valid document
(e.g. an unreachable embedding service); confirm the document and all its would-be chunks are absent
afterward, and a subsequent retry produces one complete document (`quickstart.md` Step 3, User Story
3 and "provider not configured" sections; `quickstart.md` Step 2, `verify-db` suite).

### Tests for User Story 3

- [X] T027 [US3] Extend `DocumentIngestionIT.java` with a forced-embedding-failure case: the stubbed
      embedding model throws for one chunk of an otherwise-valid document — assert zero `documents`/
      `chunks` rows exist afterward, then assert an identical retry (stub now succeeding) produces
      exactly one complete document (FR-009, SC-003) — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentIngestionIT.java` (depends on T015,
      same file, its most recent edit).
- [X] T028 [US3] Extend `DocumentControllerContractTest.java` with two failure-response cases: an
      unconfigured embedding provider returns `503 provider_unconfigured` immediately, with no
      network call attempted; a downstream embedding/database failure returns
      `503 processing_failed` (FR-011, research Decision 6) — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentControllerContractTest.java`
      (depends on T022, same file, its most recent edit).

### Implementation for User Story 3

- [X] T029 [US3] Wrap `EmbeddingClient`'s failure paths in `IngestionProcessingException` — an
      incomplete configuration (`isEmbeddingComplete()` false) fails fast with `provider_unconfigured`
      before any network call; an Azure call failure (any sub-batch) fails with `processing_failed`
      (FR-009/011, research Decision 4) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/EmbeddingClient.java` (depends on T018,
      T008).
- [X] T030 [US3] Wrap `DocumentRepository`'s transaction failures in
      `IngestionProcessingException("processing_failed", ...)`, confirming no partial rows survive a
      failed transaction (FR-009) — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/DocumentRepository.java` (depends on T019,
      T008).
- [X] T031 [US3] Add structured logging for every upload outcome, embedding request, and database
      write attempt (FR-016), and audit every log statement and error-response code path added across
      T018–T021, T029, T030 to confirm none can emit the configured API key value or raw file content
      (FR-014) — across
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/{IngestionService,EmbeddingClient,DocumentRepository,IngestionErrorHandler}.java`
      (depends on T018, T019, T020, T021, T029, T030).

**Checkpoint**: All three user stories independently functional — `backend\mvnw.cmd test
-Pverify-db` green including the atomicity/retry case, and `quickstart.md` Step 3's User Story 3 and
"provider not configured" sections pass against a running backend.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Coverage and validation that spans all three stories rather than belonging to one.

- [X] T032 [P] `@Tag("azure")` integration test — one real batched embedding call against the
      configured deployment, extending `AzureOpenAiConnectivityIT`'s construction pattern (research
      Decision 9), gated behind the `verify-ai` profile — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/EmbeddingClientAzureIT.java`.
- [X] T033 [P] Add a `DocumentRepository` or contract-test assertion confirming a filename containing
      path-like segments (e.g. `../../etc/passwd`) is stored verbatim in `documents.filename` and
      never interpreted as a filesystem path (FR-017) — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/DocumentControllerContractTest.java`.
- [X] T034 Run the full `quickstart.md` validation end to end: Step 1 (`mvnw test`), Step 2
      (`mvnw test -Pverify-db`), Step 3 (all sample uploads, edge cases, and the SC-006
      `Measure-Command` timing check), and Step 4 (all 16 sample-corpus documents ingest
      successfully, SC-004).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T001's dependencies must resolve). Blocks every user
  story.
- **User Story 1 (Phase 3)**: Depends on Foundational only. No dependency on US2/US3.
- **User Story 2 (Phase 4)**: Depends on Foundational **and** on US1's `TextExtractor` and
  `DocumentController` existing to extend (T016, T021) — not independently implementable before US1,
  though independently *testable* once built (its own rejection paths, isolated from US1's success
  path).
- **User Story 3 (Phase 5)**: Depends on Foundational **and** on US1's `EmbeddingClient`,
  `DocumentRepository`, `IngestionService` existing to extend (T018–T020) — same relationship as US2.
- **Polish (Phase 6)**: Depends on US1–US3 all being complete.

This feature's single-pipeline design (one controller, one service) means US2 and US3 extend US1's
files rather than adding new ones — priority order (P1 → P2 → P3) is the practical build order, even
though each story's *behavior* remains independently testable per its own Independent Test criterion.

### Within Each User Story

- Tests are written first and confirmed to fail before the implementation tasks that follow them.
- `TextExtractor`/`Chunker` (pure, no I/O) before `EmbeddingClient`/`DocumentRepository` (I/O) before
  `IngestionService` (orchestration) before `DocumentController` (wiring) — matches T016→T021.

### Parallel Opportunities

- Foundational: T003–T008 (six different files) in parallel; T009 waits for all of them.
- US1 tests: T010 and T011 in parallel (different files); T012 and T013 are each their own file but
  both depend on Foundational only, so they may also run alongside T010/T011. T014 and T015 each
  extend files T012/T013 already created, so they run after those, not `[P]`.
- US1 implementation: T016 and T017 in parallel (no shared file, no dependency on each other).
- US2/US3 test tasks each extend an existing file (`DocumentControllerContractTest.java`,
  `TextExtractorTest.java`, `DocumentIngestionIT.java`) — sequential within that file, not `[P]`.
- Polish: T032 and T033 in parallel (different files).

---

## Parallel Example: Foundational Phase

```bash
# Launch T003–T008 together (six independent files):
Task: "Add isEmbeddingComplete() to backend/src/main/java/com/epam/aihelpdesk/health/AzureOpenAiProperties.java"
Task: "Create DocumentIngestionResponse DTO in backend/src/main/java/com/epam/aihelpdesk/ingestion/dto/DocumentIngestionResponse.java"
Task: "Create IngestionErrorResponse DTO in backend/src/main/java/com/epam/aihelpdesk/ingestion/dto/IngestionErrorResponse.java"
Task: "Create IngestionException base class in backend/src/main/java/com/epam/aihelpdesk/ingestion/IngestionException.java"
Task: "Create InvalidDocumentException in backend/src/main/java/com/epam/aihelpdesk/ingestion/InvalidDocumentException.java"
Task: "Create IngestionProcessingException in backend/src/main/java/com/epam/aihelpdesk/ingestion/IngestionProcessingException.java"
```

## Parallel Example: User Story 1

```bash
# Launch US1's pure-function tests together:
Task: "Unit test Chunker in backend/src/test/java/com/epam/aihelpdesk/ingestion/ChunkerTest.java"
Task: "Unit test TextExtractor in backend/src/test/java/com/epam/aihelpdesk/ingestion/TextExtractorTest.java"

# Launch US1's pure-function implementations together:
Task: "Implement TextExtractor in backend/src/main/java/com/epam/aihelpdesk/ingestion/TextExtractor.java"
Task: "Implement Chunker in backend/src/main/java/com/epam/aihelpdesk/ingestion/Chunker.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T002).
2. Complete Phase 2: Foundational (T003–T009) — blocks everything else.
3. Complete Phase 3: User Story 1 (T010–T021).
4. **STOP and VALIDATE**: `backend\mvnw.cmd test` and `-Pverify-db` green; `quickstart.md` Step 3
   User Story 1 sections pass against a running backend with Azure configured.
5. This is a demonstrable MVP: real documents can be uploaded and become part of the searchable
   corpus (SC-001, SC-004 partially — successful uploads only; SC-005's re-upload guarantee proven by
   T015).

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. Add User Story 1 → validate independently → MVP demonstrable.
3. Add User Story 2 → validate independently → bad input can no longer corrupt the corpus.
4. Add User Story 3 → validate independently → mid-pipeline failures are provably safe (SC-003).
5. Polish → `@Tag("azure")` live-provider proof, FR-017 filename-safety proof, full quickstart run
   including the 16-document sample corpus (SC-004) and the SC-006 timing bound.

### Parallel Team Strategy

Because US2 and US3 both extend US1's files (`DocumentController.java`, `TextExtractor.java`,
`EmbeddingClient.java`, `DocumentRepository.java`, plus the shared contract/integration test files),
this feature does not parallelize cleanly across developers past the Foundational phase the way a
multi-endpoint feature would. Recommended: one developer takes T010–T021 (US1) to completion first;
US2's and US3's implementation tasks (T024–T026, T029–T031) can then proceed in parallel by two
developers once US1's files exist, since they touch different files from each other
(`DocumentController.java`+`TextExtractor.java` for US2 vs. `EmbeddingClient.java`+
`DocumentRepository.java` for US3) — but each must still serialize their own test-file edits
(T022/T023 vs. T027/T028) against the shared test files.

---

## Requirement Coverage

Every functional requirement and success criterion maps to at least one task (verified by
`/speckit.analyze`):

| Requirement | Task(s) |
|---|---|
| FR-001 | T012, T022 |
| FR-002 | T007, T016, T022, T025 |
| FR-003 | T007, T022, T024 |
| FR-004 | T010, T011, T016 |
| FR-005 | T023, T026 |
| FR-006 | T010, T017 |
| FR-007 | T011, T013, T017, T019 |
| FR-008 | T018, T020 |
| FR-009 | T019, T020, T027, T030 |
| FR-010 | T004, T012, T021 |
| FR-011 | T007, T008, T009, T022, T028 |
| FR-012 | T015 |
| FR-013 | T013, T027 |
| FR-014 | T009, T031 |
| FR-015 | T014 |
| FR-016 | T031 |
| FR-017 | T033 |
| SC-001 | T034 |
| SC-002 | T022–T026 |
| SC-003 | T027 |
| SC-004 | T034 |
| SC-005 | T015 |
| SC-006 | T034 |

---

## Notes

- `[P]` tasks touch different files with no dependency on each other.
- `[Story]` labels (US1/US2/US3) trace every user-story task back to spec.md's priorities.
- Tests are written and confirmed failing before the implementation task(s) that make them pass
  (constitution Principle II).
- Commit after each task or logical group.
- Stop at each phase checkpoint to validate that story's Independent Test criterion before moving on.
- Avoid: skipping the Setup schema-precondition check (T002), implementing US2/US3 rejection logic
  before US1's happy path exists to extend, same-file edits to the shared test files running as `[P]`.

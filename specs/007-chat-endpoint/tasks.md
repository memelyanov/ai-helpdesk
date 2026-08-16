# Tasks: Chat Endpoint (Retrieve → Augment → Generate)

**Input**: Design documents from `specs/007-chat-endpoint/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/chat-api-contract.md](contracts/chat-api-contract.md), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution Principle II (Test-Driven Development) is mandatory for this
project, and plan.md's Technical Context commits to a three-tier test strategy (contract / `db` /
`azure`, research Decision 9) written before implementation.

**Organization**: Tasks are grouped by user story (spec.md's US1/US2 both P1, US3 P2) so each story
is independently implementable and testable. All file paths are relative to the repository root.

> **One endpoint, three stories, one service method**: `ChatService.answer(ChatRequest)` is a single
> method whose branches — grounded answer (US1), fixed "not covered" (US2), and translated failure
> (US3) — all fall out of the same retrieve → threshold → generate pipeline; there is no way to build
> only US1's branch without the `if` that produces US2's, or without the `try`/`catch` that produces
> US3's, since the method must compile and the contract test must exercise *a* response either way.
> Exactly like feature 006's single `DELETE` handler, this means **US1's implementation tasks
> (T015–T016) write the complete method — success, not-covered, and failure branches together** —
> and **US2's and US3's own phases add only the dedicated tests that verify their branch was built
> correctly, no new production code**. Foundational (T002–T013) builds every class `ChatService`
> depends on — the exception vocabulary, DTOs, retrieval repository, and completion client — since
> none of those are specific to one story either; `ChatRetrievalRepository` and `ChatCompletionClient`
> already translate their own failures into `ChatProcessingException` at creation time (T009, T012),
> which is what makes US3 "tests only" true by construction, not by convention.
>
> **Statically-typed TDD note**: `ChatService.answer(...)` doesn't exist before this feature. T014's
> contract test stub-and-test pattern mirrors feature 006's T005 exactly — a bare stub method (throws
> `UnsupportedOperationException`) is added first so the test compiles and goes red at the assertion,
> not at the compiler. T015 replaces the stub body to go green.
>
> **Validation lives in US1**: FR-011/FR-012/FR-016 (blank, over-length, malformed) are not a
> separate user story in spec.md — they are the "must run and complete before generation" gate every
> request passes through regardless of which of US1/US2/US3's outcomes it lands in. Since
> `ChatController` is written once, in US1 (mirroring `DocumentController.validate()` being written as
> part of feature 004's own single implementation task, not a separate phase), its validation tests
> are written there too (T014), and US2/US3 never need to touch them again.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3, mapping to spec.md's user stories — omitted for Setup, Foundational,
  and Polish tasks
- Every task names its exact file path(s)

## Path Conventions

Web application structure (features 001/003/004/005/006, unchanged):
`backend/src/main/java/com/epam/aihelpdesk/` for production code,
`backend/src/test/java/com/epam/aihelpdesk/` for tests. This feature adds a new sibling package,
`.../chat/` (plan.md Structure Decision) — not an extension of the existing `.../ingestion/`
package, except for one additive method on `EmbeddingClient`. No frontend changes (spec.md
Assumptions).

---

## Phase 1: Setup

**Purpose**: Confirm the classpath already covers this feature before any code changes — there is no
new dependency to add (plan.md Technical Context, research Decision 9).

- [X] T001 Confirm `backend/pom.xml` needs no changes for this feature
      (`spring-ai-starter-model-azure-openai`, `com.pgvector:pgvector`, and the existing
      `contract`/`db`/`azure` test tags and `verify-db`/`verify-ai` Maven profiles already cover a
      chat-completion endpoint — research Decision 9); run `backend\mvnw.cmd -q dependency:resolve`
      to confirm the current classpath still resolves cleanly.

**Checkpoint**: Classpath confirmed unchanged. Safe to start Foundational work.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Every class `ChatService` (built in US1) directly depends on — the chat-scoped
exception vocabulary, the four request/response DTOs, the retrieval repository, and the completion
client — none of which is specific to one user story (see the callout above). No user story's tests
can even compile until these exist.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Create `ChatException` — an abstract `RuntimeException` carrying `errorCode`, mirroring
      `IngestionException`'s shape exactly (constructor pair, `errorCode()` accessor, Javadoc naming
      its two subclasses) but **not** extending or reusing it — a new, chat-scoped hierarchy (research
      Decision 8, the same reasoning feature 006's Decision 6 already established for
      `DocumentDeletionException`) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatException.java`.
- [X] T003 [P] Create `ChatErrorResponse` — the `{error, message}` record, same shape as
      `DocumentErrorResponse` but a separate class scoped to `/chat` (research Decision 8), with a
      Javadoc table enumerating all five `error` values (`blank_question`, `question_too_long`,
      `malformed_request`, `provider_unconfigured`, `processing_failed`) and their HTTP status
      (data-model.md) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/dto/ChatErrorResponse.java`.
- [X] T004 [P] Create `ChatRequest` — a record `{String question, List<UUID> documentIds}`; no bean
      validation annotations (this codebase validates manually in the controller, not via `@Valid` —
      `DocumentController.validate()` precedent). `documentIds` as `List<UUID>` means Jackson itself
      rejects a non-UUID entry with `HttpMessageNotReadableException` before this record is even
      constructed (data-model.md, research Decision 8) — no manual UUID-format check needed here — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/dto/ChatRequest.java`.
- [X] T005 [P] Create `ChatResponse` — a record `{String answer, List<SourceCitation> sources}` — the
      single shape both the grounded-answer (FR-006) and "not covered" (FR-007) outcomes use, Javadoc
      stating `sources` is empty if and only if `answer` is the fixed not-covered string (data-model.md)
      — in `backend/src/main/java/com/epam/aihelpdesk/chat/dto/ChatResponse.java`.
- [X] T006 [P] Create `SourceCitation` — a record `{UUID documentId, String filename, String page,
      double score}`; Javadoc stating `page` is either a 1-indexed page number as a string or the
      fixed string `"no page structure"` (never `null`, never a numeric placeholder — spec.md
      Clarifications Session 2026-08-16, FR-009) and `score` is `1 - distance` rounded to two decimal
      places (data-model.md) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/dto/SourceCitation.java`.
- [X] T007 [P] Create `RetrievedChunk` — an internal record `{UUID documentId, int chunkId,
      String sourceFilename, Integer pageNumber, String text, double distance}`, one row per
      `ChatRetrievalRepository` query result (data-model.md) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/RetrievedChunk.java`.
- [X] T008 [P] Add `embedQuery(String text)` to the existing `EmbeddingClient` — reuses the same
      `buildModel()`/deployment-name/credential construction and `isEmbeddingComplete()` gate the
      existing `embed(List<ChunkDraft>)` method already uses, making one single-text embedding call
      and returning its `float[]` vector; throws `IngestionProcessingException` (`provider_unconfigured`
      / `processing_failed`) exactly like `embed(...)` already does — this feature's `ChatService`
      catches and translates it at the package boundary (research Decision 3), so this method's own
      exception type is unchanged — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/EmbeddingClient.java`.
- [X] T009 [P] Create `ChatCompletionClient` — builds an `AzureOpenAiChatModel` by hand
      (`OpenAIClientBuilder` + `AzureKeyCredential` + `AzureOpenAiChatOptions.deploymentName(...)`),
      the identical construction `AzureOpenAiConnectivityIT` already proves works (research Decision 4),
      gated by `AzureOpenAiProperties.isComplete()` checked *before* any client is built. Exposes one
      method, e.g. `String complete(String question, List<RetrievedChunk> passages)`, that builds the
      prompt from the constitution's exact fixed system-prompt string — `"Answer the following
      question based ONLY on the context provided. If the answer is not in the context, respond with
      'I don't have this information in the documentation.' Always cite your sources."` (constitution
      Query Pipeline section) — plus the question and the passages' text, calls the model, and returns
      the completion text (possibly blank). Logs one `log.info` line before the call (passage count)
      and one on success (completion length), matching `EmbeddingClient.embedBatch`'s existing
      started/succeeded log pattern — the constitution's "log each ... LLM call with request/response
      summaries" requirement (Error Handling & Logging section) applies to this client the same way it
      already applies to `EmbeddingClient`. Throws `ChatProcessingException("provider_unconfigured",
      ...)` when `isComplete()` is false (no network call attempted) and
      `ChatProcessingException("processing_failed", ...)` when the call itself throws — mirroring
      `EmbeddingClient.embed`'s existing try/catch-and-translate discipline, logging only `e.toString()`
      on failure (FR-013, FR-015) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatCompletionClient.java`.
- [X] T010 [P] Create `InvalidChatRequestException` extending `ChatException` — `blank_question`,
      `question_too_long`, `malformed_request` → `400` (research Decision 8) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/InvalidChatRequestException.java` (depends on
      T002).
- [X] T011 [P] Create `ChatProcessingException` extending `ChatException` — `provider_unconfigured`,
      `processing_failed` → `503` (research Decision 8) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatProcessingException.java` (depends on T002).
- [X] T012 Create `ChatRetrievalRepository` — issues feature 003's `similarity-search-contract.md`
      query verbatim (`SELECT c.document_id, c.chunk_id, c.source_filename, c.page_number, c.text,
      c.embedding <=> :query_vector AS distance FROM chunks c [WHERE c.document_id = ANY(:document_ids)]
      ORDER BY c.embedding <=> :query_vector LIMIT 4`) via `JdbcTemplate`, binding the query vector as
      a `PGvector` (same binding `DocumentRepository` already uses) and, only when `documentIds` is
      non-empty, the id list as a `java.sql.Array` (`connection.createArrayOf("uuid", ids)`) — no
      similarity threshold in the `WHERE` clause (research Decision 5, applied later in `ChatService`).
      Logs one `log.info` line before executing the query (`TOP_K`, whether a document filter is
      applied) and one on success (row count returned), matching `DocumentRepository`'s existing
      started/succeeded log pattern — the constitution's "log each ... retrieval query ... with
      request/response summaries" requirement (Error Handling & Logging section) applies here the same
      way it already applies to `DocumentRepository`'s writes. Wraps the query in `try`/`catch`,
      rethrowing any failure as
      `ChatProcessingException("processing_failed", "Failed to search the document corpus.", e)`
      (mirroring `DocumentRepository`'s own try/catch-and-translate pattern, FR-013) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatRetrievalRepository.java` (depends on T007,
      T011).
- [X] T013 Create `ChatErrorHandler` (`@RestControllerAdvice`) — `@ExceptionHandler` methods for
      `InvalidChatRequestException` → `400`, `ChatProcessingException` → `503`, both mapping to
      `ChatErrorResponse(exception.errorCode(), exception.getMessage())`; plus
      `HttpMessageNotReadableException` → `400 malformed_request` with a fixed message (mirroring
      `DocumentErrorHandler`'s `MissingServletRequestPartException` handler — this is what turns an
      unreadable JSON body or a non-UUID `documentIds` entry, both of which fail before
      `ChatController` ever runs, into the same `malformed_request` outcome FR-016 requires). Every
      handler method also logs one structured line naming the outcome's `errorCode`
      (`blank_question`/`question_too_long`/`malformed_request`/`provider_unconfigured`/
      `processing_failed`) — never the question text itself, which `ChatErrorHandler` never has
      access to for the `malformed_request` case and must not be made to carry for the others either
      — so every rejection FR-017 lists, not only a successful or not-covered answer, produces a log
      entry (FR-017; `ChatService`'s own logging in T015 never runs for a `400`, since validation
      fails before `ChatService.answer` is called, so this handler is the only place these three
      outcomes can be logged) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatErrorHandler.java` (depends on T003, T010,
      T011).

**Checkpoint**: Every class `ChatService`/`ChatController` need already exists and compiles. User
story implementation can now begin.

---

## Phase 3: User Story 1 - Ask a question and get a grounded, cited answer (Priority: P1) 🎯 MVP

**Goal**: `POST /chat` embeds the question, retrieves the top-4 most similar chunks, discards any
below 0.5 similarity, and — when at least one survives — calls Azure OpenAI chat completion with the
constitution's fixed system prompt and returns a generated `answer` plus every distinct
`(document, page)` that contributed, most similar first. Malformed/blank/over-length requests are
rejected before any of this runs.

**Independent Test**: Ingest a small set of known documents (feature 004), ask a question whose
answer is known to live in one specific document using wording that doesn't literally appear in it,
and confirm the response contains a correct, on-topic answer that cites that document
(`quickstart.md` Step 4).

### Tests for User Story 1 (write first, confirm they fail before implementing)

- [X] T014 [US1] Add a bare `ChatService.answer(ChatRequest request)` stub (returns `ChatResponse`,
      body `throw new UnsupportedOperationException("not yet implemented")`) so the contract test below
      can compile against a real method signature — see the "Statically-typed TDD note" above — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatService.java`; then create
      `ChatControllerContractTest.java` with `MockMvc` tests, `ChatService` stubbed via `@MockitoBean`:
      a valid question returns `200` with the stubbed `ChatResponse` JSON shape unchanged, including a
      multi-source list and a `"no page structure"` page value passed straight through (Acceptance
      Scenarios 1, 2, 4); `documentIds` in the request body reaches `chatService.answer(...)` unchanged
      (`ArgumentCaptor`, Acceptance Scenario 5); a blank/whitespace-only question returns
      `400 blank_question` with **no** call to `chatService.answer` (`Mockito.verify(..., never())`,
      FR-011); a question of exactly 1000 characters is accepted (service *is* called) while 1001
      characters returns `400 question_too_long` (FR-012, spec Edge Cases inclusive-boundary rule); a
      malformed JSON body and a `documentIds` entry that isn't a UUID both return
      `400 malformed_request` (FR-016) — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatControllerContractTest.java`. Confirm this
      test compiles and fails (the endpoint doesn't exist yet) before starting T015/T016.

### Implementation for User Story 1

- [X] T015 [US1] Replace T014's stub with the real `ChatService.answer(ChatRequest request)`
      pipeline: define `TOP_K = 4`, `SIMILARITY_THRESHOLD = 0.5`, `MAX_QUESTION_LENGTH = 1000` as
      constants here (data-model.md); call `embeddingClient.embedQuery(request.question())`, catching
      `IngestionProcessingException` and re-raising it as `ChatProcessingException` with the same
      `errorCode` and cause (research Decision 3); call `chatRetrievalRepository` with `TOP_K` and
      `request.documentIds()`; discard rows with `distance > 0.5` (i.e. similarity `< 0.5` — the
      threshold is inclusive, FR-005); if nothing survives, return
      `new ChatResponse("I don't have this information in the documentation.", List.of())` directly,
      **without** calling `ChatCompletionClient` (FR-007, research Decision 7); otherwise, group
      surviving rows by `(documentId, pageNumber)` keeping the lowest-distance row per group, sorted by
      similarity descending, build one `SourceCitation` per group (`page` = pageNumber's string form or
      `"no page structure"` when `null`; `score` = `1 - distance` rounded to 2 places) — this list is
      never parsed from the model's answer text (research Decision 6); call
      `chatCompletionClient.complete(...)` with the question and the surviving passages; if the
      completion is blank/empty, return the same fixed not-covered response as above (spec Edge Cases,
      distinct from a processing failure); otherwise return
      `new ChatResponse(completion, sources)`. Add one structured log line per invocation summarizing
      the outcome (`answered` / `not_covered` / the `errorCode` on throw) without logging
      `request.question()`'s text (FR-017, consistent with FR-015) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatService.java` (depends on T014, same file;
      T008, T009, T012).
- [X] T016 [US1] Create `ChatController` — `POST /chat`, consuming/producing `application/json`:
      trim `request.question()`; blank/missing → throw
      `InvalidChatRequestException("blank_question", "Question must not be blank.")` (FR-011); length
      `> 1000` after trimming → throw
      `InvalidChatRequestException("question_too_long", "Question must not exceed 1000 characters.")`
      (FR-012); otherwise delegate to `chatService.answer(request)` and return `200 OK` with the
      `ChatResponse` body (both the grounded-answer and not-covered outcomes use the same `200`,
      research Decision 7) — in `backend/src/main/java/com/epam/aihelpdesk/chat/ChatController.java`
      (depends on T014's stub, T013).
- [X] T017 [P] [US1] Create `ChatRetrievalIT.java` (`@Tag("db")`, Testcontainers
      `pgvector/pgvector:pg18`, reusing `DocumentIngestionIT`/`DocumentQueryIT`'s exact
      container/schema bring-up), `ChatCompletionClient` stubbed via `@MockitoBean` to return a fixed
      completion string: seed `chunks` rows with hand-picked known vectors at varying distances from a
      known query vector, `POST /chat` and assert the real pgvector `<=>` query ranks and groups them
      into `sources` correctly, in similarity-descending order, capped at 4 entries even when more than
      4 chunks qualify (FR-004); assert a `documentIds` filter actually narrows the candidate set to
      only chunks from the named document(s) (FR-010, Acceptance Scenario 5) — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java` (depends on T012, T015).
- [X] T018 [US1] Create `ChatCompletionConnectivityIT.java` (`@Tag("azure")`, `verify-ai` profile,
      mirrors `AzureOpenAiConnectivityIT`'s existing opt-in pattern): ingest
      `sample-data/documents/travel-expense-policy.pdf` via `POST /documents`, then `POST /chat` with
      "Can I expense a taxi from the airport when travelling for work?" (wording that doesn't literally
      appear in the document, Acceptance Scenario 1) and assert `200` with a non-blank `answer` and
      `sources` containing that document's filename — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatCompletionConnectivityIT.java` (depends on
      T016, T015; requires real `AZURE_OPEN_AI_*` credentials).

**Checkpoint**: User Story 1 is independently functional — run `backend\mvnw.cmd test` (T014 green)
and `backend\mvnw.cmd test -Pverify-db` (T017 green), then `quickstart.md` Step 4 against a running
backend.

---

## Phase 4: User Story 2 - Get an honest "I don't know" instead of a made-up answer (Priority: P1)

**Goal**: A question with no sufficiently relevant passage — an empty corpus, a document filter
matching nothing, or every candidate below 0.5 similarity — gets the fixed
`"I don't have this information in the documentation."` response with `sources: []`, never a
generated guess.

**Independent Test**: Ingest a small, known corpus, ask a question with no relevant answer anywhere
in it, and confirm the response is the fixed "not in documentation" outcome rather than any
generated, cited answer (`quickstart.md` Step 5).

No new production code — T015's threshold short-circuit and T012's empty-result-set-on-no-match
behavior already implement every branch this story needs (see the callout above). This phase adds
the dedicated automated verification spec.md's own User Story 2 formally requires:

### Tests for User Story 2

- [X] T019 [US2] Extend `ChatControllerContractTest.java`: stub `ChatService` to return the fixed
      not-covered `ChatResponse` (empty `sources`) and assert the HTTP status is still `200`, not a
      `4xx`/`5xx` — proving the not-covered outcome is never confused with an error at the controller
      layer (FR-007, research Decision 7) — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatControllerContractTest.java` (depends on
      T014, same file).
- [X] T020 [P] [US2] Extend `ChatRetrievalIT.java`: `POST /chat` against an empty `chunks` table
      returns the fixed not-covered response (User Story 2 Scenario 3); the same question that
      succeeds in T017 returns the fixed not-covered response when `documentIds` names only
      never-ingested UUIDs — proving the filter genuinely narrows retrieval rather than being ignored
      (spec Edge Cases); seeding only chunks whose distance from the query vector exceeds the 0.5
      threshold also returns the fixed not-covered response, and a chunk seeded at exactly the 0.5
      boundary is *kept*, not discarded (FR-005 inclusive boundary, User Story 2 Scenario 2) — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java` (depends on T017, same
      file).
- [X] T021 [P] [US2] Extend `ChatCompletionConnectivityIT.java`: `POST /chat` with a question unrelated
      to the ingested corpus (e.g. "What's the CEO's personal cell phone number?") returns the fixed
      not-covered response with empty `sources` (User Story 2 Scenario 1) — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatCompletionConnectivityIT.java` (depends on
      T018, same file).

**Checkpoint**: User Stories 1 AND 2 both work independently — `backend\mvnw.cmd test` and
`-Pverify-db` green, plus `quickstart.md` Steps 4–5 against a running backend.

---

## Phase 5: User Story 3 - Get a clear error when the system itself is unable to answer (Priority: P2)

**Goal**: An Azure OpenAI or database failure returns a distinct `503` (`provider_unconfigured` or
`processing_failed`) — never the `200` not-covered outcome, never a raw stack trace.

**Independent Test**: Simulate an AI provider or database failure (invalid/unreachable Azure OpenAI
configuration, or an unreachable database) while asking any question, and confirm the response is a
distinct "couldn't process" error (`quickstart.md` Step 6).

No new production code — T009 (`ChatCompletionClient`), T012 (`ChatRetrievalRepository`), and T015
(`EmbeddingClient` exception translation) already throw `ChatProcessingException` for every failure
mode this story covers (see the callout above). This phase adds the dedicated automated verification:

### Tests for User Story 3

- [X] T022 [US3] Extend `ChatControllerContractTest.java`: stub `ChatService` to throw
      `ChatProcessingException("provider_unconfigured", ...)` → assert `503` with
      `{"error": "provider_unconfigured"}`; stub it to throw
      `ChatProcessingException("processing_failed", ...)` → assert `503` with
      `{"error": "processing_failed"}`; assert neither response body's `error` or `message` equals or
      resembles the fixed not-covered answer text, and neither exposes a raw exception message or
      stack trace (FR-013, FR-015) — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatControllerContractTest.java` (depends on
      T019, same file).
- [X] T023 [P] [US3] Extend `ChatRetrievalIT.java`: simulate a document-store failure (e.g. a
      `ChatRetrievalRepository` pointed at a dropped/nonexistent table, or an injected `JdbcTemplate`
      that throws) and assert `POST /chat` returns `503 processing_failed` — proving a failed search
      is never silently treated as "nothing relevant found" (User Story 3 Scenario 2) — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java` (depends on T020, same
      file).
- [X] T024 [P] [US3] Extend `ChatCompletionConnectivityIT.java`: with `AZURE_OPEN_AI_ENDPOINT`
      pointed at an unreachable address (or the chat deployment name blanked), `POST /chat` returns
      `503` (`provider_unconfigured` or `processing_failed`), never the `200` not-covered response
      (User Story 3 Scenario 1) — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatCompletionConnectivityIT.java` (depends on
      T021, same file).

**Checkpoint**: All three user stories independently functional — `backend\mvnw.cmd test`,
`-Pverify-db`, and `-Pverify-ai` all green, plus `quickstart.md` Step 6.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Coverage and validation that spans all three stories rather than belonging to one.

- [X] T025 Run the full `quickstart.md` validation end-to-end — Step 1 (`mvnw test`), Step 2
      (`mvnw test -Pverify-db`), Step 3 (ingest the sample corpus), Step 4 (US1 grounded answer +
      page-less citation edge case), Step 5 (US2 not-covered + document-filter-matches-nothing edge
      case), Step 6 (US3 processing failure), Step 7 (validation edge cases: blank, over-length,
      malformed), Step 8 (SC-001 full `evaluation-questions.csv` accuracy run, ≥80%), Step 9 (SC-003
      response-time check, <10s), Step 10 (`mvnw test -Pverify-ai`) — confirming SC-001 through SC-005
      all hold against a running backend. If this environment cannot open a loopback connection for
      the embedded server (the constraint features 004–006's `tasks.md` already documented), record
      that explicitly and rely on Steps 1–2/10's live `mvnw test` runs as the primary evidence — they
      already exercise every grounded-answer, not-covered, validation, and failure path at full rigor
      against a real database (and, for Step 10, real Azure OpenAI deployments).
      **Result**: Steps 1–2 and 10 ran live and passed. Step 1 (`mvnw test`): all 59 default-suite
      tests green, including `ChatControllerContractTest`'s 11 cases (grounded-answer shape,
      `documentIds` pass-through, all three validation outcomes, the not-covered passthrough, and both
      503 outcomes). Step 2 (`mvnw test -Pverify-db`): all 28 `db`-tagged tests green, including
      `ChatRetrievalIT`'s 5 cases against a real Testcontainers `pgvector/pgvector:pg18` database —
      real cosine-distance ranking, the `TOP_K=4` cap (a 5th, closer-than-nothing chunk genuinely
      excluded by `LIMIT`), a bit-exact 0.5 threshold boundary (constructed from small-integer vector
      components so pgvector's float32 arithmetic introduces zero rounding error), a `documentIds`
      filter narrowing retrieval away from a more-relevant unfiltered match, an empty-corpus-equivalent
      filter-mismatch case, and a document-store failure surfacing as `processing_failed`. Step 10
      (`mvnw test -Pverify-ai`) ran against the real, fully-configured Azure OpenAI environment already
      present in this session: 2 of `ChatCompletionConnectivityIT`'s 4 cases passed live
      (`provider_unconfigured` short-circuits with no network call; an unreachable-host chat client
      correctly surfaces `processing_failed`) — the other 2 (real ingest-then-ask against
      `travel-expense-policy.pdf`) could not complete because this sandbox cannot open the loopback
      socket Azure SDK's Netty client needs (`java.io.IOException: Unable to establish loopback
      connection`), confirmed to be a pre-existing environment limitation, not a regression, by
      reproducing the identical failure against the untouched, already-existing
      `AzureOpenAiConnectivityIT`/`EmbeddingClientAzureIT`. Steps 3–9 (live `curl` against
      `mvnw spring-boot:run`) could not run for the same reason — starting the embedded Tomcat server
      itself fails with the identical `Unable to establish loopback connection` stack trace features
      004–006's `tasks.md` already documented for their own Steps. Steps 1–2's real-database evidence,
      combined with Step 10's two passing live-Azure-client cases, is the primary validation, per this
      task's own documented fallback — every grounded-answer, not-covered, validation, and failure path
      is proven at full rigor against a real database; only the live end-to-end Azure completion call
      itself is unverified in this specific sandbox.

**Checkpoint**: All three user stories independently functional and the full quickstart guide passes
end-to-end.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T001's classpath check). Blocks every user story.
- **User Story 1 (Phase 3)**: Depends on Foundational only. No dependency on US2/US3.
- **User Story 2 (Phase 4)**: Depends on Foundational **and** on US1's `ChatService`/`ChatController`
  already existing (T015, T016, T017, T018) — not independently *implementable* before US1 (there is
  no separate branch for it to add), though independently *testable* as its own increment once US1 is
  built, per spec.md's own Independent Test wording.
- **User Story 3 (Phase 5)**: Same relationship to US1 as US2 — depends on T009/T012/T015 already
  throwing `ChatProcessingException`, and (for T022) on US2's T019 extending the same contract test
  file first.
- **Polish (Phase 6)**: Depends on US1, US2, and US3 all being complete.

### Within Each User Story

- Tests are written first and confirmed to fail before the implementation tasks that follow them.
- US1: T014 (test + stub) → T015 (real `ChatService` body) → T016 (`ChatController`) → T017/T018
  (integration tests) — T015/T016 both depend on T014's stub; T017 depends on T015; T018 depends on
  T015 and T016.
- US2/US3: each extends the same three files US1 created (`ChatControllerContractTest.java`,
  `ChatRetrievalIT.java`, `ChatCompletionConnectivityIT.java`), so within a phase the three extension
  tasks are independent of each other (different files) but each individually depends on the prior
  phase's edit to that same file (T019 depends on T014; T020 depends on T017; T021 depends on T018;
  T022 depends on T019; T023 depends on T020; T024 depends on T021).

### Parallel Opportunities

- Foundational: T002–T009 (8 tasks: `ChatException`, `ChatErrorResponse`, `ChatRequest`,
  `ChatResponse`, `SourceCitation`, `RetrievedChunk`, `EmbeddingClient.embedQuery`,
  `ChatCompletionClient`) are all different files with no dependency on each other and can run fully
  in parallel; T010/T011 (the two `ChatException` subclasses) can then run in parallel with each other
  once T002 lands; T012 depends on T007/T011; T013 depends on T003/T010/T011.
- US1: T014 → T015/T016 (sequential on the stub, but T015 and T016 touch different files and can run
  in parallel with each other once T014 lands) → T017/T018 (can run in parallel with each other once
  T015/T016 land).
- US2: T020 and T021 are different files and can run in parallel with each other, both depending only
  on US1's checkpoint (T019 must go first since T022 in US3 depends on it, but T019 has no dependency
  on T020/T021).
- US3: T023 and T024 are different files and can run in parallel with each other, both depending only
  on US2's checkpoint.

---

## Parallel Example: Foundational Phase

```bash
# Launch T002-T009 together (different files, no dependency on each other):
Task: "Create ChatException in backend/src/main/java/com/epam/aihelpdesk/chat/ChatException.java"
Task: "Create ChatErrorResponse in backend/src/main/java/com/epam/aihelpdesk/chat/dto/ChatErrorResponse.java"
Task: "Create ChatRequest in backend/src/main/java/com/epam/aihelpdesk/chat/dto/ChatRequest.java"
Task: "Create ChatResponse in backend/src/main/java/com/epam/aihelpdesk/chat/dto/ChatResponse.java"
Task: "Create SourceCitation in backend/src/main/java/com/epam/aihelpdesk/chat/dto/SourceCitation.java"
Task: "Create RetrievedChunk in backend/src/main/java/com/epam/aihelpdesk/chat/RetrievedChunk.java"
Task: "Add embedQuery to backend/src/main/java/com/epam/aihelpdesk/ingestion/EmbeddingClient.java"
Task: "Create ChatCompletionClient in backend/src/main/java/com/epam/aihelpdesk/chat/ChatCompletionClient.java"
```

## Parallel Example: User Story 2

```bash
# Launch T020 and T021 together (different files, both depend only on US1's checkpoint):
Task: "Extend ChatRetrievalIT.java with empty-corpus/filter-mismatch/threshold-boundary tests in backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java"
Task: "Extend ChatCompletionConnectivityIT.java with a live not-covered call in backend/src/test/java/com/epam/aihelpdesk/chat/ChatCompletionConnectivityIT.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational (T002–T013) — blocks everything else.
3. Complete Phase 3: User Story 1 (T014–T018).
4. **STOP and VALIDATE**: `backend\mvnw.cmd test` and `-Pverify-db` green; `quickstart.md` Step 4
   passes against a running backend.
5. This is a demonstrable MVP: a question about the ingested corpus now gets a grounded, cited answer
   (SC-001's accuracy bar becomes measurable for the first time).

### Incremental Delivery

1. Setup + Foundational → foundation ready (the chat error vocabulary, DTOs, retrieval repository,
   and completion client all exist, documented and compiling).
2. Add User Story 1 → validate independently → grounded, cited answers work end-to-end (MVP!).
3. Add User Story 2 → validate independently → the honest "not covered" outcome is now proven never
   to fabricate an answer (SC-002).
4. Add User Story 3 → validate independently → a genuine system failure is now proven never to be
   mistaken for "not covered" (SC-004).
5. Polish → full quickstart run, including the SC-001 evaluation-set accuracy check and the SC-003
   timing bound.

### Parallel Team Strategy

Because this feature is a single endpoint whose branches all live in one `ChatService` method (unlike
feature 005's two independent routes), true cross-developer parallelism is limited before US1's
checkpoint: T014→T015/T016→T017/T018 is largely sequential, one developer should carry it through.
Foundational's eight independent T002–T009 tasks can be split across multiple people first. Once US1's
checkpoint is reached, a second developer can pick up US2's T020/T021 while a third picks up US3's
T023/T024, once each phase's first (sequential) contract-test extension (T019, then T022) has landed.

---

## Requirement Coverage

Every functional requirement and success criterion maps to at least one task:

| Requirement | Task(s) |
|---|---|
| FR-001 | T014, T015, T016 |
| FR-002 | T008, T015 |
| FR-003 | T012, T015 |
| FR-004 | T012, T015, T017 |
| FR-005 | T015, T017, T020 |
| FR-006 | T009, T015 |
| FR-007 | T015, T019, T020, T021 |
| FR-008 | T006, T014, T015 |
| FR-009 | T006, T014, T015 |
| FR-010 | T004, T012, T014, T017, T020 |
| FR-011 | T014, T016 |
| FR-012 | T014, T016 |
| FR-013 | T009, T012, T015, T022, T023, T024 |
| FR-014 | T015 (no state read or written across requests) |
| FR-015 | T009, T013, T022, T025 |
| FR-016 | T004, T013, T014 |
| FR-017 | T013, T015 |
| SC-001 | T018, T025 |
| SC-002 | T021, T025 |
| SC-003 | T018, T025 |
| SC-004 | T022, T023, T024, T025 |
| SC-005 | T015, T025 |

---

## Notes

- `[P]` tasks touch different files with no dependency on each other.
- `[Story]` labels (US1/US2/US3) trace every user-story task back to spec.md's priorities.
- Tests are written and confirmed failing before the implementation task(s) that make them pass
  (constitution Principle II) — for T014 specifically, "failing" means a compiling test that fails at
  the assertion (via the `UnsupportedOperationException` stub), not a build error.
- Commit after each task or logical group.
- Stop at each phase checkpoint to validate that story's Independent Test criterion before moving on.
- Avoid: duplicating `EmbeddingClient`'s Azure client-construction logic inside `chat` instead of
  reusing `embedQuery` (research Decision 3's precondition); folding the similarity threshold into
  `ChatRetrievalRepository`'s SQL instead of applying it in `ChatService` (research Decision 5 explains
  why the two-step "limit, then threshold" order matters); reusing `DocumentErrorResponse`/
  `IngestionProcessingException` instead of this feature's own `Chat*` hierarchy (research Decision 8);
  and parsing the model's generated answer text to decide what to cite instead of computing `sources`
  from retrieval results directly (research Decision 6 — this is what makes SC-005 true by
  construction).

# Tasks: Chat Pipeline Diagnostic Logging & Trace

**Input**: Design documents from `specs/009-chat-diagnostic-trace/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/chat-diagnostic-trace-contract.md](contracts/chat-diagnostic-trace-contract.md), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution Principle II (Test-Driven Development) is mandatory for this
project.

**Organization**: Tasks are grouped by user story (spec.md's US1 P1, US2 P2, US3 P3) so each story
is independently implementable and testable. All file paths are relative to the repository root.

> **One pipeline, three incremental exposures**: `ChatService.answer(ChatRequest)` already exists
> (feature 007). This feature doesn't replace it — it rewires its internals once (US1: build a
> `List<ChatTraceStep>` as the pipeline runs, emit one summary log line per step, correlate every line
> via MDC) and then exposes what was already built, incrementally: **US1 changes nothing any API
> caller can observe** — only backend log output — so its Independent Test is "read the log file,"
> never an HTTP response shape. **US2 adds exactly two DTO fields and a two-line wiring change**
> (`includeTrace` in, `trace` out) that attach the *same* `steps` list US1 already builds to the
> response, only when asked. **US3 adds no production code at all** — it is regression proof that
> US2's opt-in field genuinely defaults to invisible, mirroring feature 007's own US2/US3 "tests only"
> pattern for its already-structural guarantees.
>
> **Statically-typed TDD note, applied three times**: A few of this feature's tasks cannot be *written*
> or cannot even *compile* before a small, purely mechanical production change lands first (same
> nuance feature 007's T014 handled with a throwaway stub) — not because the behavior exists yet, but
> because a method signature or record shape they reference doesn't compile otherwise:
> - `ChatServiceTest` (T010) stubs `ChatCompletionClient.complete(...)` returning a
>   `ChatCompletionResult` — this requires T003 (the record's existence, Foundational) and T006
>   (`complete(...)`'s return type actually changed) to already be in place, even though T010 is still
>   the test that goes *red at the assertion* once it compiles (today's `ChatService` doesn't yet log
>   the six-line summary sequence T010 expects) — T012 is what turns it green.
> - **T009 exists purely to keep the existing test source tree compiling.** The moment T006 changes
>   `ChatCompletionClient.complete(...)`'s return type, the pre-existing `ChatRetrievalIT.java` (an
>   `@Tag("db")` integration test, untouched by every other task in this feature) stops compiling: it
>   stubs `complete(any(), any())` returning a bare `String` three times. Because Maven compiles the
>   whole test source root together, this is not a `-Pverify-db`-only problem — it fails plain
>   `mvnw test` outright, blocking T010/T011 from ever running, until T009 rewraps those three stubs as
>   `ChatCompletionResult`. T009 MUST land in the same change as T006, not be deferred to whenever the
>   `db`-tagged tier is next exercised.
> - `ChatServiceTest`'s US2 additions (T015) and `ChatControllerContractTest`'s US2 additions (T016)
>   both construct a 3-argument `ChatRequest`/`ChatResponse` — this requires T013/T014 (the two new
>   fields) to already exist to compile. Unlike T009's problem (no such option existed for a bare
>   `String` return type), T013/T014 each add their new 3-arg canonical constructor *alongside* a
>   compact 2-arg convenience constructor equivalent to `includeTrace`/`trace = null` — so every
>   pre-existing 2-arg `new ChatRequest(...)`/`new ChatResponse(...)` call site (`ChatController`,
>   `ChatService`, and `ChatControllerContractTest.java`'s four feature-007 tests) keeps compiling
>   unchanged, with no call-site fix-up needed at all.
>
> **Foundational tasks have no preceding test, by the same precedent feature 007's Foundational phase
> set** (its T002–T013 — DTOs, exception classes, clients — had none either): T002–T005 are validated
> indirectly by US1's own tests (T010, T011), not by a dedicated red-green cycle of their own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3, mapping to spec.md's user stories — omitted for Setup, Foundational,
  and Polish tasks
- Every task names its exact file path(s)

## Path Conventions

Web application structure (features 001–007, unchanged): `backend/src/main/java/com/epam/aihelpdesk/`
for production code, `backend/src/test/java/com/epam/aihelpdesk/` for tests. This feature stays
entirely inside the existing `chat` package (plus two small touches in `health` and `ingestion`) — no
new package, no frontend change (spec.md Clarifications).

---

## Phase 1: Setup

**Purpose**: Confirm the classpath already covers this feature before any code changes — there is no
new dependency to add (plan.md Technical Context).

- [X] T001 Confirm `backend/pom.xml` needs no changes for this feature — SLF4J's `MDC` and Logback's
      `ListAppender` (needed for T010's log-content test) both ship transitively via
      `spring-boot-starter-logging`, already a dependency since feature 001; Jackson's
      `Map<String, Object>` serialization needs no extra configuration. Run
      `backend\mvnw.cmd -q dependency:resolve` to confirm the current classpath still resolves
      cleanly.

**Checkpoint**: Classpath confirmed unchanged. Safe to start Foundational work.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The two new record types US1's `ChatService` rewrite needs, plus two small, independent
cross-cutting fixes (a per-request correlation id, and a pre-existing misattributed logger) that every
later log line depends on. No user story's tests can compile or pass meaningfully until these exist.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Create `ChatTraceStep` — a record `{String stage, long durationMs, Map<String, Object>
      detail}`. Javadoc documents the six fixed `stage` string values
      (`request_received`, `question_embedded`, `vector_search_completed`, `results_filtered`,
      `prompt_assembled`, `model_response_received`) as a closed vocabulary (same convention as
      `ChatErrorResponse.error`, not a Java `enum` — research Decision 2) and points to
      data-model.md's per-stage `detail` key table — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/dto/ChatTraceStep.java`.
- [X] T003 [P] Create `ChatCompletionResult` — an internal record `{String systemPrompt, String
      prompt, String completion}`, never serialized directly (research Decision 3) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatCompletionResult.java`.
- [X] T004 [P] Fix `AzureOpenAiProperties`'s misattributed logger — its `Logger log` field currently
      reads `LoggerFactory.getLogger(ChatCompletionClient.class)` (a pre-existing bug from the manual
      logging work this feature finishes); change it to
      `LoggerFactory.getLogger(AzureOpenAiProperties.class)` — in
      `backend/src/main/java/com/epam/aihelpdesk/health/AzureOpenAiProperties.java`.
- [X] T005 [P] Add a per-request correlation id: in `ChatController.chat(...)`, generate one
      `UUID.randomUUID()` at the very top of the method — before `validate(request)` runs, so even a
      request that is ultimately rejected still gets one — put it in SLF4J's `MDC` under the key
      `chatRequestId`, and remove it in a `finally` block so it can never leak onto a later request
      handled by the same worker thread (research Decision 1). Add `%X{chatRequestId}` to both
      `logging.pattern.console` and `logging.pattern.file` in `application.yml` (Spring Boot's default
      patterns, extended additively — no `logback-spring.xml`) so every log line from this thread
      during the request carries it — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatController.java` and
      `backend/src/main/resources/application.yml`.

**Checkpoint**: `ChatTraceStep`/`ChatCompletionResult` exist and compile; every request now carries a
correlation id through MDC. User story implementation can now begin.

---

## Phase 3: User Story 1 - Reconstruct a chat request's full processing history from server logs (Priority: P1) 🎯 MVP

**Goal**: `ChatService.answer(...)` logs exactly one summary line per pipeline stage that actually ran
— `request_received`, `question_embedded`, `vector_search_completed`, `results_filtered`, and, only
when at least one candidate survived, `prompt_assembled` + `model_response_received` — each line
correlated to its request via T005's MDC id, none of them containing full passage text, the full
prompt, or the full raw model response (research Decision 4). No `ChatRequest`/`ChatResponse` field
changes in this phase — nothing an API caller can observe changes yet.

**Independent Test**: Send any chat question to the endpoint, then inspect the backend log output for
that request and confirm a log entry exists for each stage that ran, in order, sharing one correlation
id, each carrying only its stage's summary detail (spec.md User Story 1 Independent Test).

**Concurrency scope note (Acceptance Scenario 3)**: T011 below automatedly proves the MDC id is set
and cleared per HTTP call on the shared worker thread (no leak between two sequential invocations);
proving two *genuinely concurrent* requests' log lines stay uninterleaved in the actual output file is
validated manually only, via `quickstart.md` Step 3's `ForEach-Object -Parallel` edge case — this is a
deliberate scope choice (a real multi-threaded assertion against a live rolling log file would add
Testcontainers-grade flakiness to a unit test for a property that MDC's thread-local design already
guarantees by construction), not an oversight.

### Implementation for User Story 1 (land first — mechanical, needed for T010 to compile)

- [X] T006 [P] [US1] Update `ChatCompletionClient.complete(...)`: change its return type from `String`
      to `ChatCompletionResult` (`systemPrompt` = the existing fixed constant, `prompt` = the exact
      `"Context:\n" + ... + "\n\nQuestion: " + question` text already built inside the method,
      `completion` = the previously-returned raw text). Remove the two ad hoc full-content log lines
      added during the manual logging work (`log.info("user message {}", userMessage)` and
      `log.info("completion {}", completion)`); keep the existing
      "chat completion request started/succeeded" summary lines unchanged — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatCompletionClient.java` (depends on T003).
      **Same-task compile-fix**: this return-type change also breaks `ChatService.answer(...)`'s one
      existing call site (`String completion = chatCompletionClient.complete(...)`); update it to the
      minimal `chatCompletionClient.complete(...).completion()` so the module keeps compiling — this
      one-line shape is entirely superseded by T012's full rewrite, which touches this method body
      anyway, but must not be left broken in between (same whole-module-compiles principle as T009).
- [X] T007 [P] [US1] Clean up `ChatRetrievalRepository.findTopSimilarChunks(...)`: remove the ad hoc
      per-row `results.forEach(res -> log.info("retrieved chunk: ...", ...))` dump added after the
      existing "retrieval query succeeded: rowCount=" line; keep the existing started/succeeded
      summary lines unchanged (that per-row detail is superseded by T012's `vector_search_completed`
      trace step, which carries it in-memory instead) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatRetrievalRepository.java`.
- [X] T008 [P] [US1] Clean up `EmbeddingClient.embedQuery(...)`: revert the ad hoc `" for {}"` + full
      question-text addition to the `"query embedding request started"` log line back to its original,
      textless form — the question text is now captured once, in full, by T012's `request_received`
      trace step and its log line, so logging it a second time here would be redundant — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/EmbeddingClient.java`.
- [X] T009 [US1] **Compile-fix, not a behavior change**: update `ChatRetrievalIT.java`'s three
      `when(chatCompletionClient.complete(any(), any())).thenReturn("...")` stubs (currently at lines
      129, 159, 196) to `.thenReturn(new ChatCompletionResult(ChatCompletionClient.SYSTEM_PROMPT, "...",
      "..."))` (or any non-null placeholder `systemPrompt`/`prompt` — this test does not assert on
      those fields) so the file compiles against T006's new return type. The existing
      `verify(chatCompletionClient, never()).complete(any(), any())` call (line 186) needs no change.
      No other line in this file changes — this test still verifies the real pgvector retrieval path,
      untouched by this feature — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java` (depends on T003, T006 —
      MUST land in the same change as T006, see the callout above; blocks plain `mvnw test`, not just
      `-Pverify-db`, until it does).

### Tests for User Story 1 (write next; confirm T006 fails at the assertion, not the compiler)

- [X] T010 [US1] Create `ChatServiceTest.java` — plain JUnit 5 + Mockito, no `@SpringBootTest`, no
      `MockMvc`, no database, no Azure call; `EmbeddingClient`, `ChatRetrievalRepository`, and
      `ChatCompletionClient` are Mockito mocks. Attach a Logback `ListAppender`
      (`ch.qos.logback.core.read.ListAppender`, already on the classpath via
      `spring-boot-starter-logging`, T001) to `((Logger) LoggerFactory.getLogger(ChatService.class))`
      before each test. Assert: (a) a normal answered flow logs exactly six lines, one per stage, in
      that order, and none of the six messages contains the mocked full passage text, the mocked
      prompt text, or the mocked raw completion text as a substring (proving logs stay summary-only,
      research Decision 4); (b) when the mocked retrieval returns only below-threshold candidates,
      exactly four lines are logged, ending at `results_filtered` — no `prompt_assembled`/
      `model_response_received` line, and `chatCompletionClient.complete(...)` is never invoked
      (`verify(..., never())`, matching the now-clarified FR-004/FR-006); (c) when
      `chatCompletionClient.complete(...)` returns a blank completion, all six lines are still logged,
      the sixth reflecting a not-covered outcome — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatServiceTest.java` (depends on T002, T003,
      T006, T007, T008 to compile against their new shapes).
- [X] T011 [US1] Extend `ChatControllerContractTest.java`: stub `ChatService.answer(...)` with an
      `Answer` that reads `MDC.get("chatRequestId")` at invocation time and asserts it is non-null;
      after the `MockMvc` call completes, assert `MDC.get("chatRequestId")` is `null` again — proving
      `ChatController` sets and clears the correlation id around every request. Add a second case
      sending a request that fails validation (blank question) and asserting the id is still cleared
      afterward even though `ChatService.answer(...)` is never called (FR-008) — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatControllerContractTest.java` (depends on
      T005).

### Implementation for User Story 1 (makes T010/T011 pass)

- [X] T012 [US1] Rewrite `ChatService.answer(ChatRequest request)`: build
      `List<ChatTraceStep> steps = new ArrayList<>()`; immediately append `request_received`
      (`detail`: `question`, `documentIds` as strings); call `embeddingClient.embedQuery(...)` and
      append `question_embedded` (`detail`: `vectorDimensions`); call
      `chatRetrievalRepository.findTopSimilarChunks(...)` and append `vector_search_completed`
      (`detail`: `candidateCount`, `candidates` — full per-row detail, data-model.md); apply the
      existing similarity-threshold filter and append `results_filtered` (`detail`: `survivorCount`,
      `discardedCount`, `threshold`, `survivors`); log one summary `log.info(...)` line immediately
      after each append so far, built from `detail` **minus** its full-content keys (`candidates`/
      `survivors`/any full text) — if nothing survived, return the fixed not-covered `ChatResponse`
      here, per the now-clarified FR-004/FR-006 (no further step appended, `ChatCompletionClient`
      never called); otherwise call `chatCompletionClient.complete(...)` (now returning
      `ChatCompletionResult`, T006) and append `prompt_assembled` (`detail`: `systemPrompt`, `prompt`,
      `passageCount`) and `model_response_received` (`detail`: `rawResponse`, `completionLength`,
      `outcome`: `"answered"`/`"not_covered"`) together, log their two summary lines, and return the
      `ChatResponse` exactly as feature 007 already does (this phase does not yet attach `steps`
      anywhere a caller can read it). Remove the ad hoc `retrieved.forEach(...)`/
      `survivors.forEach(...)` full-dump lines T007/this rewrite supersede — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatService.java` (depends on T002, T003, T006,
      T007, T008).

**Checkpoint**: User Story 1 is independently functional — `backend\mvnw.cmd test` green (requires T009
alongside T006 for the tree to even compile; T010, T011 pass), then `quickstart.md` Step 3 (and its
concurrent-requests edge case) against a running backend.

---

## Phase 4: User Story 2 - Retrieve a request's diagnostic trace through the API (Priority: P2)

**Goal**: `ChatRequest` accepts `includeTrace`; when `true`, `ChatResponse` carries the exact `steps`
list T012 already builds — full raw content per stage — as its `trace` field.

**Independent Test**: Send a chat request with `includeTrace: true` and confirm the response includes
an ordered trace array matching the stages that ran, each with full detail; send the same question
without the option and confirm no trace data is present (spec.md User Story 2 Independent Test).

### Implementation for User Story 2 (land first — mechanical, needed for T015/T016 to compile)

- [X] T013 [P] [US2] Add `includeTrace` field to `ChatRequest` —
      `record ChatRequest(String question, List<UUID> documentIds, Boolean includeTrace)`; Javadoc
      notes `null`/`false`/absent are all equivalent to "no trace" (data-model.md) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/dto/ChatRequest.java`. **Implementation note**:
      also add a compact 2-argument convenience constructor (`this(question, documentIds, null)`) so
      every pre-existing 2-arg `new ChatRequest(...)` call site (`ChatController`, this feature's own
      new `ChatServiceTest`) keeps compiling unchanged — Jackson still deserializes via the 3-arg
      canonical constructor, so this is purely additive and does not affect the wire contract.
- [X] T014 [P] [US2] Add `trace` field to `ChatResponse` —
      `record ChatResponse(String answer, List<SourceCitation> sources, List<ChatTraceStep> trace)`,
      annotated so a `null` trace is omitted from the JSON body entirely
      (`@JsonInclude(JsonInclude.Include.NON_NULL)`), not serialized as `"trace": null` (data-model.md,
      FR-010). **Implementation note (supersedes the plan below)**: rather than fixing the four
      pre-existing, trace-unrelated `new ChatResponse(answer, sources)` call sites already in
      `ChatControllerContractTest.java` (feature 007's own tests, currently at lines 50, 72, 113, 166)
      and in this feature's own `ChatService`, add a compact 2-argument convenience constructor
      (`this(answer, sources, null)`) alongside the 3-arg canonical one — every existing 2-arg call
      site keeps compiling unchanged, with the same net compile-safety guarantee the four-call-site
      fix would have given, but with zero churn to feature-007 test code. (Originally planned: update
      all four call sites to the 3-argument form with a trailing `null`, in the same task, since
      Maven compiles the whole test source tree together and this MUST NOT be deferred — the
      constructor-overload approach satisfies that same constraint more simply.) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/dto/ChatResponse.java`.

### Tests for User Story 2 (write next; confirm they fail at the assertion, not the compiler)

- [X] T015 [US2] Extend `ChatServiceTest.java`: with `includeTrace=true` and a normal answered flow,
      assert `response.trace()` is the full six-entry list, each entry's `detail` containing the exact
      full passage text / prompt text / raw response text the mocks returned (FR-011, FR-012); with
      `includeTrace=true` and a below-threshold flow, assert `response.trace()` is the four-entry
      truncated list (FR-013); with `includeTrace` absent, `null`, or `false`, assert
      `response.trace()` is `null` even though the same six (or four) log lines from T010 still fire —
      in `backend/src/test/java/com/epam/aihelpdesk/chat/ChatServiceTest.java` (depends on T010, same
      file; T013, T014).
- [X] T016 [US2] Extend `ChatControllerContractTest.java`: `includeTrace` on the incoming JSON body
      reaches `ChatService.answer(...)` unchanged (`ArgumentCaptor`, mirrors the existing
      `documentIds` pass-through case); a stubbed `ChatResponse` with a non-null `trace` serializes a
      `"trace"` array in the JSON response body with the expected `stage` values in order; a stubbed
      `ChatResponse` with a `null` trace produces no `"trace"` key at all
      (`jsonPath("$.trace").doesNotExist()`) — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatControllerContractTest.java` (depends on
      T011, same file, already touched it; T013, T014 for the DTO shapes it constructs).

### Implementation for User Story 2 (makes T015/T016 pass)

- [X] T017 [US2] Update `ChatController.chat(...)`'s reconstruction of the validated request to also
      pass through `request.includeTrace()` (currently only `question`/`documentIds` are passed) — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatController.java` (depends on T013).
- [X] T018 [US2] Update `ChatService.answer(...)`'s two `return new ChatResponse(...)` call sites (the
      not-covered short-circuit and the normal-answer return) to pass
      `Boolean.TRUE.equals(request.includeTrace()) ? steps : null` as the third constructor argument —
      no other change to T012's pipeline logic — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatService.java` (depends on T012, T013, T014).

**Checkpoint**: User Stories 1 AND 2 both work independently — `backend\mvnw.cmd test` green, plus
`quickstart.md` Step 4 (including its early-stop-truncation edge case) against a running backend.

---

## Phase 5: User Story 3 - Default chat behavior is unaffected when diagnostics are not requested (Priority: P3)

**Goal**: Explicit regression proof that omitting `includeTrace` (or sending it `false`) leaves the
response byte-identical to feature 007's original contract.

No new production code — T014's `@JsonInclude(NON_NULL)` and T018's ternary already implement this by
construction (mirrors feature 007's own US2/US3 "tests only" pattern for a guarantee the prior phase's
code already provides). This phase adds the dedicated automated verification spec.md's own User Story
3 formally requires:

### Tests for User Story 3

- [X] T019 [US3] Extend `ChatControllerContractTest.java`: a request omitting `includeTrace` entirely
      and a request with `includeTrace: false` both produce response bodies with no `"trace"` key
      (`jsonPath("$.trace").doesNotExist()` for both) and identical `answer`/`sources` values to each
      other for the same stubbed `ChatResponse` — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatControllerContractTest.java` (depends on
      T016, same file).
- [X] T020 [P] [US3] Extend `ChatServiceTest.java`: for the same mocked pipeline inputs,
      `ChatResponse.answer()` and `.sources()` are identical across `includeTrace=true`,
      `includeTrace=false`, and `includeTrace=null` — proving FR-016's "trace never changes any other
      field" holds — in `backend/src/test/java/com/epam/aihelpdesk/chat/ChatServiceTest.java` (depends
      on T015, same file).
- [X] T021 [P] [US3] Run `backend\mvnw.cmd test -Pverify-db` and `-Pverify-ai` and confirm
      `ChatRetrievalIT.java`/`ChatCompletionConnectivityIT.java` are still green. `ChatRetrievalIT`'s
      only expected difference from feature 007 is T009's earlier mechanical stub rewrap — no further
      code change is expected here; if either file turns out to assert on now-removed ad hoc log
      content beyond what T009 already fixed, update it minimally to match — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java` and
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatCompletionConnectivityIT.java` (depends on
      T009; otherwise no dependency beyond Foundational). **Result**: `-Pverify-db` green (28/28,
      including `ChatRetrievalIT`'s 5 tests, unmodified beyond T009's stub rewrap). `-Pverify-ai`
      fails in this sandboxed execution environment with `failed to create a child event loop`
      (Azure SDK's Netty HTTP client cannot spin up) — verified, by stashing every feature-009 change
      and re-running `AzureOpenAiConnectivityIT` alone against unmodified feature-007/001 code, that
      this failure is pre-existing and environmental (this sandbox blocks the native networking the
      Azure SDK's Netty transport needs), not a regression introduced by this feature. Not
      reproducible outside this sandbox; re-run `-Pverify-ai` in a normal developer environment with
      Azure credentials configured before considering this tier's coverage complete.

**Checkpoint**: All three user stories independently functional — `backend\mvnw.cmd test`,
`-Pverify-db`, and `-Pverify-ai` all green, plus `quickstart.md` Step 5.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Coverage and validation that spans all three stories rather than belonging to one.

- [X] T022 Run the full `quickstart.md` validation end-to-end — Step 1 (`ChatServiceTest` alone),
      Step 2 (full default suite), Step 3 (US1: log reconstruction + concurrent-requests edge case —
      the only proof of genuine cross-request concurrency, by design; see Phase 3's concurrency scope
      note), Step 4 (US2: trace via API + early-stop-truncation edge case), Step 5 (US3: default
      behavior unaffected), Step 6 (credential-safety grep across logs and a captured trace response,
      SC-004), Step 7 (`-Pverify-db`/`-Pverify-ai` still green) — confirming SC-001 through SC-005 all
      hold against a running backend. **Result**: Steps 1, 2, 7 (`-Pverify-db` half) executed directly
      and green — `ChatServiceTest` alone (7/7), full default suite (78/78), `-Pverify-db` (28/28).
      Step 6's credential-safety grep executed against the accumulated `logs/ai-helpdesk.log` from
      every test run in this session: zero matches for the configured Azure key value, and sampled log
      lines confirm the new `[%X{chatRequestId}]` MDC slot is present and populated during
      `ChatService`'s pipeline-stage log lines. Steps 3-5 and `-Pverify-ai` (Step 7's other half)
      require a live, running backend making real Azure OpenAI calls; this sandboxed execution
      environment cannot make outbound Azure calls at all (`failed to create a child event loop` —
      confirmed pre-existing and environmental, not a regression, by re-running `-Pverify-ai` against
      unmodified feature-007/001 code with every feature-009 change stashed). Steps 3-5 and the
      `-Pverify-ai` half of Step 7 are validated instead by the equivalent assertions already proven
      by `ChatServiceTest`/`ChatControllerContractTest` against mocked collaborators (six-stage
      ordering, truncation, trace content, byte-identical default response) — re-run these
      quickstart.md steps against a live backend in a normal developer environment with Azure
      credentials configured before considering this feature's manual validation fully complete.

**Checkpoint**: All three user stories independently functional and the full quickstart guide passes
end-to-end.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T001). Blocks every user story.
- **User Story 1 (Phase 3)**: Depends on Foundational only. No dependency on US2/US3.
- **User Story 2 (Phase 4)**: Depends on Foundational **and** on US1's `ChatService` rewrite (T012)
  already building `steps` — nothing to attach otherwise — not independently *implementable* before
  US1, though independently *testable* as its own increment once US1 exists (spec.md's own Independent
  Test wording; mirrors feature 007's US2/US3 relationship to its US1).
- **User Story 3 (Phase 5)**: Depends on US2's DTO fields (T013, T014) and wiring (T017, T018)
  existing, to assert their absence/presence and non-interference.
- **Polish (Phase 6)**: Depends on US1, US2, and US3 all being complete.

### Within Each User Story

- US1: T006/T007/T008 (mechanical, parallel, different files) → T009 (mechanical compile-fix, depends
  on T006 landing; different file from T006/T007/T008, so no file conflict) → T010/T011 (tests, now
  compile, fail at the assertion) → T012 (`ChatService` rewrite, makes both pass).
- US2: T013/T014 (mechanical DTO fields, each with its own compact 2-arg convenience constructor so
  no other file needs touching, parallel, different files) → T015/T016 (tests, now compile, fail at
  the assertion) → T017/T018 (wiring, makes both pass).
- US3: T019 depends on T016 (same file, extends it); T020 depends on T015 (same file, extends it);
  T021 depends on T009 (same file, `ChatRetrievalIT.java`) but is otherwise independent of US2/US3 and
  needs only Foundational + whichever of US1/US2's changes already landed by the time it runs.

### Parallel Opportunities

- Foundational: T002, T003, T004, T005 are four different files with no dependency on each other —
  fully parallel.
- US1: T006, T007, T008 are three different files with no dependency on each other — fully parallel;
  T009 touches a fourth, distinct file (`ChatRetrievalIT.java`) and can be worked in parallel with
  T007/T008 (no file overlap), but must not be considered *done* before T006 lands, since it depends on
  T006's return-type change to compile; T010 and T011 are different files and can run in parallel with
  each other once T006–T009 land.
- US2: T013 and T014 are different files (`ChatRequest.java` vs. `ChatResponse.java`) with no
  overlap — parallel; T017 and T018 both depend on T013/T014 but touch different files, so they too
  can run in parallel with each other.
- US3: T020 and T021 are different files and independent of each other; T019 depends on T016 (same
  file as an earlier task) so it is sequential relative to that file's history, not to T020/T021.

---

## Parallel Example: Foundational Phase

```bash
# Launch T002-T005 together (different files, no dependency on each other):
Task: "Create ChatTraceStep in backend/src/main/java/com/epam/aihelpdesk/chat/dto/ChatTraceStep.java"
Task: "Create ChatCompletionResult in backend/src/main/java/com/epam/aihelpdesk/chat/ChatCompletionResult.java"
Task: "Fix misattributed logger in backend/src/main/java/com/epam/aihelpdesk/health/AzureOpenAiProperties.java"
Task: "Add MDC correlation id in backend/src/main/java/com/epam/aihelpdesk/chat/ChatController.java + application.yml"
```

## Parallel Example: User Story 1 (mechanical changes)

```bash
# Launch T006-T008 together (different files, no dependency on each other);
# T009 follows T006 (same return-type change it depends on) but is its own, fourth file:
Task: "Change ChatCompletionClient.complete(...) to return ChatCompletionResult in backend/src/main/java/com/epam/aihelpdesk/chat/ChatCompletionClient.java"
Task: "Remove ad hoc per-row log dump in backend/src/main/java/com/epam/aihelpdesk/chat/ChatRetrievalRepository.java"
Task: "Revert ad hoc full-question-text log line in backend/src/main/java/com/epam/aihelpdesk/ingestion/EmbeddingClient.java"
Task: "Rewrap ChatRetrievalIT's 3 complete(...) stubs as ChatCompletionResult in backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational (T002–T005) — blocks everything else.
3. Complete Phase 3: User Story 1 (T006–T012, including T009's compile-fix).
4. **STOP and VALIDATE**: `backend\mvnw.cmd test` green; `quickstart.md` Step 3 passes against a
   running backend.
5. This is a demonstrable MVP: every chat request's full processing history is now reliably
   reconstructable from the backend log alone (SC-001) — the original ask behind this feature, before
   any API surface changes.

### Incremental Delivery

1. Setup + Foundational → correlation id and new record types exist.
2. Add User Story 1 → validate independently → logs are complete and correlated (SC-001).
3. Add User Story 2 → validate independently → the trace is retrievable via the API on request
   (SC-002, SC-005).
4. Add User Story 3 → validate independently → default behavior is proven unaffected (SC-003).
5. Polish → full quickstart run, including the credential-safety check (SC-004).

### Parallel Team Strategy

Because US2 cannot be implemented before US1's `ChatService` rewrite exists (there is nothing to
attach a trace to otherwise), true cross-developer parallelism is limited before US1's checkpoint —
one developer should carry T006–T012 through. Foundational's four independent T002–T005 tasks can be
split across multiple people first. Once US1's checkpoint is reached, a second developer can pick up
US2's T013–T018 while a third prepares US3's T020–T021 (T021 has no dependency on US2 at all — only on
T009 — and can start as soon as T009 lands).

---

## Requirement Coverage

Every functional requirement and success criterion maps to at least one task:

| Requirement | Task(s) |
|---|---|
| FR-001 (request_received log) | T012, T010 |
| FR-002 (question_embedded log) | T012, T010 |
| FR-003 (vector_search_completed log) | T012, T010 |
| FR-004 (results_filtered log; final entry on empty) | T012, T010 |
| FR-005 (prompt_assembled log) | T006, T012, T010 |
| FR-006 (model_response_received log; only when model invoked) | T006, T012, T010 |
| FR-007 (ordering) | T012, T010 |
| FR-008 (per-request correlation) | T005, T011 (real cross-request concurrency: manual only, quickstart.md Step 3 edge case — see Phase 3 concurrency scope note) |
| FR-009 (no credentials) | T006, T007, T008, T012 (unchanged `e.toString()` discipline) |
| FR-010 (optional flag, unaffected default) | T013, T014, T018, T019 |
| FR-011 (ordered trace array when requested) | T014, T015, T018 |
| FR-012 (full detail per stage) | T012, T015 |
| FR-013 (truncation on early stop) | T012, T015 |
| FR-014 (no trace required on error/validation-rejection response) | design (exceptions thrown before any `ChatResponse` is constructed); T019 |
| FR-015 (no additional authorization) | N/A — no restriction is added anywhere in this feature |
| FR-016 (trace never changes other fields) | T018, T020 |
| FR-017 (no full raw content in persistent log, ever) | T006, T007, T008, T012 (research Decision 4) |
| SC-001 | T010, T022 |
| SC-002 | T015, T022 |
| SC-003 | T019, T020, T022 |
| SC-004 | T022 (quickstart Step 6); T006–T012 (summary-only logging discipline) |
| SC-005 | T015, T022 |
| SC-006 | T012, T015, T022 (trace built from already-computed pipeline data, no new call added) |

---

## Notes

- `[P]` tasks touch different files with no dependency on each other.
- `[Story]` labels (US1/US2/US3) trace every user-story task back to spec.md's priorities.
- Tests are written and confirmed to fail at the assertion (never the compiler, per the callout above)
  before the implementation task(s) that make them pass (constitution Principle II).
- T009 exists purely to keep a pre-existing, feature-007 test file (`ChatRetrievalIT.java`) compiling
  against T006's return-type change — it is not a behavior change and must land in the same
  commit/change as T006, not be deferred to a later phase, since Maven compiles the entire test source
  tree together regardless of which Maven profile is active. T013/T014 avoid the equivalent problem
  for `ChatRequest`/`ChatResponse` by each adding a compact 2-arg convenience constructor alongside
  the new 3-arg canonical one, so no test file needed an equivalent fix-up task at all.
- Commit after each task or logical group.
- Stop at each phase checkpoint to validate that story's Independent Test criterion before moving on.
- Avoid: writing full raw passage/prompt/response text to the persistent log file under any
  condition, including when `includeTrace=true` (research Decision 4 — this is the one behavior this
  feature must never regress toward); reconstructing the prompt text a second time inside
  `ChatService` instead of reading it from `ChatCompletionResult` (research Decision 3 — a second copy
  risks drifting from the real prompt); fabricating a `prompt_assembled`/`model_response_received`
  step when nothing survived filtering (research Decision 5, FR-013); forgetting to clear the MDC
  correlation id in a `finally` block (T005 — an uncleared id would leak onto the next request handled
  by the same worker thread); and landing T006 without its paired compile-fix (T009), which would
  leave the test source tree uncompilable until the next task happens to fix it.

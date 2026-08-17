# Phase 0 Research: Chat Pipeline Diagnostic Logging & Trace

**Date**: 2026-08-17 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

Six decisions. No `[NEEDS CLARIFICATION]` markers remain — both ambiguities the spec had (UI-display
scope, trace-detail depth) were already resolved during `/speckit-specify` itself, and
`/speckit-clarify` found no further ambiguity before this document was written. Most of *what* to log
was already fixed by the ratified constitution's Error Handling & Logging section and by spec.md's
FR-001–FR-006; the decisions below are about *how* it fits into feature 007's existing code, and how
to finish — rather than duplicate or leave inconsistent — the manual logging already started.

## Decision 1: A per-request correlation id via SLF4J MDC, set once in `ChatController`

- **Decision**: `ChatController.chat(...)` generates one `UUID` per request, puts it in SLF4J's `MDC`
  under a fixed key (`chatRequestId`) at the very top of the method — before validation, so the id
  exists for the request's full lifetime, including a request ultimately rejected as
  `400 blank_question`/`400 question_too_long` — and removes it in a `finally` block (or
  `try`-with-resources via `MDC.putCloseable`) so it never leaks onto a later, unrelated request
  handled by the same worker thread. `application.yml` gains one line adding `%X{chatRequestId}` to
  the existing `logging.pattern.console`/`logging.pattern.file` (Spring Boot's default patterns,
  overridden additively — no custom `logback-spring.xml`). Note: because Java's `finally` runs during
  exception unwinding, before Spring's `@RestControllerAdvice` dispatch resolves the exception, this
  id correlates every `ChatService` pipeline-stage log line (all of which run inside the `try` block)
  but not `ChatErrorHandler`'s own, separate, pre-existing (feature 007) rejection/failure log line —
  consistent with FR-008 scoping the correlation guarantee to pipeline "steps," not that unrelated
  line (spec.md Edge Cases).
- **Rationale**: FR-008 requires every logged step for a request to be identifiable as belonging to
  that request, without intermingling with a concurrent request's lines. `ChatService`,
  `ChatRetrievalRepository`, `ChatCompletionClient`, and `EmbeddingClient` are four separate classes
  that already call `log.info(...)`/`log.warn(...)` independently; threading an explicit request-id
  parameter through every one of their public method signatures (and every call site) would be a much
  larger, more invasive change for the same outcome. MDC is the standard SLF4J mechanism for exactly
  this — a value set once on a thread is automatically included in every subsequent log line from that
  thread until cleared — and this codebase's entire chat pipeline runs synchronously on the one HTTP
  request thread (no `@Async`, no thread pool hand-off anywhere in `chat`), so there is no risk of the
  id being read from the wrong thread.
- **Alternatives considered**: an explicit `requestId` parameter added to every logging call site
  (rejected — invasive, and easy to forget on a future call site since nothing enforces it); a
  request-scoped Spring bean carrying the id (rejected — more machinery than a four-line MDC
  set/clear for a value that only needs to reach the logging framework, not application code);
  relying on Spring's own `traceId`/`spanId` (Micrometer Tracing) (rejected — that dependency is not
  on the classpath and adding it is exactly the kind of new-dependency decision the constitution
  reserves for a deliberate stack change, not a logging-detail fix).

## Decision 2: `ChatTraceStep` is one small record with a per-stage `Map<String, Object>` detail, not six separate typed records

- **Decision**: A single new record, `ChatTraceStep(String stage, long durationMs, Map<String,
  Object> detail)`. `stage` is one of six fixed, closed string values (`request_received`,
  `question_embedded`, `vector_search_completed`, `results_filtered`, `prompt_assembled`,
  `model_response_received`) — plain `String` constants defined alongside `ChatService`'s existing
  `TOP_K`/`SIMILARITY_THRESHOLD` constants, not a Java `enum`. `detail`'s key set is fixed per stage
  and documented in [data-model.md](data-model.md), but the field's declared type stays
  `Map<String, Object>` rather than six distinct record types.
- **Rationale**: Every other machine-readable code vocabulary already in this codebase
  (`ChatErrorResponse.error`: `blank_question`, `provider_unconfigured`, etc.) is a plain string
  constant, not an enum — matching that existing pattern keeps one convention across the package
  instead of introducing a second. A `Map<String, Object>` for `detail` is the pragmatic choice given
  each stage's diagnostic payload is genuinely different in shape (an embedding step has no passage
  list; a retrieval step does; a prompt-assembly step has prompt text no other step does) — six
  separate typed records (one per stage) would need a sealed interface or a shared marker type for
  `ChatTraceStep.detail`'s field to even hold them polymorphically, machinery this feature's small,
  additive scope does not otherwise need. Jackson serializes a `Map<String, Object>` as a plain nested
  JSON object with no extra configuration, so the wire format is unaffected by this internal choice.
- **Alternatives considered**: a `sealed interface ChatTraceDetail` with six `record` implementations
  (rejected — the added type-safety benefits an internal-only field that is built and read in exactly
  one place, `ChatService`, and consumed by the API caller as plain JSON either way); a flat record with
  every possible field nullable across all six stages (rejected — six mostly-null fields per step is
  harder to read than one map whose keys are simply documented per stage).

## Decision 3: `ChatCompletionClient.complete(...)` returns a new `ChatCompletionResult` record instead of a bare `String`

- **Decision**: `ChatCompletionClient.complete(String question, List<RetrievedChunk> passages)` now
  returns `ChatCompletionResult(String systemPrompt, String prompt, String completion)` instead of
  `String`. `systemPrompt` is the existing fixed constant; `prompt` is the exact `"Context:\n...\n\n
  Question: ..."` text already built inside `complete(...)` today; `completion` is the raw text
  previously returned directly.
- **Rationale**: FR-012 requires the trace's `prompt_assembled`/`model_response_received` steps to
  carry the exact prompt text sent to the model and the exact raw response received. That text is
  constructed and received entirely inside `ChatCompletionClient` today — `ChatService` (the one place
  building the trace, Decision 2) never sees it. Returning a small record from the one method that
  already computes these values is a strictly additive, minimal-surface change — `ChatService` gains
  access to what it needs without `ChatCompletionClient` importing `ChatTraceStep` or knowing tracing
  exists at all, keeping the trace-assembly concern entirely in `ChatService` (consistent with
  `ChatService` already being this pipeline's one orchestrator, per feature 007's own class-level
  Javadoc).
- **Alternatives considered**: passing a mutable trace-collector object into `complete(...)` for it to
  populate (rejected — makes a previously pure, easily-unit-tested client method depend on and mutate
  a caller-supplied side-effect object, a less idiomatic shape for this codebase than the
  record-return style every other collaborator already uses, e.g. `RetrievedChunk`,
  `EmbeddedChunk`); leaving `complete(...)` returning `String` and reconstructing the prompt text a
  second time inside `ChatService` (rejected — a second, independent copy of the exact prompt-format
  string risks silently drifting from the one actually sent, defeating the point of a trace meant to
  show the real prompt).

## Decision 4: Persistent logs stay summary-only, always; full raw content appears only in the opt-in API response

- **Decision**: The always-on log line each pipeline stage produces (FR-001–FR-006) never contains the
  full retrieved-passage text, the full assembled prompt, or the full raw model response — only
  counts, filenames, pages, scores, lengths, and (for `request_received` only, per FR-001's explicit
  wording) the question text itself. That full raw content exists only inside the in-memory
  `ChatTraceStep.detail` map, and is only ever serialized into an HTTP response — and only when the
  caller's request has `includeTrace=true` — never written to the log file by this feature, regardless
  of whether tracing was requested.
- **Rationale**: The constitution's Error Handling & Logging section already mandates "request/response
  **summaries**" for structured logging, not full payload dumps — this reconciles the two clarified
  decisions from `/speckit-specify` (full raw content is fine in the *opt-in API response*; nothing in
  that clarification said "and also always write it to the persistent, rolling log file"). Keeping logs
  at summary level regardless of the trace flag avoids a real operational risk this feature would
  otherwise introduce: `logging.file.name` (already configured, `logs/ai-helpdesk.log`, rolling with a
  500MB total cap) means anything written to the log persists to disk for every request going forward,
  not just the one request that opted in — unconditionally logging full document passage text at INFO
  would make every ingested document's content recoverable from the log directory, which the API-level
  opt-in flag was never meant to imply.
- **Consequence for the manual logging already in progress**: the two full-content lines added ad hoc
  to `ChatCompletionClient` (`log.info("user message {}", userMessage)`,
  `log.info("completion {}", completion)`), the ad hoc full-question-text addition to
  `EmbeddingClient.embedQuery` (`"query embedding request started for {}"`), and the three ad hoc
  `.forEach` per-row dumps (`ChatService` ×2, `ChatRetrievalRepository` ×1) are all removed as part of
  finishing this work — each is superseded by exactly one stage-summary log line in `ChatService`
  (Decision 5) plus, when requested, the same full detail surfacing in the trace API response instead.
- **Alternatives considered**: also writing full content to the log file when `includeTrace=true`
  (rejected — reasoning above: a per-request API flag is not a sound trigger for a persistent,
  disk-durable change in what gets written for that request, especially since the flag is available to
  any caller per FR-015 with no additional authorization).

## Decision 5: `ChatService` is the single place that both builds the trace and emits the six stage-summary log lines

- **Decision**: `ChatService.answer(...)` builds `List<ChatTraceStep> steps` inline as it executes its
  existing pipeline, appending one `ChatTraceStep` per stage that actually runs (never a step for one
  that didn't, satisfying FR-013's truncation requirement structurally — the code simply returns before
  appending a step it never reached). After each append, a small private helper logs exactly one
  `log.info(...)` summary line for that stage, sourced from the same `detail` map minus its full-content
  keys. `ChatRetrievalRepository` keeps its existing "retrieval query started/succeeded: rowCount=N"
  lines (a lower-level "did the DB call succeed" concern, not a pipeline-stage summary) but drops its ad
  hoc per-row `.forEach` dump; `ChatCompletionClient` keeps its existing
  "chat completion request started/succeeded" lines for the same reason but drops the two ad hoc
  full-content lines (Decision 4).
- **Rationale**: `ChatService` is already this pipeline's sole orchestrator (feature 007's class Javadoc)
  and is the only class with visibility into every stage transition in sequence — it is the natural,
  single place to guarantee FR-007's ordering requirement (steps logged/traced in the order they occur)
  without coordinating timing or sequencing logic across four separate classes. Centralizing the
  stage-summary logging here also directly resolves this feature's stated goal of finishing (not adding
  a second, parallel layer alongside) the logging the user already started by hand in multiple files.
- **Alternatives considered**: leaving each collaborator responsible for logging its own "stage
  complete" summary and having `ChatService` only build the trace array separately (rejected — this is
  close to today's actual ad hoc state, and it is exactly what produces drift risk: two independently
  maintained descriptions of the same event, one in a log line and one in a trace step, that could say
  different things about the same request).

## Decision 6: A new, isolated `ChatServiceTest` unit test; existing three-tier suite otherwise unaffected

- **Decision**: A new test class, `ChatServiceTest`, constructs `ChatService` directly with
  `EmbeddingClient`, `ChatRetrievalRepository`, and `ChatCompletionClient` as plain Mockito mocks — no
  `@SpringBootTest`, no `MockMvc`, no database, no Azure call. It is the first test in this codebase to
  exercise `ChatService`'s own logic directly rather than only indirectly through
  `ChatControllerContractTest` (which stubs `ChatService` itself) or the `@Tag("db")`/`@Tag("azure")`
  integration tiers. `ChatControllerContractTest` gains a small number of additional cases (pass-through
  of `includeTrace` into the captured `ChatRequest`; presence/absence of the `trace` key in the JSON
  response body). `ChatRetrievalIT` and `ChatCompletionConnectivityIT` need no *logic* changes — they
  verify behavior this feature does not touch — but `ChatRetrievalIT` does need a small, mechanical
  compile-fix: it stubs `ChatCompletionClient.complete(...)` three times returning a bare `String`
  (e.g. `.thenReturn("A grounded answer.")`), which no longer compiles once Decision 3 lands
  (`complete(...)` now returns `ChatCompletionResult`). This fix (rewrap each stub as
  `new ChatCompletionResult(...)`) must land in the same change as Decision 3's return-type change,
  not be deferred to whenever this test tier is next run, since Maven compiles the whole test source
  tree together — an uncompilable `@Tag("db")` test file blocks `mvnw test` outright, not just
  `-Pverify-db`. `ChatCompletionConnectivityIT` needs no change (confirmed: it does not stub or assert
  on `complete(...)`'s return shape).
- **Rationale**: This feature's actual new decision logic — which stages get a `ChatTraceStep`, in what
  order, with what `detail`, and how truncation on early-stop behaves — lives entirely inside
  `ChatService.answer(...)`, a method the existing test suite has never exercised directly (constitution
  Principle II: unit tests verify individual services in isolation). Testing it only through
  `ChatControllerContractTest`'s stubbed `ChatService` would mean the trace-assembly logic itself is
  never actually run by any test — a gap this feature should not introduce given `TDD` is mandatory.
- **Alternatives considered**: testing trace assembly only via the `@Tag("db")` integration tier against
  a real database (rejected — needlessly couples fast-running, pipeline-shape assertions to
  Testcontainers/Docker availability; the existing `db`-tier test already proves the real pgvector query
  behavior, which this feature does not change and does not need to re-prove).

## Open questions

None.

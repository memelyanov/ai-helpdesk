# Feature Specification: Chat Pipeline Diagnostic Logging & Trace

**Feature Branch**: `main` (no feature branch created — no `before_specify` hook is registered,
consistent with [008-frontend-chat-ui](../008-frontend-chat-ui/spec.md) and every feature since
[002-frontend-health-wire](../002-frontend-health-wire/spec.md))

**Created**: 2026-08-17

**Status**: Draft

## Clarifications

### Session 2026-08-17

- Q: Should the diagnostic trace be visibly rendered in the chat UI itself, or is it enough that the
  trace data is returned by the backend for now? → A: API only — the chat response payload carries
  the trace steps when requested; this feature adds no new UI screen or panel to view them.
- Q: How much detail should each trace step expose in the API response returned to the frontend? →
  A: Full raw content — each step includes the full text of retrieved passages and the exact prompt
  sent to, and raw response received from, the language model, in addition to summary metadata
  (counts, filenames, pages, scores, timing).

**Input**: User description: "необходимо добавить детальное логгирование всех шагов на стороне
бэка. Если пришел запрос от UI в логе должна появится запись, если в запросе есть информация,
логировать эту информацию. Все шаги процесса чата логировать. Пришел запрос от фронта ? Запись в
лог. Выполнили поиск по векторной базе ? Запись в лог. Отфильтровали вектора ? Запись в лог.
Сформировали запрос для ЛЛМ - запись все в лог. Получили ответ от ллм - запись в лог. Я уже начал
руками добавлять дополнительное логирование, надо завершить эту работу. Второе важное требование,
сделать так чтобы эти логи можно было получить на фронтэнде, например расширить ChatRequest
дополнительной опцией для получения логов, а в ответ добавить массив в котором по шагам
добавлялись элементы лога"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reconstruct a chat request's full processing history from server logs (Priority: P1)

A developer or support engineer investigating a chat answer (or the lack of one) needs to open the
backend log output and see, for that one request, a complete and ordered record of every step the
system went through: the request arriving, the question being embedded, the vector search running,
the results being filtered, the prompt being assembled for the language model, and the model's
response coming back. Today several of these steps are logged only partially or inconsistently
(some were added ad hoc while diagnosing a live issue); this story finishes that work so every step,
for every request, is reliably recorded.

**Why this priority**: This is the foundational capability the rest of the feature depends on, and
it delivers value on its own — today, diagnosing why an answer was wrong or why nothing was found
requires guesswork or attaching a debugger. Reliable step-by-step logs are the highest-leverage,
lowest-risk improvement.

**Independent Test**: Send any chat question to the endpoint, then inspect the backend log output
for that request and confirm a log entry exists for each of: request received, question embedded,
vector search completed, results filtered, prompt assembled, and model response received (or the
pipeline's early stop, when nothing survives filtering) — each entry carrying exactly the detail
FR-001 through FR-006 specify for that step (e.g. question text, candidate/survivor counts, filenames
and scores, answer length); those six requirements, not this illustrative list, are the exhaustive
definition of what each entry must contain.

**Acceptance Scenarios**:

1. **Given** a chat question that is answered normally, **When** the request completes, **Then** the
   backend log contains, in order, one entry for each pipeline step that ran, including the question
   received, the number of candidates retrieved from the vector search, the number that survived
   filtering, the prompt sent to the language model, and the response received from it.
2. **Given** a chat question for which no retrieved passage meets the relevance threshold, **When**
   the request completes, **Then** the backend log shows the steps up through filtering and clearly
   records that the pipeline stopped there with a "not covered" outcome — with no entries implying a
   language-model call happened.
3. **Given** two chat requests arriving close together, **When** their logs are inspected, **Then**
   each request's steps can be told apart and reconstructed independently, without one request's
   entries being mistaken for the other's.

---

### User Story 2 - Retrieve a request's diagnostic trace through the API (Priority: P2)

A frontend developer, or a support user working through the UI's tooling, wants to see how a
specific answer was produced without needing access to the backend's server logs. They opt in to a
diagnostic trace when asking a question, and the answer comes back together with an ordered,
step-by-step account of how it was produced.

**Why this priority**: This makes the diagnostic information from Story 1 usable by anyone who can
call the API, not only someone with server log access — valuable, but it depends on Story 1's
logging already existing and being complete, so it is built second.

**Independent Test**: Send a chat request with the diagnostic-trace option enabled, and confirm the
response includes an ordered list of trace steps matching the pipeline stages that actually ran for
that request, each with its own detail. Send the same question again without the option and confirm
no trace data is present in the response.

**Acceptance Scenarios**:

1. **Given** a chat request with the diagnostic-trace option enabled, **When** the answer is
   generated normally, **Then** the response includes an ordered array of trace steps covering
   request-received, embedding, vector search, filtering, prompt assembly, and model response — each
   step carrying its full detail (including the retrieved passage text and the exact prompt and raw
   model response text).
2. **Given** a chat request with the diagnostic-trace option enabled that stops early because no
   passage survives filtering, **When** the response is returned, **Then** the trace array includes
   only the steps that actually ran (through filtering) and does not include a prompt-assembly or
   model-response step.
3. **Given** a chat request with the diagnostic-trace option enabled that fails with a processing
   error, **When** the error response is returned, **Then** it keeps its existing error shape and is
   not required to carry a trace array.
4. **Given** a chat request with the diagnostic-trace option enabled that is rejected before
   processing begins (e.g., a blank question), **When** the error response is returned, **Then** it
   keeps its existing validation-error shape and is not required to carry a trace array.

---

### User Story 3 - Default chat behavior is unaffected when diagnostics are not requested (Priority: P3)

An ordinary caller of the chat endpoint who does not opt in to diagnostics continues to get exactly
the same answer, citations, and response shape as before this feature existed.

**Why this priority**: Protects existing behavior and every existing automated test; lowest priority
only because it is a guarantee to preserve rather than new capability to build, but it must hold for
the feature to be safe to ship.

**Independent Test**: Send a normal chat request without the diagnostic-trace option set (or with an
older client that doesn't know about it) and confirm the response contains no trace data and is
otherwise identical to the pre-existing contract.

**Acceptance Scenarios**:

1. **Given** a chat request that omits the diagnostic-trace option entirely, **When** the response is
   returned, **Then** it matches the existing (pre-feature) response shape exactly, with no trace
   field populated.
2. **Given** a chat request with the diagnostic-trace option explicitly set to false, **When** the
   response is returned, **Then** behavior is identical to omitting the option.

---

### Edge Cases

- What happens when the pipeline fails partway through (e.g. the embedding call or the language-model
  call errors out)? The existing error response is returned unchanged, regardless of whether the
  diagnostic-trace option was requested; the steps that did complete before the failure are still
  recorded in the backend log as usual (this holds whether or not `includeTrace` was set — logging
  behavior never depends on the flag), but the trace array is not required to appear on an error
  response. Specifically, when the embedding call itself fails, only `request_received` was logged
  before the failure — no vector search, filtering, prompt-assembly, or model-response step ever ran,
  so none is logged or would be traced.
- What happens when no passage survives the similarity threshold ("not covered" outcome)? Both the
  log and, when requested, the trace array reflect the steps that actually ran (through filtering)
  and stop there — no prompt-assembly or model-response step is fabricated.
- What happens when the language model returns an empty or blank completion? This is logged and
  traced as its own distinct outcome, consistent with the existing "not covered" handling for that
  case — distinguishable both by the `model_response_received` entry's outcome value and, more simply,
  by the fact that all six stages appear at all (an empty-completion outcome always reaches
  `model_response_received`; a threshold short-circuit never does).
- What happens when the request restricts retrieval to specific documents? The logged and traced
  detail reflects that restriction (e.g. the requested document filter, and that it was applied). A
  `documentIds` filter that names a document id which does not exist in the corpus is not distinguished
  from one that names a real document with no matching passages — both simply produce zero candidates,
  the same unchanged feature-007 retrieval behavior this feature only observes and reports.
- What happens with a request that both restricts by document and enables diagnostics? Both options
  apply independently and simultaneously; the trace reflects the filtered search that actually ran.
- Is the logged/traced question text ever truncated? No — the full question text is always logged and
  traced untruncated; the endpoint already rejects any over-length question before `ChatService` ever
  runs (feature 007's existing validation, unchanged by this feature — see the validation-rejection
  bullet above), so the text this feature logs is already bounded by that same limit and needs no
  separate truncation rule.
- What happens when a request is rejected before processing begins (e.g., a blank or over-length
  question) with the diagnostic-trace option set? The existing validation-error response is returned
  unaffected — no pipeline stage ever ran, so no pipeline-stage log entry is produced (the existing,
  unrelated rejection log line the endpoint already emits for every validation failure still fires) and
  no trace array is expected on the response (FR-014).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST log an entry when a chat request is accepted for processing (i.e., after
  it passes the endpoint's existing validation — see FR-014 for the validation-rejection case, which
  this entry does not cover), including the question text and, when present, the requested document-id
  filter.
- **FR-002**: The system MUST log an entry when the question has been embedded into a vector,
  recording that the step completed (or, on failure, that it failed). A failure entry MUST include the
  failure's cause as a short, non-sensitive description (consistent with FR-009 — never the raw
  exception stack trace, and never a credential), matching the existing failure-logging discipline
  already used elsewhere in the embedding client.
- **FR-003**: The system MUST log an entry when the vector-similarity search completes, including how
  many candidate passages were retrieved.
- **FR-004**: The system MUST log an entry when candidate passages have been filtered by the
  relevance threshold, including how many survived and how many were discarded. When no candidate
  survives, this entry is also the final log entry the pipeline produces for the request — the
  outcome is "not covered" by construction, and FR-006 produces no additional entry for it (see
  FR-006).
- **FR-005**: The system MUST log an entry when the prompt sent to the language model has been
  assembled, including which passages were included in it.
- **FR-006**: The system MUST log an entry when the language model's response is received, including
  the outcome (answered vs. not covered) and the length of the generated answer. This entry is
  produced only when the model was actually invoked — i.e., at least one candidate survived filtering
  (FR-004). When the pipeline stops earlier because no candidate survived, FR-004's entry is the
  request's final log entry; no second, early-stop entry is fabricated here.
- **FR-007**: A single chat request's logged steps MUST appear in the order they actually occurred,
  so the full sequence of that request's processing can be reconstructed from the log output alone.
- **FR-008**: Every logged step for a chat request MUST be identifiable as belonging to that specific
  request, so that steps from concurrent requests are never intermingled or ambiguous when read back.
- **FR-009**: Log entries MUST NOT contain the Azure OpenAI API key or any other credential, per the
  project's existing logging requirement.
- **FR-010**: The chat request accepted from the frontend MUST support an explicit, optional
  diagnostic-trace flag, accepting exactly `true`, `false`, or absent; when the flag is absent or
  false, the response's shape and content MUST be identical to the existing (pre-feature) contract. A
  request body where this field is present but is not a boolean value is a malformed request, handled
  by the endpoint's existing malformed-request-body error path — this feature introduces no new
  validation logic for the field's type.
- **FR-011**: When the diagnostic-trace flag is set to true, the chat response MUST include an
  ordered array of trace-step entries — one per pipeline stage that actually ran for that request —
  in the order they occurred.
- **FR-012**: Each trace-step entry MUST identify its pipeline stage and include the full detail
  captured for that stage, including: for the vector-search stage, the full text of every retrieved
  candidate passage; for the filtering stage, the full text of every passage that survived; for the
  prompt-assembly stage, the exact prompt text assembled from those passages; and for the
  model-invocation stage, the full raw response text received from the language model.
- **FR-013**: When the diagnostic-trace flag is set to true but the pipeline stops early (no
  passage survives filtering, or the model returns an empty completion), the trace array MUST include
  every stage that actually ran up to and including that stopping point, and MUST NOT include a stage
  that never executed.
- **FR-014**: When the pipeline fails with a processing error, or when the request is rejected before
  processing begins (e.g., a blank or over-length question), the existing error response contract is
  unchanged by this feature; the trace array is not required to be present on any non-`200` response —
  including a validation rejection — regardless of whether the diagnostic-trace flag was set on the
  request.
- **FR-015**: The diagnostic-trace flag MUST be usable by any caller of the chat endpoint; this
  feature introduces no additional authorization beyond the endpoint's existing access model. This is a
  deliberate, risk-accepted scope decision, not an oversight: any caller who can already reach
  `POST /chat` can already retrieve every retrieved passage's full text indirectly through repeated
  questions, so gating the trace flag behind new authorization would not meaningfully reduce exposure
  while the endpoint itself remains open — a future feature may revisit this if the endpoint's access
  model changes.
- **FR-016**: Enabling the diagnostic-trace flag MUST NOT change the value of `answer`, `sources`, or
  any other field defined on the `ChatResponse` contract as of this feature (i.e., the fields feature
  007 already defined, plus `trace` itself); this guarantee does not extend to fields a later,
  unrelated feature might add to that contract in the future.
- **FR-017**: Full raw content — the retrieved passage text, the exact prompt, and the raw model
  response — MUST NOT be written to the persistent server log by this feature under any circumstance,
  including when the diagnostic-trace flag is set to true; it MUST appear only in the opt-in API
  response (spec.md Clarifications). The persistent log stays at the same summary level described by
  FR-001–FR-006 regardless of the flag's value.

### Key Entities

- **Chat Trace Step**: One recorded stage of processing a single chat request (request received,
  question embedded, vector search completed, results filtered, prompt assembled, or model response
  received), identified by its stage and position in the sequence, carrying the detail captured for
  that stage.
- **Chat Diagnostic Trace**: The ordered collection of Chat Trace Steps produced for one chat
  request, present in the response only when the caller explicitly opted in.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For 100% of chat requests accepted for processing (i.e., past the endpoint's existing
  validation — a request rejected before processing began has no pipeline steps to reconstruct, per
  FR-001/FR-014), a developer or support engineer can reconstruct the full, correctly ordered sequence
  of pipeline steps (request received → embedding → retrieval → filtering → prompt assembly → model
  response → outcome) from the backend log output alone, with no additional debugging tools.
- **SC-002**: When the diagnostic-trace option is enabled, 100% of chat responses include a trace
  entry for every pipeline stage that actually executed, correctly ordered, with zero missing or
  fabricated steps.
- **SC-003**: When the diagnostic-trace option is not set, the chat response body (answer content,
  citations, and response shape) is byte-for-byte identical to the pre-existing contract, verified by
  every pre-existing automated test for the chat endpoint continuing to pass unmodified with no test
  changes required. Response latency is not expected to change, since no pipeline step, retrieval call,
  or model call is added, removed, or altered by this feature when the option is off; this is a
  qualitative expectation, not a criterion this feature adds a performance test to measure.
- **SC-004**: Zero occurrences of API keys or other credentials appear in log output or in trace
  content, across all pipeline stages, verified by the credential-safety check
  ([quickstart.md](quickstart.md) Step 6 — a grep across captured log output and a captured trace
  response for the configured Azure OpenAI key value) run by whoever validates this feature before it
  ships.
- **SC-005**: Given only a single chat response's trace array — with no access to server logs — every
  field FR-012 requires (document ids, filenames, pages, similarity scores, the exact prompt sent, and
  the raw model response received) is present and attributable to a specific pipeline stage, so the
  answer's full provenance can be read directly off the trace's own fields.
- **SC-006**: When the diagnostic-trace option is enabled, response payload growth is bounded by the
  same fixed retrieval limit (`TOP_K`, currently 4 passages per request — see Assumptions) the
  pipeline already enforces before this feature exists; enabling the option adds no new retrieval call,
  no new model call, and no additional round-trip, so any latency change is limited to the cost of
  serializing the already-computed trace data.

## Assumptions

- The existing `POST /chat` contract from [007-chat-endpoint](../007-chat-endpoint/spec.md) remains
  the default behavior for any caller that does not set the new option; this feature only adds one
  optional request field and one optional response field.
- Log entries continue to use the same structured, English-only logging approach already established
  by prior features and the project constitution — specifically, the constitution's Error Handling &
  Logging section (request/response summaries, no credentials) and its Code & Documentation Language
  Standard (English-only), both unchanged since [007-chat-endpoint](../007-chat-endpoint/plan.md); no
  new logging infrastructure or tooling is introduced.
- This feature does not add a UI panel or screen to view the trace inside the chat interface (per
  Clarifications); the trace is available to whatever reads the API response (browser tooling, a
  future feature, or manual inspection).
- Because retrieval is already bounded to a small, fixed number of passages per request — currently
  `TOP_K = 4` in [007-chat-endpoint](../007-chat-endpoint/spec.md)'s `ChatService` — including full
  passage and prompt text in the trace does not produce an unbounded response payload; a future change
  to that constant would proportionally change the trace's size and should prompt a revisit of this
  assumption (see also SC-006).
- The manually added log statements already present in the backend (in the chat completion client,
  the retrieval repository, the chat service, the embedding client, and the Azure OpenAI properties
  component) are treated as a partial, in-progress start on this feature's logging requirement, to be
  completed and made consistent rather than as a separate, unrelated concern. The specific mapping of
  which existing ad hoc log line is superseded by which of this feature's requirements is a planning
  concern, not a specification one, and is recorded in
  [research.md](research.md)'s Decision 4 and Decision 5 rather than duplicated here.
- No new authentication or authorization is introduced for the diagnostic-trace option; it follows
  the chat endpoint's current access model (see also FR-015 for why this is a deliberate decision, not
  an oversight).
- A caller retrying a request after a `503` processing failure is treated as an entirely new, unrelated
  request — its own correlation id, its own log entries, its own trace if requested — with no retry-
  aware behavior (e.g. no linking a retry back to the failed attempt) in this feature's scope.
- The two-concurrent-requests scenario (User Story 1 Acceptance Scenario 3) establishes that logged
  steps stay distinguishable per request; this feature does not commit to a specific tested concurrency
  ceiling beyond that — the correlation mechanism chosen in [research.md](research.md) is expected to
  generalize to any number of simultaneous requests by construction, but validating that at scale is
  out of scope for this feature's acceptance criteria.

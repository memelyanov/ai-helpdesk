# Implementation Plan: Chat Pipeline Diagnostic Logging & Trace

**Branch**: `main` (no dedicated feature branch — no `before_specify`/`before_plan` hook is
registered in `.specify/extensions.yml`, same situation every feature since
[002-frontend-health-wire](../002-frontend-health-wire/plan.md) recorded) | **Date**: 2026-08-17 |
**Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-chat-diagnostic-trace/spec.md`

## Summary

Finish the manual, ad hoc logging already started across `ChatService`, `ChatRetrievalRepository`,
`ChatCompletionClient`, `EmbeddingClient`, and `AzureOpenAiProperties` (visible today as uncommitted
changes) by replacing it with one consistent mechanism: `ChatService.answer()` builds an ordered,
in-memory list of `ChatTraceStep`s as it executes the existing retrieve → threshold → augment →
generate pipeline, emits exactly one summary-level log line per step it appends (correlated across
lines by a per-request id carried in SLF4J's MDC, no new logging library), and — only when the
caller's request explicitly opts in — attaches that same list, expanded with full raw content, to
the `ChatResponse` it returns. `ChatRequest` gains one new optional field (`includeTrace`);
`ChatResponse` gains one new optional field (`trace`), present only when requested and otherwise
indistinguishable from feature 007's original contract (spec.md FR-010, User Story 3).

`ChatCompletionClient.complete(...)` changes its return type from a bare `String` to a small
`ChatCompletionResult` record (`systemPrompt`, `prompt`, `completion`) so `ChatService` — the one
place with visibility into every pipeline stage — can put the exact prompt text into the trace
without `ChatCompletionClient` needing to know tracing exists. No other collaborator's public
signature changes. Six decisions carry the design (full reasoning in [research.md](research.md)):
a per-request correlation id via MDC rather than threading an id through every method signature;
`ChatTraceStep`'s `detail` as a small, per-stage `Map<String, Object>` rather than six separate typed
records; persistent server logs staying at summary level always (constitution's own "request/response
summaries" wording) while full raw content — retrieved passage text, the exact prompt, the raw model
response — appears only in the opt-in API response (spec.md Clarifications); the six ad hoc `.forEach`
full-content log dumps and the one misattributed logger (`AzureOpenAiProperties` logging under
`ChatCompletionClient`'s name) being removed/fixed as part of finishing this work, not left alongside
the new mechanism; and a new `ChatServiceTest` unit test (first direct, mocked-collaborator test of
`ChatService`, no Spring context) covering trace assembly and step-ordering, alongside targeted
extensions to the existing `ChatControllerContractTest`.

## Technical Context

**Language/Version**: Java 17, Spring Boot 3.5.16 (unchanged from features 001–007).

**Primary Dependencies**: none new. SLF4J's `MDC` (already transitively available via
`spring-boot-starter-logging`/Logback, used nowhere in this codebase yet) is the only "new" API
surface touched, and it ships with the existing logging stack — no `pom.xml` change. Jackson (already
on the classpath) serializes the new `ChatTraceStep.detail` `Map<String, Object>` with no additional
configuration.

**Storage**: No change. This feature persists nothing new and reads/writes no new table or column —
it only changes what is logged and what one existing endpoint's response body can optionally include.

**Testing**: JUnit 5 + Mockito + AssertJ (existing stack, no new dependency):

- **New unit test, `ChatServiceTest`** (always run, no Spring context, no `MockMvc`): `EmbeddingClient`,
  `ChatRetrievalRepository`, and `ChatCompletionClient` are plain Mockito mocks. Asserts the trace is
  `null`/absent on `ChatResponse` when `includeTrace` is absent/false; asserts the full, correctly
  ordered six-step trace (with full raw passage/prompt/response content, FR-012) when
  `includeTrace=true` and the pipeline completes normally; asserts the trace truncates after
  `results_filtered` with no `prompt_assembled`/`model_response_received` steps when nothing survives
  the threshold (FR-013); asserts an empty completion is still traced as its own
  `model_response_received` step rather than silently dropped.
- **Extended `ChatControllerContractTest`** (existing file, `MockMvc`, stubbed `ChatService`): asserts
  `includeTrace` on the incoming JSON body reaches `ChatService.answer()` unchanged (mirrors the
  existing `documentIds`-pass-through test), and that a stubbed `ChatResponse` with a non-null `trace`
  serializes it in the JSON response while a `null` trace produces no `"trace"` key at all (FR-010,
  User Story 3's byte-identical-shape guarantee).
- **`ChatRetrievalIT`/`ChatCompletionConnectivityIT`** (existing `@Tag("db")`/`@Tag("azure")` opt-in
  tiers): unaffected by this feature's *logic*. `ChatRetrievalIT` does need a mechanical compile-fix —
  its three `chatCompletionClient.complete(any(), any())` stubs return a bare `String` today, which no
  longer compiles once `complete(...)`'s return type changes to `ChatCompletionResult`; this fix must
  land in the same change as that return-type change, since Maven compiles the whole test source tree
  together (research Decision 6). `ChatCompletionConnectivityIT` needs no change.

Tests MUST NOT require live AI provider credentials to pass by default (constitution Principle II) —
the new `ChatServiceTest` and the extended `ChatControllerContractTest` both stub every collaborator.

**Target Platform**: Same as every prior backend feature — local developer machine, Docker Compose for
PostgreSQL only; backend runs locally (`backend\mvnw.cmd spring-boot:run`).

**Project Type**: Web application (existing structure). Backend-only change
(`backend/src/main/java/.../chat/`, one line in `application.yml`) — no frontend change (spec.md
Clarifications: API-only, no UI panel in this feature).

**Performance Goals**: SC-003 — when `includeTrace` is absent/false, response time and payload show no
measurable change from feature 007's existing behavior. Building the trace list in memory is O(1)
extra work per pipeline stage (at most 4 retrieved passages, `TOP_K`) regardless of whether it is
ultimately attached to the response, so this is a non-goal to optimize away — the cost is already
negligible.

**Constraints**:
- Persistent log output MUST stay at summary level for every request, opted-in or not (constitution
  Error Handling & Logging: "request/response summaries") — full raw passage/prompt/response text
  MUST NOT be written to the log file by this feature; it appears only in the opt-in API response
  (spec.md Clarifications, FR-012).
- No Azure OpenAI credential may appear in a log line or in trace content under any circumstance
  (FR-009, SC-004) — unchanged from feature 007's existing discipline (`e.toString()`, never a raw
  exception or credential value).
- Enabling `includeTrace` MUST NOT alter `answer`, `sources`, or any other existing `ChatResponse`
  field's value (FR-016) — the trace is purely additive, computed from data the pipeline already
  produces, never fed back into it.
- Every log line for one request MUST be attributable to that request (FR-008) without changing the
  call signature of every existing `log.info(...)`/`log.warn(...)` call site across four classes — an
  MDC-carried correlation id, set once per request, is the only mechanism that satisfies both.

**Scale/Scope**: One new record (`ChatTraceStep`), one new record (`ChatCompletionResult`), one
modified DTO (`ChatRequest` +1 field), one modified DTO (`ChatResponse` +1 field), one modified service
(`ChatService`, trace assembly + step logging), one modified client
(`ChatCompletionClient.complete(...)`'s return type), one modified repository
(`ChatRetrievalRepository`, drop the ad hoc per-row dump), one modified client (`EmbeddingClient`, drop
the ad hoc full-question-text dump — already logged once, at `request_received`), one modified
component (`AzureOpenAiProperties`, fix the misattributed logger), one `application.yml` line (MDC
pattern). No new package, no new REST resource, no schema change, no new dependency.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.4.1** (unchanged since feature 007; no
amendment has landed between that feature and this one).

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` precedes this plan; both clarifications the spec needed (UI-display scope, trace-detail depth) were resolved during `/speckit-specify` itself, and `/speckit-clarify` found no further ambiguity before this plan was written. |
| II | Test-Driven Development (Mandatory) | ✅ PASS | A new unit test (`ChatServiceTest`) and extensions to the existing contract test are planned before any implementation task is written; the default suite needs no live database or Azure credentials. |
| III | Grounded Answers (RAG-First) | ✅ PASS — unaffected | This feature adds no new answer-generation logic; it observes and reports the existing retrieve → augment → generate pipeline, it does not change what is retrieved or generated. |
| IV | No Hallucination (Context Adherence) | ✅ PASS — unaffected | The fixed system prompt and threshold short-circuit (feature 007) are untouched; the trace reports whichever outcome the existing pipeline already produced, never influences it. |
| V | Semantic Understanding (Meaning-Based Retrieval) | ✅ PASS — unaffected | No change to embedding, retrieval, or ranking logic — this feature only observes `RetrievedChunk` results already computed by feature 007's existing query. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ✅ PASS — unaffected | No new external call, no new data leaving the existing Azure OpenAI / self-hosted pgvector boundary — the trace is built from data already flowing through the pipeline. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ✅ PASS — unaffected | No change to retrieval ranking or the evaluation mechanism; `sample-data/evaluation-questions.csv` runs are unaffected since `includeTrace` defaults to absent/false. |

**Error Handling & Logging compliance**: this feature *is* the constitution's "structured logging...
with request/response summaries" requirement, completed — every embedding request, retrieval query,
and LLM call gets one summary-level log line per stage (FR-001–FR-006), correlated per request via
MDC (FR-008), with API keys/credentials never appearing in either the log or the trace (FR-009, SC-004,
unchanged discipline from feature 007), and full raw content (passage text, prompt, model response)
never reaching the persistent log regardless of `includeTrace` (FR-017, research Decision 4). The existing `4xx`/`503` error-response contract (feature 007)
is unchanged; a request that fails validation or processing is not required to carry a `trace`
(FR-014).

**Code & Documentation Language Standard compliance**: this plan and all Phase 0/1 artifacts are in
English ✅; implementation-phase code, comments, and commit messages will follow the same standard —
including the log messages and `detail` map keys this feature adds/changes.

**Technology Stack compliance**: Java 17 / Spring Boot 3 ✅; no new dependency, no deviation from the
mandated stack; SLF4J `MDC` and Jackson `Map<String, Object>` serialization are both already-present
capabilities of the existing logging/JSON stack, not new tooling.

**AI Provider Configuration / Chunking & Embedding Strategy / Ingestion Pipeline / Query Pipeline
compliance**: N/A — this feature changes no configuration variable, no chunking/embedding logic, and
no ingestion behavior; the Query Pipeline's top-K/threshold/system-prompt/response-shape requirements
(feature 007) are unchanged, only observed and reported.

**Post-Phase 1 re-check**: ✅ No change. Phase 1 design (data-model.md, contracts/, quickstart.md)
introduces no new persisted table or column, no new dependency, and no deviation from the mandated
tech stack.

**Gate result**: PASS — no violations, no justifications required. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/009-chat-diagnostic-trace/
├── plan.md                                  # This file
├── research.md                              # Phase 0 — 6 decisions
├── data-model.md                            # Phase 1 — request/response/internal shapes (no new persisted entities)
├── quickstart.md                            # Phase 1 — bring-up and per-user-story validation
├── contracts/
│   └── chat-diagnostic-trace-contract.md    # The additive delta on top of 007's POST /chat contract
├── checklists/
│   ├── requirements.md                      # Spec quality checklist — 16/16 items pass
│   └── quality.md                           # Requirements-quality self-check — 33 items
└── tasks.md                                 # Phase 2 — created by /speckit-tasks, NOT by this command
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                                  # UNCHANGED — no new dependency
├── src/main/resources/application.yml                       # MODIFIED — +1 logging.pattern line (MDC correlation id, research Decision 1)
├── src/main/java/com/epam/aihelpdesk/
│   ├── health/
│   │   └── AzureOpenAiProperties.java                        # MODIFIED — fix misattributed logger (was ChatCompletionClient.class)
│   ├── ingestion/
│   │   └── EmbeddingClient.java                              # MODIFIED — drop ad hoc full-question-text log line (dupes request_received)
│   └── chat/                                                 # UNCHANGED package layout (feature 007), files below modified/new
│       ├── ChatController.java                                # MODIFIED — sets/clears the MDC correlation id for the request (research Decision 1)
│       ├── ChatService.java                                   # MODIFIED — builds ChatTraceStep list, emits one summary log per stage, attaches trace when requested
│       ├── ChatRetrievalRepository.java                       # MODIFIED — drop ad hoc per-row log dump (superseded by ChatService's stage summary)
│       ├── ChatCompletionClient.java                           # MODIFIED — complete(...) returns ChatCompletionResult, not String; drops ad hoc full-text log lines
│       ├── ChatCompletionResult.java                           # NEW — { systemPrompt, prompt, completion } (research Decision 3)
│       └── dto/
│           ├── ChatRequest.java                                # MODIFIED — +1 field: includeTrace (Boolean, nullable)
│           ├── ChatResponse.java                                # MODIFIED — +1 field: trace (List<ChatTraceStep>, @JsonInclude(NON_NULL))
│           └── ChatTraceStep.java                                # NEW — { stage, durationMs, detail } (research Decision 2)
└── src/test/java/com/epam/aihelpdesk/
    └── chat/
        ├── ChatServiceTest.java                                # NEW — unit test, mocked collaborators, no Spring context
        ├── ChatControllerContractTest.java                     # MODIFIED — +includeTrace/trace pass-through cases
        ├── ChatRetrievalIT.java                                 # MODIFIED — mechanical compile-fix only: 3 stubs rewrapped as ChatCompletionResult
        └── ChatCompletionConnectivityIT.java                    # UNCHANGED

frontend/                                                       # UNCHANGED — no frontend work in this feature (spec.md Clarifications)
```

**Structure Decision**: No new package and no new REST resource — this feature stays entirely inside
the `chat` package feature 007 already created, plus two small, surgical touches outside it
(`EmbeddingClient`'s duplicate log line, `AzureOpenAiProperties`'s misattributed logger) that the
manual in-progress logging work already started. No frontend directory is touched.

## Complexity Tracking

*No entries — the Constitution Check gate passed with no violations requiring justification.*

# Phase 1 Data Model: Chat Pipeline Diagnostic Logging & Trace

**Date**: 2026-08-17 | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

This feature persists nothing new — no new table, no new column. Everything below is either an
additive field on feature 007's existing `POST /chat` request/response DTOs, or a new, purely
in-memory shape built fresh per request and discarded once the HTTP response is sent (same lifecycle
as feature 007's own `RetrievedChunk`, see
[specs/007-chat-endpoint/data-model.md](../007-chat-endpoint/data-model.md)).

## Request: `ChatRequest` (modified — +1 field)

| Field | Type | Required | Notes |
|---|---|---|---|
| `question` | `String` | yes | Unchanged from feature 007. |
| `documentIds` | `List<UUID>` | no | Unchanged from feature 007. |
| `includeTrace` | `Boolean` | no | **New.** `true` requests the diagnostic trace on the response (FR-010/FR-011). Absent, `null`, or `false` are all equivalent to "no trace" — the response is byte-identical to feature 007's original contract (FR-010, User Story 3). A boxed `Boolean`, not a primitive `boolean`, matching the existing nullable-optional style `documentIds` already established for this record, and making the three-way "explicitly true / explicitly false / not sent" distinction visible in code even though only the first is ever treated differently. |

## Internal: `ChatCompletionResult` (new)

Returned by `ChatCompletionClient.complete(...)` (research Decision 3) in place of the bare `String`
it returned in feature 007 — never serialized directly; `ChatService` reads it to build both the
`prompt_assembled` and `model_response_received` trace steps (when requested) and its own
summary-only log lines (always).

| Field | Type | Notes |
|---|---|---|
| `systemPrompt` | `String` | The fixed constant `ChatCompletionClient.SYSTEM_PROMPT` (constitution Query Pipeline section), included for completeness even though it never varies per request. |
| `prompt` | `String` | The exact `"Context:\n" + <passages> + "\n\nQuestion: " + question` text sent to the model — never reconstructed a second time elsewhere (research Decision 3). |
| `completion` | `String` | The raw completion text — identical to what feature 007's `complete(...)` returned directly; possibly blank (unchanged "empty completion" handling). |

## Internal: `ChatTraceStep` (new)

One recorded pipeline stage (spec.md's "Chat Trace Step" entity), built by `ChatService.answer(...)`
as it executes (research Decisions 2 and 5). At most six per request, in the order the stages actually
ran; fewer when the pipeline stops early (FR-013).

| Field | Type | Notes |
|---|---|---|
| `stage` | `String` | One of the six fixed values below — a closed vocabulary, same convention as `ChatErrorResponse.error` (feature 007). |
| `durationMs` | `long` | Wall-clock time this stage took, measured immediately around the stage's own work (e.g. around the `ChatCompletionClient.complete(...)` call for `model_response_received`). Included in both the log line and the trace `detail`, so timing is visible either way. |
| `detail` | `Map<String, Object>` | Stage-specific fields, documented per stage below (research Decision 2). Serializes as a plain nested JSON object. |

### `stage` values and their `detail` keys

| `stage` | When appended | `detail` keys | FR |
|---|---|---|---|
| `request_received` | Immediately, at the start of `ChatService.answer(...)` (request already validated by `ChatController`). | `question` (String, full text), `documentIds` (List\<String\>, the UUIDs as strings, empty list if none supplied) | FR-001 |
| `question_embedded` | After `EmbeddingClient.embedQuery(...)` returns successfully. | `vectorDimensions` (int — the embedding's length, e.g. `1536`; never the raw float array itself, which is meaningless to a human reader and unrelated to the "full raw content" clarification, which was scoped to passage/prompt/response *text*) | FR-002 |
| `vector_search_completed` | After `ChatRetrievalRepository.findTopSimilarChunks(...)` returns. | `candidateCount` (int), `candidates` (array of `{documentId, chunkId, sourceFilename, page, text, distance, similarity}` — one per retrieved row, full passage `text` included per FR-012) | FR-003 |
| `results_filtered` | After the similarity-threshold filter is applied. | `survivorCount` (int), `discardedCount` (int), `threshold` (double, `SIMILARITY_THRESHOLD`), `survivors` (same per-row shape as `candidates` above, filtered to the surviving subset) | FR-004 |
| `prompt_assembled` | After `ChatCompletionClient.complete(...)` returns (its `ChatCompletionResult.prompt`/`systemPrompt` reported here; appended only when at least one passage survived, since this stage never runs otherwise). | `systemPrompt` (String), `prompt` (String, full text), `passageCount` (int) | FR-005 |
| `model_response_received` | Immediately after `prompt_assembled`, same pipeline pass (both steps are appended together, sourced from the same `ChatCompletionResult`, since the client call that produces the prompt and the one that produces the response are the same call — appended as two steps because they answer two distinct FRs, FR-005 and FR-006). | `rawResponse` (String, full text, possibly blank), `completionLength` (int), `outcome` (`"answered"` or `"not_covered"` — `"not_covered"` when `rawResponse` is blank) | FR-006 |

When no candidate survives filtering, the trace ends at `results_filtered` — no `prompt_assembled` or
`model_response_received` step is appended, and `ChatCompletionClient` is never called (unchanged
feature 007 behavior; FR-013). When a candidate survives but the model returns a blank completion, all
six steps appear, with `model_response_received.detail.outcome = "not_covered"` — this is how the
"empty completion" edge case (spec.md Edge Cases) is distinguished from the threshold short-circuit in
the trace, even though both ultimately produce the same fixed `answer` text.

## Response: `ChatResponse` (modified — +1 field)

| Field | Type | Notes |
|---|---|---|
| `answer` | `String` | Unchanged from feature 007. |
| `sources` | `List<SourceCitation>` | Unchanged from feature 007. |
| `trace` | `List<ChatTraceStep>` | **New.** `null` (and omitted from the JSON body entirely, `@JsonInclude(Include.NON_NULL)`) unless the request had `includeTrace=true` (FR-010/FR-011). When present, one entry per stage that actually ran, in execution order (FR-007/FR-011). Never changes `answer` or `sources`' value (FR-016) — purely observational. |

## Logging: what reaches the persistent log file vs. the trace

| | Persistent log (`logs/ai-helpdesk.log`, always) | `ChatResponse.trace` (only when `includeTrace=true`) |
|---|---|---|
| Stage occurred (which, when, how long) | ✅ one line per stage | ✅ one `ChatTraceStep` per stage |
| Counts (candidates, survivors, discarded) | ✅ | ✅ |
| Filenames, pages, similarity scores | ✅ | ✅ |
| Full retrieved-passage text | ❌ | ✅ |
| Full assembled prompt / system prompt | ❌ | ✅ |
| Full raw model response text | ❌ | ✅ |
| Question text | ✅ (FR-001 requires this once, at `request_received`) | ✅ |
| Per-request correlation id | ✅ (MDC `chatRequestId`, every line) | n/a — the HTTP response itself is already scoped to one request |
| Azure OpenAI credential | ❌ — never, under any circumstance (FR-009, SC-004) | ❌ — never, under any circumstance |

(research Decision 4 — the rationale for this split.)

## Out of scope for this feature's data shapes

- **No persisted trace/log-history entity** — every `ChatTraceStep` is constructed fresh per request
  and discarded once the HTTP response is sent (spec.md Assumptions: no new persistence).
- **No trace content on `ChatErrorResponse`** — a `400`/`503` outcome keeps its existing feature-007
  shape; this feature does not add a trace field to the error response (FR-014).
- **No pagination on `trace`** — bounded to at most 6 entries, an array never large enough to need it.
- **No raw embedding vector in the trace** — see `question_embedded`'s `detail` above; excluded
  deliberately, not an oversight.

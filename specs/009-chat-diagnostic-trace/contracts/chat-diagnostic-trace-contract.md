# Contract: `POST /chat` — diagnostic trace addition

**Feature**: [Chat Pipeline Diagnostic Logging & Trace](../spec.md) | **Data model**:
[../data-model.md](../data-model.md)

The additive delta on top of
[../../007-chat-endpoint/contracts/chat-api-contract.md](../../007-chat-endpoint/contracts/chat-api-contract.md),
which remains the complete baseline contract for `POST /chat` — request shape, both success outcomes,
both error outcomes, and every non-guarantee it documents are unchanged by this feature. This file
documents only what is new: one optional request field, one optional response field.

## Request — `includeTrace`

```
POST /chat
Content-Type: application/json

{
  "question": "Can I expense a taxi from the airport?",
  "includeTrace": true
}
```

- `includeTrace` (boolean, optional): when `true`, the response includes the `trace` array documented
  below. Absent, `null`, or `false` are all equivalent — the response is byte-for-byte identical to
  the baseline contract (FR-010; spec.md User Story 3).
- Combines freely with `documentIds` — both apply independently.
- Available to any caller, with no additional authorization beyond the baseline contract's existing
  "no authentication" non-guarantee (FR-015).

## Success response — grounded answer, with trace requested

```
200 OK
Content-Type: application/json

{
  "answer": "Yes — ground transport between the airport and your hotel or office is reimbursable...",
  "sources": [
    { "documentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "filename": "travel-expense-policy.pdf", "page": "2", "score": 0.81 }
  ],
  "trace": [
    {
      "stage": "request_received",
      "durationMs": 0,
      "detail": { "question": "Can I expense a taxi from the airport?", "documentIds": [] }
    },
    {
      "stage": "question_embedded",
      "durationMs": 412,
      "detail": { "vectorDimensions": 1536 }
    },
    {
      "stage": "vector_search_completed",
      "durationMs": 38,
      "detail": {
        "candidateCount": 4,
        "candidates": [
          { "documentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "chunkId": 7, "sourceFilename": "travel-expense-policy.pdf", "page": 2, "text": "Ground transport, including taxis and ride-share...", "distance": 0.19, "similarity": 0.81 }
        ]
      }
    },
    {
      "stage": "results_filtered",
      "durationMs": 1,
      "detail": { "survivorCount": 1, "discardedCount": 3, "threshold": 0.5, "survivors": [ "...same shape as candidates..." ] }
    },
    {
      "stage": "prompt_assembled",
      "durationMs": 0,
      "detail": {
        "systemPrompt": "Answer the following question based on the context provided. Always cite your sources.",
        "prompt": "Context:\n\nGround transport, including taxis and ride-share...\n\nQuestion: Can I expense a taxi from the airport?",
        "passageCount": 1
      }
    },
    {
      "stage": "model_response_received",
      "durationMs": 1140,
      "detail": {
        "rawResponse": "Yes — ground transport between the airport and your hotel or office is reimbursable...",
        "completionLength": 187,
        "outcome": "answered"
      }
    }
  ]
}
```

- `trace` is present, and is an array with one entry per stage that actually ran, in execution order
  (FR-011; data-model.md's stage table).
- Every step's `detail` carries the full detail documented in data-model.md, including full passage
  text, the exact prompt, and the exact raw model response (FR-012, spec.md Clarifications).
- `answer` and `sources` are computed exactly as the baseline contract describes — `trace`'s presence
  never changes their value (FR-016).

## Success response — documentation does not cover this, with trace requested

```
200 OK
Content-Type: application/json

{
  "answer": "I don't have this information in the documentation.",
  "sources": [],
  "trace": [
    { "stage": "request_received", "durationMs": 0, "detail": { "question": "...", "documentIds": [] } },
    { "stage": "question_embedded", "durationMs": 398, "detail": { "vectorDimensions": 1536 } },
    { "stage": "vector_search_completed", "durationMs": 41, "detail": { "candidateCount": 4, "candidates": [ "..." ] } },
    { "stage": "results_filtered", "durationMs": 1, "detail": { "survivorCount": 0, "discardedCount": 4, "threshold": 0.5, "survivors": [] } }
  ]
}
```

- Only four steps appear — `prompt_assembled` and `model_response_received` are absent, because
  neither stage ran; `ChatCompletionClient` is never called when nothing survives the threshold
  (FR-013, unchanged feature 007 behavior).
- The same truncation applies, for the same reason, when `documentIds` filters the candidate set to
  zero rows, or when the corpus has never had a document ingested.

## Success response — trace not requested (unchanged baseline behavior)

```
200 OK
Content-Type: application/json

{
  "answer": "Yes — ground transport...",
  "sources": [ "..." ]
}
```

- No `trace` key at all — not `"trace": null`, simply absent — when `includeTrace` is absent, `null`,
  or `false` (FR-010; data-model.md's `@JsonInclude(Include.NON_NULL)`). A client written against the
  baseline feature 007 contract, unaware this field exists, sees no difference whatsoever.

## Error responses

Unchanged from the baseline contract — a `400` or `503` outcome is not required to carry a `trace`
field, regardless of whether `includeTrace` was set on the request (FR-014). See
[../../007-chat-endpoint/contracts/chat-api-contract.md](../../007-chat-endpoint/contracts/chat-api-contract.md)
for the full `error`/`message` shape and vocabulary, which this feature does not modify.

## Non-guarantees (explicitly out of scope)

- **No UI to view the trace** — this contract describes what the API returns; no chat-interface screen
  or panel renders it in this feature (spec.md Clarifications).
- **No trace content written to the persistent server log** — regardless of `includeTrace`, the log
  file stays at the summary detail level the baseline contract's logging already implies; only the
  HTTP response carries full raw content (FR-017; data-model.md's logging-vs-trace table, research
  Decision 4).
- **No per-request tuning of what a step's `detail` contains** — the six stages and their fixed key
  sets (data-model.md) are not configurable per request.

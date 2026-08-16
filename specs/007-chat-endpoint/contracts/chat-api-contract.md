# Contract: `POST /chat`

**Feature**: [Chat Endpoint](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

What a caller — the future Angular chat view (`poc-concept.md` §10 item 7), a test harness, or a
manual `curl`/HTTP client in the meantime — can rely on.

## Request

```
POST /chat
Content-Type: application/json

{
  "question": "Can I expense a taxi from the airport?",
  "documentIds": ["3fa85f64-5717-4562-b3fc-2c963f66afa6"]
}
```

- `question` (string, required): the plain-text question. 1–1000 characters after trimming leading
  and trailing whitespace.
- `documentIds` (array of UUID strings, optional): when present and non-empty, restricts retrieval to
  chunks belonging to these documents only. Omit the field, send `null`, or send an empty array to
  search the entire corpus.

## Success response — grounded answer

```
200 OK
Content-Type: application/json

{
  "answer": "Yes — ground transport between the airport and your hotel or office is reimbursable. Keep your receipt and submit it within 30 days of returning from the trip; rides over EUR 80 need manager pre-approval.",
  "sources": [
    { "documentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "filename": "travel-expense-policy.pdf", "page": "2", "score": 0.81 }
  ]
}
```

- Returned when at least one retrieved passage meets the 0.5 similarity threshold (FR-006).
- `sources` lists every distinct document-and-page that contributed a retrieved passage, most similar
  first — never a document that did not contribute (FR-008).
- A source from a document with no page structure (a plain `.txt` upload) shows `"page": "no page
  structure"` instead of a number:

  ```json
  { "documentId": "9c858901-8a57-4791-81fe-4c455b0922de", "filename": "corporate-card-rules.txt", "page": "no page structure", "score": 0.77 }
  ```

## Success response — documentation does not cover this

```
200 OK
Content-Type: application/json

{
  "answer": "I don't have this information in the documentation.",
  "sources": []
}
```

- Returned when no retrieved passage meets the similarity threshold, including: the corpus has never
  had a document ingested, `documentIds` matches no ingested document, or every candidate passage
  falls below 0.5 similarity (FR-007, spec Edge Cases).
- Always `sources: []` — never a partial or low-confidence source list attached to this outcome.
- This exact wording is also what the AI provider is separately instructed to produce on its own
  initiative when handed sufficient context but no real answer within it; both paths converge on the
  identical string, so a caller only ever sees one "not covered" message.

## Error response — invalid request

```
400 Bad Request
Content-Type: application/json

{
  "error": "blank_question",
  "message": "Question must not be blank."
}
```

`error` is one of:

| `error` | Meaning |
|---|---|
| `blank_question` | `question` is missing, empty, or all whitespace (FR-011). |
| `question_too_long` | `question` exceeds 1000 characters after trimming (FR-012). |
| `malformed_request` | The request body could not be parsed (invalid/empty JSON, or a non-UUID entry in `documentIds`). |

No retrieval or generation is attempted for any `400` outcome.

## Error response — could not process the question

```
503 Service Unavailable
Content-Type: application/json

{
  "error": "processing_failed",
  "message": "Failed to process the question."
}
```

`error` is one of:

| `error` | Meaning |
|---|---|
| `provider_unconfigured` | The Azure OpenAI embedding or chat configuration is incomplete; no request was attempted. |
| `processing_failed` | The embedding call, the retrieval query, or the chat completion call failed for an otherwise-valid question. |

- Returned only for an unexpected server-side failure — never for "nothing relevant was found," which
  is always the `200` "documentation does not cover this" outcome above (FR-013). A caller can always
  tell the two apart from the status code alone, with no need to inspect `message`.
- As with every other error response in this system, `message` is a human-readable string a caller
  MUST NOT parse to decide anything — the HTTP status and `error` code together are the full, stable
  contract. `message` never contains an AI provider credential (FR-015).
- A caller may safely retry the identical request once the underlying condition (provider
  reachability, database availability) clears.

## Non-guarantees (explicitly out of scope)

- **No conversation memory** — each request is handled independently; there is no session, thread, or
  history concept anywhere in this contract (FR-014).
- **No streaming response** — `answer` is returned complete in one response body, not incrementally.
- **No authentication/authorization** — consistent with features 004–006, any caller that can reach
  this endpoint may ask any question against the entire corpus (subject to any `documentIds` filter
  they themselves supply).
- **No per-request tuning** of `TOP_K` or the similarity threshold — both are fixed server-side
  defaults (data-model.md); this contract accepts no parameters to override them.

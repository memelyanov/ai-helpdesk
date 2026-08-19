# Phase 1 Data Model: Chat Endpoint (Retrieve → Augment → Generate)

**Date**: 2026-08-16 | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

This feature persists nothing new. It is a new *reader* of feature 003's existing `documents`/
`chunks` tables (see [specs/003-document-vector-schema/data-model.md](../003-document-vector-schema/data-model.md)
for their full schema) — the first to `SELECT ... ORDER BY embedding <=> :query_vector` against
`chunks` rather than only writing or counting them. Everything below is a request/response DTO or an
internal, in-memory shape; none of it is a database table.

## Request: `ChatRequest`

| Field | Type | Required | Notes |
|---|---|---|---|
| `question` | `String` | yes | 1–1000 characters after trimming (FR-011/FR-012, Clarifications Session 2026-08-16). Blank/missing → `400 blank_question`. Over 1000 characters → `400 question_too_long`. |
| `documentIds` | `List<UUID>` | no | When present and non-empty, restricts retrieval to chunks whose `document_id` is in this set (FR-010). Absent, `null`, or empty means "search the whole corpus." An entry that is not a well-formed UUID fails JSON deserialization → `400 malformed_request` (research Decision 8), before this DTO is even constructed. An id naming a document that doesn't exist (or was deleted, feature 006) is not an error — it simply narrows the candidate set to zero rows, which resolves to the same "not covered" outcome as no matches (FR-007, spec Edge Cases). |

## Internal: `RetrievedChunk`

One row returned by `ChatRetrievalRepository`'s similarity query (research Decision 5), reusing
feature 003's `similarity-search-contract.md` column set exactly:

| Field | Type | Notes |
|---|---|---|
| `documentId` | `UUID` | `chunks.document_id` |
| `chunkId` | `int` | `chunks.chunk_id` |
| `sourceFilename` | `String` | `chunks.source_filename` (denormalized, feature 003) |
| `pageNumber` | `Integer` (nullable) | `chunks.page_number`; `null` = no page structure |
| `text` | `String` | `chunks.text` — the passage content included in the generation prompt |
| `distance` | `double` | pgvector cosine distance (`embedding <=> :query_vector`); similarity = `1 - distance` |

At most 5 rows (`TOP_K`), ordered by ascending `distance` (closest match first), for one question —
never persisted, discarded after the request completes.

## Internal: retrieval defaults

Fixed, system-wide constants (constitution Query Pipeline section; spec.md Assumptions — not
per-request tunable in this feature's scope), defined alongside `ChatService`. **Current values as of
feature 011 (Retrieval Accuracy Tuning)** — this feature's own
[data-model.md](../011-retrieval-accuracy-tuning/data-model.md) is the source of truth for these two
going forward, corrected here so this file doesn't contradict shipped behavior:

| Constant | Value | Requirement |
|---|---|---|
| `TOP_K` | 5 | FR-004 |
| `SIMILARITY_THRESHOLD` | 0.35 (cosine similarity; equivalently `distance <= 0.65`) | FR-005 |
| `MAX_QUESTION_LENGTH` | 1000 (characters) | FR-012, Clarifications Session 2026-08-16 |

## Response (success or "not covered"): `ChatResponse`

Both FR-006 (grounded answer) and FR-007 ("not covered") outcomes use this same shape and the same
`200 OK` status — they are distinguished only by whether `sources` is empty and by `answer`'s fixed
text, never by status code (research Decision 7).

| Field | Type | Notes |
|---|---|---|
| `answer` | `String` | The generated answer (FR-006), or the fixed string `"I don't have this information in the documentation."` (FR-007) when nothing sufficiently relevant was retrieved. Never blank. |
| `sources` | `List<SourceCitation>` | Every distinct `(documentId, page)` that contributed a retrieved passage to `answer` (FR-008), in descending similarity order. Empty exactly when `answer` is the fixed "not covered" string (FR-007) — never empty alongside a generated answer, never non-empty alongside the fixed string. |

### `SourceCitation`

| Field | Type | Notes |
|---|---|---|
| `documentId` | `UUID` | Identifies the source document the same way features 005/006 already do — a caller can pass this straight to `GET /documents/{id}/content` (feature 005) to fetch the original file. |
| `filename` | `String` | `chunks.source_filename` — the contributing document's name at ingestion time (FR-009). |
| `page` | `String` | Either the 1-indexed page number as a string (e.g. `"3"`), or the fixed string `"no page structure"` when the source chunk's `pageNumber` is `null` (FR-009, Clarifications Session 2026-08-16). A string, not a nullable integer, precisely so the API surface never represents "no page" as an ambiguous `null`/`0`/missing-field case a caller would have to special-case separately from a real page number. |
| `score` | `double` | Retrieval confidence — `1 - distance`, rounded to two decimal places; always ≥ 0.35 (`SIMILARITY_THRESHOLD`) for any citation that appears here (FR-009). |

## Error: `ChatErrorResponse`

Same `{error, message}` shape `DocumentErrorResponse` already established for `/documents`, but a
separate class scoped to `/chat` (research Decision 8) — its own closed `error` enumeration:

| `error` | HTTP status | Thrown by | Meaning |
|---|---|---|---|
| `blank_question` | 400 | `InvalidChatRequestException` | `question` is missing, empty, or all whitespace (FR-011). |
| `question_too_long` | 400 | `InvalidChatRequestException` | `question` exceeds 1000 characters after trimming (FR-012). |
| `malformed_request` | 400 | `HttpMessageNotReadableException`, mapped directly by `ChatErrorHandler` (no `InvalidChatRequestException` instance involved — Jackson fails before `ChatRequest` is ever constructed) | The request body itself could not be parsed as valid `ChatRequest` JSON (empty body, invalid JSON, or a non-UUID entry in `documentIds`). |
| `provider_unconfigured` | 503 | `ChatProcessingException` | The Azure OpenAI embedding or chat configuration is incomplete; no network call was attempted (FR-013). |
| `processing_failed` | 503 | `ChatProcessingException` | The embedding call, the chat completion call, or the retrieval query failed for an otherwise-valid question (FR-013). |

`message` is informational only, exactly like `DocumentErrorResponse` — a caller MUST NOT parse it;
the HTTP status and `error` code together are the stable contract (FR-015: never a credential value).

## Traceability: from a `RetrievedChunk` group to a `SourceCitation`

```text
chunks row (post-threshold)          SourceCitation
────────────────────────────         ──────────────────────
documentId, pageNumber   ──group──►  documentId
sourceFilename           ──────────► filename
pageNumber (nullable)    ──format──► page ("N" or "no page structure")
min(distance) in group   ──────────► score (1 - distance, rounded)
```

Grouping key is `(documentId, pageNumber)` (research Decision 6) — two chunks from the same document
but different pages become two citations; two chunks from the same document and page (never possible
today, since `chunks` has `UNIQUE (document_id, chunk_id)` and one `page_number` per `chunk_id`, but
kept as an explicit rule for clarity) would collapse into one.

## Out of scope for this feature's data shapes

- **No persisted `Question`/`Answer`/conversation entity** — every shape above is constructed fresh
  per request and discarded once the HTTP response is sent (spec.md Assumptions: no new persistence,
  single-turn/stateless).
- **No pagination** on `sources` — bounded by `TOP_K` (at most 5) already, an array is never large
  enough to need it.
- **No confidence/score field on `ChatErrorResponse`** — an error carries no partial retrieval
  result; FR-013's guarantee is that on failure, the caller gets the error shape only, never a mix of
  error and partial answer content.

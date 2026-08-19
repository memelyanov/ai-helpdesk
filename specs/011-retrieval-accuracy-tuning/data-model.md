# Phase 1 Data Model: Retrieval Accuracy Tuning

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

No new persisted entity, no schema change, no new DTO field, and no change to any existing DTO's
shape. This feature changes the *value* of four existing constants and the values in a handful of
tests/fixtures that were built around the old values — nothing here introduces a new concept the
`documents`/`chunks` schema (feature 003) or the `/chat`/`/documents` DTOs (features 004/007) don't
already have.

## Tuning values (the only thing this feature actually changes)

| Constant | Location | Before | After |
|---|---|---|---|
| `SIMILARITY_THRESHOLD` | [`ChatService.java:46`](../../backend/src/main/java/com/epam/aihelpdesk/chat/ChatService.java) | `0.5` | `0.35` |
| `TOP_K` | [`ChatService.java:43`](../../backend/src/main/java/com/epam/aihelpdesk/chat/ChatService.java) | `4` | `5` |
| `TARGET_TOKENS` | [`Chunker.java:32`](../../backend/src/main/java/com/epam/aihelpdesk/ingestion/Chunker.java) | `800` | `500` |
| `OVERLAP_TOKENS` | [`Chunker.java:33`](../../backend/src/main/java/com/epam/aihelpdesk/ingestion/Chunker.java) | `100` | `63` |

`TOP_K`'s increase (research Decision 6) is a direct response to the other two changes: smaller
passages (`TARGET_TOKENS`) mean a topic that used to fit in fewer, larger passages can now need one
more passage to stay fully covered, and the lowered `SIMILARITY_THRESHOLD` means more candidates are
eligible in the first place — retrieving one more per question keeps coverage from regressing (FR-009).

## Conceptual entities (from spec.md's Key Entities — unchanged in shape, only in the numbers above)

- **Retrieved Passage** (spec.md): maps to the existing `RetrievedChunk` record
  ([`RetrievedChunk.java`](../../backend/src/main/java/com/epam/aihelpdesk/chat/RetrievedChunk.java))
  and the existing `chunks` table row (feature 003). Same fields — `documentId`, `chunkId`,
  `sourceFilename`, `pageNumber`, `text`, `distance` — this feature changes only how large `text`
  typically is for a newly-ingested document (fewer tokens per window) and never changes what
  metadata a row carries.
- **Relevance Bar** (spec.md): maps to `ChatService.SIMILARITY_THRESHOLD` above. Still a single
  `double`, still compared the same way (`distance <= 1 - threshold`, inclusive), still applied
  identically to every question — this feature only changes the number.
- **Retrieval Breadth** (spec.md): maps to `ChatService.TOP_K` above. Still a single `int`, still
  used the same way (the `LIMIT` on the top-K nearest-chunks query, applied before the relevance
  bar) — this feature only changes the number.

## What does not change

- `ChatRequest`/`ChatResponse`/`SourceCitation` (feature 007) — same fields, same JSON shape. A
  citation's `score` can now be as low as `0.35` instead of `0.5` (`SourceCitation.java`'s Javadoc
  is corrected to say so, research Decision 5), but the field itself is unchanged.
- `ChunkDraft`/`EmbeddedChunk`/the `chunks` table (features 003/004) — same columns, same
  constraints; a newly ingested document simply produces more, smaller rows than it would have
  before.
- Any REST route, request/response schema, or error vocabulary — this feature touches no controller
  and adds no endpoint.

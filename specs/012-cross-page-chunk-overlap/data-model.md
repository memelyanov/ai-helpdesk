# Phase 1 Data Model: Cross-Page Chunk Overlap

No new persisted entity, no schema change, and no change to any existing type's fields. This
feature changes what text ends up inside `ChunkDraft.text()` for the chunks adjacent to a page
boundary; it does not change the shape of `ChunkDraft`, `EmbeddedChunk`, the `chunks` table
(`specs/003-document-vector-schema/contracts/chunk-schema.md`), or any REST request/response DTO
(`specs/004-document-ingestion-endpoint/contracts/ingestion-api-contract.md`).

## `ChunkDraft` (existing type, unchanged shape, changed content semantics)

`backend/src/main/java/com/epam/aihelpdesk/ingestion/ChunkDraft.java` — `record ChunkDraft(int
chunkId, Integer pageNumber, String text)`.

| Field | Change | Notes |
|---|---|---|
| `chunkId` | Unchanged | Still sequential, 0-indexed, unique within the document (FR-008). |
| `pageNumber` | Unchanged *rule*, clarified *edge case* | Still a single page number per chunk (FR-004). For a chunk carrying one or two cross-page excerpts, this is now explicitly defined as the **anchor page** — the page whose own reading-context loop produced the chunk (FR-004/FR-010, Clarifications Session 2026-08-21) — rather than left implicit as "the page it came from." In the ordinary case this is unchanged from today's behavior. |
| `text` | **Content only**, not type | May now include a short excerpt from an adjacent page's own text prepended (a page's first chunk) and/or appended (a page's last chunk), in addition to the page's own text. Still a plain `String`; still decoded via the same `Encoding` used to produce it (research.md Decision 1/2). |

## New internal (non-persisted) concept: cross-page excerpt

Not a class or a stored field — a token-count-bounded slice of a neighboring page's own token
array, computed and consumed entirely inside `Chunker.chunk()` (research.md Decision 1–3):

| Concept | Definition |
|---|---|
| Trailing excerpt | Up to `OVERLAP_TOKENS` (63) tokens taken from the **end** of the nearest preceding non-blank page's own tokens; prepended to a page's first window. Empty if no such page exists. |
| Lead-in excerpt | Up to `OVERLAP_TOKENS` (63) tokens taken from the **start** of the nearest following non-blank page's own tokens; appended to a page's last window. Empty if no such page exists. |

Both are derived exclusively from a neighbor's *own* tokens (never from an already-excerpt-extended
neighbor window), so excerpts never compound across more than one page boundary (research.md
Decision 1).

## Relationships (unchanged)

`ChunkDraft` → one `Document` (via the ingestion pipeline's existing write path) → zero-or-more
`ChunkDraft`s per `Document`, each embedded into one `EmbeddedChunk` and written to one `chunks`
row. This feature does not add, remove, or reshape any of these relationships.

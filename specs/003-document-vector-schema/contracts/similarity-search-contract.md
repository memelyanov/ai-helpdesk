# Contract: similarity-search query shape

**Feature**: [Document & Vector Storage Schema](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

This feature defines no `/chat` or search endpoint — that belongs to a future query-pipeline
feature (poc-concept.md §5.2). What it does define is the query shape that feature MUST be able to
run against this schema to satisfy FR-009 and User Story 3, so that feature's design does not have
to renegotiate the table layout.

## The query

```sql
SELECT
    c.document_id,
    c.chunk_id,
    c.source_filename,
    c.page_number,
    c.text,
    c.embedding <=> :query_vector AS distance
FROM chunks c
ORDER BY c.embedding <=> :query_vector
LIMIT :k;
```

- `<=>` is pgvector's cosine-distance operator — matches the constitution's Query Pipeline section
  ("top-K nearest chunks by cosine similarity"). Lower `distance` is a closer match.
- `:query_vector` MUST be produced by the same embedding deployment used at ingestion time
  (constitution Principle V) — this schema enforces the *dimension* (1536) matches, but cannot
  enforce that the *deployment* matches; that discipline belongs to the caller.
- `:k` is the caller's top-K (constitution default `K=4`).

## What every result row guarantees (User Story 3 / FR-009)

- `document_id` is present on every row and always resolves to a live `documents` row — pass it
  directly to the document-retrieval path in
  [document-schema.md](document-schema.md) to fetch the full source document. No join, no lookup
  table, no extra query needed to make this connection.
- `source_filename` and `page_number` are present on every row without a join, satisfying the
  constitution's "exact-match columns for metadata filtering" requirement — a caller MAY add
  `WHERE c.source_filename = :filter` to the query above to scope a search to one document, without
  touching `documents` at all.
- Multiple result rows sharing the same `document_id` (a document contributing more than one chunk
  to the same top-K result set) all report that same `document_id` — a caller collecting distinct
  source documents for citation can safely deduplicate on it.

## What this contract does not cover

- Similarity-score thresholding (the constitution's "below 0.5 cosine similarity → not in
  documentation" rule) is a decision made by the caller using the `distance` column this query
  already returns; this schema does not enforce a threshold itself.
- Result ranking beyond distance order (e.g. recency, per-document diversity) is caller
  responsibility, not a schema concern.

# Contract: `chunks` table

**Feature**: [Document & Vector Storage Schema](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

What any future consumer — an ingestion feature writing embedded chunks, a retrieval/chat feature
reading them via similarity search — may rely on when it uses the `chunks` table.

## Guaranteed shape

```sql
CREATE TABLE chunks (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id      UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_id         INTEGER NOT NULL CHECK (chunk_id >= 0),
    source_filename  TEXT NOT NULL,
    page_number      INTEGER CHECK (page_number IS NULL OR page_number > 0),
    text             TEXT NOT NULL CHECK (length(text) > 0),
    embedding        VECTOR(1536) NOT NULL,
    UNIQUE (document_id, chunk_id)
);
```

## Guarantees a writer (future ingestion feature) can rely on

- Insert `document_id`, `chunk_id`, `source_filename`, `page_number` (or `NULL`), `text`, and
  `embedding` together, in one row, in one statement — `id` is assigned by the database.
- `document_id` MUST reference an existing row in `documents`; the foreign key rejects orphaned
  inserts rather than allowing them and requiring a later integrity sweep (FR-007).
- `chunk_id` only needs to be unique **within the same `document_id`** (FR-012) — chunk numbering
  restarts at `0` for every new document without colliding with any other document's chunks.
  A second insert with a `(document_id, chunk_id)` pair already used raises a uniqueness violation.
- `source_filename` is expected to be a **denormalized copy** of the owning `documents.filename` at
  the time the chunk is written, not a live reference — this is intentional (research Decision 4)
  so that similarity-search queries can filter/display it without a join. A writer that later
  renames a document (not a capability this feature defines) would need to update existing chunk
  rows to keep this in sync; no such rename capability exists yet.
- `page_number` MUST be omitted (`NULL`) for chunks from documents with no page structure (e.g.
  `.txt` sources) — this is the one documented "no page" convention (FR-008); do not write `0` or
  any other sentinel.
- `embedding` MUST be a 1536-dimension vector, matching the constitution's mandated
  `text-embedding-3-small` deployment. Vectors of any other dimension are rejected by the column
  type itself — this is what satisfies FR-016 ("every embedding vector has the same
  dimensionality"): it is not a discipline the writer must maintain, it is unrepresentable to break.
- All chunks for a document MUST be written with `text` and `embedding` populated together — this
  schema has no representation for a chunk whose text exists but whose embedding does not yet.
- **All of a document's chunk rows MUST be written inside one transaction** (FR-017): issue every
  `INSERT` for that document's chunk set, then commit once; if any insert in the set fails, the
  writer MUST roll back the whole transaction rather than leave a partial chunk set committed. This
  is the one guarantee in this contract that DDL alone cannot enforce — it is the writer's
  responsibility, stated here explicitly so a future ingestion feature does not have to rediscover
  it.

## Guarantees a reader (future retrieval/chat feature) can rely on

- Every row returned by a similarity search already carries `document_id`, `source_filename`, and
  `page_number` — no join against `documents` is required to satisfy FR-009 (resolve a search hit
  to its source document) or to display a citation. See
  [similarity-search-contract.md](similarity-search-contract.md) for the query shape.
- `document_id` on any `chunks` row is guaranteed, by the foreign key, to resolve to exactly one
  live row in `documents` — a reader never needs to defensively handle a dangling reference
  (FR-007, SC-004).
- If a document is deleted, every `chunks` row that referenced it disappears in the same
  transaction (FR-011) — a reader will never see a chunk survive its source document's deletion.

## Non-guarantees (explicitly out of scope)

- No ANN index exists on `embedding` yet (research Decision 8) — queries are exact, not
  approximate, at current corpus scale. A reader must not assume index-backed sub-linear query
  time; it is not yet needed at PoC volume, but a future feature revisiting this decision may add
  one without changing this contract's column shape.
- No re-embedding / versioning column exists. Changing the embedding deployment invalidates
  existing rows (constitution Principle V) — the mechanism for detecting and replacing stale
  vectors belongs to a future ingestion feature, not this schema.

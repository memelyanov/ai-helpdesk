# Contract: `documents` table

**Feature**: [Document & Vector Storage Schema](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

What any future consumer — an ingestion endpoint that writes rows, a download endpoint that reads
them, a chat/retrieval feature that joins against `chunk_id` — may rely on when it uses the
`documents` table. This is the interface this feature hands off; changing any guarantee below is a
breaking change to every feature built on top of it.

## Guaranteed shape

```sql
CREATE TABLE documents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename      TEXT NOT NULL,
    content_type  TEXT NOT NULL CHECK (content_type IN ('text/plain', 'application/pdf')),
    content       BYTEA NOT NULL CHECK (octet_length(content) > 0),
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## Guarantees a writer (future ingestion feature) can rely on

- Insert `filename`, `content_type`, and `content`; `id` and `uploaded_at` are assigned by the
  database — do not supply them unless a specific value is required for a test fixture.
- `content_type` MUST be exactly `text/plain` or `application/pdf`; any other value — and therefore
  any file that is neither `.txt` nor `.pdf` — is rejected at insert time by the `CHECK` constraint
  (FR-015), not silently accepted or coerced.
- Duplicate `filename` values are permitted — a second upload of `travel-expense-policy.pdf`
  produces a second row with a new `id` (FR-013). Filename is not, and MUST NOT be treated as, a
  unique key.
- A document is deleted with an ordinary `DELETE FROM documents WHERE id = :id` (FR-014) — no
  special deletion procedure or soft-delete flag exists.
- Deleting a `documents` row cascades to every `chunks` row referencing it (FR-011) — a writer
  never needs to delete a document's chunks first.

## Guarantees a reader (future download endpoint) can rely on

- `SELECT content, filename, content_type FROM documents WHERE id = :id` returns content that is
  byte-for-byte identical to what was originally inserted (FR-003, SC-001) — no transformation,
  re-encoding, or normalization happens to `content` between write and read.
- A missing `id` returns zero rows — it is the caller's responsibility to turn that into a 404 or
  equivalent; the table itself does not distinguish "never existed" from "was deleted," and callers
  MUST NOT try to recover that distinction from this table (spec.md User Story 1, Scenario 3).
- A document with zero associated `chunks` rows is a normal, expected, permanently valid state
  (FR-010) — a reader MUST NOT treat "no chunks" as a signal that the document is incomplete or
  should be hidden.

## Non-guarantees (explicitly out of scope)

- No column expresses ingestion/chunking status. A reader cannot tell, from this table alone,
  whether a document has been chunked yet — that requires checking for `chunks` rows, and even
  their absence is not an error state (see above).
- No access-control column exists. Any row is retrievable by any caller who has its `id`; this
  matches the constitution's current out-of-scope stance on authentication/authorization.

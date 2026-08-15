-- Creates the documents and chunks tables (feature 003: Document & Vector Storage Schema).
-- Depends on 01-init-vector.sql having already enabled the `vector` extension in this database.
-- Runs automatically only when the container's data directory is empty; a changed copy of this
-- file has no effect on an already-initialised volume — see
-- specs/003-document-vector-schema/quickstart.md "Stale-volume warning" (same trap as
-- 01-init-vector.sql, FR-024 in feature 001).

-- The original uploaded file, stored in full (FR-001, FR-003, FR-014, FR-015).
-- See specs/003-document-vector-schema/contracts/document-schema.md for the guarantees this
-- shape provides to writers and readers.
CREATE TABLE documents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename      TEXT NOT NULL,
    content_type  TEXT NOT NULL CHECK (content_type IN ('text/plain', 'application/pdf')),
    content       BYTEA NOT NULL CHECK (octet_length(content) > 0),
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One embedded, searchable segment of a document's text (FR-006–FR-008, FR-011, FR-012, FR-016).
-- `source_filename`/`page_number` are denormalized copies of the owning document's data, written
-- once at chunk-insert time, so a similarity-search query never needs a join to `documents` for
-- exact-match metadata filtering (constitution "Chunking & Embedding Strategy").
-- See specs/003-document-vector-schema/contracts/chunk-schema.md for the guarantees this shape
-- provides to writers and readers.
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

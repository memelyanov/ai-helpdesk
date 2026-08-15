# Phase 1 Data Model: Document Ingestion Endpoint

**Date**: 2026-08-15 | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

This feature introduces **no new persisted entities and no schema change**. It is the first writer
of the `Document` and `Chunk` tables `specs/003-document-vector-schema/data-model.md` already fully
specifies — every row this feature inserts must satisfy that data model's validation rules and the
guarantees in [document-schema.md](../003-document-vector-schema/contracts/document-schema.md) and
[chunk-schema.md](../003-document-vector-schema/contracts/chunk-schema.md) unchanged. This document
covers only the request/response shapes at the API boundary, plus the in-memory pipeline shapes
between them — none of which are stored.

## Request: document upload

One `multipart/form-data` request to `POST /documents`, a single part named `file`.

| Field | Type | Constraint |
|---|---|---|
| `file` | file part | required, with a non-empty `filename`; content type MUST resolve to `.txt` or `.pdf`, checked from content (FR-002); size MUST be > 0 and ≤ 20 MB (FR-003 — the single source of truth for that number) |

Any other form field present is ignored, not rejected (contracts/ingestion-api-contract.md). Batch
upload (multiple files in one request) is out of scope (spec Assumptions).

## Response: `DocumentIngestionResponse` (success, `201 Created`)

| Field | Type | Meaning |
|---|---|---|
| `documentId` | string (UUID) | The `documents.id` assigned to the new row (FR-010) — identical to the value a caller would use to fetch the stored document per `document-schema.md` |
| `chunkCount` | integer | Number of `chunks` rows written for this document (FR-010); `0` is valid and expected for a document with no extractable text (FR-015) |

## Response: `IngestionErrorResponse` (failure, `400` or `503`)

| Field | Type | Meaning |
|---|---|---|
| `error` | string | Machine-readable code, one of: `unsupported_type`, `invalid_file` (empty or oversized), `unparseable` (parsing failed), `provider_unconfigured`, `processing_failed` (embedding or database failure) — see [contracts/ingestion-api-contract.md](contracts/ingestion-api-contract.md) for the full list and status-code mapping |
| `message` | string | Human-readable explanation (FR-002/003/005/011) — MUST NOT contain an API key, endpoint, or deployment name (FR-014) |

## In-memory pipeline shapes (not persisted, not serialized to any API consumer)

These exist only inside `IngestionService` between pipeline steps (research Decision 5); they are
documented here because their shape drives what the chunker and writer need to agree on, not
because any contract depends on their exact Java representation.

- **`ExtractedPage`**: one page's text (`.pdf`) or the whole document's text as a single instance
  (`.txt`, no page structure) — `pageNumber` (nullable int, 1-indexed) + `text` (string). Output of
  `TextExtractor` (research Decision 2).
- **`ChunkDraft`**: one candidate chunk before embedding — `chunkId` (int, 0-indexed, sequential
  within the document), `pageNumber` (nullable int, carried from its source `ExtractedPage`),
  `text` (string, 500–1000 tokens per interior chunk, shorter allowed only for a document's final
  or sole chunk — research Decision 3). Output of `Chunker`.
- **`EmbeddedChunk`**: a `ChunkDraft` plus its `embedding` (`float[1536]`) once the batched
  embedding call — or calls, if the document's chunk count needed sub-batching, research Decision 4 —
  returns. This is the only shape `DocumentRepository` accepts
  for the chunk-insert step — there is no representation of a chunk without its vector, matching
  `chunk-schema.md`'s "no chunk whose text exists but embedding does not" guarantee.

## Relationship to the stored schema

```text
multipart upload
      │
      ▼
TextExtractor  ──▶  ExtractedPage[]        (in memory)
      │
      ▼
Chunker        ──▶  ChunkDraft[]           (in memory)
      │
      ▼
EmbeddingClient──▶  EmbeddedChunk[]        (in memory; one batched call, sub-batched only if needed — research Decision 4)
      │
      ▼
DocumentRepository  ──▶  documents (1 row) + chunks (N rows, N ≥ 0)   ← ONE transaction, feature 003's schema
      │                                                                  (research Decision 5)
      ▼
DocumentIngestionResponse { documentId, chunkCount }
```

No `ExtractedPage`, `ChunkDraft`, or `EmbeddedChunk` value is ever written to a database or returned
to a caller individually — only the final counts and the `documents`/`chunks` rows feature 003
already defines.

## Out of scope for this feature's data model

- **Re-parsing or re-chunking an existing document**: not a capability this feature defines; a
  second upload of the same content produces an entirely independent document (FR-012), not an
  update to the first.
- **Any status/progress representation**: the endpoint is synchronous (spec Assumption); there is
  no "ingestion in progress" state to model, request/response is the entire lifecycle from the
  caller's point of view.

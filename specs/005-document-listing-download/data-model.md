# Phase 1 Data Model: Document Listing and Download Endpoints

**Date**: 2026-08-15 | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

This feature introduces **no new persisted tables or columns**. It reads the `documents`/`chunks`
schema [specs/003-document-vector-schema/data-model.md](../003-document-vector-schema/data-model.md)
defines and feature 004 first populated — unchanged. Everything below is a request/response or
internal in-memory shape, mirroring how
[specs/004-document-ingestion-endpoint/data-model.md](../004-document-ingestion-endpoint/data-model.md)
documented its own DTOs against the same, unmodified schema.

## Response shape: `DocumentSummaryResponse`

One entry in `GET /documents`'s JSON array. Maps directly to spec.md FR-002.

| Field | Type | Source | Requirement |
|---|---|---|---|
| `documentId` | `UUID` | `documents.id` | FR-002 — the identifier a caller uses against the download endpoint |
| `filename` | `String` | `documents.filename` | FR-002 — original filename, verbatim (feature 004 FR-017) |
| `contentType` | `String` | `documents.content_type` | FR-002 — `text/plain` or `application/pdf` (schema `CHECK`, feature 003) |
| `uploadedAt` | `OffsetDateTime` | `documents.uploaded_at` | FR-002 — when the document was ingested |
| `chunkCount` | `long` | `COUNT(chunks.id)` grouped by `document_id` | FR-002/FR-003 — `0` is a valid, expected value (feature 004 FR-015), never omitted or nulled |

**Never included**: chunk `text` or `embedding` content (FR-012 — this feature's list response is a
summary, not a chunk browser).

## Internal carrier: `DocumentContent`

Not a JSON DTO — an internal record passed from `DocumentQueryRepository` to `DocumentController`
for the download response, since a download's "shape" is HTTP headers + a raw byte body, not JSON.

| Field | Type | Source |
|---|---|---|
| `filename` | `String` | `documents.filename` |
| `contentType` | `String` | `documents.content_type` |
| `content` | `byte[]` | `documents.content` |

`DocumentQueryRepository.findContentById(UUID id)` returns `Optional<DocumentContent>` — empty when
no `documents` row matches `id`, which `DocumentController` maps to `DocumentNotFoundException`
(→ `404 document_not_found`, research Decision 4).

## Response shape: `DocumentErrorResponse` (renamed from `IngestionErrorResponse`, research Decision 6)

Unchanged shape from feature 004 — `{ error: String, message: String }` — now documented as the
shared error body for all three `/documents` endpoints, not `POST /documents` alone:

| `error` | HTTP status | Endpoint | Cause |
|---|---|---|---|
| `unsupported_type`, `invalid_file`, `unparseable`, `provider_unconfigured`, `processing_failed` | `400`/`503` | `POST /documents` | Unchanged from feature 004 — see [ingestion-api-contract.md](../004-document-ingestion-endpoint/contracts/ingestion-api-contract.md) |
| `document_not_found` | `404` | `GET /documents/{id}/content` | This feature — the id does not resolve to any stored document (malformed or nonexistent, research Decision 4) |

## Relationship to feature 003's schema

```text
documents (1) ──────< (many) chunks
   id  ◄──────────────  document_id

GET /documents            → reads: documents.*, COUNT(chunks.id) GROUP BY documents.id
GET /documents/{id}/content → reads: documents.filename, documents.content_type, documents.content
```

Both queries are plain `SELECT`s against feature 003's existing tables — no lock, no write, no
schema migration. Neither endpoint is affected by, nor affects, the `ON DELETE CASCADE` relationship
those tables already define; this feature adds no delete capability (spec.md Assumptions).

## Out of scope for this feature's data model

- **Pagination metadata** (total count, page tokens): no pagination this feature (spec.md
  Assumptions) — `DocumentSummaryResponse` carries no page-related fields to omit or leave null.
- **Chunk-level response shapes**: `GET /documents` reports only a count; retrieving individual
  chunk text/embeddings is out of scope (FR-012) and has no DTO here.
- **Access-control / ownership fields**: consistent with feature 004 and the constitution's
  current PoC-phase scope — no "who uploaded this" or "who may see this" field exists anywhere in
  this feature's shapes.

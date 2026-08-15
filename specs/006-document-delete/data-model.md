# Phase 1 Data Model: Document Deletion Endpoint

**Date**: 2026-08-16 | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

This feature introduces **no new persisted tables or columns**. It performs the first-ever
caller-triggered write against feature 003's existing `chunks.document_id ... ON DELETE CASCADE`
relationship (FR-011) — a schema guarantee that has existed since feature 003 but that no feature
has exercised until now. Everything below is a request/internal shape, mirroring how features
004/005 documented their own DTOs against the same, unmodified schema.

## Request: no body, one path parameter

`DELETE /documents/{id}` — `{id}` is the same `documentId` a caller obtained from `POST /documents`
(feature 004) or `GET /documents` (feature 005). No request body, no query parameters.

## Success response: `204 No Content`, no body

Deletion has nothing to report back beyond "it happened" (FR-006), and the caller already knows
which id it deleted (it supplied it) — so, unlike `POST /documents`'s `DocumentIngestionResponse` or
`GET /documents`'s `DocumentSummaryResponse` list, no new response DTO is introduced. `204 No
Content` itself is the success signal, structurally impossible to confuse with the `404` (not found)
or `503` (deletion failed) error responses below.

## Response shape: `DocumentErrorResponse` (unchanged shape, one new `error` value)

Reuses the existing shared `{ error: String, message: String }` body (feature 005 Decision 6) — no
new DTO. This feature adds one new row to the table `DocumentErrorResponse`'s Javadoc already
documents:

| `error` | HTTP status | Endpoint | Cause |
|---|---|---|---|
| `unsupported_type`, `invalid_file`, `unparseable`, `provider_unconfigured`, `processing_failed` | `400`/`503` | `POST /documents` | Unchanged from feature 004 |
| `document_not_found` | `404` | `GET /documents/{id}/content`, and now `DELETE /documents/{id}` | The id does not resolve to any stored document — malformed, never issued, or already deleted (research Decision 3; FR-005/FR-008) |
| `deletion_failed` | `503` | `DELETE /documents/{id}` (this feature) | The id names an existing document, but an unexpected server-side failure prevented its deletion from completing (research Decision 6; FR-010) |

## Internal exception: `DocumentDeletionException`

Not a JSON shape — the internal signal `DocumentRepository.deleteById` throws on an unexpected
failure, which `DocumentErrorHandler` maps to the `deletion_failed` row above (research Decision 6).

| Field | Type | Purpose |
|---|---|---|
| `errorCode` | `String` | Always `"deletion_failed"` — fixed, not derived from the underlying cause, so no internal detail (e.g. a driver-level message) ever reaches the response body |
| `message` | `String` | Fixed, code-reviewed text (same "no credential value in an error response" discipline as every other exception in this package) |
| `cause` | `Throwable` | The underlying JDBC failure, logged server-side only, never included in the response |

## `DocumentRepository.deleteById(UUID id)`: return shape

Not a DTO — an internal `boolean` (or equivalent) the repository returns to `DocumentController`:

| Return value | Meaning | Controller outcome |
|---|---|---|
| `true` | Exactly one `documents` row (and, by cascade, every one of its `chunks` rows) was deleted | `204 No Content` |
| `false` | No `documents` row matched `id` — it never existed or was already deleted | `DocumentNotFoundException` → `404 document_not_found` |
| *(throws)* `DocumentDeletionException` | The `DELETE` statement itself failed unexpectedly (e.g. the database became unreachable mid-operation) — no row was deleted (statement-level atomicity, research Decision 5) | `503 deletion_failed`, propagated by `DocumentErrorHandler` |

## Relationship to feature 003's schema

```text
documents (1) ──────< (many) chunks
   id  ◄──────────────  document_id   (ON DELETE CASCADE, feature 003 FR-011)

DELETE /documents/{id} → DELETE FROM documents WHERE id = ?
                          (chunks rows removed automatically by the database, same statement)
```

This is the first feature to trigger that cascade relationship — feature 003 defined it, features
004/005 never exercised it (both are pure reads or pure inserts). No schema migration, no new
column, no new index: the existing primary key on `documents.id` and the existing foreign key on
`chunks.document_id` are exactly what this feature's single `DELETE ... WHERE id = ?` needs.

## Out of scope for this feature's data model

- **Soft-delete / trash fields**: no `deleted_at`, `is_deleted`, or similar column is added anywhere
  — deletion is a hard, permanent row removal (spec.md FR-009, Assumptions).
- **Audit/history of deletions**: no "who deleted what, when" record is created — consistent with
  the constitution's current no-authentication PoC-phase scope (spec.md Assumptions), the same scope
  features 004/005 already operate under.
- **Bulk-delete request shape**: no array-of-ids body or batch response — one document per request
  (spec.md Assumptions).

# Contract: `DELETE /documents/{id}`

**Feature**: [Document Deletion Endpoint](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

What a caller — the future Angular document-browse view (`poc-concept.md` §10 item 7), a test
harness, or a manual `curl`/HTTP client in the meantime — can rely on. Together with
[../../004-document-ingestion-endpoint/contracts/ingestion-api-contract.md](../../004-document-ingestion-endpoint/contracts/ingestion-api-contract.md)
and
[../../005-document-listing-download/contracts/document-query-api-contract.md](../../005-document-listing-download/contracts/document-query-api-contract.md),
this is the complete contract for the `/documents` resource.

## `DELETE /documents/{id}` — permanently delete a document and its chunks

### Request

```
DELETE /documents/{id}
```

- `{id}` is the `documentId` a caller obtained from `POST /documents` (feature 004) or
  `GET /documents` (feature 005).
- No request body, no query parameters.

### Success response

```
204 No Content
```

- No response body — the `204` status itself is the confirmation (FR-006), structurally impossible
  to confuse with either error response below.
- The identified document row and every `chunks` row that referenced it are gone, in the same
  atomic operation (FR-002, FR-011 via feature 003's `ON DELETE CASCADE`).
- Takes effect immediately: the very next `GET /documents` call no longer lists the document, and
  the very next `GET /documents/{id}/content` call for the same id returns the `404` response below
  (FR-003, SC-001).
- A document with `chunkCount: 0` (feature 004's zero-chunk outcome) is deleted exactly the same way
  as any other document — chunk count has no bearing on deletability (FR-004).

### Error response — not found

```
404 Not Found
Content-Type: application/json

{
  "error": "document_not_found",
  "message": "No document exists with the given id."
}
```

- Returned when `{id}` is not a valid identifier at all, when it is well-formed but does not match
  any stored document, **and** when it names a document that was already deleted (FR-005, FR-008) —
  a caller sees one consistent outcome in all three cases and never needs to distinguish them to
  know the document isn't there. This is the identical outcome and response shape
  `GET /documents/{id}/content` already returns for the same three cases (feature 005's
  `document-query-api-contract.md`, research Decision 3).
- Nothing is deleted when this response is returned.

### Error response — deletion failed

```
503 Service Unavailable
Content-Type: application/json

{
  "error": "deletion_failed",
  "message": "Failed to delete the document."
}
```

- Returned only when `{id}` names a document that does exist, but an unexpected server-side failure
  (for example, the database became unreachable mid-operation) prevented the deletion from
  completing (spec.md Clarifications, Session 2026-08-16; FR-010).
- The document and every one of its chunks are guaranteed to remain exactly as they were beforehand
  — no partial deletion is possible (research Decision 5's single-statement atomicity guarantee). A
  caller may safely retry the identical request.
- As with every other `/documents` error response, `message` is a human-readable string a caller
  MUST NOT parse to decide anything — the HTTP status (`503`) and the `error` code together are the
  full, stable contract.

## Non-guarantees (explicitly out of scope)

- **No recovery/undelete** — once a `204` response is received, the document is permanently gone;
  no soft-delete, trash, or restore capability exists anywhere in this system (FR-009).
- **No bulk delete** — one document per request; no array-of-ids request body or batch response
  shape (spec.md Assumptions).
- **No authentication/authorization** — consistent with features 004 and 005, any caller that can
  reach this endpoint may delete any document.
- **No special "already deleted" error** — requesting deletion of an id that was already deleted
  returns the exact same `404 document_not_found` as an id that was never issued (FR-008); it is
  never reported as a distinct outcome or as a success.

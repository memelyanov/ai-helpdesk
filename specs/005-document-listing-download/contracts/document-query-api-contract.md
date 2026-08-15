# Contract: `GET /documents` and `GET /documents/{id}/content`

**Feature**: [Document Listing and Download Endpoints](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

What a caller — the future Angular document-browse view (`poc-concept.md` §10 item 7), a test
harness, or a manual `curl`/HTTP client in the meantime — can rely on. Together with
[../../004-document-ingestion-endpoint/contracts/ingestion-api-contract.md](../../004-document-ingestion-endpoint/contracts/ingestion-api-contract.md),
this is the complete contract for the `/documents` resource.

## `GET /documents` — list every ingested document

### Request

```
GET /documents
```

No path, query, or body parameters. No filtering, sorting override, or pagination controls exist
(spec.md Assumptions — deferred until a real usage pattern justifies them).

### Response

```
200 OK
Content-Type: application/json

[
  {
    "documentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "filename": "travel-expense-policy.pdf",
    "contentType": "application/pdf",
    "uploadedAt": "2026-08-15T14:32:07.123Z",
    "chunkCount": 12
  },
  {
    "documentId": "9c858901-8a57-4791-81fe-4c455b099bc9",
    "filename": "blank-upload.txt",
    "contentType": "text/plain",
    "uploadedAt": "2026-08-15T14:20:00.000Z",
    "chunkCount": 0
  }
]
```

- Always `200 OK` — an empty corpus returns `200 OK` with `[]`, never an error (FR-006). A
  non-empty-corpus failure to produce the list (e.g. an unexpected server-side error) is reported as
  an error response, never as an empty list (FR-006).
- Entries are ordered most-recently-uploaded first (`uploadedAt` descending; FR-005); when two entries
  share an identical `uploadedAt`, their relative order is still deterministic between identical calls
  (a secondary, well-defined tiebreak), never arbitrary.
- `chunkCount: 0` is a normal entry, not an error state or an omitted document (FR-003) —
  corresponds to feature 004's FR-015 zero-extractable-text outcome.
- Two entries may share the same `filename` (feature 004 FR-012 allows independent re-uploads); each
  still has its own `documentId`, own `uploadedAt`, and own `chunkCount`.
- This response never includes chunk `text` or `embedding` content (FR-012) — only the five summary
  fields shown above.
- Reflects every document whose ingestion has already committed, with no delay (FR-004) — a document
  still mid-upload (feature 004's atomic ingestion in progress) simply does not appear yet; there is
  no partial or placeholder entry for it.

## `GET /documents/{id}/content` — download a document's original file

### Request

```
GET /documents/{id}/content
```

- `{id}` is the `documentId` a caller obtained from `GET /documents` or from `POST /documents`'s
  success response (both feature 004 and this feature's list endpoint use the same identifier).

### Success response

```
200 OK
Content-Type: <the document's stored content type — text/plain or application/pdf>
Content-Disposition: attachment; filename="<original filename>"

<original file bytes, byte-for-byte identical to what was uploaded>
```

- The body is exactly `documents.content` (FR-008) — no re-encoding, no reformatting.
- `Content-Type` is always exactly `text/plain` or `application/pdf` — the schema's own `CHECK`
  constraint (feature 003) makes any other value unrepresentable (FR-009).
- `Content-Disposition` carries the original filename so a browser or HTTP client saves/displays it
  correctly without the caller needing prior knowledge of the file (FR-009) — the filename is encoded
  safely enough that no character it might contain (e.g. a quotation mark or control character,
  feature 004 FR-017's verbatim-storage guarantee) can corrupt or be misinterpreted within the header
  itself.
- A document with `chunkCount: 0` in the list is downloadable exactly the same as any other — chunk
  count has no bearing on download availability (FR-011).

### Error response

```
404 Not Found
Content-Type: application/json

{
  "error": "document_not_found",
  "message": "No document exists with the given id."
}
```

- Returned both when `{id}` is not a valid identifier at all, and when it is well-formed but does
  not match any stored document (research Decision 4) — a caller sees one consistent outcome either
  way and never needs to distinguish the two to know what to do next: the requested document is not
  retrievable (FR-010).
- No `Content-Disposition` header is present on this response — that header only ever accompanies a
  successful file body, never an error body.
- As with `POST /documents`'s error responses, `message` is a human-readable string a caller MUST NOT
  parse to decide anything — the HTTP status (`404`) and the `error` code together are the full,
  stable contract; only `message`'s *content* is guaranteed (never a credential value, FR-014), not
  its exact wording.

## Non-guarantees (explicitly out of scope)

- **No pagination, filtering, or sorting override** on `GET /documents` — the full corpus is
  returned in one response every time (spec.md Assumptions).
- **No partial/range downloads** (HTTP `Range` requests) on `GET /documents/{id}/content` — the
  full file is always returned in one response body.
- **No authentication/authorization** on either endpoint (spec.md Assumptions, constitution's
  current PoC-phase scope) — any caller that can reach these endpoints may list or download any
  document.
- **No delete, update, or versioning semantics** — both endpoints are read-only; neither creates,
  modifies, nor removes any row (spec.md Assumptions).

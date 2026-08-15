# Contract: `POST /documents`

**Feature**: [Document Ingestion Endpoint](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

What a caller — the future Angular upload view (`poc-concept.md` §10 item 7), a test harness, or a
manual `curl`/HTTP client in the meantime — can rely on when it calls this endpoint. Changing any
guarantee below is a breaking change to whatever is built against it next.

## Request

```
POST /documents
Content-Type: multipart/form-data; boundary=...

--boundary
Content-Disposition: form-data; name="file"; filename="travel-expense-policy.pdf"
Content-Type: application/pdf

<file bytes>
--boundary--
```

- Exactly one `file` part per request (FR-001). A request with zero or more than one `file` part, or
  a `file` part with no `filename`, is rejected (`400`, `invalid_file`) without inspecting content.
- Any additional form fields beyond `file` are **ignored**, not rejected — this keeps the contract
  stable if a future caller (e.g. the eventual frontend upload view) needs to add optional metadata
  without a breaking change to existing callers that send only `file`.
- File type is resolved from content, not trusted from the filename extension alone — an upload
  whose actual content is not `text/plain` or `application/pdf` is rejected regardless of what
  `filename` claims (FR-002).
- Maximum size: **20 MB — see spec.md FR-003, the single source of truth for this value.** A larger
  upload is rejected before any parsing is attempted (Edge Cases).
- **Validation order**: the size/malformed-request check (FR-003) runs first, then the type check
  (FR-002), then parsing (FR-005) — each check only runs once the ones before it have passed. A file
  that is both oversized and an unsupported type is reported `invalid_file`, never `unsupported_type`
  (spec Edge Cases).

## Success response

```
201 Created
Content-Type: application/json

{
  "documentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "chunkCount": 12
}
```

- `documentId` is the same identifier a caller would use against a future document-retrieval
  endpoint (built on [document-schema.md](../../003-document-vector-schema/contracts/document-schema.md)).
- `chunkCount` is `0` for a document that parsed successfully but had no extractable text (FR-015)
  — this is a success response, not an error, even though the document is not yet searchable.
- By the time this response is returned, the document and every one of its chunks are already
  committed and visible to any reader (FR-013) — there is no follow-up "is it ready yet" call to
  make (spec Assumption: synchronous processing).

## Error responses

Every response this endpoint returns — the `201` success response above and every error response
below, with no exception — carries `Content-Type: application/json`. Error responses share one
shape:

```
Content-Type: application/json

{
  "error": "<code>",
  "message": "<human-readable, no credentials — FR-014>"
}
```

| HTTP status | `error` code | Cause | Retry guidance for the caller |
|---|---|---|---|
| `400` | `unsupported_type` | File is neither `.txt` nor `.pdf` by content (FR-002) | Do not retry the same file |
| `400` | `invalid_file` | File is empty, exceeds 20 MB, or the request is malformed (missing/duplicate `file` part, or a `file` part with no filename) (FR-003) | Do not retry the same file |
| `400` | `unparseable` | File claims a supported type but Tika cannot parse it (e.g. corrupted `.pdf`) (FR-005) | Do not retry the same file |
| `503` | `provider_unconfigured` | Azure OpenAI embedding configuration is absent or incomplete (research Decision 6) | Retry once an operator configures the deployment — not a caller-fixable input problem |
| `503` | `processing_failed` | The embedding call or the database write failed for an otherwise-valid document (FR-009's failure case) | Retry the identical upload; per FR-009 no partial data was left behind, so a retry is safe and not a duplicate |

This table is FR-011's contract: `4xx` always means "the input itself was the problem, retrying the
same file will not help"; `503` always means "the input was fine, the system could not currently
process it, retrying may help." A caller MUST NOT need to parse `message` to make that decision —
the status code alone is sufficient for the retry decision.

`provider_unconfigured` and `processing_failed` are deliberately **two different `error` codes**
sharing **one status code** (`503`): FR-011 only requires the *status code* to carry the
retry-or-not signal, and both codes agree there (retry may help for both). The `error` code exists
one level below that, for whoever is operating the system: `provider_unconfigured` says "an operator
needs to finish configuring Azure OpenAI" (no amount of caller retrying helps until that happens),
while `processing_failed` says "a transient failure occurred against a configured provider" (a
caller retry alone may succeed). A caller that only reads the status code still behaves correctly
either way; a caller or operator that reads `error` gets the more specific diagnosis.

## Non-guarantees (explicitly out of scope)

- No batch upload — one `file` part per request (spec Assumption). A caller uploading several
  documents issues several requests.
- No progress/status endpoint — the response above is the entire lifecycle; there is nothing to
  poll (spec Assumption: synchronous processing).
- No authentication/authorization on this endpoint (spec Assumption, constitution's current
  PoC-phase scope) — any caller that can reach it may upload.
- No update/versioning semantics — re-uploading a filename already in the corpus always produces a
  new, independent `documentId` (FR-012); this endpoint has no notion of "replace" or "new version
  of."

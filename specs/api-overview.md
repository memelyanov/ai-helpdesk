# API Overview

A single-page reference for every HTTP endpoint the ai-helpdesk backend exposes, across all
shipped features (001–007). This document is generated from the actual controller/DTO source, not
from the specs alone — where the two ever disagree, the source (and each feature's own
`contracts/*.md`) is authoritative and this file should be regenerated.

**Base URL**: `http://localhost:8080` (no context path). All request/response bodies are
`application/json` unless noted otherwise. There is no authentication layer (PoC scope).

A companion machine-readable spec lives alongside this file: [openapi.yaml](openapi.yaml).

## Endpoint index

| Method | Path | Purpose | Feature |
|---|---|---|---|
| GET | `/actuator/health` | Liveness + dependency status | [001](001-project-scaffolding/plan.md) |
| POST | `/documents` | Ingest a document | [004](004-document-ingestion-endpoint/plan.md) |
| GET | `/documents` | List all ingested documents | [005](005-document-listing-download/plan.md) |
| GET | `/documents/{id}/content` | Download a document's original bytes | [005](005-document-listing-download/plan.md) |
| DELETE | `/documents/{id}` | Permanently delete a document | [006](006-document-delete/plan.md) |
| POST | `/chat` | Ask a question, get a grounded, cited answer | [007](007-chat-endpoint/plan.md) |

Every error-carrying endpoint shares one two-field error shape: `{ "error": "<code>", "message":
"<human-readable>" }`. `error` codes never overlap across resources, so a client can dispatch
purely on that string; `message` is informational only and MUST NOT be parsed.

---

## `GET /actuator/health`

Spring Boot Actuator, provided by the framework — not a hand-written controller. No request body,
no path/query parameters.

**Response** `200 OK` (db reachable) or `503 Service Unavailable` (db unreachable) —
`application/vnd.spring-boot.actuator.v3+json`:

```json
{
  "status": "UP",
  "components": {
    "azureOpenAi": {
      "status": "UP",
      "details": { "configured": true, "endpointConfigured": true, "chatDeploymentConfigured": true }
    },
    "db": {
      "status": "UP",
      "details": { "database": "PostgreSQL", "validationQuery": "isValid()" }
    }
  }
}
```

- `components.db.status` is `"DOWN"` (with `details.error` naming the underlying exception) when
  the database is unreachable — overall `status` becomes `"DOWN"`, HTTP `503`.
- `components.azureOpenAi.status` is `"UNKNOWN"` (with `details.missing: [...]`) when Azure
  credentials are absent or incomplete — this does **not** pull the overall status down; `200`
  stays `200`. No network call is made to Azure to produce this.
- Actuator also auto-registers `diskSpace`, `ping`, `ssl`; their presence/shape is not guaranteed.
- The API key never appears anywhere in the payload.

Full contract: [health-api.md](001-project-scaffolding/contracts/health-api.md).

---

## `POST /documents`

Ingest a file: extract text, chunk it, embed each chunk, store document + chunks.

**Request**: `multipart/form-data`, exactly one `file` part, ≤ 20 MB, non-empty, with a filename.
Supported types: PDF, DOCX, TXT, MD (see the ingestion contract for the exact list).

**Response** `201 Created`:

```json
{ "documentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "chunkCount": 12 }
```

`chunkCount` may legitimately be `0` (a document that parsed but yielded no extractable text) —
that is a valid, non-error outcome.

**Errors**:

| Status | `error` | Meaning |
|---|---|---|
| 400 | `invalid_file` | No/duplicate `file` part, no filename, empty, or > 20 MB |
| 400 | `unsupported_type` | File extension/content type not supported |
| 400 | `unparseable` | Recognized type, but content could not be parsed |
| 503 | `provider_unconfigured` | Azure OpenAI embedding deployment not configured |
| 503 | `processing_failed` | Read/embedding/storage failure after validation passed |

Full contract: [ingestion-api-contract.md](004-document-ingestion-endpoint/contracts/ingestion-api-contract.md).

---

## `GET /documents`

List every ingested document, newest first.

**Request**: none.

**Response** `200 OK` (always — an empty corpus returns `[]`, never an error):

```json
[
  {
    "documentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "filename": "travel-expense-policy.pdf",
    "contentType": "application/pdf",
    "uploadedAt": "2026-08-14T09:15:00Z",
    "chunkCount": 12
  }
]
```

Never includes chunk `text` or `embedding` content.

Full contract: [document-query-api-contract.md](005-document-listing-download/contracts/document-query-api-contract.md).

---

## `GET /documents/{id}/content`

Download a document's original file bytes, byte-for-byte.

**Request**: `id` path parameter (any string — a malformed UUID and a well-formed-but-nonexistent
one both resolve to the same `404`).

**Response** `200 OK`: raw bytes, `Content-Type` = the stored content type, `Content-Disposition:
attachment; filename="<original filename>"`.

**Errors**:

| Status | `error` | Meaning |
|---|---|---|
| 404 | `document_not_found` | No document exists with the given id |

Full contract: [document-query-api-contract.md](005-document-listing-download/contracts/document-query-api-contract.md).

---

## `DELETE /documents/{id}`

Permanently delete a document and every chunk derived from it.

**Request**: `id` path parameter.

**Response** `204 No Content`: empty body.

**Errors**:

| Status | `error` | Meaning |
|---|---|---|
| 404 | `document_not_found` | No document exists with the given id (malformed, nonexistent, or already-deleted id — all identical) |
| 503 | `deletion_failed` | The id names an existing document, but deletion failed server-side |

Full contract: [document-delete-api-contract.md](006-document-delete/contracts/document-delete-api-contract.md).

---

## `POST /chat`

Ask a question; get an answer grounded only in retrieved passages that meet the similarity
threshold, with deterministic citations — or an honest "not covered" response.

**Request**:

```json
{
  "question": "Can I expense a taxi from the airport?",
  "documentIds": ["3fa85f64-5717-4562-b3fc-2c963f66afa6"]
}
```

- `question`: required, 1–1000 characters after trimming.
- `documentIds`: optional. When present and non-empty, restricts retrieval to those documents only.
  Absent, `null`, or `[]` means "search the whole corpus." Every entry must be a valid UUID.

**Response** `200 OK` — one shape for both outcomes:

Grounded answer:

```json
{
  "answer": "Yes, taxis are reimbursable within policy limits.",
  "sources": [
    { "documentId": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "filename": "travel-expense-policy.pdf", "page": "2", "score": 0.81 },
    { "documentId": "8b1a9953-c461-4a9a-8b1a-9953c461f9a2", "filename": "corporate-card-rules.txt", "page": "no page structure", "score": 0.63 }
  ]
}
```

"Not covered" (still `200`, never an error):

```json
{ "answer": "I don't have this information in the documentation.", "sources": [] }
```

- `sources` is empty **iff** `answer` equals the fixed not-covered string — never mixed.
- `sources[].page` is either a 1-indexed page number as a string, or the fixed string
  `"no page structure"` — never `null`, never a bare number.
- `sources[].score` is `1 − distance`, rounded to two decimals, always ≥ 0.5.
- `sources[].documentId` is the same id `GET /documents/{id}/content` and `DELETE /documents/{id}`
  accept.
- Citations are computed from retrieval results grouped by `(documentId, pageNumber)` — never
  parsed from the model's answer text.

**Errors**:

| Status | `error` | Meaning |
|---|---|---|
| 400 | `blank_question` | `question` missing, empty, or whitespace-only |
| 400 | `question_too_long` | `question` exceeds 1000 characters after trimming |
| 400 | `malformed_request` | Body is not valid JSON, or `documentIds` contains a non-UUID entry |
| 503 | `provider_unconfigured` | Azure OpenAI chat deployment not configured |
| 503 | `processing_failed` | Embedding, retrieval, or completion request failed after validation passed |

Full contract: [chat-api-contract.md](007-chat-endpoint/contracts/chat-api-contract.md).

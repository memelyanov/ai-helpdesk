# Feature Specification: Document Ingestion Endpoint

**Feature Branch**: `004-document-ingestion-endpoint`

**Created**: 2026-08-15

**Status**: Draft

**Input**: User description: "let's work on backend support (check poc-concept.md file - Next Steps- Implement the ingestion endpoint (Tika → chunking → Azure OpenAI embeddings → write into the schema from step 4)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Upload a document and make it searchable (Priority: P1)

Someone responsible for keeping the company's document corpus current uploads a single `.txt` or
`.pdf` file. In one request, the system reads the file, splits it into semantically self-contained
pieces, converts each piece into a form the search system can compare by meaning, and stores
everything so the document is immediately part of the searchable corpus. The caller receives back
confirmation of what was stored: which document this became and how many searchable pieces it was
split into.

**Why this priority**: This is the front door of the whole PoC. Without it, the vector database
designed in the previous feature has no way to receive real content, and nothing downstream (chat,
retrieval, evaluation) has anything to search.

**Independent Test**: Can be fully tested by submitting one `.txt` and one `.pdf` file to the
endpoint and confirming each produces a stored document with one or more associated searchable
pieces, without needing the chat/retrieval feature to exist yet.

**Acceptance Scenarios**:

1. **Given** a well-formed `.txt` file containing several paragraphs of policy text, **When** it
   is uploaded, **Then** the system responds with a newly-assigned document identifier and a chunk
   count greater than zero, and the document's full original content remains retrievable
   afterward.
2. **Given** a well-formed multi-page `.pdf` file, **When** it is uploaded, **Then** the system
   responds with a document identifier and a chunk count greater than zero, and the searchable
   pieces retain which page of the source document they came from.
3. **Given** a large document whose text spans many chunk-sized pieces, **When** it is uploaded,
   **Then** every piece is stored with a distinct sequence position within that document, in the
   original reading order, and consecutive pieces share a small amount of overlapping text so no
   idea is cut cleanly in half at a boundary.

---

### User Story 2 - Reject unsupported or invalid uploads cleanly (Priority: P2)

Someone attempts to upload a file the system does not support (for example, a `.docx` or `.png`
file), or a file that is empty or unreadable. Instead of silently accepting it, partially
processing it, or crashing, the system rejects the request with a clear explanation and stores
nothing.

**Why this priority**: A corpus that silently contains garbage or half-processed documents quietly
destroys the trust the whole PoC is meant to build. Rejecting bad input loudly and immediately is
cheaper to get right now than to discover during the evaluation phase.

**Independent Test**: Can be fully tested by submitting an unsupported file type and a zero-byte
file to the endpoint and confirming both are rejected with no document or chunk data left behind
afterward.

**Acceptance Scenarios**:

1. **Given** a file with an unsupported type (neither `.txt` nor `.pdf`), **When** it is uploaded,
   **Then** the system rejects the request, explains why, and creates no document record.
2. **Given** a zero-byte file, **When** it is uploaded, **Then** the system rejects the request and
   creates no document record.
3. **Given** a `.pdf` file that is corrupted and cannot be parsed, **When** it is uploaded, **Then**
   the system rejects the request with an explanation that parsing failed, and creates no document
   record.

---

### User Story 3 - No partial results when something goes wrong mid-pipeline (Priority: P3)

A document passes initial validation and text extraction, but a later step in the pipeline (for
example, generating the searchable representation for a chunk) fails partway through. The caller
needs to know the upload did not succeed, and the corpus must not end up with a document that has
only some of its pieces stored — every piece or none.

**Why this priority**: A document with half its chunks missing would be silently and invisibly
wrong: it would appear in the corpus, sometimes get found by search, and sometimes not, with no
signal to anyone that it is incomplete. This is a correctness guarantee, not a nice-to-have, but it
depends on P1 existing and ranks below P2 because a clean "reject bad input" path already prevents
most of the failure cases this story guards against — what remains is external failures (e.g. a
temporary outage of the embedding service) that can occur even on perfectly valid input.

**Independent Test**: Can be fully tested by forcing a failure partway through the pipeline (for
example, an unreachable embedding service) for an otherwise-valid document and confirming the
document and every one of its would-be chunks are absent afterward, and the caller receives an
error rather than a partial success.

**Acceptance Scenarios**:

1. **Given** a valid document whose text has been extracted successfully, **When** generating the
   searchable representation for one of its chunks fails (e.g. the embedding service is
   unreachable), **Then** the system reports the upload as failed, and neither the document nor any
   of its chunks appear in the corpus afterward.
2. **Given** the same failure scenario, **When** the caller retries the identical upload after the
   underlying failure is resolved, **Then** the retry succeeds and produces one complete, normal
   document — not a duplicate of a partial attempt, because no partial attempt was ever stored.

---

### Edge Cases

- What happens when the same file (same filename, same or different content) is uploaded more than
  once, including two uploads of byte-identical content submitted at the same instant? Each upload
  MUST produce its own independent document with its own identifier; the system MUST NOT treat
  filename or content as identity, deduplicate, merge the uploads, or overwrite the earlier one
  (consistent with the existing storage schema's resolved behavior). "At the same instant" is not a
  special case — it is two ordinary, unrelated requests.
- What happens when an uploaded file exceeds the accepted size limit (FR-003)? The request MUST be
  rejected before any parsing is attempted, with no document record created. When a file is
  simultaneously oversized **and** an unsupported type, the size check MUST be evaluated first (it
  requires no content inspection beyond the byte count already known from the request), so the
  caller receives `invalid_file` rather than `unsupported_type` in that combination.
- What happens when a `.txt` file uses a character encoding that cannot be decoded as valid text
  (e.g. bytes that are not valid UTF-8, or an unsupported legacy encoding)? A decoding failure MUST
  be treated exactly like a parse failure (FR-005): the upload is rejected as `unparseable`, and no
  text is silently stored as if the misdecoded bytes were valid content. The system MUST NOT guess
  or transliterate an encoding.
- What happens when two uploads for different documents are submitted at the same time? Each MUST
  be processed independently to completion (or failure) without interfering with the other's
  document or chunk data.
- What happens when a `.pdf` has pages with no extractable text mixed with pages that do have text
  (for example, a divider page)? Only pages that produce text contribute chunks; a page producing no
  text simply contributes none, without failing the whole upload. Page numbering for subsequent
  pages is unaffected — a text-producing page's `page_number` is always its actual 1-indexed
  position in the source document, never renumbered to account for skipped pages.
- What happens when a client disconnects, or the request times out, before the pipeline completes?
  This is indistinguishable from any other mid-pipeline failure: FR-009's all-or-nothing guarantee
  applies unchanged — no document or chunk record exists afterward, and a subsequent retry behaves
  as an ordinary new upload, not a resume.
- What happens when the multipart request itself is malformed — no `file` part present, more than
  one `file` part present, or a `file` part with no filename at all? The request MUST be rejected as
  `invalid_file` (FR-003) without attempting to parse anything, the same as an empty or oversized
  file.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a way to upload exactly one document (`.txt` or `.pdf`) per
  request.
- **FR-002**: The system MUST reject, without storing anything, any upload whose file type — determined
  from the file's actual content, not merely a filename extension or a caller-declared content type —
  is neither `.txt` nor `.pdf`, and MUST tell the caller why it was rejected. A caller cannot make an
  unsupported file accepted by naming it `report.pdf`, nor can it make a supported file rejected by
  mislabeling it.
- **FR-003**: The system MUST reject, without storing anything, any upload that is empty (zero
  bytes) or that exceeds the maximum accepted file size of **20 MB**. This check MUST run before
  FR-002's type check and before any parsing is attempted, since file size is already known from the
  request without inspecting content — so a file that is both oversized and an unsupported type is
  reported as too large, not as an unsupported type. The same rejection applies to a malformed
  request carrying no `file` part, more than one `file` part, or a `file` part with no filename (see
  Edge Cases).
- **FR-004**: The system MUST extract the readable text content of every accepted upload,
  regardless of whether it is `.txt` or `.pdf`, without the caller needing to specify which format
  it is.
- **FR-005**: The system MUST reject, without storing anything, any accepted-format upload whose
  content cannot actually be parsed (e.g. a corrupted `.pdf`), and MUST tell the caller that
  parsing failed.
- **FR-006**: The system MUST split each document's extracted text into sequential chunks of
  500–1000 tokens, with 10–15% overlap in text between consecutive chunks, preserving original
  reading order. This 500–1000 range is a target for every chunk that has a following chunk after
  it; the **last chunk of a document** (and the **only** chunk of a document shorter than 500
  tokens) MAY fall under 500 tokens, because there is no remaining text to extend it with without
  violating the same range on a neighboring chunk. This is the one documented exception to the
  range, not a license to produce undersized chunks elsewhere.
- **FR-007**: Each chunk the system produces MUST retain: the source document's filename, a
  chunk-sequence position starting at 0 that is unique within its document, and — for formats with
  page structure — the source page number (omitted for formats without page structure, such as
  `.txt`).
- **FR-008**: The system MUST generate a searchable (embedding) representation for every chunk
  before any of that document's data is persisted. Whether this takes one request to the embedding
  provider or several (e.g. because a very large document's chunk count exceeds the provider's
  per-request batch limit) is an implementation detail; the requirement is only that every chunk has
  its representation in hand before FR-009's persistence step begins.
- **FR-009**: The system MUST persist a document's original content and its full set of chunks (each
  with its searchable representation) as a single all-or-nothing outcome: if any step from parsing
  through chunk storage fails for a document, the system MUST leave no document record and no chunk
  records for that upload attempt. "Full set of chunks" includes the **empty set**: a document whose
  chunk set is legitimately empty (FR-015) still persists as one document row with zero chunk rows —
  this is FR-009's outcome succeeding with a set of size zero, not an exception to it.
- **FR-010**: On a successful upload, the system MUST return to the caller the identifier assigned
  to the new document and the number of chunks it was split into.
- **FR-011**: On a failed upload, the system MUST return an error response that distinguishes two
  categories, so a caller can tell whether retrying the same file is worth attempting:
  - **"The input itself was invalid"** (FR-002, FR-003, FR-005 — unsupported type, empty/oversized/
    malformed request, or unparseable content). Retrying the identical file is never worth
    attempting; the file itself must change.
  - **"The input was valid but processing failed"** (FR-009's failure case) — including a downstream
    embedding-service failure **and** the AI provider being unconfigured or incompletely configured.
    Both are grouped here, not with the first category, because in both the file itself is not at
    fault: the same upload may succeed later once the underlying condition (a transient outage, or
    an operator completing the provider's configuration) is resolved.

  These two categories MUST be distinguishable by the response's status code alone (see
  [contracts/ingestion-api-contract.md](contracts/ingestion-api-contract.md) for the exact mapping)
  — a caller MUST NOT need to parse the error message to make the retry decision.
- **FR-012**: Re-uploading a file with a filename that already exists in the corpus MUST create a
  new, independent document with its own identifier; it MUST NOT overwrite, version, or merge with
  any previously uploaded document.
- **FR-013**: The system MUST NOT expose a partially-processed document as if it were complete — a
  document only becomes visible to the rest of the system once its full chunk set (or its
  deliberate zero-chunk state, per FR-015) has been committed.
- **FR-014**: The system MUST NOT include credentials (such as the AI provider's API key) in any
  log output or error response, even when reporting a failure from that provider. This is
  verifiable directly: a log line or error response resulting from any failure scenario in this
  spec MUST NOT contain the configured API key value, and reviewing the source of every log
  statement and error-response body in the ingestion path MUST show no code path that includes it.
  Ordinary operational details — filename, file size, rejected/failed status, and the `error` code
  (see contract) — MAY be logged; only the credential value itself, and the raw uploaded file
  content, are excluded from log output.
- **FR-015**: When an accepted-format document is parsed successfully but yields no extractable
  text (for example a blank file, or an image-only/scanned PDF with no OCR performed), the system
  MUST still store the document (it is retrievable in full afterward) with zero chunks, rather than
  rejecting the upload. This is a valid but unsearchable outcome, consistent with the existing
  schema's documented "document exists, 0 chunks" state (spec 003, FR-010) — it is not treated as
  an error.
- **FR-016**: The system MUST log, for every upload attempt, a structured record of the outcome
  (accepted/rejected/failed, which category per FR-011, and — on success — the document id and
  chunk count), and a structured record of each embedding request issued and each database write
  attempted, sufficient to diagnose a failure without re-running the request (constitution's Error
  Handling & Logging section). This requirement is satisfied by log output; it is not a separate
  API or query surface (see Assumptions).
- **FR-017**: The system MUST store the uploaded file's original filename exactly as provided,
  without interpreting it as a filesystem path or executing it in any way — the filename is
  opaque text data, stored in the same `documents.filename` column feature 003 already defines,
  never used to construct a file-system path on the server.

### Key Entities

This feature does not introduce new stored entities. It is the first writer of the `Document` and
`Chunk` entities already defined in
[specs/003-document-vector-schema/spec.md](../003-document-vector-schema/spec.md) and its
[contracts](../003-document-vector-schema/contracts/): a `Document` represents one uploaded source
file stored in full, and a `Chunk` represents one embedded, searchable segment of a document's
text, carrying its own source-document reference, sequence position, and (where applicable) page
number. This feature is responsible for populating both correctly; it does not change their shape.
Every functional requirement above that touches storage (FR-006/007/008/009/015/017) is written to
be satisfiable **without** altering that schema — implementation MUST confirm, before work begins,
that the deployed `documents`/`chunks` tables still match
[data-model.md](../003-document-vector-schema/data-model.md) unchanged; if they do not, that is a
signal to fix the schema drift, not to add a workaround here.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A typical multi-page policy document — approximately 7–8 pages, roughly 1,800 words
  (the scale of `security-policy.pdf`/`travel-expense-policy.pdf` in the project's sample corpus,
  `sample-data/documents/`) — is fully uploaded, processed, and confirmed searchable within 15
  seconds of the request being submitted.
- **SC-002**: 100% of uploads of unsupported file types, empty files, or unparseable files are
  rejected with no document or chunk data left behind, verified across repeated attempts.
- **SC-003**: 100% of uploads that fail partway through processing (after initial validation
  passes) leave zero partial documents or chunks behind — every failed attempt is either fully
  absent or, on caller retry once the failure condition is resolved, fully present.
- **SC-004**: Every one of the 16 documents in the project's sample corpus can be uploaded
  successfully, producing a fully searchable, chunked, embedded copy of each — establishing the
  corpus the evaluation set depends on.
- **SC-005**: Uploading the same document twice is verified independent on three specific,
  separately checkable points: (1) the two uploads receive two different document identifiers, (2)
  each identifier's chunks can be retrieved and are complete on their own, and (3) deleting one of
  the two documents leaves the other's document row and every one of its chunks completely
  unaffected.
- **SC-006**: A file at the maximum accepted size (20 MB, FR-003) either completes successfully or
  is reported as failed within 60 seconds of the request being submitted — no upload is left
  neither confirmed nor failed indefinitely.

## Assumptions

- **Single file per request, as a deliberate scope decision**: the endpoint accepts one document
  per upload request; batch/multi-file upload in a single request is out of scope, and uploading
  several documents means submitting several requests. The constitution's Ingestion Pipeline
  section phrases this as "the `/documents` REST endpoint MUST accept `.txt` and `.pdf` uploads"
  (plural) — read here as "uploads, over time, of files in either format," not "multiple files
  per request." This spec makes that reading explicit rather than leaving it implicit: nothing in
  the constitution, `poc-concept.md`, or User Story 1's single-file walkthrough calls for
  multi-file batching, and every acceptance scenario in this spec is written against one file per
  request.
- **Maximum accepted file size is 20 MB, stated as FR-003** — not merely a default noted here. This
  Assumptions entry is the *rationale* for that number (a generous ceiling for the kind of
  policy/HR/IT documents in scope for this PoC — the largest individual sample document is under
  12 KB (`sample-data/documents/`, 16 files, ~140 KB combined); not specified as a number elsewhere
  in the project's documentation). FR-003 is the single source of truth for the value itself — any
  other document (contracts, quickstart, a future frontend upload view) that states this limit
  MUST match FR-003, not restate an independently chosen number.
- **A document's chunks may be embedded across more than one request to the embedding provider**
  if a single document's chunk count would otherwise exceed the provider's per-request batch limit
  (FR-008 permits this explicitly). At this PoC's corpus scale — a few dozen chunks per document at
  most — a single batched call is expected to suffice in practice; the multi-call path exists so
  FR-008/FR-009 remain correct even if a future, larger document needs it. See
  [research.md](research.md) Decision 4 for the resolved design.
- **Synchronous processing**: the endpoint completes the full pipeline (parse, chunk, embed, store)
  before responding, matching the constitution's requirement that a successful response carries the
  final chunk count. Background/asynchronous processing with a separate status check is out of
  scope for this PoC. There is correspondingly no ingestion-status query surface — an operator
  determines how many documents have been ingested by querying the database directly (or a future
  admin feature), not through this endpoint; FR-016's logging requirement is what makes a given
  attempt diagnosable after the fact, not a substitute for a status API.
- **No defined concurrency ceiling**: the requirement (Edge Cases) is only that concurrent uploads
  do not interfere with each other's data, not a specific number of simultaneous uploads the system
  must sustain without degradation. This PoC's expected usage — one operator populating a
  16-document corpus — does not exercise meaningful concurrency; a throughput target is deferred
  until a real usage pattern exists to size it against.
- **Uploading is not exposed through a user interface in this feature.** This feature is the REST
  endpoint only; the Angular upload view is a separate, later step (see `docs/poc-concept.md`
  Next Steps, item 7). The endpoint is expected to be exercised directly (e.g. via HTTP client or
  test harness) until that UI exists.
- **No authentication/authorization**: consistent with the constitution's current PoC-phase scope,
  any caller who can reach the endpoint may upload a document. Access control, and any notion of
  "who uploaded this," are explicitly out of scope — no other spec or constitution section in this
  repository currently implies an audit-trail or uploader-identity requirement that this feature
  would need to accommodate; if one is introduced later, it is that future feature's amendment to
  make, not a gap in this one.
- **Filename storage carries no path-traversal or execution risk**: the original filename is stored
  verbatim as opaque text (FR-017) in the same `documents.filename TEXT` column feature 003 already
  defines — it is never used to construct a filesystem path or executed in any way, so no
  sanitization beyond "store the bytes as given" is required.
- **Chunk token measurement, embedding-call retry/backoff policy, and specific error-response
  formatting are implementation decisions**, not specification decisions, and are left to the
  planning phase.

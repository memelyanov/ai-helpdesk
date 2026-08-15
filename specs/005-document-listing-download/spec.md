# Feature Specification: Document Listing and Download Endpoints

**Feature Branch**: `005-document-listing-download`

**Created**: 2026-08-15

**Status**: Draft

**Input**: User description: "Add document listing and download endpoints so the UI can function: (1) an endpoint to list all documents that have been ingested into the database (filename, upload date/time, chunk count, etc.), and (2) an endpoint to download the original file content of a specific document by its id. This complements feature 004 (the ingestion endpoint), which only covers uploading."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See what's already in the corpus (Priority: P1)

Someone responsible for keeping the company's document corpus current — or anyone building the
upload/browse screen of the UI — needs to see, at a glance, every document that has been ingested
so far: its name, when it was added, and how many searchable pieces it produced. Without this,
there is no way to confirm an upload actually landed, spot a document that came in empty (zero
chunks), or know what's already there before uploading more.

**Why this priority**: This is the read-side counterpart to feature 004's write-only ingestion
endpoint. Right now the only way to know what has been ingested is to query the database directly.
Every other capability in this spec (picking a document to download) depends on first being able
to see the list of documents that exist.

**Independent Test**: Can be fully tested by ingesting a small number of documents (via feature
004's endpoint), then calling the listing endpoint and confirming every ingested document appears
exactly once with correct filename, upload timestamp, and chunk count — without needing the
download endpoint or any UI to exist yet.

**Acceptance Scenarios**:

1. **Given** three documents have been successfully ingested (two with chunks, one that produced
   zero chunks per feature 004's documented zero-chunk outcome), **When** the listing endpoint is
   called, **Then** the response contains exactly three entries, each showing its document
   identifier, original filename, upload date/time, and chunk count (two entries with a count
   greater than zero, one entry with a count of zero).
2. **Given** no documents have ever been ingested, **When** the listing endpoint is called, **Then**
   the response is a successful, empty list — not an error.
3. **Given** documents were ingested at different times, **When** the listing endpoint is called,
   **Then** the entries are ordered most-recently-uploaded first, so the newest additions are
   immediately visible without the caller having to sort them.

---

### User Story 2 - Retrieve a document's original file (Priority: P2)

Someone browsing the document list wants to open or save the original file behind one of the
entries — for example, to double-check what was actually uploaded, or to hand a colleague the
source document a chat answer was drawn from. They provide the document's identifier and receive
back the exact original file, byte for byte, with enough information to know what kind of file it
is and what to name it.

**Why this priority**: This depends on User Story 1 existing first (a caller needs a document
identifier to download, and the listing endpoint is how they get one), and it is the second half of
"the UI can function" — browsing alone doesn't let anyone verify or retrieve what was uploaded.

**Independent Test**: Can be fully tested by ingesting a known `.txt` and a known `.pdf` file (via
feature 004), noting the identifiers returned, then downloading each by identifier and confirming
the retrieved bytes are byte-for-byte identical to the file originally uploaded — without needing
the listing endpoint to exist yet (an identifier obtained from ingestion is enough).

**Acceptance Scenarios**:

1. **Given** a `.pdf` document was previously ingested, **When** it is downloaded by its document
   identifier, **Then** the response contains exactly the original file's bytes, identifies the
   content as a PDF, and carries the original filename.
2. **Given** a `.txt` document was previously ingested, **When** it is downloaded by its document
   identifier, **Then** the response contains exactly the original file's bytes, identifies the
   content as plain text, and carries the original filename.
3. **Given** a document that was ingested but produced zero chunks (feature 004's documented
   zero-chunk outcome), **When** it is downloaded by its document identifier, **Then** the original
   file is still returned in full — chunk count has no bearing on whether the original file is
   retrievable.

---

### Edge Cases

- What happens when a caller requests download for a document identifier that was never ingested? The
  system MUST report clearly that no such document exists, rather than returning an empty or
  malformed file or crashing. (No delete capability exists anywhere in this system yet — see
  Assumptions — so "an already-deleted document" is not a reachable scenario today; this edge case is
  limited to identifiers that were simply never issued.)
- What happens when a caller requests download using an identifier that is not even validly formatted
  (for example, not a UUID)? The system MUST report the exact same "not found" outcome as a
  nonexistent-but-well-formed identifier — a caller never needs to know whether identifiers happen to
  follow a particular format to understand that the requested document isn't retrievable.
- What happens when the listing endpoint cannot produce a result because of an unexpected server-side
  failure (as opposed to a genuinely empty corpus)? The system MUST report an error rather than an
  empty list — an empty list is reserved exclusively for the case where the corpus truly has no
  documents in it (FR-006).
- What happens when the listing endpoint is called while another upload is actively in progress?
  Because feature 004 guarantees a document only becomes visible once its full ingestion outcome
  has committed (all-or-nothing), the in-progress upload simply does not yet appear in the list — no
  partial or half-written document entry is ever returned.
- What happens when two documents share the same original filename (feature 004 explicitly allows
  this — each upload is independent)? Both MUST appear in the list as separate entries with their
  own identifiers, upload times, and chunk counts; the filename alone is never used to distinguish
  or deduplicate them.
- What happens when a document's original file is large (up to the 20 MB ingestion limit)? The
  download endpoint MUST still return the complete, unmodified original content.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a way to retrieve a list of every document currently stored
  in the corpus.
- **FR-002**: Each entry in the list MUST include, at minimum: the document's identifier, its
  original filename, its content type, the date/time it was uploaded, and the number of chunks
  currently associated with it.
- **FR-003**: The list MUST include documents that have zero chunks (feature 004's documented valid
  outcome for unsearchable content) on equal footing with documents that have one or more chunks —
  it MUST NOT hide, filter out, or otherwise treat zero-chunk documents as errors or omissions.
- **FR-004**: The list MUST reflect every document immediately once its ingestion has fully
  committed, with no caching or refresh delay a caller would need to work around.
- **FR-005**: The list MUST be ordered with the most recently uploaded document first, by default.
  When two or more documents share the exact same upload timestamp, their relative order MUST still
  be deterministic (a stable, well-defined tiebreak) — a caller MUST NOT see the order of
  same-timestamp entries change between identical, back-to-back calls.
- **FR-006**: When the corpus contains no documents, the listing endpoint MUST return a successful
  response containing an empty list, not an error. An empty list MUST only ever mean "the corpus is
  genuinely empty" — if the list cannot be produced for any other reason (e.g. an unexpected
  server-side failure), the system MUST report an error rather than an empty list, so a caller can
  never mistake "nothing to show" for "something went wrong."
- **FR-007**: The system MUST provide a way to retrieve, by document identifier, the exact original
  file content of a previously ingested document.
- **FR-008**: The downloaded content MUST be byte-for-byte identical to the content originally
  submitted at ingestion — no re-encoding, re-formatting, or modification of any kind.
- **FR-009**: The download response MUST identify the content's original type (`text/plain` or
  `application/pdf`) and the document's original filename, so a caller can save or display the file
  correctly without prior knowledge of what it is. Because the original filename is stored and
  returned verbatim (feature 004 FR-017) and may contain characters with special meaning in an HTTP
  header (for example, quotation marks or control characters), the system MUST encode the filename
  safely enough that no such character can corrupt or be misinterpreted in the response.
- **FR-010**: When a download is requested for a document identifier that does not exist, or that is
  not even a validly formatted identifier, the system MUST reject the request with the same, clear
  "not found" outcome in both cases and MUST NOT return any file content — a caller never needs to
  distinguish "malformed" from "well-formed but missing" to know the document isn't retrievable.
- **FR-011**: A document's chunk count being zero MUST have no effect on whether its original file
  can be downloaded — download depends only on the document existing, never on it having
  searchable chunks.
- **FR-012**: The list response MUST NOT include chunk text content or embedding vectors — only the
  summary fields in FR-002. Retrieving the searchable content of a document's chunks is out of scope
  for these two endpoints.

### Key Entities

This feature introduces no new stored entities and changes nothing about how `Document` or `Chunk`
rows are written. It is a second reader of the `Document` and `Chunk` entities that
[specs/003-document-vector-schema/spec.md](../003-document-vector-schema/spec.md) defines and that
[specs/004-document-ingestion-endpoint/spec.md](../004-document-ingestion-endpoint/spec.md) is the
first writer of:

- **Document**: one uploaded source file, stored in full — this feature's listing endpoint
  summarizes it (identifier, filename, content type, upload time, chunk count) and its download
  endpoint returns its stored original content unchanged.
- **Chunk**: one embedded, searchable segment of a document's text — this feature only counts a
  document's chunks for the listing endpoint; it does not expose individual chunk content.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A caller can retrieve the full list of ingested documents, with all summary fields
  correct, for a corpus of the project's full 16-document sample set in under 2 seconds — measured as
  wall-clock time for the HTTP call to complete against a locally running backend (`quickstart.md`
  Step 6), not a server-internal-only processing time.
- **SC-002**: 100% of downloaded documents are byte-for-byte identical to what was originally
  uploaded, verified across every document in the project's sample corpus.
- **SC-003**: 100% of download requests for a nonexistent or malformed document identifier return the
  clear "not found" outcome FR-010 defines, rather than a crash, an empty file, or a silently wrong
  file.
- **SC-004**: A newly ingested document appears in the list on the very next listing call after its
  upload response is received — zero observed delay.
- **SC-005**: Listing an empty corpus and listing a populated one both succeed without error;
  emptiness is never reported as a failure.

## Assumptions

- **Two related but independently usable endpoints**: listing and download are specified together
  because the UI needs both to function, but each is independently testable and deliverable — a
  caller with a document identifier obtained some other way (e.g. from feature 004's ingestion
  response) can use the download endpoint without the listing endpoint existing, and vice versa.
- **No pagination or filtering for this PoC's scale**: the sample corpus is 16 documents
  (`sample-data/documents/`), and nothing in the constitution or `poc-concept.md` calls for a larger
  corpus at this stage. The listing endpoint returns the complete list in one response; pagination,
  filtering, and search-by-filename are deferred until a real usage pattern justifies them.
- **No authentication/authorization**: consistent with feature 004 and the constitution's current
  PoC-phase scope, any caller who can reach these endpoints may list or download any document. No
  notion of "who is allowed to see this document" exists yet.
- **Read-only against feature 003's schema**: both endpoints only read from the `documents`/`chunks`
  tables; neither creates, modifies, nor deletes any row. Deletion of a document is out of scope for
  this feature (not requested, and no existing spec defines a delete-by-id endpoint yet). Because no
  delete or update capability exists anywhere in the system, no concurrent-mutation race (e.g. a
  download in flight while some future delete happens) is possible today; this feature defines no
  behavior for such a race, deferring it to whichever future feature first introduces mutation.
- **No partial/range downloads**: the download endpoint always returns the complete file in one
  response; HTTP `Range` requests are not supported at this PoC's scale, consistent with FR-008's
  byte-for-byte, whole-file guarantee.
- **Chunk count is a simple count, not a health signal**: FR-002's chunk count reflects exactly how
  many `chunks` rows reference the document at query time; this feature does not judge or flag
  "expected vs. actual" chunk counts — a zero-chunk document is reported as zero, not as a warning
  or degraded state, per feature 004's FR-015.
- **Downloading does not require knowing the content type or filename in advance**: the caller
  supplies only the document identifier; the system determines and returns the original content
  type and filename from the stored record (feature 003's `documents.content_type` and
  `documents.filename` columns).

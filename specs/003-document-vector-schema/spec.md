# Feature Specification: Document & Vector Storage Schema

**Feature Branch**: `main` (no feature branch created — no `before_specify` hook is registered)

**Created**: 2026-08-15

**Status**: Draft

**Input**: User description (translated from the original Russian per constitution v1.4.0's
Code & Documentation Language Standard — English-only in the repository): "We need to work out
the database schema to be used in the project. The requirements are described in poc-concept.md,
but let's restate what's needed: the ability to upload a source document and retrieve it; Vector +
text + metadata (`filename`, `page`, `chunk_id`) written to the vector database; having performed a
vector search, we must be able to get the id of the source document, so that after searching the
vector database the user can download the document in full."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Store and retrieve the original document (Priority: P1)

A document (`.txt` or `.pdf`) is uploaded to the system. Later — seconds or weeks afterward — someone needs the exact original file back: to open it, to hand it to another tool, or because a search result pointed at it.

**Why this priority**: Nothing else in this feature is possible without a durable, addressable copy of the original file. It is also the second half of the PoC's core promise: an answer isn't just a citation, it's a document the user can actually open (§3, poc-concept.md).

**Independent Test**: Upload a `.txt` file and a `.pdf` file, each receives an identifier; request each document back by its identifier and confirm the returned content is byte-identical to what was uploaded, with the original filename and file type preserved.

**Acceptance Scenarios**:

1. **Given** a `.pdf` file is uploaded, **When** the upload completes, **Then** the system assigns it a unique, stable document identifier and stores the original file content in full.
2. **Given** a document identifier from a prior upload, **When** the document is requested by that identifier, **Then** the system returns the original file content unchanged, byte-for-byte, along with its original filename.
3. **Given** a document identifier that does not exist — whether because it was never assigned or because that document has since been deleted — **When** the document is requested, **Then** the system reports that no such document exists rather than returning empty or corrupted content; the response MUST NOT distinguish between the two cases.
4. **Given** a document has just been uploaded and no chunks have been stored for it yet, **When** the document is requested by its identifier, **Then** the system returns the original file content exactly as in Scenario 2 — the absence of chunks does not affect retrieval (FR-010).

---

### User Story 2 - Store searchable chunks with vector, text, and metadata (Priority: P1)

Once a document is ingested, it is split into chunks. For each chunk the system computes an embedding vector and needs to persist that vector alongside the chunk's text and identifying metadata, so the chunk can later be found by meaning and traced back to where it came from.

**Why this priority**: This is the storage half of the RAG pipeline (§5.1, poc-concept.md) and a constitutional requirement (Principle III/V) — without it there is nothing for a similarity search to search.

**Independent Test**: Given a set of chunks derived from an already-stored document (text, page number, and a chunk identifier per chunk, plus a precomputed vector), store them and confirm each stored chunk record can be read back with its vector, its text, and its `source_filename` / `page_number` / `chunk_id` metadata intact.

**Acceptance Scenarios**:

1. **Given** a stored document has been split into chunks with computed embedding vectors, **When** the chunks are saved, **Then** each chunk record persists its vector, its text content, its source filename, its page reference, and its chunk identifier.
2. **Given** a document with no natural page boundaries (e.g. a `.txt` file), **When** its chunks are saved, **Then** the page metadata field is stored using the documented convention for "no page" rather than being omitted or causing a failure.
3. **Given** a chunk record, **When** it is inspected, **Then** it is possible to determine unambiguously which stored document it was derived from.

---

### User Story 3 - Trace a search hit back to a downloadable document (Priority: P2)

A similarity search over the chunk vectors returns one or more matching chunks. The person who ran the search — or the system acting on their behalf — needs to get from "this chunk matched" to "here is the whole document it came from," so the source can be opened or downloaded.

**Why this priority**: This is the payoff of the other two stories and the specific requirement called out by the user: search must not be a dead end. It depends on both US1 (a document to download) and US2 (a chunk to search), so it is sequenced after them, but it is what makes the schema fit for the product's purpose rather than an arbitrary vector store.

**Independent Test**: Given chunks belonging to a known document are stored, run a similarity search that matches one of those chunks and confirm the result identifies the source document by its identifier, and that identifier resolves to a downloadable copy via User Story 1's retrieval path.

**Acceptance Scenarios**:

1. **Given** a similarity search returns a matching chunk, **When** the result is read, **Then** it includes the identifier of the source document the chunk belongs to.
2. **Given** the source document identifier from a search result, **When** it is used to request the document, **Then** the full original document is returned (per User Story 1).
3. **Given** multiple returned chunks originate from the same document, **When** the results are read, **Then** they all resolve to the same source document identifier.

---

### Edge Cases

- When a document is deleted (FR-014), all of its chunks are deleted with it (cascade, FR-011) — a search performed immediately after MUST NOT return chunks from the deleted document.
- A document is stored but has not yet been chunked (chunking is a separate, later step) — the document MUST still exist and remain downloadable with zero associated chunks (FR-010).
- A `.txt` document has no pages — the page metadata field MUST use a defined "not applicable" convention rather than an arbitrary or missing value that later breaks citation display (FR-008).
- Two different documents each legitimately have a chunk numbered identically (e.g., both have a "chunk 1") — this is expected and harmless, since `chunk_id` is scoped per-document (FR-012); the two chunks remain distinguishable via their source document identifiers.
- The same file (identical filename, possibly identical content) is uploaded a second time — it is stored as a second, independent document (FR-013); both remain retrievable and searchable side by side.
- A chunk record is found to reference a document identifier that no longer exists — this state MUST be structurally prevented (foreign-key enforcement plus cascade delete, FR-007/FR-011), not just handled defensively at query time.
- An uploaded file's type is neither `.txt` nor `.pdf` — the system MUST reject it and MUST NOT create a document record (FR-015).
- A document-delete request runs concurrently with an in-flight chunk insert for that same document — the same foreign key and cascade that satisfy FR-007/FR-011 resolve this without a separate concurrency rule: the insert either commits against a document that still exists, or is cascaded away once the delete commits.
- A deletion is requested for a document identifier that does not exist — the system reports that no such document exists, the same as a read of a missing identifier (User Story 1, Acceptance Scenario 3); nothing is deleted.
- A similarity search runs before any document has ever been ingested (the `chunks` table is empty) — the search returns zero results, not an error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST persist the original content of every uploaded document (`.txt` or `.pdf`) in full, such that it can later be returned unchanged.
- **FR-002**: The system MUST assign each uploaded document a unique identifier at the time of upload, distinct from its filename; this identifier MUST NOT change for the lifetime of the document record.
- **FR-003**: The system MUST allow the original document content to be retrieved by its identifier, returning content that is byte-for-byte identical to what was uploaded.
- **FR-004**: The system MUST retain each document's original filename and file type alongside its content, so a retrieved document can be presented/downloaded with correct naming.
- **FR-005**: The system MUST record, for every uploaded document, when it was uploaded.
- **FR-006**: The system MUST store, for every chunk produced from a document, its embedding vector, its text content, and its metadata (`source_filename`, `page_number`, `chunk_id`) together as one retrievable record. `source_filename` MUST match the source document's `filename` at the time the chunk is written.
- **FR-007**: Every stored chunk record MUST reference the identifier of the document it was derived from, and that reference MUST always resolve to an existing document — a chunk MUST NOT exist without a valid source document.
- **FR-008**: The system MUST support documents with no natural page structure (e.g. `.txt`) by recording a single, consistently applied "no page" value — never a numeric placeholder such as `0` — rather than leaving the field ambiguous.
- **FR-009**: Every result produced by a vector similarity search MUST include the identifier of the source document the matched chunk belongs to, so the identifier can be used immediately to retrieve the whole document via FR-003.
- **FR-010**: A document MUST remain retrievable even before it has any chunks stored against it (upload and chunking are separate steps in time).
- **FR-011**: When a document is deleted, the system MUST cascade the deletion to all chunk records derived from it, so no chunk ever outlives its source document.
- **FR-012**: The `chunk_id` recorded in chunk metadata MUST be unique only within its source document: a per-document sequence starting at `0` and incrementing by 1 in chunking order, suitable for citation display such as "chunk 3 of travel-policy.pdf". Global uniqueness across the corpus is provided separately by the chunk's own record identifier, not by `chunk_id`. A write that would duplicate an existing `(document, chunk_id)` pair MUST be rejected.
- **FR-013**: When a document is uploaded whose filename matches an existing document, the system MUST store it as a new, independent document — duplicate filenames are permitted and coexist, distinguished by their unique document identifiers (FR-002).
- **FR-014**: The system MUST support deleting a document by its identifier; deletion is the trigger for the cascade behavior specified in FR-011.
- **FR-015**: The system MUST reject an uploaded file whose type is neither `.txt` nor `.pdf`, without creating a document record.
- **FR-016**: Every embedding vector stored in the system MUST have the same dimensionality, corresponding to a single embedding model used consistently across the entire corpus; the system MUST reject an embedding vector of any other dimensionality.
- **FR-017**: All chunks produced from a single document MUST be written as one atomic operation — either every chunk for that document is persisted, or none are; a partially written chunk set for a document MUST NOT be possible.

### Key Entities

- **Document**: A single uploaded source file. Represents the durable, retrievable original — the thing a user ultimately wants back. Key attributes: a unique identifier, original filename, file type, the full original content, and upload timestamp. One document has many chunks.
- **Chunk**: A segment of a document's extracted text, sized for embedding. Represents one unit of searchable meaning. Key attributes: a chunk identifier (`chunk_id`), the chunk's text content, its embedding vector, its source document's filename captured at write time (`source_filename`), its page reference within the source document (`page_number`), and a reference to the document it belongs to. Many chunks belong to one document.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of documents retrieved after upload return content that is byte-identical to what was originally uploaded, across both supported file types.
- **SC-002**: 100% of chunk records stored for a document carry metadata (`source_filename`, `page_number`, `chunk_id`) whose values are byte-for-byte equal to the values recorded for that chunk at write time, verified across the full PoC corpus (16 documents, sample-data).
- **SC-003**: 100% of similarity-search results carry a source document identifier that successfully resolves to a downloadable document.
- **SC-004**: Zero chunk records exist that reference a non-existent document, at any point in time — enforced continuously by referential-integrity constraints rather than checked periodically, and verifiable at any moment by a query joining `chunks` to `documents` and finding no unmatched rows.
- **SC-005**: The schema stores at least 100 documents and 10,000 chunks — an order of magnitude above the PoC's actual evaluation corpus (16 documents, ~107k characters) — without requiring a structural (column or table) change.

## Assumptions

- The database engine is PostgreSQL with the `pgvector` extension, per the constitution's mandated technology stack — this feature defines the schema within that engine, not a choice of engine. No functional requirement above names an engine or storage technology; the choice is confined to this section.
- Original document content is stored inside the same PostgreSQL database used for vectors and metadata (a binary/large-object column), rather than on a separate filesystem or object store — consistent with the PoC's "one database for vectors and metadata" approach (poc-concept.md §6.2) and its minimal-infrastructure goal. A production phase could move originals to object storage without changing the chunk-to-document relationship.
- Only `.txt` and `.pdf` are in scope, matching the PoC corpus; other formats are rejected per FR-015.
- No upper bound is imposed by this schema on a single document's stored content size or on the number of chunks a document may produce, beyond PostgreSQL's own engine limits. A smaller practical limit, if ever needed, is a future upload-validation concern, not a schema constraint.
- File size limits, virus/malware scanning, and access control on uploaded documents are out of scope for this feature (no authentication/authorization exists yet, per constitution §9 out-of-scope).
- Concurrent uploads and concurrent chunk-writes rely on standard database transaction isolation; no additional locking or coordination requirement is introduced by this schema.
- How `content_type` is determined at upload time (trusted from the caller vs. derived from the file itself) and how a document is split into chunks (size, overlap, tokenization) are decisions for a future ingestion feature; this schema only validates `content_type` against the fixed allowed set (FR-015) and imposes no minimum or maximum length on chunk `text` beyond "not empty" (data-model.md). That future feature is also the one that must produce the well-formed `filename`, `content_type`, chunk boundaries, and embeddings this schema assumes it receives — this schema does not validate content quality, only structural shape.
- **Re-ingestion is a distinct operation from re-upload.** FR-013 covers re-upload: uploading a file with a filename that already exists creates an unrelated second document. Re-ingestion — replacing an *existing* document's own chunk set (e.g. after a chunking-strategy or embedding-model change) — is not defined by this feature and has no dedicated column or operation here. Because FR-012 rejects a duplicate `(document, chunk_id)` write, a future re-ingestion workflow MUST delete a document's existing chunk rows before writing its new ones; FR-011's cascade delete is scoped to whole-document deletion and does not, by itself, provide a "delete this document's chunks only" operation.
- FR-017 requires all chunks for one document to be written as a single atomic operation. Within that batch, each individual chunk row is still written complete — `text` and `embedding` together, per FR-006 — so there is no schema state for a chunk with text but no vector, nor for a partially written batch: a document's chunk set is either entirely present or entirely absent.

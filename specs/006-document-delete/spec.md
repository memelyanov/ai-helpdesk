# Feature Specification: Document Deletion Endpoint

**Feature Branch**: `006-document-delete`

**Created**: 2026-08-16

**Status**: Draft

**Input**: User description: "implement delete document functionality that I accidentally missed previously."

## Clarifications

### Session 2026-08-16

- Q: When deletion of an existing document fails partway due to an unexpected server-side error (not a "not found" case), what MUST the system guarantee about the document's state, and how MUST the failure be reported? → A: Atomic, explicit failure — the whole deletion (document + chunks) happens in one all-or-nothing operation; on failure, nothing is deleted and the caller receives a distinct "deletion failed" error, never confused with "not found."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Remove a document from the corpus (Priority: P1)

Someone responsible for keeping the company's document corpus current — the same person who
uploads documents (feature 004) and browses what's already there (feature 005) — needs to remove a
document that no longer belongs: it was uploaded by mistake, has gone stale, or contains something
that shouldn't be searchable anymore. They identify the document (by the identifier they already
have from listing or uploading it) and delete it. Afterward, it and everything derived from it are
gone: it no longer appears in the document list, it can no longer be downloaded, and it no longer
surfaces in search results.

**Why this priority**: This is the one capability the document corpus has been missing since its
schema was designed — feature 003 (`documents`/`chunks` schema) already anticipated deletion at the
data level (its `chunks` table cascades on document delete), but no feature has ever exposed a way
to actually trigger it. Without this, an unwanted document is stuck in the corpus forever, which
undermines confidence in what feature 005's listing view shows.

**Independent Test**: Can be fully tested by ingesting a document (feature 004), confirming it
appears in the document list and its chunks are searchable, calling the deletion capability with its
identifier, then confirming it no longer appears in the list and none of its chunks are returned by
a search — without needing any other new feature to exist.

**Acceptance Scenarios**:

1. **Given** a document with one or more chunks was previously ingested, **When** it is deleted by
   its identifier, **Then** the deletion succeeds, the document no longer appears in the document
   list, and none of its chunks are returned by a subsequent search.
2. **Given** a document that produced zero chunks (feature 004's documented zero-chunk outcome) was
   previously ingested, **When** it is deleted by its identifier, **Then** the deletion succeeds the
   same as for any other document — having no chunks has no bearing on whether it can be deleted.
3. **Given** a document was just deleted, **When** a caller attempts to download it (feature 005's
   download capability) using the same identifier, **Then** the same "not found" outcome is returned
   as for an identifier that was never issued.

---

### User Story 2 - Get clear feedback for a delete request that can't succeed (Priority: P2)

A caller attempts to delete a document using an identifier that doesn't correspond to anything —
because it was mistyped, was never issued, isn't even a validly formatted identifier, or refers to a
document that was already deleted a moment ago. They need an unambiguous, predictable response
rather than a crash, a silent no-op, or an error that leaves them unsure whether anything happened.

**Why this priority**: This depends on User Story 1 existing (there must be a delete capability to
give feedback about), and reliable error feedback is what lets calling code (a future UI, a script,
another engineer) build correct retry and confirmation logic on top of the core capability — but the
core capability of successfully deleting a document is the higher-value first slice.

**Independent Test**: Can be fully tested by calling the deletion capability with an identifier that
was never issued and, separately, with a string that isn't validly formatted at all, and confirming
both produce the same clear "not found" outcome — without needing any document to actually exist.

**Acceptance Scenarios**:

1. **Given** no document exists with the given identifier, **When** a deletion is requested,
   **Then** the system reports a clear "not found" outcome and nothing in the corpus is changed.
2. **Given** the supplied identifier is not even validly formatted, **When** a deletion is
   requested, **Then** the system reports the exact same "not found" outcome as a well-formed but
   nonexistent identifier — a caller never needs to know whether identifiers follow a particular
   format to understand that nothing was deleted.
3. **Given** a document was already deleted once, **When** a deletion is requested again with the
   same identifier, **Then** the system reports the same "not found" outcome as any other
   nonexistent identifier — not a special "already deleted" error, and not a silent success.

---

### Edge Cases

- What happens when a document with a very large number of chunks is deleted? All of its chunks are
  removed along with it; the caller does not need to delete chunks separately or in any particular
  order.
- What happens when two delete requests for the same identifier arrive at nearly the same time? At
  most one succeeds; whichever loses the race finds the document already gone and receives the same
  "not found" outcome as User Story 2 — deletion is never applied twice, and nothing about this
  overlap requires a special error distinct from "not found."
- What happens when a document is deleted while a listing call or a download of a *different*
  document is in progress? Only the identified document and its own chunks are affected; every other
  document's listing entry, chunk count, and downloadability are unchanged.
- What happens when a deletion is requested for a document whose download or listing was already
  retrieved earlier in the same session? Those earlier results are simply now stale, as with any
  delete-after-read; the system is not required to notify past callers, only to ensure the next
  listing or download call reflects the deletion.
- What happens when deletion of an identifier that does exist fails partway due to an unexpected
  server-side error (for example, the database becomes unreachable mid-operation)? The document and
  its chunks MUST remain exactly as they were beforehand — no partial deletion — and the caller MUST
  receive an explicit failure outcome that is distinct from the "not found" outcome (Clarifications,
  Session 2026-08-16).
- What happens when a caller's download of a document (feature 005) is already in progress at the
  exact moment that same document is deleted? No additional coordination is required beyond ordinary
  database read-committed behavior: since both the download's read and the deletion's write are each
  a single, already-atomic database operation, whichever one's operation completes (commits) first
  determines the outcome — either the download already has the complete, valid content in hand
  before the deletion removes it (and the download succeeds normally), or the deletion completes
  first and the download finds nothing (and reports "not found," per feature 005 FR-010). No torn,
  partial, or corrupted read is possible either way.
- What happens when a document is deleted and a new file with the identical original filename is
  then uploaded? The new upload is a wholly new, independent document with its own identifier — this
  follows directly from feature 004's FR-013 (duplicate filenames are always independent uploads,
  whether or not an earlier document by that name still exists) and is not a special case introduced
  by deletion.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a way to delete a specific, previously ingested document by
  its identifier, permanently removing it from the corpus.
- **FR-002**: Deleting a document MUST also remove every chunk derived from it, so no chunk is ever
  left referencing a document that no longer exists.
- **FR-003**: Once a document is deleted, it MUST NOT appear in the document listing capability
  (feature 005) and MUST NOT be retrievable through the download capability (feature 005) — both
  MUST behave exactly as if the identifier had never been issued.
- **FR-004**: A document with zero chunks MUST be deletable exactly the same as a document with one
  or more chunks — chunk count MUST have no bearing on whether deletion succeeds.
- **FR-005**: When deletion is requested for an identifier that does not exist, or that is not even
  validly formatted (for example, not a UUID, an empty string, or a string containing characters
  that could never form a valid identifier), the system MUST reject the request with the same, clear
  "not found" outcome in both cases and MUST NOT delete anything — consistent with how the download
  capability (feature 005) already handles this same distinction.
- **FR-006**: A successful deletion MUST be confirmed to the caller through a single, unambiguous
  signal that is structurally distinct from both the "not found" outcome (FR-005/FR-008) and the
  "deletion failed" outcome (FR-010) — a caller MUST be able to tell all three apart without
  inspecting any human-readable text, and MUST always know unambiguously whether the document was
  actually removed.
- **FR-007**: Deleting a document MUST NOT alter any other document or its chunks — the effect of a
  deletion is scoped strictly to the one identified document.
- **FR-008**: Requesting deletion of an identifier that has already been deleted MUST produce the
  same "not found" outcome as an identifier that never existed — deletion is a one-time,
  non-reversible transition, not an operation with its own distinct "already deleted" error.
  Malformed (FR-005), never issued (FR-005), and already-deleted (this requirement) are jointly
  exhaustive: there is no fourth "not found" variant this system distinguishes separately, for any
  identifier value a caller could supply.
- **FR-009**: The system MUST NOT provide any way to recover a document once it has been deleted —
  no soft-delete, trash, or restore capability is in scope.
- **FR-010**: If deletion is requested for an identifier that does name an existing document (the
  FR-005/FR-008 "not found" cases do not apply), the operation MUST be all-or-nothing: an unexpected
  server-side failure that prevents that deletion from completing MUST leave the document and every
  one of its chunks exactly as they were beforehand — never partially deleted — and the system MUST
  report an explicit failure outcome to the caller that is clearly distinguishable from both the
  "not found" outcome (FR-005) and the success outcome (FR-006). This failure outcome signals a
  transient processing problem, not an invalid request — a caller MAY safely retry the identical
  request once the underlying condition has cleared, consistent with how this system already treats
  every other "input was valid but processing failed" outcome (feature 004 FR-011's equivalent
  category for uploads).
- **FR-011**: Every deletion attempt — whether it succeeds, finds nothing to delete, or fails — MUST
  be logged with enough detail (at minimum, the requested identifier and the outcome) for an
  operator to reconstruct what happened to a given document after the fact, consistent with this
  system's existing structured-logging practice for other document lifecycle events (feature 004
  FR-016 for ingestion outcomes). This is the only record of a deletion this system keeps — it is
  diagnostic logging, not an audit trail or an undo mechanism (FR-009).

### Key Entities

This feature introduces no new stored entities. It is the first and only writer of *deletions*
against the `Document` and `Chunk` entities that
[specs/003-document-vector-schema/spec.md](../003-document-vector-schema/spec.md) defines, that
[specs/004-document-ingestion-endpoint/spec.md](../004-document-ingestion-endpoint/spec.md) is the
first writer (creator) of, and that
[specs/005-document-listing-download/spec.md](../005-document-listing-download/spec.md) reads:

- **Document**: one previously uploaded source file — this feature permanently removes one
  identified document's record.
- **Chunk**: one embedded, searchable segment of a document's text — this feature removes every
  chunk belonging to the document being deleted, as an inseparable consequence of that document's
  deletion.

Neither term is redefined here — both keep exactly the meaning feature 003 gives them. This
feature's implementation MUST NOT begin until feature 003's `chunks.document_id ...
ON DELETE CASCADE` relationship (FR-011) is confirmed present in the deployed schema: FR-002's
"deleting a document also removes every chunk derived from it" guarantee depends structurally on
that relationship already existing, not on any new logic this feature adds.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A caller can delete an ingested document and confirm, on the very next listing call,
  that it no longer appears — zero observed delay.
- **SC-002**: 100% of a deleted document's chunks are removed along with it, verified by a
  subsequent similarity search over the chunk vectors — the same schema-level search capability
  [specs/003-document-vector-schema/spec.md](../003-document-vector-schema/spec.md) already defines
  and already verifies against deletion (its own Edge Cases) — returning none of them. This is not a
  dependency on any `/chat`-style endpoint; no such endpoint exists yet, and none is required to
  verify this criterion.
- **SC-003**: 100% of deletion requests for a nonexistent or malformed identifier return the same
  clear "not found" outcome, never a crash, a silent no-op, or a misleading success.
- **SC-004**: Deleting one document never changes the retrievability, chunk count, or content of any
  other document, verified across the project's full sample corpus.
- **SC-005**: A document can be deleted and confirmed gone — via a listing call or a download
  attempt reporting "not found" — in under 2 seconds end to end, regardless of how many chunks the
  document had, since deletion cost is dominated by a single indexed-by-identifier operation, not by
  the number of chunks removed with it.
- **SC-006**: 100% of deletions that fail because of an unexpected server-side error leave the
  targeted document and its chunks fully intact and retrievable exactly as before, with the failure
  reported distinctly from the "not found" outcome — never a partial deletion.

## Assumptions

- **Hard delete only**: deletion is permanent with no soft-delete, trash, or restore capability,
  matching feature 003 FR-014's plain "delete" framing and the constitution's current PoC-phase
  scope. This is also documented in FR-009.
- **No authentication/authorization**: consistent with features 004 and 005 and the constitution's
  current PoC-phase scope, any caller who can reach this capability may delete any document. No
  notion of "who is allowed to delete this document" exists yet. No other specification or the
  constitution implies a delete-time actor identity is required for an audit trail at this PoC
  phase — the same cross-check feature 004's Assumptions already made for uploads applies here
  identically; FR-011's logging requirement is diagnostic only (it records *what* happened, never
  *who* did it, since no caller identity exists to record).
- **Chunk cascade relies on the existing schema guarantee**: feature 003 (FR-011) already defines
  that chunks are removed automatically when their source document is deleted. This feature is the
  first to actually trigger that behavior via a caller-facing capability; it introduces no new
  schema or cascade logic of its own.
- **This feature completes feature 003's FR-014**: that requirement ("the system MUST support
  deleting a document by its identifier") was written into the schema design from the start but was
  never given an actual endpoint — this is that missing piece, not a new capability invented from
  scratch.
- **Concurrent access uses standard database transaction isolation**: consistent with feature 003's
  existing assumption for concurrent uploads and chunk-writes, no additional application-level
  locking or coordination is introduced for deletion beyond ordinary read-committed behavior (see
  Edge Cases). This is a distinct concern from FR-010's failure-*detection*-and-*reporting*
  requirement: FR-010 does not ask for any new locking or coordination mechanism either — it asks
  only that a failure of the single, already-atomic delete statement be caught and reported
  explicitly rather than left to surface as an unmapped error. The two requirements are
  complementary, not in tension: "no new coordination needed" (concurrency) and "report failures of
  the one statement that runs" (error handling) address different questions.
- **No bulk delete**: one document is deleted per request, consistent with the identifier-scoped
  pattern already established by the download capability (feature 005). Deleting multiple documents
  at once is out of scope until a real usage pattern justifies it.
- **No rate limiting or abuse protection**: consistent with the absence of any caller identity (no
  authentication exists to rate-limit against), repeated or rapid delete requests are not throttled
  or specially detected at this PoC phase. This is a deliberate scope boundary, not an oversight —
  deferred until whichever future feature introduces caller identity and access control, the same
  feature that would also resolve the authentication/authorization gap above.

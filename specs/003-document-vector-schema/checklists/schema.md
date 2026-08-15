# Schema Requirements Quality Checklist: Document & Vector Storage Schema

**Purpose**: Validate the *requirements* in `spec.md` — data integrity/referential-integrity
coverage, general completeness/clarity, consistency with the constitution's binding schema
vocabulary, and lifecycle/edge-case coverage — before proceeding to `/speckit-tasks`. This is a
pre-implementation gate: thorough, focused on this feature's central risk (schema correctness),
not an exhaustive release audit.

**Created**: 2026-08-15
**Feature**: [spec.md](../spec.md)
**Depth**: Standard pre-implementation gate
**Focus**: Data integrity & referential integrity; full spec-quality audit; traceability/consistency with the constitution; lifecycle & edge-case coverage (combined, per user selection)

**Note**: This checklist tests whether `spec.md`'s requirements are complete, clear, consistent,
and measurable — it does **not** test whether `db/init/02-documents-and-chunks.sql` or any code
correctly implements them (no such code exists yet).

**Status**: All 33 items resolved on 2026-08-15 — `spec.md` was corrected directly rather than left
with open gaps. See the resolution after each item and the summary in Notes.

## Data Integrity & Referential Integrity Requirements

- [x] CHK001 - Does spec.md state a requirement that all stored embedding vectors share one dimensionality/embedding model, so similarity comparison stays valid across the whole corpus — or is this left entirely implicit? [Gap, Spec §FR-006, relates to constitution Principle V]
  → **Resolved**: added FR-016.
- [x] CHK002 - Is behavior specified for an embedding vector that does not match the corpus's expected shape (reject vs accept)? [Gap]
  → **Resolved**: FR-016 requires rejection; structurally enforced by the fixed `vector(1536)` column (data-model.md).
- [x] CHK003 - Does spec.md establish document deletion as a supported capability anywhere, or does FR-011 only specify cascade *behavior* for a deletion that no requirement actually grants? [Gap, Spec §FR-011]
  → **Resolved**: added FR-014.
- [x] CHK004 - Is behavior specified for a chunk-insert attempt whose `(document, chunk_id)` pair already exists — rejected outright, or replaced/upserted? [Gap, Spec §FR-012]
  → **Resolved**: FR-012 now states the duplicate write MUST be rejected.
- [x] CHK005 - Is behavior specified for a document-deletion request racing against an in-flight chunk insert for the same document? [Gap, Edge Case]
  → **Resolved**: documented as an Edge Case — the existing FK/cascade already resolves this; no new requirement needed.
- [x] CHK006 - Is behavior specified for a deletion request targeting a document identifier that does not exist? [Gap]
  → **Resolved**: documented as an Edge Case (same "not found" behavior as a missing read).
- [x] CHK007 - Does the constitution's requirement that "vectors and metadata MUST be written atomically... all chunks from one document succeed or all fail" appear anywhere in spec.md's functional requirements, or only as a per-chunk guarantee (FR-006) with no document-level batch-atomicity requirement? [Conflict, Spec §FR-006 vs constitution "Ingestion Pipeline"]
  → **Resolved**: added FR-017; Assumptions reworded to stop implying per-chunk atomicity was the whole story.

## Requirement Completeness

- [x] CHK008 - Is an explicit decision recorded for whether the schema commits to any upper bound on a single document's stored content size? [Gap, Spec §Assumptions]
  → **Resolved**: Assumptions now states no upper bound beyond PostgreSQL's own engine limits.
- [x] CHK009 - Is the system's required behavior specified for an uploaded file that is neither `.txt` nor `.pdf`? [Gap, Edge Case]
  → **Resolved**: added FR-015; corresponding Edge Case added.
- [x] CHK010 - Are concurrency requirements defined for simultaneous uploads, or simultaneous chunk-writes targeting the same document? [Gap]
  → **Resolved**: Assumptions now states reliance on standard database transaction isolation, no additional requirement.
- [x] CHK011 - Is a functional requirement (not only an Assumption) stated for replacing/re-writing a document's chunk set during re-ingestion? [Gap, Spec §Assumptions]
  → **Resolved**: kept as an Assumption deliberately (re-ingestion workflow is a future feature) but reworded to spell out the mechanism: FR-012 forces a delete-then-insert, not an upsert.
- [x] CHK012 - Does spec.md state how `content_type` is determined at upload time (e.g., trusted from the caller vs derived from content), or is this left fully unspecified? [Gap, Spec §FR-004]
  → **Resolved**: Assumptions now explicitly defers this decision to a future ingestion feature.
- [x] CHK013 - Is a requirement stated for whether a document may be re-chunked in place (replacing existing chunk rows) versus only ever accumulating new chunk rows? [Gap]
  → **Resolved**: covered by the same Assumptions addition as CHK011.

## Requirement Clarity

- [x] CHK014 - Is "stable" in FR-002's "unique, stable identifier" defined precisely (e.g., immutable for the lifetime of the row), or does it leave room for interpretation? [Ambiguity, Spec §FR-002]
  → **Resolved**: FR-002 reworded — "MUST NOT change for the lifetime of the document record."
- [x] CHK015 - Is the exact starting value and increment rule for `chunk_id` normatively required, or only given as a non-binding example ("e.g., 0, 1, 2…")? [Ambiguity, Spec §FR-012]
  → **Resolved**: FR-012 reworded — starts at `0`, increments by 1, stated normatively (not "e.g.").
- [x] CHK016 - Does FR-008 itself state what the "not applicable" page convention is, or only that a documented convention must exist somewhere? [Clarity, Spec §FR-008]
  → **Resolved**: FR-008 reworded — a single consistently applied value, explicitly never a numeric placeholder.
- [x] CHK017 - Is "correctly identifies their source document" in SC-002 tied to a specific, checkable definition of correctness, or left to reader interpretation? [Clarity, Spec §SC-002]
  → **Resolved**: SC-002 reworded to "byte-for-byte equal to the values recorded... at write time."

## Requirement Consistency & Constitution Traceability

- [x] CHK018 - Does the Chunk key entity's attribute list include the `filename` metadata field that FR-006 requires every chunk to persist? [Conflict, Spec §Key Entities vs §FR-006]
  → **Resolved**: Chunk key entity now lists `source_filename` explicitly.
- [x] CHK019 - Do spec.md's plain-language metadata field names (`filename`, `page`) and the constitution's mandated field names (`source_filename`, `page_number`) get reconciled anywhere in spec.md itself, or only downstream in the plan? [Consistency, Spec §FR-006 vs constitution "Chunking & Embedding Strategy"]
  → **Resolved**: FR-006, Key Entities, and User Story 2's Independent Test now all use `source_filename`/`page_number` directly.
- [x] CHK020 - Are the functional requirements free of engine/implementation names (e.g., PostgreSQL, pgvector, bytea), consistent with those choices being confined to the Assumptions section? [Consistency, Spec §Requirements vs §Assumptions]
  → **Confirmed, no defect**: verified true; Assumptions now says so explicitly.
- [x] CHK021 - Is the constitution's 500–1000 token chunk-sizing rule reflected as a testable requirement anywhere in spec.md, or is chunk sizing entirely absent from this feature's scope? [Gap, relates to constitution "Chunking & Embedding Strategy"]
  → **Resolved**: Assumptions now explicitly defers chunk-sizing policy to a future ingestion feature; this schema only requires non-empty `text`.
- [x] CHK022 - Do the three clarification answers recorded in FR-011/FR-012/FR-013 appear consistently in both the Functional Requirements and the Edge Cases section, with no contradicting statement in either? [Consistency, Spec §FR-011–013 vs §Edge Cases]
  → **Confirmed, no defect**: verified consistent; Edge Cases updated to also reference the new FR-014/FR-015.

## Lifecycle & Edge Case Coverage

- [x] CHK023 - Is the zero-chunk document state (FR-010) covered by an acceptance scenario in User Story 2, or only mentioned in Edge Cases? [Coverage, Spec §User Story 2 vs §Edge Cases]
  → **Resolved**: added Acceptance Scenario 4 to User Story 1.
- [x] CHK024 - Are requirements defined for what a similarity search returns when the entire `chunks` table is empty (no documents ingested yet)? [Gap, Edge Case]
  → **Resolved**: added Edge Case — returns zero results, not an error.
- [x] CHK025 - Are requirements defined for the maximum number of chunks a single document may produce, or is this left unbounded by design? [Gap]
  → **Resolved**: covered by the same Assumptions addition as CHK008 (no upper bound).
- [x] CHK026 - Is behavior specified for uploading a `.txt` or `.pdf` file with zero extractable content (e.g., a technically valid but empty file)? [Gap, Edge Case]
  → **Resolved, existing coverage confirmed sufficient**: FR-010 already allows a document to have zero chunks indefinitely; no separate requirement needed since content extraction/chunking is out of this feature's scope.
- [x] CHK027 - Does spec.md distinguish, anywhere a document is "not found," between a document that never existed and one that existed and was deleted — or is that distinction explicitly declared irrelevant? [Ambiguity, Spec §User Story 1 Acceptance Scenario 3]
  → **Resolved**: Acceptance Scenario 3 reworded — explicitly MUST NOT distinguish the two cases.

## Acceptance Criteria Quality / Measurability

- [x] CHK028 - Can SC-005 ("accommodates... without requiring structural redesign") be objectively verified, or is it inherently a qualitative judgment call? [Measurability, Spec §SC-005]
  → **Resolved**: SC-005 reworded with concrete numbers (≥100 documents, ≥10,000 chunks).
- [x] CHK029 - Is SC-004's "at any point in time" tied to a defined verification method (e.g., a standing constraint versus a point-in-time query), or left ambiguous about how continuity would be checked? [Measurability, Spec §SC-004]
  → **Resolved**: SC-004 reworded — names the verification query and the continuous (constraint-based) enforcement mechanism.
- [x] CHK030 - Does every acceptance scenario in User Stories 1–3 map to at least one functional requirement or success criterion, with no scenario left untraceable? [Traceability, Spec §User Scenarios]
  → **Confirmed, no defect**: verified; the two newly added scenarios (CHK023, CHK027) also cite their FRs.

## Dependencies & Assumptions

- [x] CHK031 - Is the assumption that "chunk text and vector are always written together, as a single ingestion step" validated against, or reconciled with, the constitution's document-level atomicity requirement (see CHK007)? [Assumption, Spec §Assumptions]
  → **Resolved**: Assumptions reworded to state both the per-chunk (FR-006) and per-document-batch (FR-017) atomicity guarantees without contradiction.
- [x] CHK032 - Is the dependency on a future ingestion feature to populate `content_type`, chunk boundaries, and embeddings correctly made explicit everywhere spec.md assumes well-formed input? [Dependency, Spec §Assumptions]
  → **Resolved**: consolidated into one explicit Assumptions statement.
- [x] CHK033 - Is the assumption that re-ingestion/versioning is "a separate feature" consistent with FR-013's answer that duplicate filenames simply coexist as independent documents — i.e., does spec.md make clear that re-ingestion is not the same operation as re-upload? [Consistency, Spec §Assumptions vs §FR-013]
  → **Resolved**: added an explicit "re-ingestion is a distinct operation from re-upload" Assumptions bullet.

## Notes

- Every item above tests `spec.md`'s requirements, not any implementation — no code exists yet for
  this schema-only feature.
- Net changes to `spec.md`: FR-002, FR-006, FR-008, FR-012 reworded; FR-014–FR-017 added;
  SC-002/SC-004/SC-005 reworded; two acceptance scenarios added (User Story 1); five edge cases
  added; the Chunk key entity and Assumptions section both revised. `research.md`, `data-model.md`,
  `contracts/document-schema.md`, `contracts/chunk-schema.md`, `plan.md`, and `quickstart.md` were
  updated to match.
- FR-017 is the one new requirement with no schema-level (DDL) enforcement — it is a transaction
  obligation on the future ingestion writer, documented in `contracts/chunk-schema.md` rather than
  as a constraint, since no such constraint exists in SQL for "all these separate statements commit
  together."
- Items marked `[Gap]` were absent requirements, not necessarily defects; each was resolved either
  by adding a requirement or by recording an explicit, deliberate scope exclusion in Assumptions —
  none were left as silent omissions.

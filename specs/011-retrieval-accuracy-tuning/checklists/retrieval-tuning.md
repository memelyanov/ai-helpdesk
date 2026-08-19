# Requirements Quality Checklist: Retrieval Accuracy Tuning

**Purpose**: Standard-depth, reviewer-grade validation that spec.md's requirements — covering both
the relevance-bar (threshold) change and the passage-size (chunking) change equally — are complete,
unambiguous, measurable, and internally consistent, before they're broken into tasks.
**Created**: 2026-08-19
**Feature**: [spec.md](../spec.md)

**Note**: This checklist tests the requirements as written, not the implementation. Every item asks
whether the requirement is well-specified — it does not ask whether any code behaves correctly.

**Status**: All 23 items resolved directly in `spec.md` (see Resolution notes below); none deferred.

## Requirement Completeness

- [x] CHK001 Is a recorded numeric baseline for the current false-refusal rate documented anywhere, given SC-001 measures the new rate as an improvement "up from the false-refusal rate observed in manual testing"? [Gap, Spec §SC-001]
  - *Resolution*: SC-001 no longer implies a stored baseline number — it now states an explicit manual verification procedure (re-run the same motivating question set, compare pass rates directly). Research Decision 7.
- [x] CHK002 Is a recorded baseline score for the existing evaluation question set documented anywhere, given SC-003 requires the post-change score to be "at least as high... as it did before"? [Gap, Spec §SC-003]
  - *Resolution*: Same fix as CHK001 — SC-003 now specifies running the evaluation set before *and* after the change and comparing pass counts directly, no stored baseline required. Research Decision 7.
- [x] CHK003 Are non-functional requirements defined for the ingestion-time cost of producing more, smaller chunks per document (storage volume, embedding request count/latency)? [Gap, Non-Functional Requirements]
  - *Resolution*: New Assumptions bullet explicitly scopes this as a proportional, not new, load and states no new performance target is introduced (PoC corpus scale).
- [x] CHK004 Is there a requirement covering what happens when a single topic now needs more passages than the retrieval cap allows, given passages are smaller than before? [Gap, Spec §User Story 2 Scenario 2]
  - *Resolution*: New FR-009 + Retrieval Breadth key entity — top-K raised from 4 to 5 specifically to offset the smaller passage size. Research Decision 5.
- [x] CHK005 Are requirements defined for how a reviewer verifies SC-003's "same evaluation methodology already in use" if that methodology itself isn't described in this spec? [Gap, Spec §SC-003]
  - *Resolution*: SC-003 now names the concrete file (`sample-data/evaluation-questions.csv`) and the before/after comparison procedure explicitly.

## Requirement Clarity

- [x] CHK006 Is "genuinely relevant content" (used to define both the false-refusal fix and the not-covered guarantee) given any operational test a reviewer could apply consistently? [Ambiguity, Spec §User Story 1, §Edge Cases]
  - *Resolution*: Edge Cases section now opens with an explicit operational definition of "genuinely relevant" and "weakly related," used consistently for both terms thereafter.
- [x] CHK007 Is "weakly related" (Edge Cases) distinguished with any criterion from "genuinely relevant," or could the two overlap under the lowered bar? [Ambiguity, Spec §Edge Cases]
  - *Resolution*: Same definition bullet as CHK006 draws the distinction directly (shares vocabulary/topic vs. actually answers the question).
- [x] CHK008 Is SC-004's "higher proportion of directly relevant content" given any criterion a reviewer could apply consistently, beyond subjective judgment? [Measurability, Spec §SC-004]
  - *Resolution*: SC-004 now cites the same Edge Cases definition of "genuinely relevant" as its explicit comparison criterion.
- [x] CHK009 Is "closely enough to match existing documentation wording that a manual reviewer would expect a grounded answer" (SC-001) specific enough that two reviewers would classify the same question the same way? [Clarity, Spec §SC-001]
  - *Resolution*: SC-001 now anchors "closely enough" to a concrete, reusable artifact — the same question set from the feature's motivating manual test — rather than a fresh subjective judgment each time.

## Requirement Consistency

- [x] CHK010 Do FR-007 ("apply the new relevance bar and the new passage size... after this change goes live") and FR-008 (existing passages are NOT reprocessed) read as consistent about which of the two changes is retroactive on already-stored data and which is not? [Consistency, Spec §FR-007, §FR-008]
  - *Resolution*: FR-007 rewritten to explicitly split immediate (relevance bar, retrieval breadth — query-time) from prospective-only (passage size — ingestion-time) effect, with an explicit cross-reference to FR-008.
- [x] CHK011 Are the Assumptions section's numeric commitments (0.35, 500-token floor) reflected consistently in every functional requirement that depends on them (FR-001, FR-003, FR-005), or could an implementer read an FR in isolation and miss the tie to Assumptions? [Consistency, Spec §FR-001, §FR-003, §FR-005, §Assumptions]
  - *Resolution*: FR-001, FR-003, and FR-005 each now carry an explicit "(see Assumptions for the exact value adopted)" pointer.

## Acceptance Criteria Quality

- [x] CHK012 Can SC-001's "at least 95% of such cases" be verified against a defined, bounded sample of questions, or is the sample-selection method itself unspecified? [Measurability, Spec §SC-001]
  - *Resolution*: SC-001's manual-verification procedure names the sample explicitly (the motivating manual test's own question set).
- [x] CHK013 Can SC-002's "same reliability as before this change" be checked against a quantified prior reliability figure, or only qualitatively? [Measurability, Spec §SC-002]
  - *Resolution*: SC-002 now specifies re-asking this project's existing negative/out-of-scope test questions and confirming each is still refused — a concrete, repeatable check rather than an unquantified feeling.
- [x] CHK014 Is the acceptance scenario for User Story 1's "grounded answer" testable independent of the LLM's non-deterministic phrasing (i.e., does it specify what's checked — citation presence, not exact wording)? [Clarity, Spec §User Story 1 Acceptance Scenario 1]
  - *Resolution*: Acceptance Scenario 1 now explicitly states the check is citation presence and fallback-string absence, not exact answer wording.

## Scenario Coverage

- [x] CHK015 Is a scenario defined for a passage that sits exactly at the *previous* threshold (0.5) but now, under the new bar, is comfortably above it — confirming no special-casing is expected versus a passage newly crossing the new bar? [Coverage, Gap]
  - *Resolution*: New Edge Cases bullet states explicitly that a previously-acceptable passage remains acceptable — the lowered bar only ever adds newly-acceptable passages.
- [x] CHK016 Is a scenario defined for a document filter (`documentIds`) combined with the lowered bar — i.e., does scoping to one document change how many passages clear 0.35 versus an unscoped search? [Coverage, Gap]
  - *Resolution*: New Edge Cases bullet states the filter and the relevance bar act independently — filtering narrows the candidate set, then the same bar applies to it.
- [x] CHK017 Are requirements defined for a question that would have been correctly refused under the old bar but now incorrectly clears the new, lower bar — i.e., a false-positive regression case for the threshold change specifically? [Coverage, Gap]
  - *Resolution*: New User Story 3 Acceptance Scenario 2 covers this directly — a previously-correct refusal must still be a refusal after this change.

## Edge Case Coverage

- [x] CHK018 Does the edge case for "a document short enough that shrinking passage size... produces several [passages]" address whether page/citation metadata could now produce duplicate-looking citations for the same page (multiple passages, same page number)? [Edge Case, Spec §Edge Cases]
  - *Resolution*: That Edge Cases bullet now explicitly notes the existing same-page citation dedup behavior (feature 007) already prevents this.
- [x] CHK019 Is there an edge case for a document already at or near the new 500-token floor before this change (i.e., one whose chunks were already small) — confirming the change is a no-op for it rather than an unspecified behavior? [Edge Case, Gap]
  - *Resolution*: New Edge Cases bullet added, stating this explicitly.

## Dependencies & Assumptions

- [x] CHK020 Is the "existing governance range for passage size" Assumption traceable to a specific, named source document, or does it rely on the reader already knowing which document defines that range? [Traceability, Spec §Assumptions]
  - *Resolution*: Assumptions bullet now links directly to `.specify/memory/constitution.md`'s Chunking & Embedding Strategy section.
- [x] CHK021 Is the assumption that "the existing curated evaluation question set... remains the mechanism for validating this change" validated against that set actually existing and being current, or merely presumed? [Assumption, Spec §Assumptions]
  - *Resolution*: Assumptions bullet now states the file's presence was confirmed in the repository, not merely presumed.
- [x] CHK022 Is the operational cost of changing a hard-coded constant (requires a new deployment, no runtime toggle) acknowledged anywhere as a constraint on how this change can be rolled out or rolled back? [Gap, Spec §Assumptions]
  - *Resolution*: Assumptions bullet now states this matches the exact operational pattern already used to introduce the previous values (feature 007) — no new deployment/rollback mechanism is introduced.

## Ambiguities & Conflicts

- [x] CHK023 Is there any conflict between FR-002's "does not remove the floor itself" and SC-001's 95% target — could a reviewer read the two as implying the floor should almost never trigger for in-scope questions, versus FR-002's framing that floor-triggering remains normal, expected behavior for out-of-scope questions? [Ambiguity, Spec §FR-002, §SC-001]
  - *Resolution*: FR-002 rewritten to explicitly name SC-001's population (near-verbatim questions) as disjoint from its own (genuinely out-of-scope questions) — the two are about different question sets, not in tension.

## Notes

- Focus: both the relevance-bar (threshold) and passage-size (chunking) requirement clusters,
  reviewed together (Q1: C) — matches how spec.md itself couples the two changes as one
  accuracy-tuning effort.
- Depth: standard, reviewer-grade (Q2: B) — this feature touches a constitution-mandated numeric
  range and the constitution's non-negotiable accuracy gate, so items lean toward traceability and
  measurability rather than a shallow sanity pass.
- Audience/timing: author self-check before `/speckit-tasks` (Q3: A).
- One finding (CHK004) turned into new scope, not just a wording fix: the retrieval cap (`TOP_K`)
  is now raised from 4 to 5 alongside the two originally-requested changes, since shrinking passages
  without widening the cap would have risked exactly the coverage regression this item flagged. See
  spec.md FR-009 and research.md Decision 5.

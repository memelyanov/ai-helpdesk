# Requirements Completeness Checklist: Chat Endpoint (Retrieve → Augment → Generate)

**Purpose**: A broad "unit tests for requirements" pass across `spec.md` before `/speckit-tasks` —
validates completeness, clarity, consistency, measurability, and edge-case coverage of the written
requirements themselves, not the (not-yet-written) implementation.
**Created**: 2026-08-16
**Feature**: [spec.md](../spec.md)
**Depth**: Standard pre-implementation gate

**Note**: This checklist tests whether `spec.md`'s requirements are complete, unambiguous, and
consistent. It does not test whether any code works — there is no code yet.

**Status**: All 30 items resolved by editing `spec.md` directly (2026-08-16, same day as creation).

## Requirement Completeness

- [x] CHK001 Are requirements defined for every distinct response outcome this endpoint can produce (grounded answer, "not covered", each `400` variant, the `503` outcome)? [Completeness, Spec §FR-007/FR-011/FR-012/FR-013] — **Resolved**: added FR-016 (malformed request body), closing the previously-undocumented fifth outcome; Assumptions' "Distinct error vocabulary" bullet now enumerates all five.
- [x] CHK002 Does the spec state what a caller receives when `documentIds` names a well-formed but nonexistent document identifier, as distinct from the filter matching zero documents overall? [Gap, Spec §FR-010, Edge Cases] — **Resolved**: Edge Cases now explicitly covers a filter mixing a real identifier with a non-matching one.
- [x] CHK003 Is a maximum stated for how many entries `sources` may contain, beyond FR-004's "bounded number of passages retrieved" language? [Completeness, Spec §FR-004/FR-008] — **Resolved**: FR-004 now states the source list can never exceed the retrieval bound.
- [x] CHK004 Does the spec define behavior for a question that consists only of non-meaningful content (control characters, whitespace-adjacent symbols) that would pass a "not blank" check but isn't a real question? [Gap, Spec §Edge Cases, FR-011] — **Resolved**: Edge Cases now states the system does not judge "meaningfulness" — non-blank, non-relevant input naturally resolves via FR-007.
- [x] CHK005 Are requirements defined for what a caller can rely on regarding the stability of the fixed retrieval defaults (top-K, similarity threshold) across requests, beyond stating they are system-wide? [Gap, Spec §FR-004/FR-005, Assumptions] — **Resolved**: Assumptions' "Fixed defaults" bullet now states the defaults are identical for every request, not just a starting point.

## Requirement Clarity

- [x] CHK006 Is "a curated set of evaluation questions" (SC-001) specific enough to be independently reproducible from `spec.md` alone, without relying on an artifact the spec never names? [Clarity, Spec §SC-001] — **Resolved**: SC-001 now names `sample-data/evaluation-questions.csv` and its expected-document column directly.
- [x] CHK007 Is "typical question" in SC-003 quantified (length, complexity), or left to the reader's interpretation? [Clarity, Spec §SC-003] — **Resolved**: SC-003 now reads "any question up to the maximum allowed length (1000 characters, FR-012)."
- [x] CHK008 Does the Edge Cases entry for "the question is extremely long" state the concrete threshold itself, or only cross-reference it implicitly through FR-012 elsewhere in the document? [Clarity, Spec §Edge Cases, FR-012] — **Resolved**: the bullet now states the 1000-character limit and its inclusive boundary directly.
- [x] CHK009 Is "contributed a retrieved passage" (FR-008) defined precisely enough to distinguish a passage that was merely retrieved (pre-threshold) from one that survived the relevance threshold and was actually used? [Clarity, Spec §FR-008, FR-005] — **Resolved**: FR-008 now defines "contributed" explicitly against FR-005's threshold.

## Requirement Consistency

- [x] CHK010 Are the three top-level outcomes (FR-007 "not covered", FR-011/FR-012 validation errors, FR-013 processing failure) defined so that no single caller input could plausibly satisfy two of them at once? [Consistency, Spec §FR-007/FR-011/FR-012/FR-013] — **Resolved**: FR-011 now states validation always completes before retrieval/generation, and the Assumptions bullet states this ordering guarantees exactly one outcome per request.
- [x] CHK011 Does FR-007's "MUST cite no sources" requirement for the "not covered" outcome align cleanly with FR-008's citation rule for a successful answer, with no overlap case left ambiguous? [Consistency, Spec §FR-007/FR-008] — **Resolved**: FR-008 now states explicitly that its source list and FR-007's outcome are mutually exclusive.
- [x] CHK012 Does the Edge Cases entry for an empty AI-provider completion ("treat the same as not in documentation") read consistently with FR-013's list of processing-failure triggers, so a reader can confirm an empty completion is never one of FR-013's triggers? [Consistency, Spec §Edge Cases, FR-013] — **Resolved**: the bullet now states explicitly this is distinct from FR-013 and must not be reported as a system failure.

## Acceptance Criteria Quality

- [x] CHK013 Can SC-001's "≥80% ... correct source document appears among the cited sources" be evaluated using only what this spec defines, without assuming an undocumented measurement process? [Measurability, Spec §SC-001] — **Resolved**: SC-001 now states the exact comparison (returned `sources` vs. the evaluation file's expected-document column).
- [x] CHK014 Is SC-005's "zero mismatched or fabricated citations" verifiable from the response contract alone, or does confirming it require visibility into internal retrieval state this spec doesn't expose to a caller? [Measurability, Spec §SC-005] — **Resolved**: SC-005 now states it's verified the same way as SC-001, against the evaluation set.
- [x] CHK015 Does SC-003's 10-second response-time target have a corresponding acceptance scenario a reviewer could execute, or does it exist only as a standalone success-criteria bullet? [Acceptance Criteria, Spec §SC-003] — **Resolved**: added User Story 1 Acceptance Scenario 6 (Given/When/Then for the 10-second target).

## Scenario Coverage

- [x] CHK016 Is there an acceptance scenario for a caller who supplies `documentIds` and receives a grounded answer correctly scoped to only the allowed documents — as opposed to only the "filter matches nothing" edge case currently covered? [Coverage, Gap, Spec §FR-010] — **Resolved**: added User Story 1 Acceptance Scenario 5.
- [x] CHK017 Is there an acceptance scenario or edge case covering a question at exactly the 1000-character boundary (accepted) versus 1001 characters (rejected)? [Coverage, Edge Case, Spec §FR-012] — **Resolved**: the length Edge Cases bullet now states the exact boundary behavior.
- [x] CHK018 Are requirements or scenarios defined for two callers submitting overlapping or identical questions concurrently? [Gap, Non-Functional] — **Resolved**: added an Assumptions bullet stating no coordination is needed since every request is independent and read-only.

## Edge Case Coverage

- [x] CHK019 Does the spec state explicitly whether an empty `documentIds` array and an omitted `documentIds` field are guaranteed equivalent ("search everything"), or could a reader infer an empty array means "search nothing"? [Ambiguity, Spec §FR-010] — **Resolved**: FR-010 now states both are identical and there is no way to express "search nothing."
- [x] CHK020 Is there a stated requirement for a retrieved passage whose similarity score lands exactly at the 0.5 threshold boundary — included or excluded? [Edge Case, Gap, Spec §FR-005] — **Resolved**: FR-005 now states the threshold is inclusive.
- [x] CHK021 Does the spec define whether one malformed entry inside an otherwise well-formed `documentIds` list invalidates the whole request, or is silently ignored? [Gap, Spec §FR-010] — **Resolved**: FR-010 and new FR-016 state this is a distinct validation failure, never silently ignored.

## Non-Functional Requirements

- [x] CHK022 Are any logging or observability requirements stated for this feature, beyond FR-015's "MUST NOT log a credential" prohibition? [Gap, Spec §FR-015] — **Resolved**: added FR-017 requiring a structured per-request outcome log entry.
- [x] CHK023 Are concurrency or throughput expectations specified for this endpoint, or does SC-003's 10-second target implicitly assume a single in-flight request? [Gap, Non-Functional, Spec §SC-003] — **Resolved**: added Assumptions bullet on concurrency (same bullet as CHK018).
- [x] CHK024 Is it specified whether a request that eventually succeeds but exceeds SC-003's 10-second target counts as a failure, a degraded success, or is simply undefined? [Gap, Spec §SC-003, FR-013] — **Resolved**: SC-003 now states this is a slow success, not a failure.

## Dependencies & Assumptions

- [x] CHK025 Is the Assumptions section's "document filter shape" (restricting retrieval to document identifiers) cross-checked against the actual identifier format features 005/006 already expose, or only asserted by analogy? [Assumption, Spec §Assumptions] — **Resolved**: the bullet now states the format explicitly (UUIDs).
- [x] CHK026 Is the dependency on "the same embedding deployment used at ingestion" (FR-002, constitution Principle V) traced to the specific existing feature responsible for that guarantee, or only implied? [Traceability, Spec §FR-002] — **Resolved**: FR-002 now links directly to feature 004's spec.
- [x] CHK027 Is the "no new persistence" assumption reconciled with whether a helpdesk tool's usual need for question/answer auditability was considered and deliberately excluded, versus simply not raised? [Assumption, Spec §Assumptions] — **Resolved**: the bullet now states this was a deliberate exclusion, not an oversight, and why.

## Ambiguities & Conflicts

- [x] CHK028 Is "typical question" (SC-003) reconciled with FR-012's 1000-character maximum, so a reader can't read "typical" as describing something shorter or longer than what the system actually allows? [Ambiguity, Spec §SC-003, FR-012] — **Resolved**: same SC-003 rewording as CHK007.
- [x] CHK029 Does "genuinely contributed a retrieved passage" (SC-005) have a spec-stated definition distinct from "was retrieved" (pre-threshold) or "was in the top-K" (pre-threshold), given FR-005's separate threshold step? [Ambiguity, Spec §SC-005, FR-005] — **Resolved**: SC-005 now cross-references FR-008's precise definition of "contributed."
- [x] CHK030 Is FR-006's "answer MUST NOT include claims that cannot be traced back to that content" written in a way that is itself checkable against a response, or does it describe an outcome with no spec-level way to verify it was met? [Ambiguity, Spec §FR-006] — **Resolved**: FR-006 now states the enforcement mechanism (never giving the model anything beyond the passages) explicitly, rather than implying post-hoc verification.

## Notes

- Every item was resolved by editing `spec.md` directly — no items required new `[NEEDS
  CLARIFICATION]` markers, since each had a reasonable, spec-consistent answer derivable from
  existing decisions (the constitution, prior features' conventions, or this spec's own
  Clarifications session).
- Net changes to `spec.md`: 2 new acceptance scenarios (User Story 1), 2 new functional requirements
  (FR-016 malformed request, FR-017 structured logging), one new Assumptions bullet (concurrency),
  and clarifying sentences added to FR-002/004/005/006/008/010/011, SC-001/003/005, and four Edge
  Cases bullets. No existing requirement was removed or renumbered.

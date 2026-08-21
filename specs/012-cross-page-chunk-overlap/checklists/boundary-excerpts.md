# Boundary Excerpt Requirements Checklist: Cross-Page Chunk Overlap

**Purpose**: Formal pre-implementation gate on the requirements-writing quality of the cross-page
excerpt / anchor-page mechanics (spec.md FR-001–FR-010, Clarifications, Edge Cases) before
`/speckit-tasks` breaks them into tasks. Scope is deliberately narrow — this is the one genuinely
intricate part of the spec; general spec-wide quality was already validated by
`checklists/requirements.md` at `/speckit-specify` time.
**Created**: 2026-08-21
**Feature**: [spec.md](../spec.md)
**Audience/Timing**: Author, before `/speckit-tasks`

**Note**: This checklist tests whether the *requirements* are complete, unambiguous, and
consistent — not whether an implementation satisfies them. Items marked `[Gap]` point at something
the spec does not currently say, not at a code defect.

## Requirement Completeness

- [x] CHK001 - Do FR-001/FR-002 specify whether a borrowed excerpt is always taken from a
  neighboring page's own native text, or whether it could be taken from a neighbor's chunk text
  that has itself already been extended with a *further* excerpt from beyond it — i.e., is
  excerpt-chaining across more than one page boundary explicitly ruled in or out? [Gap, Spec
  §FR-001/FR-002]
- [x] CHK002 - Is a requirement stated for the total number of chunks a page produces staying
  identical before and after this feature (only chunk *text* changes at boundaries, not chunk
  *count*), or is this left to be inferred from FR-008's numbering-stability wording alone?
  [Completeness, Spec §FR-008]
- [x] CHK003 - **Out of Scope** (confirmed by user 2026-08-21) - Does the spec state what, if
  anything, a caller/consumer of `pageNumber` should be told (via documentation, not code) about
  the anchor-page definition now differing from a literal majority in the FR-010 collision case, or
  is that distinction left entirely to spec.md itself with no requirement that it be surfaced
  anywhere a citation is shown? [Gap, Spec §FR-004/FR-010]

## Requirement Clarity

- [x] CHK004 - Do FR-001 and FR-002, read on their own, make clear that "the following page"/"the
  preceding page" means the *nearest non-blank* neighbor (FR-005's rule), or does that connection
  depend on the reader independently combining FR-001/002 with FR-005? [Clarity, Spec
  §FR-001/FR-002/FR-005]
- [x] CHK005 - Is "short" in "short lead-in excerpt" / "short trailing excerpt" (FR-001/FR-002)
  fully resolved by FR-003's size rule by the time a reader reaches FR-001/FR-002, or could a
  reader reasonably expect a qualitative (sentence-boundary-aware) meaning of "short" before
  reaching FR-003? [Clarity, Spec §FR-001/FR-002/FR-003]
- [x] CHK006 - Does FR-010 unambiguously state whether the system must actively *detect* the
  collision condition (own text shorter than combined excerpts) or whether the "always report the
  anchor page" outcome is simply the natural, unconditional behavior regardless of any ratio check?
  [Clarity, Spec §FR-010]

## Requirement Consistency

- [x] CHK007 - Does the Key Entities "Chunk" bullet's claim that the reported page number "stays
  singular and unchanged in meaning" still hold precisely, now that FR-004/FR-010 define a more
  specific anchor-page rule than the plain majority rule the rest of the spec otherwise implies?
  [Consistency, Spec §Key Entities vs §FR-004/FR-010]
- [x] CHK008 - Do User Story 2's acceptance scenarios (which describe page-number outcomes only for
  the ordinary single-excerpt case) need an explicit cross-reference to FR-010's collision-case
  rule, or is the omission intentional because User Story 3 owns that case? [Consistency, Spec
  §User Story 2 vs §FR-010]

## Acceptance Criteria Quality

- [x] CHK009 - Does SC-001's claim that chunk pairs contain "the full immediate context from both
  sides" hold given FR-003 caps each excerpt at a fixed, bounded size (the existing overlap
  amount) — i.e., is "full" accurate when the split passage could exceed that bound, or should
  SC-001 be phrased as "the bounded excerpt defined by FR-003," not "full"? [Measurability/
  Consistency, Spec §SC-001 vs §FR-003]
- [x] CHK010 - Is SC-004's "no more than roughly the size of one extra overlap window per page
  boundary" precise enough to be objectively verified, or does the hedge word "roughly" leave the
  actual pass/fail threshold undefined? [Measurability, Spec §SC-004]
- [x] CHK011 - Is there a measurable acceptance criterion for the FR-010 collision case
  specifically (a short page between two content pages), or does SC-001/SC-003 only exercise the
  more common single-excerpt boundary? [Coverage, Spec §Success Criteria]

## Edge Case & Scenario Coverage

- [x] CHK012 - Does the Edge Cases section (or an FR) address a document where *every* page is
  short enough to be a single-chunk page with neighbors on both sides (i.e., the FR-010 collision
  case repeating across the whole document, not just one isolated page)? [Gap, Spec §Edge Cases]
- [x] CHK013 - Is the interaction between FR-006 (no excerpt at the very first/last page of a
  document) and FR-005 (skip blank pages when locating a neighbor) fully specified for a document
  that *begins* or *ends* with one or more blank pages before its first/last page with text? [Gap,
  Spec §FR-005/FR-006]

## Ambiguities & Conflicts

- [x] CHK014 - Is there any remaining requirement in spec.md that still uses "majority" language
  without the FR-010 qualifier attached, that a reader could interpret as contradicting the
  anchor-page rule? [Conflict, Spec §Assumptions]

## Notes

- Scope: narrowed to the cross-page excerpt / anchor-page mechanics (FR-001–FR-010,
  Clarifications, relevant Edge Cases) per explicit user direction — general spec-wide quality is
  covered by `checklists/requirements.md`, not re-checked here.
- Depth: formal pre-implementation gate — items left unchecked should be resolved (by editing
  spec.md, e.g. via `/speckit-clarify` or a direct spec edit) before `/speckit-tasks` is run, not
  deferred to task-writing time.
- Items marked `[Gap]`/`[Ambiguity]`/`[Conflict]` point at the requirement text, not at any
  implementation choice already recorded in research.md/plan.md — those documents may already have
  informally resolved some of these (e.g., research.md Decision 1 addresses CHK001's chaining
  question at the design level), which is itself a sign the spec should say so explicitly rather
  than leaving the resolution only in a downstream artifact.
- **2026-08-21 resolution pass**: CHK001, CHK002, CHK004–CHK014 resolved by direct spec.md edits
  (FR-001/FR-002 now cite FR-003/FR-005 and rule out excerpt-chaining; FR-010 states the anchor-page
  rule is unconditional; the Key Entities `Chunk` bullet and User Story 2's Independent Test now
  name FR-004/FR-010 explicitly instead of unqualified "majority" language; SC-001/SC-004 reworded
  for accuracy; new SC-005 covers the FR-010 collision case; two new Edge Cases bullets cover a
  document composed entirely of flanked short pages and leading/trailing blank pages). **CHK003
  marked Out of Scope** (user confirmation, 2026-08-21): `pageNumber` is, and remains, a single
  value — the page currently being processed (its anchor page, FR-004) — even when that chunk
  borrows a little text from the previous or next page. No page-attribution-confidence indicator,
  disclosure field, or citation UI change is being introduced by this feature; `SourceCitation`
  stays exactly as it is today. This is a scope boundary, not an unresolved requirements gap.

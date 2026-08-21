# Tasks: Cross-Page Chunk Overlap

**Input**: Design documents from `specs/012-cross-page-chunk-overlap/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution Principle II (Test-Driven Development) is mandatory for this
project, and plan.md's Technical Context commits to unit-level `ChunkerTest` cases written before
the `Chunker.chunk()` change. Test tasks below are ordered to run and fail first.

**Organization**: Tasks are grouped by user story (spec.md's P1/P2/P3). This feature touches exactly
two existing files — `Chunker.java` and `ChunkerTest.java` — no new file, no new dependency, no
schema change (plan.md, data-model.md). Phase order below follows real build order, not strict
P1→P2→P3 priority order; see "Why User Story 2 is built last" in Dependencies & Execution Order.

> **T001–T019 completed via `/speckit-implement`** (2026-08-21). TDD followed strictly per-story:
> each story's `ChunkerTest` cases were written and confirmed red before its implementation task,
> then confirmed green with zero regressions to the prior baseline at every checkpoint. One test bug
> was found and fixed during T013 (US3's implementation): T002's fixture asserted page 1's own chunk
> stayed unmodified, but once FR-001 existed, page 1's chunk — which is also page 1's *last* window,
> with page 2 following it — correctly gained page 2's lead-in excerpt too; the assertion was wrong,
> not the code. A second, minor test fragility was found in T015: BPE re-encoding of a
> decode-then-concatenate result can merge a token or two across the seam (normal tokenizer
> behavior), so an exact-sum sanity assertion was loosened to a robust lower bound. **Final result:
> 18/18 `ChunkerTest` cases green** (5 original + 13 new), plus the full default backend suite
> (`mvnw test`, 12 test classes, 0 failures) and `DocumentIngestionIT` under `-Pverify-db` (4/4
> green, via a real Testcontainers Postgres — `security-policy.pdf` still produces 8 chunks,
> confirming FR-008's chunk-count-unchanged guarantee).
>
> **T020 not run in this session**: the evaluation-set regression check (SC-002) requires a live
> Azure OpenAI call. This sandbox hits the identical Netty NIO loopback-selector limitation feature
> 004's `tasks.md` already documented ("Unable to establish loopback connection"), reproduced here
> directly via `EmbeddingClientAzureIT` (`-Pverify-ai`) — confirmed to be an environment constraint,
> not a code defect, since raw network reachability to the configured Azure endpoint itself succeeds
> (a plain `curl` returns `401`, i.e. the host is reachable; only the Netty-based client's local
> loopback selector fails to open in this specific sandbox). T020 is ready to run in a normal
> developer machine or CI. Quickstart.md Step 2's live-PDF-upload-via-curl manual check is blocked
> for the same reason (the embedded Tomcat server also depends on Netty's loopback selector); the
> equivalent proof was instead obtained through `DocumentIngestionIT`'s real-database write path
> (stubbed embedding, real `Chunker` output) plus `ChunkerTest`'s exact expected-token-sequence
> assertions for every boundary case.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3, mapping to spec.md's user stories — omitted for Setup, Foundational,
  and Polish tasks
- Every task names its exact file path

## Path Conventions

Existing web application structure (feature 001/003/004, unchanged). Both files this feature
touches already exist:
- `backend/src/main/java/com/epam/aihelpdesk/ingestion/Chunker.java`
- `backend/src/test/java/com/epam/aihelpdesk/ingestion/ChunkerTest.java`

Because almost every task edits one of these same two files, most tasks are sequential (not `[P]`)
even within a phase — matching how feature 004 handled sequential edits to its own shared test
files.

---

## Phase 1: Setup

**Purpose**: Establish the pre-change baseline this feature's diff must not regress.

- [X] T001 Run `backend\mvnw.cmd test -Dtest=ChunkerTest` and confirm the existing 5 test cases pass
      unchanged before any edit — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/ChunkerTest.java` (no code change; this is
      the baseline every later checkpoint compares against).

**Checkpoint**: Baseline confirmed green. Safe to start User Story 1.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: N/A for this feature. There is no new dependency, schema, DTO, or shared exception
type to stand up first (plan.md Technical Context) — unlike feature 004, this feature is a single,
targeted change inside one existing method. User Story 1 begins directly after Setup.

**Checkpoint**: N/A — proceed directly to Phase 3.

---

## Phase 3: User Story 1 - Answers stay complete when the source text crosses a page break (Priority: P1) 🎯 MVP

**Goal**: FR-002 — when a page has a preceding page, the first chunk built from that page's own text
gains a trailing excerpt borrowed from the end of the nearest preceding page that has text.

**Independent Test**: Ingest a two-page test document where a sentence is split across the boundary;
confirm page 2's first chunk (page 1's tail + page 2's own text) contains the complete sentence
(spec.md User Story 1, Acceptance Scenario 1 — satisfiable by FR-002 alone, since the retrieved
chunk that already contains "the rest of the sentence" is exactly the one that needs the missing
lead-in from page 1, not the other direction).

### Tests for User Story 1 (write first, confirm they fail before implementing)

- [X] T002 [US1] Add a `ChunkerTest` case: two non-blank pages where page 2's own text is short
      enough to produce exactly one chunk; assert that chunk's text equals page 1's trailing
      `OVERLAP_TOKENS` (63) tokens followed by page 2's own text, and that its `pageNumber` is still
      `2` (FR-002, FR-003, FR-004) — in
      `backend/src/test/java/com/epam/aihelpdesk/ingestion/ChunkerTest.java`.
- [X] T003 [US1] Add a `ChunkerTest` case: a document's first page-with-text (no preceding page)
      produces its first chunk with no trailing excerpt, byte-identical to today's behavior (FR-006).
      Include a variant where one or more blank pages precede that first page-with-text in the
      `pages` list, confirming they are not mistaken for a phantom preceding page with real text
      (spec.md Edge Cases, leading-blank-pages case) — in the same file.
- [X] T004 [US1] Add a `ChunkerTest` case: the preceding page's own text is shorter than
      `OVERLAP_TOKENS` (e.g. ~20 tokens); the borrowed trailing excerpt is exactly those ~20 tokens,
      never padded or fabricated (spec.md Edge Cases) — in the same file.
- [X] T005 [US1] Add a `ChunkerTest` case: a blank/divider page sits between the current page and its
      nearest actual preceding page with text; the trailing excerpt still comes from that nearest
      non-blank page, skipping the blank one (FR-005) — in the same file.

### Implementation for User Story 1

- [X] T006 [US1] Implement FR-002 in `Chunker.chunk()`: as pages are processed in order, carry
      forward each non-blank page's own trailing `OVERLAP_TOKENS` tokens and prepend them to the
      **first** window built for the following non-blank page (skipping blank pages when finding
      "the following non-blank page," satisfying FR-005 for this direction); every interior window
      and the last window of each page stay exactly as they are today — in
      `backend/src/main/java/com/epam/aihelpdesk/ingestion/Chunker.java` (research.md Decision 1–3;
      makes T002–T005 pass).

**Checkpoint**: `backend\mvnw.cmd test -Dtest=ChunkerTest` green — T001's original 5 cases plus
T002–T005, no regressions. User Story 1 is independently functional and testable per its Independent
Test above.

---

## Phase 4: User Story 3 - Boundary context works in both reading directions (Priority: P3)

**Goal**: FR-001 — when a page has a following page, the **last** chunk built from that page's own
text gains a lead-in excerpt borrowed from the start of the nearest following page that has text —
extending the same mechanism T006 introduced to also look ahead, not just carry forward.

**Independent Test**: For the same two-page test document, confirm page 1's last chunk (its own text
+ page 2's head) and page 2's first chunk (page 1's tail + its own text, from US1) each carry the
other side's excerpt, both sized at `OVERLAP_TOKENS` (spec.md User Story 3).

### Tests for User Story 3 (write first, confirm they fail before implementing)

- [X] T007 [US3] Add a `ChunkerTest` case: two non-blank pages where page 1's own text is short
      enough to produce exactly one chunk; assert that chunk's text equals page 1's own text followed
      by page 2's leading `OVERLAP_TOKENS` (63) tokens, and that its `pageNumber` is still `1` (FR-001,
      FR-003, FR-004) — in `ChunkerTest.java`.
- [X] T008 [US3] Add a `ChunkerTest` case: a document's last page-with-text (no following page)
      produces its last chunk with no lead-in excerpt, byte-identical to today's behavior (FR-006).
      Include a variant where one or more blank pages follow that last page-with-text in the `pages`
      list, confirming they are not mistaken for a phantom following page with real text (spec.md
      Edge Cases, trailing-blank-pages case) — in the same file.
- [X] T009 [US3] Add a `ChunkerTest` case: the following page's own text is shorter than
      `OVERLAP_TOKENS`; the borrowed lead-in excerpt is exactly that shorter amount, never padded or
      fabricated (spec.md Edge Cases) — in the same file.
- [X] T010 [US3] Add a `ChunkerTest` case: a blank/divider page sits between the current page and its
      nearest actual following page with text; the lead-in excerpt still comes from that nearest
      non-blank page, skipping the blank one (FR-005, forward direction this time) — in the same
      file.
- [X] T011 [US3] Add a `ChunkerTest` case: a single short page flanked by both a preceding and a
      following page produces exactly one chunk carrying **both** the trailing excerpt (from before)
      and the lead-in excerpt (from after) at once; assert its `pageNumber` is still that page's own
      number regardless of the borrowed-vs-native token ratio (FR-010, spec.md Clarifications
      Session 2026-08-21, SC-005) — in the same file.
- [X] T012 [US3] Add a `ChunkerTest` case: many/all pages in one document are each short enough to be
      independently flanked, single-chunk pages; assert every one of them reports its own page number
      as anchor — there is no cumulative or document-wide exception (spec.md Edge Cases) — in the
      same file.

### Implementation for User Story 3

- [X] T013 [US3] Extend `Chunker.chunk()` to a genuine two-pass structure (research.md Decision 1):
      tokenize every non-blank page up front into an ordered list, then iterate by index so each
      page's loop can also see the **next** non-blank page's own tokens and append up to
      `OVERLAP_TOKENS` of its leading tokens to the current page's **last** window. T006's
      trailing-excerpt behavior on the first window is preserved unchanged; a page whose single
      window is simultaneously first and last receives both extensions from the same two branches
      (research.md Decision 2) — in `Chunker.java` (depends on T006; makes T007–T012 pass).

**Checkpoint**: `backend\mvnw.cmd test -Dtest=ChunkerTest` green — T002–T012 all pass alongside
T001's original 5, no regressions. User Story 1 **and** User Story 3 are both independently
functional.

---

## Phase 5: User Story 2 - Citations stay trustworthy even for boundary chunks (Priority: P2)

**Goal**: Confirm FR-004's anchor-page rule and FR-007/FR-008/FR-009's stability guarantees hold
across every boundary case T006 and T013 introduced. Per research.md Decision 4, this story needs
**no new production code** — anchor-page attribution already falls out of the existing per-page loop
structure — so this phase is a dedicated assertion pass, not an implementation task.

**Independent Test**: Inspect the chunks produced for a multi-page test document and confirm every
chunk — including ones adjacent to a page boundary in either direction — reports exactly one page
number, its anchor page (spec.md User Story 2). Scenario 1 (a chunk that starts a new page) is
already exercised by T002; Scenario 2 (a chunk that ends a page) needs T013 to exist first — this is
why this phase is built after User Story 3, not before it (see Dependencies & Execution Order).

### Tests for User Story 2

- [X] T014 [US2] Add a `ChunkerTest` case: across a 3+ page document exercising both FR-001 and
      FR-002 boundary chunks together, assert `chunkId` stays unique, 0-indexed, and sequential with
      no gaps or duplicates even though boundary chunk *text* grew (FR-008) — in `ChunkerTest.java`.
- [X] T015 [US2] Add a `ChunkerTest` case: assert every chunk produced across T002–T012's fixtures —
      including T011's both-excerpts collision chunk, the worst case — stays within the document's
      500–1000 token interior range or the documented short-chunk exception (FR-009) — in the same
      file.
- [X] T016 [US2] Add a `ChunkerTest` case: a `.txt`-style single unpaged `ExtractedPage`
      (`pageNumber() == null`) is chunked identically before and after this feature — no cross-page
      excerpt logic triggers when there is no page structure (FR-007) — in the same file.

**Checkpoint**: Full `ChunkerTest.java` suite (T001's original 5 + T002–T016) green. All three user
stories independently functional and verified together.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and end-to-end validation that spans all three stories.

- [X] T017 [P] Update `Chunker.java`'s class-level Javadoc — it currently states windows are built
      "independently per `ExtractedPage`... not across the whole document's concatenated text"; add
      the cross-page excerpt behavior this feature introduces so the comment matches shipped
      behavior (constitution Principle I: documentation that contradicts shipped behavior is a
      defect) — in `Chunker.java`.
- [X] T018 [P] Update `ChunkerTest.java`'s class-level Javadoc one-line summary to mention the new
      boundary-excerpt and anchor-page coverage alongside the existing token-window/overlap/edge-case
      coverage it already documents — in `ChunkerTest.java`.
- [X] T019 Run `quickstart.md` Steps 1–3 end to end: Step 1 (`mvnw test`), Step 2 (ingest a real
      multi-page PDF, e.g. `sample-data/documents/security-policy.pdf`, and manually inspect chunk
      text either side of a page boundary via `psql`), Step 3 (`mvnw test -Pverify-db` — confirms
      `DocumentIngestionIT` from feature 004 still passes unchanged).
- [ ] T020 Run the evaluation set (`sample-data/evaluation-questions.csv`) against a re-ingested
      corpus and report the ≥80% accuracy result in the PR (SC-002; constitution Governance →
      Compliance Review gate).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: N/A, no tasks — proceeds straight through.
- **User Story 1 (Phase 3)**: Depends on Setup only. No dependency on US2/US3. This is the MVP.
- **User Story 3 (Phase 4)**: Depends on Setup and on User Story 1's `Chunker.java` change (T006) to
  extend — not independently implementable before US1, though independently *testable* once built
  (its own lead-in-excerpt behavior, on top of whatever US1 already added).
- **User Story 2 (Phase 5)**: Depends on Setup **and** on both User Story 1 (T006) **and** User
  Story 3 (T013) — its second acceptance scenario and FR-010's collision case (T011) exercise
  boundary chunks that only exist once both directions are implemented. No new production code is
  written in this phase (research.md Decision 4); it is purely a verification pass.
- **Polish (Phase 6)**: Depends on US1, US3, and US2 all being complete.

### Why User Story 2 is built last

Phases below are numbered P1→P2→P3 in their headings (matching spec.md's priority labels), but their
**build order** is US1 → US3 → US2, not P1 → P2 → P3 in phase order. This mirrors feature 004's
precedent of documenting a real dependency that cuts across strict priority order: User Story 2's
own acceptance scenarios name a chunk that "starts a new page" (US1's behavior) *and* a chunk that
"ends a page" (US3's behavior) — verifying both, plus the FR-010 collision case that only exists once
a page can receive excerpts from both sides at once, genuinely requires both T006 and T013 to exist
first. User Story 2 remains independently *testable* — its own dedicated assertions in T014–T016 —
just not independently *buildable* before US1 and US3.

### Within Each User Story

- Tests are written first and confirmed to fail before the implementation task that follows them.
- US1 (T002–T005) and US3 (T007–T012) each extend the same two files sequentially — no `[P]` within
  a phase, since every task in Phases 3–5 touches `ChunkerTest.java` and/or `Chunker.java`, already
  edited by an earlier task in the same phase.

### Parallel Opportunities

- Phase 6 only: T017 (`Chunker.java`) and T018 (`ChunkerTest.java`) touch different files and can run
  in parallel.
- No other `[P]` opportunities exist in this feature — every other task shares one of the same two
  files as an earlier task in its phase.

---

## Parallel Example: Polish

```bash
# Launch T017 and T018 together (different files):
Task: "Update Chunker.java's class-level Javadoc in backend/src/main/java/com/epam/aihelpdesk/ingestion/Chunker.java"
Task: "Update ChunkerTest.java's class-level Javadoc in backend/src/test/java/com/epam/aihelpdesk/ingestion/ChunkerTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001).
2. Phase 2: Foundational — N/A, no tasks.
3. Complete Phase 3: User Story 1 (T002–T006).
4. **STOP and VALIDATE**: `backend\mvnw.cmd test -Dtest=ChunkerTest` green; User Story 1's
   Independent Test passes.
5. This is a demonstrable MVP: a chunk retrieved from the *start* of a page now includes the tail of
   the previous page — the more common case (spec.md User Story 3's "Why this priority" note) is
   already fixed.

### Incremental Delivery

1. Setup → baseline confirmed.
2. Add User Story 1 → validate independently → MVP demonstrable (forward context restored).
3. Add User Story 3 → validate independently → the symmetric case (backward context from a page's
   last chunk) is also fixed; the FR-010 collision case now exists and is covered.
4. Add User Story 2 → validate independently → page-number/citation correctness is explicitly proven
   across every boundary case introduced by US1 and US3, not just assumed.
5. Polish → Javadoc kept truthful (constitution Principle I), full `quickstart.md` run, evaluation
   set regression check (SC-002).

### Parallel Team Strategy

Not recommended for this feature: US1 (T006) and US3 (T013) both modify the same method in the same
file, with T013 explicitly building on top of T006's change — there is no way to split this work
across developers without one blocking the other. A single developer taking T001 → T020 in order is
the natural path; the only real parallel opportunity is T017/T018 in Polish.

---

## Requirement Coverage

Every functional requirement and success criterion maps to at least one task:

| Requirement | Task(s) |
|---|---|
| FR-001 | T007, T008, T009, T010, T013 |
| FR-002 | T002, T003, T004, T005, T006 |
| FR-003 | T002, T004, T007, T009 |
| FR-004 | T002, T007, T011, T014 |
| FR-005 | T005, T010 |
| FR-006 | T003, T008 |
| FR-007 | T016 |
| FR-008 | T014 |
| FR-009 | T015 |
| FR-010 | T011, T012 |
| SC-001 | T002, T007, T019 |
| SC-002 | T019, T020 |
| SC-003 | T014, T002, T007, T011 |
| SC-004 | T014, T015 |
| SC-005 | T011, T012 |

---

## Notes

- `[P]` tasks touch different files with no dependency on each other — rare in this feature, since
  almost everything edits one of two shared files.
- `[Story]` labels (US1/US2/US3) trace every user-story task back to spec.md's priorities; phase
  *build order* (US1 → US3 → US2) differs from priority order for the documented reason above.
- Tests are written and confirmed failing before the implementation task(s) that make them pass
  (constitution Principle II).
- Commit after each task or logical group.
- Stop at each phase checkpoint to validate that story's Independent Test criterion before moving on.
- Avoid: writing T013 (US3's implementation) before T006 (US1's) exists to extend; treating T014–T016
  (US2) as needing their own `Chunker.java` change — they do not.

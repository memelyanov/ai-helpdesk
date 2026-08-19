# Tasks: Retrieval Accuracy Tuning

**Input**: Design documents from `specs/011-retrieval-accuracy-tuning/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/retrieval-tuning-contract.md](contracts/retrieval-tuning-contract.md), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution Principle II (Test-Driven Development) is mandatory for this
project. This feature's "production code" is three existing constants, and every one of them is
already covered by an existing assertion built around its *old* value — so the TDD cycle here is
"edit the assertion to the new expected value, confirm it now fails against the still-unchanged
constant (red), then move the constant (green)," not net-new test scaffolding.

**Organization**: Tasks are grouped by user story (spec.md's P1/P2/P3) so each story is
independently implementable and testable. All file paths are relative to the repository root.

> **Two stories share one production file.** `ChatService.SIMILARITY_THRESHOLD` (US1) and
> `ChatService.TOP_K` (US2, FR-009) are both constants in
> `backend/src/main/java/com/epam/aihelpdesk/chat/ChatService.java`. Their edits (T005, T011) are
> therefore **sequential, not parallel**, even though they're conceptually independent — same file,
> same reason feature 006's T005→T006 chain was sequential. The same is true of
> `specs/007-chat-endpoint/contracts/chat-api-contract.md`, corrected once for the threshold fact
> (T007) and again for the top-K fact (T012).
>
> **User Story 2 depends on User Story 1 having already landed**, not just on Foundational: T009's
> rebuilt `ChatRetrievalIT` fixture relies on all six of its candidate chunks already clearing the
> *new* `0.35` relevance bar (they sit at similarity ≈0.41–1.0, comfortably above `0.35` but three of
> them — ≈0.41, ≈0.447, ≈0.5 — sit *below* the *old* `0.5` bar). That only holds once T005 has shipped.
> This mirrors feature 006's US2-depends-on-US1 framing: not independently *implementable* before
> US1, but independently *testable* as its own increment once US1 is built.
>
> **0.35 doesn't have as clean an integer-vector construction as 0.5 did.** The original `0.5`
> boundary test exploited `0.5 = 1/√4` using only `0`/`1` integer vector components, which stays
> bit-exact through pgvector's float32 arithmetic. `0.35` is not `1/√k` for any small integer `k`, so
> T004's new boundary test uses an epsilon-tolerant assertion (e.g. AssertJ's
> `isCloseTo(0.35, within(0.0001))`) instead of exact equality — a deliberate, documented deviation
> from the existing bit-exact technique, not an oversight.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3, mapping to spec.md's user stories — omitted for Setup, Foundational,
  and Polish tasks
- Every task names its exact file path(s)

## Path Conventions

Web application structure (features 001–010, unchanged): `backend/src/main/java/com/epam/aihelpdesk/`
for production code, `backend/src/test/java/com/epam/aihelpdesk/` for tests. This feature adds no new
package — every production edit lands in the existing `ingestion`/`chat` packages. No frontend
changes (spec.md Assumptions). One edit lands outside `backend/`: the current-value-fact correction
in feature 007's already-published `specs/007-chat-endpoint/contracts/chat-api-contract.md`.

---

## Phase 1: Setup

**Purpose**: Confirm no new dependency is needed, and capture the pre-change baseline SC-003's
"after" run will later be compared against (research Decision 7) — this must happen before any other
task in this list touches production code, so it is Setup, not a User Story 3 task, even though it
exists to serve SC-003.

- [X] T001 [P] Confirm `backend/pom.xml` needs no changes for this feature (`jtokkit`, `pgvector-java`,
      and the existing Testcontainers `pgvector/pgvector:pg18` test setup already cover every change
      this feature makes — no new library, no version bump); run
      `backend\mvnw.cmd -q dependency:resolve` to confirm the current classpath still resolves
      cleanly.
- [ ] T002 [P] Run the existing curated evaluation set
      (`sample-data/evaluation-questions.csv`) against the system **as it stands today, before any
      task below changes anything** — the same way it's already being run per the constitution's
      Testing & Validation section — and record the pass count directly under this task once done.
      This is the "before" half of SC-003's manual before/after comparison (research Decision 7); no
      new tooling, no file written elsewhere in the repo, just this run's own recorded result.
      **Result**: **Not run.** This execution environment cannot bind the backend's embedded Tomcat
      socket — `spring-boot:run` (tried both directly and via the Browser pane's dev-server preview)
      fails with `java.io.IOException: Unable to establish loopback connection` from the JDK's NIO
      selector, before any HTTP endpoint becomes reachable. This is the same known loopback-connection
      limitation features 004–006's `tasks.md` already recorded for this environment (see T016). A
      Testcontainers-backed `MockMvc` context (no real socket bind) works fine here and is what
      `ChatRetrievalIT` uses instead — see T003/T004/T009's green results below — but a live
      before/after evaluation-set run needs a real developer machine or CI runner. Deferred to the user
      to execute via `quickstart.md`'s User Story 3 steps before merging; not a substitute for the
      automated-suite evidence this session did produce.

**Checkpoint**: Classpath confirmed unchanged; pre-change evaluation baseline recorded. Safe to start
User Story work.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: N/A for this feature — there is no shared infrastructure, new exception type, new
dependency, or new schema element that any user story needs before it can start. Every change this
feature makes is a self-contained constant edit (plus the tests/docs describing that constant) inside
code that already exists from features 004/007. Proceed directly to User Story 1.

---

## Phase 3: User Story 1 - A near-verbatim question gets a grounded answer instead of a refusal (Priority: P1) 🎯 MVP

**Goal**: `ChatService.SIMILARITY_THRESHOLD` moves from `0.5` to `0.35`, inclusive comparison
unchanged, so a passage a manual test showed was genuinely relevant — but scored below the old bar —
now reaches answer generation, while a question with no genuinely relevant content still gets the
fixed "not in documentation" fallback.

**Independent Test**: Ask a question closely paraphrasing a sentence that exists verbatim in an
already-ingested sample document, and confirm the response is a grounded answer citing that document
(`quickstart.md` User Story 1, Steps 1–4) — this holds regardless of whether User Story 2's chunk-size
change has landed yet, since it only needs one already-ingested passage to cross the new bar.

### Tests for User Story 1 (write first, confirm they fail before implementing)

- [X] T003 [P] [US1] In `ChatRetrievalIT.java`'s
      `everyCandidateBelowThresholdReturnsTheFixedNotCoveredResponseWithoutCallingCompletion` test,
      rebuild the single candidate's vector so its cosine similarity sits safely below the *new* `0.35`
      bar — its current construction (`axisSum(130, 131, 132, 133, 134)`, cosine `1/√5 ≈ 0.447`) is
      safely below the *old* `0.5` bar but is now *above* `0.35` and would flip this test's outcome if
      left unchanged. Extend the axis sum to more terms (e.g. `axisSum(130..139)`, ten terms,
      `1/√10 ≈ 0.316`, comfortably below `0.35`) and update the comment accordingly. This is a fixture
      safety fix, not a red/green demonstration on its own — it must land before T005 so the test
      doesn't start failing for the wrong reason (FR-002, SC-002) — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java`.
- [X] T004 [P] [US1] Add a new test, `aCandidateBetweenTheOldAndNewThresholdIsNowIncludedInclusively`,
      to `ChatRetrievalIT.java`: seed one chunk whose cosine similarity to the query is as close to
      `0.35` as a simple construction allows and asserts it survives (its `sources` entry appears,
      `score` is `isCloseTo(0.35, within(0.0001))` — see the epsilon note above, not exact equality),
      and separately seed one chunk comfortably *below* `0.35` (reuse T003's ten-term construction on
      a disjoint axis slice) and assert it does **not** survive. Confirm this test compiles and fails
      (red — the still-`0.5` production threshold excludes the near-`0.35` chunk today) before
      starting T005 — in `backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java`
      (FR-001, FR-002; inclusive-boundary requirement).

### Implementation for User Story 1

- [X] T005 [US1] Change `ChatService.SIMILARITY_THRESHOLD` from `0.5` to `0.35` — the comparison
      itself (`chunk.distance() <= (1 - SIMILARITY_THRESHOLD)`) is untouched, only the constant moves
      — in `backend/src/main/java/com/epam/aihelpdesk/chat/ChatService.java` (depends on T003, T004
      already existing and T004 failing red).
- [X] T006 [P] [US1] Update `SourceCitation.java`'s Javadoc — `@param score`'s
      `"always ≥ 0.5 (the similarity threshold)"` → `"always ≥ 0.35 (the similarity threshold)"` — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/dto/SourceCitation.java` (research Decision 6;
      constitution Spec-First — a stale current-value fact is a defect, not stale text to leave
      alone).
- [X] T007 [P] [US1] Correct `specs/007-chat-endpoint/contracts/chat-api-contract.md`'s threshold
      prose — its `FR-006` line ("Returned when at least one retrieved passage meets the 0.5
      similarity threshold") and its `FR-007` line ("falls below 0.5 similarity") both become `0.35`
      — in `specs/007-chat-endpoint/contracts/chat-api-contract.md` (research Decision 6; touches only
      the threshold-related lines — the top-K-related line is T012's job, not this task's).

**Checkpoint**: User Story 1 is independently functional — run `backend\mvnw.cmd test -Pverify-db`
(T003, T004 green), then `quickstart.md` User Story 1 Steps 1–4 against a running backend, confirming
a near-verbatim question now gets a grounded, cited answer and a genuinely unrelated question still
gets the fallback.

---

## Phase 4: User Story 2 - Retrieved passages carry less unrelated surrounding content (Priority: P2)

**Goal**: `Chunker`'s target window moves from 800 to 500 tokens (overlap scaling from 100 to 63,
same ~12.5% ratio), and `ChatService.TOP_K` moves from 4 to 5 to keep a multi-passage topic as fully
coverable as it was before passages got smaller (FR-009).

**Independent Test**: Delete and re-upload an already-ingested sample document, ask a question scoped
to one narrow part of it, and confirm the retrieved passage is noticeably smaller and less padded
with unrelated text than before (`quickstart.md` User Story 2, Steps 1–4); separately, confirm a
question about a topic spanning more than one window now retrieves up to 5 passages, not 4.

### Tests for User Story 2 (write first, confirm they fail before implementing)

- [X] T008 [P] [US2] In `ChunkerTest.java`, re-point every 800/100-token expectation to 500/63:
      `longPageProducesFullSizeInteriorWindowsWithOverlapAndAShortFinalWindow`'s interior-chunk
      token-count assertion (`800` → `500`) and its overlap check (`lastN`/`firstN` window of `100` →
      `63`); `shortPageProducesExactlyOneChunkEvenUnderTheFiveHundredTokenTarget`'s sanity check
      (`isLessThan(500)`) and its own name/doc-comment now describe the *new* floor, so retitle it
      `shortPageProducesExactlyOneChunkEvenUnderTheTargetTokenCount` and adjust the fixture's word
      count so it still stays under the new, smaller 500-token target with margin. Confirm the suite
      fails (red — `Chunker` still emits 800-token windows with 100-token overlap) before starting
      T010 — in `backend/src/test/java/com/epam/aihelpdesk/ingestion/ChunkerTest.java`.
- [X] T009 [US2] Rebuild `ChatRetrievalIT.java`'s
      `ranksChunksBySimilarityCapsAtTopKAndIncludesTheInclusiveThresholdBoundary` test as a top-K-only
      concern (the inclusive-boundary concern moved to T004): grow the fixture from 5 to 6 chunks
      (`axisSum(100)` through `axisSum(100..105)`, cosines `1.0, 0.707, 0.577, 0.5, 0.447, 0.408` —
      every one comfortably above the *new* `0.35` bar, so none of them is excluded by relevance, only
      by the cap) and assert exactly 5 survive, closest-first, with the 6th (weakest) excluded purely
      by `TOP_K`; rename the test to
      `ranksChunksBySimilarityAndCapsAtTopKWhenAllCandidatesClearTheRelevanceBar`. Confirm this fails
      (red — today's `TOP_K = 4` returns only 4 sources) before starting T011 — in
      `backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java` (depends on T005 already
      shipped — see the cross-story dependency note above).

### Implementation for User Story 2

- [X] T010 [P] [US2] Change `Chunker.TARGET_TOKENS` from `800` to `500` and `Chunker.OVERLAP_TOKENS`
      from `100` to `63` — in `backend/src/main/java/com/epam/aihelpdesk/ingestion/Chunker.java`
      (depends on T008 already existing and failing red).
- [X] T011 [US2] Change `ChatService.TOP_K` from `4` to `5` — in
      `backend/src/main/java/com/epam/aihelpdesk/chat/ChatService.java` (depends on T009 already
      existing and failing red; sequential with T005, same file — see the shared-file note above).
- [X] T012 [US2] Correct `specs/007-chat-endpoint/contracts/chat-api-contract.md`'s top-K prose —
      its "**No per-request tuning** of `TOP_K` or the similarity threshold" line's implied current
      value, and any other line stating `4`/`K=4` as current — to `5` — in
      `specs/007-chat-endpoint/contracts/chat-api-contract.md` (research Decision 6; depends on T007,
      same file, sequential).

**Checkpoint**: User Stories 1 AND 2 both work independently — `backend\mvnw.cmd test` (T008 green)
and `backend\mvnw.cmd test -Pverify-db` (T003, T004, T009 green) pass end-to-end, plus
`quickstart.md` User Story 2 Steps 1–4 against a running backend.

---

## Phase 5: User Story 3 - Retrieval quality does not regress on questions that already worked (Priority: P3)

**Goal**: Confirm the combined effect of US1 and US2 doesn't quietly regress a previously-working
class of question — a question correctly refused before this change is still refused, and the
existing evaluation set's pass rate doesn't drop.

**Independent Test**: Run the existing curated evaluation question set after both prior stories are
complete and compare its pass rate against T002's pre-change baseline (`quickstart.md` User Story 3).

No new production code — every FR this story exercises (FR-002's floor, FR-008's no-reprocessing
guarantee) is already fully implemented by T005/T010/T011. This phase is validation only, per
research Decision 7 (SC-001/SC-002/SC-003 are all verified manually, not via new automated tooling).
T003 and T004 (User Story 1) already give this story's core regression concern — "a previously-weak
candidate stays excluded" — automated, code-level coverage; the tasks below are the manual checks
spec.md's Success Criteria commit to on top of that.

- [ ] T013 [US3] **Manual** — re-ask this project's existing negative/out-of-scope test questions
      (the ones already used in manual testing and in `ChatController`/`ChatService`'s own negative
      test coverage) against the running system and confirm every one still returns the "not in
      documentation" fallback (SC-002). Record the outcome directly under this task.
      **Result**: **Not run** — needs a live backend, blocked by the same loopback-connection
      limitation recorded under T002. `ChatServiceTest`'s and `ChatRetrievalIT`'s negative-path tests
      (e.g. `aCandidateBetweenTheOldAndNewThresholdIsNowIncludedInclusively`'s excluded second chunk,
      `everyCandidateBelowThresholdReturnsTheFixedNotCoveredResponseWithoutCallingCompletion`) exercise
      this same "still correctly refused" behavior at the code level and are green; a live re-ask
      against the running system is deferred to the user via `quickstart.md`.
- [ ] T014 [US3] **Manual** — run the existing curated evaluation set
      (`sample-data/evaluation-questions.csv`) again, now that T005/T010/T011 have all shipped, and
      compare its pass count against T002's recorded baseline (SC-003). Record both counts and the
      comparison directly under this task.
      **Result**: **Not run** — depends on T002's baseline, which could not be captured (same
      loopback-connection limitation). Deferred to the user, to run both halves together via
      `quickstart.md`'s User Story 3 steps on a machine that can actually bind the backend's port.
- [ ] T015 [US3] **Manual** — re-run the same near-verbatim question set used during this feature's
      motivating manual test (the one that surfaced the original false-refusal problem) and confirm
      at least 95% now receive a grounded, cited answer (SC-001). Record the pass rate directly under
      this task.
      **Result**: **Not run** — same loopback-connection limitation; needs a live backend to ask real
      questions against. `aCandidateBetweenTheOldAndNewThresholdIsNowIncludedInclusively` (T004) is the
      automated, code-level version of this same claim (a passage that used to score below 0.5 now
      survives and produces a grounded, cited answer) and is green. Deferred to the user via
      `quickstart.md`.

**Checkpoint**: All three user stories independently functional; no regression observed on the
existing evaluation set or on previously-correct refusals.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Full end-to-end validation spanning all three stories.

- [ ] T016 Run the full `quickstart.md` validation end-to-end — User Story 1 Steps 1–4, User Story 2
      Steps 1–4 (including SC-004's manual before/after passage-content comparison), User Story 3's
      automated-suite commands (`mvnw test`, `mvnw test -Pverify-db`) and its manual evaluation-set
      re-run — confirming every Functional Requirement and Success Criterion in spec.md holds against
      a running backend. Record which steps ran live versus which (if any) hit this environment's
      known loopback-connection limitation (features 004–006's `tasks.md` precedent), and rely on the
      automated-suite evidence as the primary proof in that case.
      **Result**: **Automated-suite commands ran live and are green**: `backend\mvnw.cmd test` — 78/78
      passing, including the re-pointed `ChunkerTest` (500/63) and the unchanged `ChatServiceTest`;
      `backend\mvnw.cmd test -Pverify-db` — 29/29 passing, including `ChatRetrievalIT`'s three
      US1/US2-relevant tests (the rebuilt below-threshold fixture, the new inclusive-0.35-boundary
      test, and the rebuilt 6-candidate/TOP_K=5 cap test). **Every step requiring a live backend
      process (`mvnw spring-boot:run` / the Browser pane's dev-server preview) hit this environment's
      known loopback-connection limitation**: Tomcat's NIO `Selector` fails with
      `java.io.IOException: Unable to establish loopback connection` before any port is actually bound
      — confirmed two ways (direct `mvnw spring-boot:run`, and the Browser pane's `preview_start`),
      both failing the same way, matching the precedent features 004–006 already recorded for this
      environment. This blocks User Story 1 Steps 1/2/4-live-question-asking, User Story 2 Steps 1–4
      (re-upload + live passage-length comparison), and User Story 3's manual evaluation-set re-run
      (T002/T013/T014/T015 above) — all deferred to the user to run via `quickstart.md` on a machine
      that can bind the port. The automated-suite evidence is the primary proof for this session's
      implementation; SC-001/SC-002/SC-003/SC-004's manual, live-system verification is still
      outstanding and owned by the user.

**Checkpoint**: All three user stories independently functional, full quickstart guide passes
end-to-end, and both stale published facts (`SourceCitation.java`, feature 007's contract doc) are
corrected.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately. T002 must complete before any other task
  changes production behavior, so its "before" number stays meaningful.
- **Foundational (Phase 2)**: No tasks — nothing blocks User Story 1.
- **User Story 1 (Phase 3)**: Depends on Setup only. No dependency on US2 or US3.
- **User Story 2 (Phase 4)**: Depends on Setup **and** on User Story 1's `SIMILARITY_THRESHOLD` change
  (T005) already being live — not independently *implementable* before US1 (T009's fixture math
  assumes the new `0.35` bar is already active), though independently *testable* as its own increment
  once US1 is built, per spec.md's own Independent Test wording (mirrors feature 006's US1/US2
  relationship).
- **User Story 3 (Phase 5)**: Depends on User Story 1 **and** User Story 2 both being complete — it's
  a regression check over their combined effect, not a story with its own production code.
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### Within Each User Story

- Tests are written first and confirmed to fail before the implementation task(s) that follow them.
- US1: T003 and T004 (different concerns, same file) can be written in parallel with each other, but
  both must exist and T004 must be red before T005. T006/T007 (different files, doc-only) can follow
  T005 in parallel with each other.
- US2: T008 (`ChunkerTest.java`) and T009 (`ChatRetrievalIT.java`) touch different files and can be
  written in parallel with each other, but T009 additionally requires T005 (US1) already shipped.
  T010 depends on T008; T011 depends on T009 and is sequential with T005 (same file, `ChatService.java`).
  T012 depends on T007 (same file, sequential).

### Parallel Opportunities

- Setup: T001 and T002 are independent and can run in parallel.
- US1: T003 and T004 (different assertions in the same test *file*, but non-overlapping test
  *methods* — treat as parallel-safe for two people, sequential-safe for one); T006 and T007 (fully
  different files) can run in parallel with each other once T005 is done.
- US2: T008 and T009 (different files) can run in parallel with each other, once T005 has shipped for
  T009's sake.
- Different user stories cannot run fully in parallel across developers here, because US2 depends on
  US1's `ChatService.java` edit landing first — a single-file, single-constant dependency, not a
  broad blocking phase.

---

## Parallel Example: User Story 1

```bash
# Launch T003 and T004 together (different test methods, no dependency on each other):
Task: "Rebuild the below-threshold fixture in backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java"
Task: "Add the new inclusive-boundary test in backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java"

# Once T005 is done, launch T006 and T007 together (different files):
Task: "Update SourceCitation.java's Javadoc in backend/src/main/java/com/epam/aihelpdesk/chat/dto/SourceCitation.java"
Task: "Correct the threshold prose in specs/007-chat-endpoint/contracts/chat-api-contract.md"
```

## Parallel Example: User Story 2

```bash
# Launch T008 and T009 together (different files; T009 additionally needs T005 already shipped):
Task: "Re-point ChunkerTest.java's assertions to 500/63 in backend/src/test/java/com/epam/aihelpdesk/ingestion/ChunkerTest.java"
Task: "Rebuild the top-K cap test in backend/src/test/java/com/epam/aihelpdesk/chat/ChatRetrievalIT.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T002).
2. Complete Phase 3: User Story 1 (T003–T007) — no Foundational phase to complete first.
3. **STOP and VALIDATE**: `backend\mvnw.cmd test -Pverify-db` green; `quickstart.md` User Story 1
   passes against a running backend.
4. This is a demonstrable MVP: a near-verbatim question that used to be falsely refused now gets a
   grounded, cited answer (SC-001's core claim), while genuinely out-of-scope questions are still
   refused.

### Incremental Delivery

1. Setup → pre-change baseline recorded (T002).
2. Add User Story 1 → validate independently → the false-refusal problem is fixed (MVP!).
3. Add User Story 2 → validate independently → passages are smaller and less diluted, and topic
   coverage is preserved via the raised `TOP_K`.
4. Add User Story 3 → validate independently → no regression on previously-correct refusals or on
   the existing evaluation set.
5. Polish → full quickstart run, both stale published facts corrected.

### Parallel Team Strategy

With two developers: one carries T003→T004→T005→(T006‖T007) for User Story 1 to its checkpoint; the
second starts T008 (`ChunkerTest.java`) immediately (no dependency on US1) but must wait for US1's
checkpoint before finishing T009→T011 (which need `SIMILARITY_THRESHOLD` already at `0.35`) and T012
(which needs T007 already landed in the same file). User Story 3 (T013–T015) is a single reviewer's
manual pass once both prior stories are checkpointed — not parallelizable across developers in any
meaningful way.

---

## Requirement Coverage

Every functional requirement and success criterion maps to at least one task:

| Requirement | Task(s) |
|---|---|
| FR-001 | T003, T004, T005 |
| FR-002 | T003, T004, T005, T013 |
| FR-003 | T008, T010 |
| FR-004 | T008, T010 |
| FR-005 | T008, T010 (values checked against the constitution's 500–1000/10–15% ranges in research Decisions 2–3, no code enforces the range itself) |
| FR-006 | T008, T010 (metadata fields untouched by construction — `Chunker` never touches `pageNumber`/`chunkId`/`sourceFilename`) |
| FR-007 | T005, T010, T011 (differing immediate/prospective effect is inherent to where each constant is read — query time vs. ingestion time — not a separate code task) |
| FR-008 | T010 (no reprocessing code is added — this requirement is satisfied by the *absence* of a migration task, verified by inspection) |
| FR-009 | T009, T011 |
| SC-001 | T004, T015 |
| SC-002 | T003, T013 |
| SC-003 | T002, T014 |
| SC-004 | T016 (manual passage-content comparison, per spec.md's own "Verified manually" methodology) |

---

## Notes

- `[P]` tasks touch different files, or non-overlapping methods in the same test file, with no
  dependency on each other.
- `[Story]` labels (US1/US2/US3) trace every user-story task back to spec.md's priorities.
- Tests are written and confirmed failing before the implementation task(s) that make them pass
  (constitution Principle II) — T003 is the one exception, a fixture *safety* fix rather than a
  red/green demonstration, called out explicitly as such in its own task description.
- Commit after each task or logical group.
- Stop at each phase checkpoint to validate that story's Independent Test criterion before moving on.
- Avoid: reintroducing a bit-exact vector construction for the `0.35` boundary (T004 already
  documents why that's impractical here, unlike `0.5`'s clean `1/√4` case); adding a bulk
  document-reprocessing task (explicitly out of scope, spec.md Assumptions); leaving either
  `SourceCitation.java`'s Javadoc or feature 007's contract doc uncorrected while fixing the other
  (T006/T007 and T012 each correct a distinct, independently-stale fact — none is optional).

# Implementation Plan: Retrieval Accuracy Tuning

**Branch**: `main` (no dedicated feature branch — no `before_specify`/`before_plan` hook is
registered in `.specify/extensions.yml`, same situation features 004–006's plans recorded) |
**Date**: 2026-08-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/011-retrieval-accuracy-tuning/spec.md`

## Summary

Retune three existing, project-wide constants that manual testing — and a `/speckit-checklist` pass
over the resulting spec — showed were mis-set, all three already framed by the constitution itself as
tunable "defaults": `ChatService.SIMILARITY_THRESHOLD` moves from `0.5` to `0.35` so genuinely
relevant passages stop being discarded before answer generation ever sees them (User Story 1);
`Chunker`'s target window moves from 800 to 500 tokens (its overlap scaling proportionally, 100 → 63)
so a retrieved passage carries less unrelated surrounding content (User Story 2); and
`ChatService.TOP_K` moves from `4` to `5` so a topic now split across more, smaller passages stays as
fully coverable as it was before (also User Story 2, added after the checklist flagged the coverage
risk of shrinking passages without widening the retrieval cap). No schema change, no new dependency,
no new endpoint, no new persisted field, and no frontend change — this is a three-constant behavior
change plus the test/fixture/doc updates that follow from it.

Seven decisions carry the design (full reasoning in [research.md](research.md)):

- **500 tokens, not 400** — a literal half of 800 would fall under the constitution's 500–1000 token
  floor (Chunking & Embedding Strategy section); confirmed with the user directly rather than
  amending the constitution, so 500 (the floor) is the new target.
- **Overlap scales with the target, keeping the same ~12.5% ratio** — 100/800 becomes 63/500 (12.6%),
  staying inside the constitution's 10–15% band rather than becoming a fixed count that would drift
  outside it as the window shrinks.
- **No reprocessing of already-ingested documents** — the new `Chunker` values apply to documents
  ingested from now on; an already-stored document keeps its previous chunk sizes until it is
  deleted and re-uploaded through the existing `POST /documents`/`DELETE /documents/{id}` endpoints.
  The lowered threshold and the raised top-K, by contrast, apply immediately to every question, since
  both are query-time behavior over whatever chunks already exist.
- **Top-K raised from 4 to 5** — added after a `/speckit-checklist` pass showed smaller passages plus
  a lower threshold could otherwise regress topic coverage against a fixed retrieval cap; confirmed
  with the user, and already inside the constitution's own "K ≈ 4–6" evaluation-range wording, so no
  amendment is needed for this either.
- **Existing tests are edited in place, not duplicated** — `ChunkerTest` and `ChatRetrievalIT`'s
  fixtures/assertions move to the new values directly, following this codebase's existing pattern
  across features 004–010. `ChatServiceTest` needs no change: its own threshold-adjacent fixtures use
  distances `0.1`/`0.9`/`0.95` (similarity `0.9`/`0.1`/`0.05`), all comfortably clear of both the old
  `0.5` and the new `0.35` boundary, and its mocked `ChatRetrievalRepository` ignores the `topK`
  argument entirely — verified by inspection before writing this plan, not assumed.
- **Stale published-contract facts get corrected as part of this feature** —
  `SourceCitation.java`'s Javadoc and feature 007's `contracts/chat-api-contract.md` both currently
  assert `0.5`/`4` as the live threshold/top-K; both are corrected, per the constitution's Spec-First
  rule that documentation contradicting shipped behavior is a defect, not stale text to leave alone.
- **SC-001/SC-002/SC-003 are verified manually, not against a stored baseline** — the checklist pass
  also showed those success criteria implied a baseline number that was never actually recorded;
  confirmed with the user to specify the verification *procedure* (re-run before/after, compare)
  instead of inventing a number retroactively.

## Technical Context

**Language/Version**: Java 17, Spring Boot 3.5.16 (unchanged from features 001–010).

**Primary Dependencies**: none new. The three changed values live in classes already on the
classpath — `ChatService` (feature 007) and `Chunker` (feature 004) — using the same `jtokkit`
`cl100k_base` encoding and the same `pgvector` `<=>` operator already in place.

**Storage**: Same PostgreSQL 18 + pgvector instance and `documents`/`chunks` tables (feature 003) —
no schema change, no migration. Existing rows are untouched (research Decision 4); only newly
ingested documents produce rows shaped by the new chunk-size constants.

**Testing**: JUnit 5 (existing stack), same three-tier split features 007–009 already established:
- **Unit** (`ChunkerTest`, always run): window/overlap assertions re-pointed from 800/100 to 500/63.
- **Unit/contract** (`ChatServiceTest`, always run, mocked collaborators): no change needed — its
  fixtures already sit comfortably away from both the old and new threshold, and `topK` is mocked
  away entirely (confirmed by inspection, see Summary).
- **`@Tag("db")`** (`ChatRetrievalIT`, `verify-db` profile, Testcontainers real pgvector), two tests
  affected:
  - `ranksChunksBySimilarityCapsAtTopKAndIncludesTheInclusiveThresholdBoundary` — its crafted vectors
    are rebuilt so the exact-threshold case lands on `0.35` instead of `0.5`, and the fixture grows
    from 5 to 6 chunks so the cap-exclusion case lands on the 6th chunk instead of the 5th (research
    Decision 6) — same technique (integer 0/1 axis components for a bit-exact float32 boundary), new
    target ratio/count.
  - `everyCandidateBelowThresholdReturnsTheFixedNotCoveredResponseWithoutCallingCompletion` — its
    single candidate currently sits at similarity `1/√5 ≈ 0.447` ("safely below the 0.5 threshold"
    per its own comment), which is now *above* the new `0.35` threshold and would flip this test's
    outcome if left unchanged; its vector is rebuilt to a similarity safely below `0.35` instead
    (found by inspection while writing this plan, not by first running the suite — research Decision
    6's scope explicitly includes this test, not only the boundary test).
- **No new `azure` tier**: the existing evaluation-set run (`sample-data/evaluation-questions.csv`,
  constitution Testing & Validation section) remains the mechanism for User Story 3's regression
  check, now explicitly run manually before *and* after the change and compared directly (research
  Decision 7) — this feature adds no new automated live-Azure test and no new baseline-storage
  tooling.

**Target Platform**: Same as features 001–010 — local developer machine, Docker Compose for
PostgreSQL only; backend runs locally (`mvnw spring-boot:run`).

**Project Type**: Web application (existing structure). Backend-only change — spec.md's Assumptions
explicitly rule out any new user-facing control, so no Angular code changes.

**Performance Goals**: No new goal. A smaller `TARGET_TOKENS` means a newly-ingested document
produces more, smaller chunk rows than before (more `INSERT`s, more embedding-request line items,
still governed by `EmbeddingClient`'s existing `MAX_BATCH_SIZE = 2048` sub-batching), and a larger
`TOP_K` means one more passage is retrieved and sent to the chat completion call per question — both
a scale change, not a new performance requirement; the 16-document sample corpus this PoC targets
stays well inside what a single Azure OpenAI embedding batch call and a 5-passage chat completion
prompt both already handle today (spec.md Assumptions).

**Constraints**:
- The new chunk-size target MUST stay inside the constitution's existing 500–1000 token range
  (research Decision 2) — 500 is the floor, not a value requiring a constitution amendment.
- The new overlap MUST stay inside the constitution's existing 10–15% band (research Decision 3).
- The new top-K MUST stay inside the constitution's "K ≈ 4–6" evaluation-range wording (research
  Decision 5) — 5 is within it, not a value requiring a constitution amendment.
- The threshold comparison's inclusive-boundary semantics (`distance <= 1 - threshold`) MUST NOT
  change — only the constant moves, not the comparison operator or direction.
- Already-stored `chunks` rows MUST NOT be silently deleted, corrupted, or mixed with
  differently-sized rows as a side effect of this change (FR-008) — no migration touches them.

**Scale/Scope**: Three production constants changed (`ChatService.SIMILARITY_THRESHOLD`,
`ChatService.TOP_K`, `Chunker.TARGET_TOKENS` + `Chunker.OVERLAP_TOKENS`), one Javadoc correction
(`SourceCitation.java`), one prior-feature contract-doc correction
(`specs/007-chat-endpoint/contracts/chat-api-contract.md`), and the test/fixture updates that follow
directly from the constant changes (`ChunkerTest`, two tests in `ChatRetrievalIT`; `ChatServiceTest`
needs no change). No new class, no new package, no new endpoint, no new persisted entity.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.4.1** (unchanged since feature 010; no
amendment has landed between that feature and this one — and this feature deliberately does not
introduce one, research Decisions 2 and 5).

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` precedes this plan; the governance conflict the spec surfaced (500–1000 floor vs. literal halving) was resolved with the user before this plan was written, not silently, and a subsequent `/speckit-checklist` pass's two findings (coverage risk, unverifiable success criteria) were folded back into spec.md before this plan was finalized, not left as known gaps. This plan also corrects now-stale facts in feature 007's contract doc rather than leaving them to contradict shipped behavior. |
| II | Test-Driven Development (Mandatory) | ✅ PASS | No new behavior is added without existing test coverage already in place to re-point — `ChunkerTest`/`ChatRetrievalIT` already assert the exact values this feature changes, so "adjust the failing assertions to the new expected values, watch them pass" is this feature's whole TDD cycle. The default suite still needs no live DB/Azure credentials. |
| III | Grounded Answers (RAG-First) | ✅ PASS | Answers remain sourced exclusively from retrieved chunks; this feature changes which chunks clear the bar, how many are retrieved, and how large they are, never the sourcing discipline itself. |
| IV | No Hallucination (Context Adherence) | ✅ PASS | The "not in documentation" fallback path (FR-002) is explicitly preserved — lowering the bar and raising top-K widen what's retrieved and accepted, they do not remove the floor or let ungrounded content through (spec.md Acceptance Scenario, User Story 1 #2 / User Story 3 #2 / SC-002). |
| V | Semantic Understanding (Meaning-Based Retrieval) | ✅ PASS | Same embedding deployment, same query-time embedding call, same cosine-distance ranking — this feature tunes the acceptance bar, retrieval breadth, and passage granularity, not the retrieval mechanism itself. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ⏭️ N/A — unaffected | No change to where vectors are stored or which provider is called; same self-hosted pgvector instance, same Azure OpenAI deployments. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ✅ PASS | SC-003 explicitly requires the evaluation set score at least as high after this change, verified by running it before and after and comparing directly (research Decision 7); this is the primary validation gate for the whole feature, not an afterthought. |

**AI Provider Configuration compliance**: N/A — no change to how `AZURE_OPEN_AI_*` variables are
read, validated, or used; `EmbeddingClient`/`ChatCompletionClient` are unmodified by this feature.

**Chunking & Embedding Strategy compliance**: the new 500-token target and 63-token (12.6%) overlap
both satisfy the constitution's literal "500–1000 tokens... 10–15% overlap" wording (research
Decisions 2–3) — this is one of two constitution sections this feature's numbers had to be checked
against most carefully, and both new values land inside the stated ranges with no amendment needed.

**Ingestion Pipeline compliance**: unchanged — `Chunker` still runs at ingestion time only (never
query time), atomicity of the chunk write is untouched, Tika parsing is untouched.

**Query Pipeline compliance**: the "top-K (default K=4)" and "threshold (default: 0.5 cosine
similarity)" wording in the constitution's Query Pipeline section already frames both as tunable
defaults, not fixed requirements — this feature exercises exactly that tunability for both, and the
new top-K (5) also stays inside Principle VII's separately-stated "K ≈ 4–6" evaluation range
(research Decision 5) — the second constitution section checked most carefully for this feature.

**Error Handling & Logging compliance**: N/A — no new error path, no new external call, no change to
any exception type or status-code mapping.

**Testing & Validation compliance**: the evaluation set (`sample-data/evaluation-questions.csv`),
run manually before and after this change and compared directly (research Decision 7), is this
feature's central acceptance gate (User Story 3), consistent with the constitution's own mandate
that it run after every major change.

**Code & Documentation Language Standard compliance**: this plan and all Phase 0/1 artifacts are in
English ✅; the doc corrections this feature makes (`SourceCitation.java` Javadoc, feature 007's
contract doc) stay in English ✅.

**Technology Stack compliance**: Java 17 / Spring Boot 3 ✅; no new dependency ✅; PostgreSQL +
pgvector ✅ (no schema change); no deviation from the mandated tech stack anywhere in this feature.

**Post-Phase 1 re-check**: ✅ No change. Phase 1 design (data-model.md, contracts/, quickstart.md)
confirmed all three new numeric values sit inside the constitution's existing ranges, introduced no
new dependency, no new persisted entity, and no deviation from the mandated tech stack.

**Gate result**: PASS — no violations, no justifications required. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/011-retrieval-accuracy-tuning/
├── plan.md                                  # This file
├── research.md                              # Phase 0 — 7 decisions
├── data-model.md                            # Phase 1 — tuning-value table (no new persisted entities)
├── quickstart.md                            # Phase 1 — bring-up and per-user-story validation
├── contracts/
│   └── retrieval-tuning-contract.md         # Corrected current-value facts; no new endpoint/schema
├── checklists/
│   ├── requirements.md                      # Spec quality checklist — all items pass
│   └── retrieval-tuning.md                  # Requirements-quality checklist — findings folded back into spec.md
└── tasks.md                                 # Phase 2 — created by /speckit-tasks, NOT by this command
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                                     # UNCHANGED — no new dependency
├── src/main/java/com/epam/aihelpdesk/
│   ├── ingestion/
│   │   ├── Chunker.java                                        # MODIFIED — TARGET_TOKENS 800→500, OVERLAP_TOKENS 100→63
│   │   └── (EmbeddingClient.java, DocumentController.java, ...) # UNCHANGED
│   └── chat/
│       ├── ChatService.java                                    # MODIFIED — SIMILARITY_THRESHOLD 0.5→0.35, TOP_K 4→5
│       ├── dto/SourceCitation.java                              # MODIFIED — Javadoc "≥ 0.5" → "≥ 0.35"
│       └── (ChatController.java, ChatRetrievalRepository.java, ...) # UNCHANGED
└── src/test/java/com/epam/aihelpdesk/
    ├── ingestion/
    │   └── ChunkerTest.java                                    # MODIFIED — assertions re-pointed to 500/63
    └── chat/
        ├── ChatServiceTest.java                                # UNCHANGED — fixtures already clear of both boundaries (see Summary)
        └── ChatRetrievalIT.java                                # MODIFIED — 2 tests: 0.5/4-cap fixture re-pointed to 0.35/5-cap; below-threshold fixture's 0.447 similarity rebuilt safely under 0.35

specs/007-chat-endpoint/
└── contracts/chat-api-contract.md                              # MODIFIED — "0.5"/"4" current-value facts corrected to "0.35"/"5" (research Decision 6)

frontend/                                                       # UNCHANGED — spec.md Assumptions rule out any new user-facing control
```

**Structure Decision**: Web application structure from features 001–010 is unchanged. Every
production-code edit lands inside the two existing packages (`ingestion`, `chat`) that already own
the constants being tuned — no new package, no new top-level directory, no frontend change. The one
edit outside `backend/` and this feature's own `specs/011-.../` directory is the current-value-fact
correction in feature 007's already-published contract doc (research Decision 6), made because
leaving it stating `0.5`/`4` would make it actively wrong about shipped behavior, not because this
feature is reopening feature 007's scope.

## Complexity Tracking

*No entries — the Constitution Check gate passed with no violations requiring justification.*

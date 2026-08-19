# Phase 0 Research: Retrieval Accuracy Tuning

**Date**: 2026-08-19 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

Seven decisions. No `[NEEDS CLARIFICATION]` markers remain — the one open question spec.md's
Assumptions section originally flagged (the constitutional 500–1000 token floor conflicting with a
literal "half of 800") was resolved directly with the user before Phase 0 began: adopt 500 tokens,
leave the constitution's range untouched. Two further decisions (5 and 7 below) were added after a
`/speckit-checklist` pass surfaced a real coverage gap and two unverifiable success criteria; both
were also resolved directly with the user rather than left as spec-time guesses.

## Decision 1: Lower `ChatService.SIMILARITY_THRESHOLD` from `0.5` to `0.35`, no other change to the filter

- **Decision**: [`ChatService.java:46`](../../backend/src/main/java/com/epam/aihelpdesk/chat/ChatService.java)'s
  `SIMILARITY_THRESHOLD` constant changes from `0.5` to `0.35`. The comparison itself
  (`chunk.distance() <= (1 - SIMILARITY_THRESHOLD)`, inclusive) is untouched — only the constant's
  value moves, per the manual-testing finding that genuinely relevant passages were scoring in the
  0.4–0.6 range and being discarded before ever reaching answer generation.
- **Rationale**: This is exactly the single, project-wide tuning point the constitution's own
  wording anticipates — Query Pipeline (constitution.md line 232) calls `0.5` a "default," not a
  fixed requirement, and `ChatService.java`'s own Javadoc already documents this as the one place
  the value lives. Changing one constant is the smallest change that satisfies FR-001/FR-002.
- **Alternatives considered**: a per-request or per-document configurable threshold (rejected —
  spec.md Assumptions explicitly rules out a new user-facing control; this is an internal tuning
  value, not a feature); a relative/gap-based threshold instead of an absolute cutoff (rejected —
  bigger redesign than the manual-testing finding calls for, and the constitution's Query Pipeline
  section specifically describes a single absolute cosine-similarity threshold, not a relative one;
  revisiting that shape is a separate decision from tuning its value).

## Decision 2: Lower `Chunker`'s target window from 800 to 500 tokens — the constitutional floor, not a literal half

- **Decision**: [`Chunker.java:32`](../../backend/src/main/java/com/epam/aihelpdesk/ingestion/Chunker.java)'s
  `TARGET_TOKENS` changes from `800` to `500`. A literal half of 800 (400) is not used, because the
  constitution's Chunking & Embedding Strategy section (line 211) sets a hard `500–1000` token
  range; 500 is the smallest value that both meaningfully shrinks passages (a 37.5% reduction) and
  stays inside that range without amending it.
- **Rationale**: Confirmed directly with the user in the specification session: take 500 tokens, do
  not touch the constitution. This keeps the change a same-day, code-only tuning fix rather than a
  governance amendment with its own ratification process.
- **Alternatives considered**: 400 tokens via a constitution amendment first (rejected by the user —
  explicitly out of scope for this feature); leaving the target at 800 and instead re-ranking or
  re-chunking retrieved passages at query time into smaller sub-passages (rejected — meaningfully
  bigger change than the manual-testing finding calls for, and duplicates work `Chunker` already
  does once at ingestion time, which the constitution's Ingestion Pipeline section deliberately
  optimizes for — "not query time").

## Decision 3: Scale `OVERLAP_TOKENS` from 100 to 63, keeping the same ~12.5% ratio

- **Decision**: `Chunker.java`'s `OVERLAP_TOKENS` changes from `100` to `63` (500 × 0.125 = 62.5,
  rounded up). 63/500 = 12.6%, inside the constitution's mandated 10–15% overlap band, and close to
  the exact ratio the previous 100/800 = 12.5% already used.
- **Rationale**: FR-004 requires the overlap ratio to scale down together with the smaller target
  rather than staying a fixed token count (which would have silently become a much larger fraction
  of a smaller chunk, e.g. 100/500 = 20%, outside the band). Preserving the same ratio the codebase
  already chose and tested for (rather than picking a different point inside the 10–15% band) is
  the smallest change — no new rationale is needed for why 12.5%-ish is the right ratio, since
  that's already an existing, working decision this feature isn't revisiting.
- **Alternatives considered**: a round number like 60 tokens (12.0%, also inside the band) —
  rejected only for being a slightly less faithful match to the existing ratio, not for any
  correctness reason; either value would satisfy FR-004 and the constitution equally well.

## Decision 4: No reprocessing of already-ingested documents; the change applies to ingestion going forward only

- **Decision**: Neither this feature's implementation nor its task list adds a bulk
  "re-chunk every existing document" capability. `chunks` rows already written under the previous
  800/100 windows are left exactly as they are; they keep being retrieved and scored (now against
  the new `0.35` threshold, since that filter is applied at query time, not ingestion time) exactly
  as before. Only a document removed and re-uploaded through the existing `DELETE`/`POST /documents`
  endpoints (features 004/006) gets re-chunked at the new 500/63 size.
- **Rationale**: Matches spec.md's Assumptions and FR-008 exactly, and keeps this feature's scope to
  what the user actually asked for (two constants), not a data-migration project. It also avoids a
  correctness trap: an automatic bulk reprocessing job would need its own atomicity story (constitution's
  "all chunks from one document succeed or all fail" rule) and its own retry/failure handling for
  Azure OpenAI re-embedding calls across the whole corpus — real scope the spec explicitly excludes.
- **Alternatives considered**: an automatic reprocessing job triggered by this change (rejected —
  explicitly out of scope per spec.md Assumptions); a database migration that discards and
  re-derives existing `chunks` rows without re-embedding (rejected — would leave stored vectors that
  no longer correspond to the stored text, silently violating constitution Principle V's "vectors
  produced by different models/inputs are not comparable" spirit).

## Decision 5: Raise `ChatService.TOP_K` from 4 to 5 to offset the smaller passage size

- **Decision**: [`ChatService.java:43`](../../backend/src/main/java/com/epam/aihelpdesk/chat/ChatService.java)'s
  `TOP_K` constant changes from `4` to `5`. Nothing else about the retrieval query
  (`ChatRetrievalRepository`'s `LIMIT :k`) changes — the value moves, the mechanism doesn't.
- **Rationale**: A `/speckit-checklist` pass surfaced a real gap (CHK004): shrinking passages
  (Decision 2/3) means a topic that used to fit inside 2–3 of the previous ~800-token passages can
  now need more of the smaller ~500-token ones to stay fully covered, and the lowered threshold
  (Decision 1) simultaneously makes more candidates eligible in the first place — two independent
  pressures on the same fixed cap. Confirmed directly with the user: raise `TOP_K` by one to absorb
  both, rather than leaving User Story 2's "shrinking passage size does not cause a topic to lose
  coverage" claim resting only on the previous cap. Like the threshold, the constitution's Query
  Pipeline section already frames top-K as a tunable "default" (`"default K=4"`), so this needs no
  constitution amendment either.
- **Alternatives considered**: leaving `TOP_K` at 4 and accepting that some multi-passage topics might
  now be less completely covered (rejected — this is exactly the coverage regression risk the
  checklist flagged, and the spec's own User Story 2 already claims coverage is preserved); raising it
  by more than one, e.g. to 6–8, to more aggressively guarantee coverage (rejected — the user asked
  for one additional passage, not a re-derivation of the constitution's "K ≈ 4–6" evaluation range;
  5 is the smallest change that meaningfully addresses the gap and stays inside that stated range).

## Decision 6: Existing tests and stale published-contract facts are updated in place, not superseded by a parallel copy

- **Decision**: The test fixtures and assertions in `ChunkerTest.java` (800/100-token expectations)
  and two tests in `ChatRetrievalIT.java` (the 0.5-boundary/4-passage-cap test, and the
  below-threshold test whose `1/√5 ≈ 0.447` fixture is now above the new `0.35` bar) are edited to
  the new 500/63/0.35/5 values directly, rather than adding a second parallel set of tests.
  `ChatServiceTest.java` needs no equivalent edit — its fixtures were confirmed, by inspection, to
  already sit clear of both the old and new threshold and to mock away `topK` entirely. Three pieces
  of prose that document the previous literal values as current fact — `SourceCitation.java`'s
  Javadoc (`"always ≥ 0.5"`), and
  `specs/007-chat-endpoint/contracts/chat-api-contract.md`'s FR-006/FR-007 wording (`"the 0.5
  similarity threshold"`) together with its "top-K" prose wherever it states `4` as current — are
  corrected to `0.35`/`5` as part of this feature's task list, per the constitution's Spec-First
  principle ("documentation that contradicts shipped behaviour is treated as a defect, not as stale
  text").
- **Rationale**: This codebase's existing pattern (features 004–010) always updates the test file
  that already covers the behavior being changed, rather than leaving stale assertions alongside new
  ones. `docs/poc-concept.md` (line 80, "~500–1000 token chunks") and the constitution's own
  "default: 0.5"/"default K=4"/"500–1000 token" wording need no edit — all three already describe the
  *range*/the fact that the values are tunable defaults, which stays true; only the files that assert
  the specific *current value* as fact go stale and need the correction.
- **Alternatives considered**: leaving feature 007's contract doc untouched as a "historical record"
  (rejected — that document doesn't read as a historical snapshot, it reads as the current, binding
  contract for `POST /chat`, and constitution Principle I is explicit that a document contradicting
  shipped behavior is a defect regardless of which feature originally wrote it).

## Decision 7: SC-001/SC-002/SC-003 are verified manually; no automated baseline dataset is introduced

- **Decision**: All three success criteria that compare "after this change" against "before this
  change" (SC-001's false-refusal rate, SC-002's refusal reliability, SC-003's evaluation-set score)
  are verified by a reviewer manually re-running the relevant question set before and after the
  change and comparing outcomes directly — not by checking against a numeric baseline value recorded
  in this spec or captured in new automated tooling.
- **Rationale**: A `/speckit-checklist` pass surfaced that SC-001 and SC-003 as originally worded
  implied a stored baseline number that didn't actually exist anywhere in this spec or its linked
  artifacts (CHK001/CHK002), which would have made both criteria unverifiable as written. Confirmed
  directly with the user: rather than inventing a baseline figure retroactively (which would have no
  real grounding) or building new tooling to capture one going forward (bigger scope than this
  feature's two-constant tuning fix), the spec now states the verification *procedure* explicitly —
  run the same question set before/after and compare — which is checkable without any stored number.
  This also matches how spec.md's Assumptions already frames validation: the existing curated
  evaluation set and manual-testing process, not new tooling.
- **Alternatives considered**: recording a specific baseline percentage in this spec based on the
  original manual test that motivated the feature (rejected — no such number was actually captured
  during that manual test, so writing one down now would be fabricated precision, not a real
  baseline); building a small script/fixture to snapshot and store evaluation-set results
  automatically before this change ships (rejected — real scope beyond what the user asked for, and
  spec.md's Assumptions already commits to "no new evaluation tooling is introduced").

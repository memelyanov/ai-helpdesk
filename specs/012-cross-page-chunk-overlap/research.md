# Phase 0 Research: Cross-Page Chunk Overlap

All four items below were fully resolved during `/speckit-specify` and `/speckit-clarify` (spec.md
Assumptions + Clarifications, Session 2026-08-21); nothing here carries a `NEEDS CLARIFICATION`
marker into Phase 1. This file records the *implementation-level* decisions that turn the spec's
FR-001–FR-010 into a concrete change to `Chunker.java`.

## Decision 1: Two-pass per-page algorithm with page-local lookback/lookahead seeds

- **Decision**: `Chunker.chunk()` first filters `pages` down to the non-blank ones and tokenizes
  each with `ENCODING.encode()` exactly as today, building an ordered list of (pageNumber,
  ownTokens) entries. It then iterates that list by index `i`, so each page's own token-window
  loop (unchanged from the current implementation) has cheap access to `ownTokens` of `i-1` and
  `i+1` when they exist. No document-wide concatenated token stream is ever built.
- **Rationale**: This is the minimal change that satisfies FR-001–FR-002 (both directions) without
  touching the per-page window math (start/end index bookkeeping) the current implementation and
  its existing tests (`ChunkerTest`) already rely on. Because blank-page filtering happens once, up
  front, FR-005 ("skip over blank/empty intervening pages") falls out for free: index `i-1`/`i+1` in
  the filtered list is already the nearest non-blank neighbor, however many blank pages sat between
  them in the original `pages` list.
- **Alternatives considered**:
  - *Single document-wide token stream* (concatenate every page's text, tokenize once, window over
    the whole thing) — rejected in the original `Chunker` design (research.md of feature 004,
    Decision 3) specifically to keep `pageNumber` exact per FR-007, and rejected again here for the
    same reason: it would require re-deriving page boundaries from token offsets after the fact,
    which is exactly the "average or a guess" feature 004 ruled out.
  - *Carry-over only in the forward direction* (the shape originally sketched before this feature
    was specified: prepend the previous page's tail to the next page's stream) — rejected because
    it only solves half of User Story 1/3; the *last* chunk of a page still would not know what
    follows it, which the spec's Clarifications session and FR-001 explicitly require.

## Decision 2: Only a page's first and last window are extended; interior windows are untouched

- **Decision**: For a given page's own token-window loop (the existing `while (start <
  tokens.size())` loop, unchanged), only two window texts are materialized differently from today:
  - The **first** window of the page: `decode(trailingSeed(prevPage) + ownTokens[start:end])`.
  - The **last** window of the page: `decode(ownTokens[start:end] + leadingSeed(nextPage))`.
  - If a page produces exactly **one** window (both first and last at once — the FR-010 collision
    case), it gets both: `decode(trailingSeed(prevPage) + ownTokens[start:end] +
    leadingSeed(nextPage))`.
  - Every other (interior) window is `decode(ownTokens[start:end])`, byte-for-byte identical to the
    current implementation.
- **Rationale**: This keeps the well-tested interior-window and overlap math (`ChunkerTest`'s
  `longPageProducesFullSizeInteriorWindowsWithOverlapAndAShortFinalWindow`) completely unchanged —
  only the text *materialization* of the two boundary windows differs, not the `start`/`end` index
  arithmetic that decides how many windows a page needs or how they overlap each other. It also
  means every existing `ChunkerTest` case that uses a single-page `pages` list (no neighbor to
  borrow from) needs no changes at all: `trailingSeed`/`leadingSeed` are both empty for a page with
  no non-blank neighbor on that side, which is exactly today's behavior.
- **Alternatives considered**: Re-deriving every page's own window boundaries from a
  seed-prepended combined stream (i.e., treat `trailingSeed + ownTokens` as the stream to window
  over from scratch, as in the original single-directional sketch) — rejected because it shifts
  every window's start/end for that page, not just the first one, making the change harder to
  reason about and to test against the existing suite, for no behavioral benefit (FR-003 only
  requires the *boundary* windows to carry the excerpt, not the whole page's windowing to shift).

## Decision 3: Cross-page excerpt size reuses `OVERLAP_TOKENS` unchanged

- **Decision**: `trailingSeed`/`leadingSeed` each borrow `min(OVERLAP_TOKENS, neighbor page's own
  token count)` tokens — the same `OVERLAP_TOKENS = 63` constant already used for same-page overlap,
  no new constant introduced.
- **Rationale**: FR-003 requires this explicitly ("MUST equal the same overlap amount already used
  between consecutive chunks within a single page"), and it keeps the constitution's 10–15% overlap
  band as the single source of truth for "how much context is enough" rather than introducing a
  second, independently-tunable number that could drift out of that band. The `min(...)` guards the
  spec's edge case ("a page's own text is shorter than the excerpt size normally borrowed").
- **Alternatives considered**: A larger, independently-tuned cross-page excerpt size (e.g. a full
  sentence via NLP boundary detection) — rejected per spec.md Assumptions: the mechanism is
  intentionally mechanical/token-count-based, matching how same-page overlap already works, not a
  linguistic one.

## Decision 4: Anchor-page attribution requires no new code — it already falls out of the loop structure

- **Decision**: `ChunkDraft.pageNumber()` for every window built inside page `i`'s own loop
  iteration is simply `pages.get(i).pageNumber()`, exactly as today. FR-004/FR-010's "anchor page"
  rule (Clarifications, Session 2026-08-21: always the page whose loop built the chunk, regardless
  of borrowed-vs-native token ratio) requires no conditional logic, no token-count comparison, and
  no special case — it is the natural consequence of Decision 1's per-page loop structure, where a
  window's page number was never computed from its content in the first place.
- **Rationale**: The clarification's resolution (Option B: always the anchor/loop page) was chosen
  specifically because it is what the existing implementation already does structurally; adopting
  literal token-count majority (the rejected Option A) would have required new cap/shrink logic at
  the FR-010 boundary case for no behavioral difference in the ordinary (single-excerpt) case.
- **Alternatives considered**: See spec.md Clarifications for the three options weighed (cap
  excerpts to preserve literal majority; anchor-page regardless of ratio — chosen; drop the second
  excerpt entirely in the collision case).

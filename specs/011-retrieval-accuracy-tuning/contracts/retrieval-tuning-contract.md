# Contract: retrieval tuning values

**Feature**: [Retrieval Accuracy Tuning](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

This feature defines no new endpoint, request, or response shape — `POST /chat`
([feature 007's contract](../../007-chat-endpoint/contracts/chat-api-contract.md)) and
`POST /documents` ([feature 004's contract](../../004-document-ingestion-endpoint/contracts/ingestion-api-contract.md))
are byte-identical before and after this change. What this document fixes is the three published,
current-value facts that this feature makes stale, and states the corrected values other features
and callers should now treat as current.

## Corrected facts

- **`POST /chat`'s acceptance threshold is `0.35` cosine similarity, inclusive**, not `0.5`.
  Supersedes feature 007's contract wherever it states `0.5` as the current threshold (its
  `FR-006`/`FR-007` prose) — that document's own file is updated alongside this feature (research
  Decision 5) rather than left to silently disagree with this one.
- **`POST /chat` retrieves the top `5` nearest passages, not `4`.** Supersedes feature 007's contract
  and the constitution's Query Pipeline section wherever either states `4`/`K=4` as the current
  top-K — both already frame it as a tunable "default," and this feature exercises that tunability
  (research Decision 6), the same way it already did for the threshold.
- **A newly-ingested document's chunks target ~500 tokens with ~63-token (12.6%) overlap**, not
  ~800/100. This only affects documents ingested *after* this feature ships (research Decision 4);
  a chunk already stored under the previous ~800/100 windows keeps that size until its source
  document is deleted and re-uploaded.

## What every `/chat` response row still guarantees (unchanged from feature 007)

- A `sources` entry's `score` is still `1 - distance`, rounded to two decimal places, still
  computed the same way — only the floor it can now sit at moves from `0.5` down to `0.35`.
- The comparison is still inclusive: a passage scoring exactly `0.35` is accepted, exactly like a
  passage scoring exactly `0.5` was accepted before (feature 007's `ChatRetrievalIT` boundary test,
  re-pointed at the new value by this feature rather than replaced).
- The retrieve → threshold → augment → generate pipeline shape itself (constitution Query Pipeline
  section) is unchanged — only its two tunable defaults (threshold, top-K) move, both applied
  immediately to already-ingested passages since both are query-time behavior, not ingestion-time.

## What this contract does not cover

- No new configuration surface is introduced — the threshold and chunk-size values remain
  compile-time constants, not environment variables or request parameters (spec.md Assumptions).
- Reprocessing of already-ingested documents is out of scope (research Decision 4) — this document
  describes the values newly-ingested documents get, not a guarantee about any specific
  already-stored document's chunk size.

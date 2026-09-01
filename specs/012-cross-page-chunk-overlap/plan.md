# Implementation Plan: Cross-Page Chunk Overlap

**Branch**: `main` (no dedicated feature branch — no `before_specify`/`before_plan` hook is
registered in `.specify/extensions.yml`, same situation features 004–006 and 011's plans recorded) |
**Date**: 2026-08-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/012-cross-page-chunk-overlap/spec.md`

## Summary

`Chunker` currently builds token windows independently per `ExtractedPage` (feature 004,
research.md Decision 3) so that every chunk's `pageNumber` stays exact — but that also means a page
is treated as "a fresh reading context," so a sentence, list, or paragraph that a source PDF splits
across a page boundary can end up fragmented across two chunks with no shared context between them.
This feature closes that gap without weakening the exactness guarantee: `Chunker.chunk()` gains a
one-page lookback/lookahead so that a page's **first** window can be prefixed with a short excerpt
borrowed from the end of the previous page, and a page's **last** window can be suffixed with a
short excerpt borrowed from the start of the next page — both sized at the existing same-page
overlap amount (`OVERLAP_TOKENS`, 63 tokens). Every window's `pageNumber` stays exactly what it is
today: the page whose own loop iteration built it (its *anchor page*), even in the one edge case
(a short page flanked on both sides) where the borrowed text could outweigh the page's own text.

Two decisions carry the design (full reasoning in [research.md](research.md)):

- **Two-pass, not a document-wide token stream.** Pages are tokenized once each (as today), then
  iterated by index so a page's loop can look at its neighbors' own token arrays. This is what lets
  `pageNumber` stay exact (feature 004's original reason for per-page independence) while still
  closing the boundary gap — the two goals turn out not to be in tension once excerpts are treated
  as borrowed text, not a merged token stream.
- **Only the first and last window of each page are touched.** Interior windows, and every
  existing `ChunkerTest` assertion about them, are untouched — this keeps the change to a single,
  well-isolated addition inside an existing loop rather than a rewrite of the window-sizing math.

## Technical Context

**Language/Version**: Java 17, Spring Boot 3.5.16 (unchanged — no dependency or platform change).

**Primary Dependencies**: None new. `com.knuddels:jtokkit` (already a `backend/pom.xml` dependency
since feature 004) is the only library this change touches — no new `encode`/`decode` API beyond
what `Chunker.java` already calls.

**Storage**: N/A — no schema change. The `chunks.text` column (feature 003) is already unbounded
`TEXT`; a chunk carrying up to ~126 extra borrowed tokens (both-sides case) is well within its
existing capacity, and no other column is affected.

**Testing**: JUnit 5 (existing stack), unit-level only. New `ChunkerTest` cases (pure functions, no
I/O) covering FR-001/002/003/004/005/006/007/009/010; no new integration or contract test tier is
needed, since `POST /documents`'s request/response shape and the `DocumentIngestionIT`
(`db`-tagged) atomicity guarantees are both unaffected (data-model.md) — that suite is re-run in
Step 3 of quickstart.md as a regression check, not extended.

**Target Platform**: Same as feature 004 — local developer machine, Docker Compose for PostgreSQL
only, backend runs locally. No target platform demands from this feature.

**Project Type**: Web application (existing structure). This feature adds no new package and no new
file — it modifies one existing class, `backend/.../ingestion/Chunker.java`, and its test.

**Performance Goals**: Negligible added cost — up to two extra small `encode`/`decode` calls (≤63
tokens each) per page boundary, on top of the per-page encode/decode `Chunker` already does. Stays
well inside feature 004's SC-001 (~15s typical document) and SC-006 (60s at the 20 MB cap) budgets,
which this feature does not reopen.

**Constraints**:
- FR-004/FR-010: every chunk's `pageNumber` MUST remain the anchor page — the page whose own loop
  built it — with no new ambiguity introduced by the borrowed excerpts (Clarifications Session
  2026-08-21).
- FR-003: excerpt size MUST equal `OVERLAP_TOKENS` (currently 63, feature 011), never a new,
  independently-tuned value.
- FR-007: `.txt` (unpaged) documents — a single `ExtractedPage` with `pageNumber() == null` — MUST
  be chunked exactly as today; this feature's lookback/lookahead only has an effect when there is
  more than one non-blank page.
- FR-009: a chunk extended with one or two excerpts MUST still fall within the existing 500–1000
  token range (interior chunks) / the documented short-chunk exception (final/sole chunk) — the
  worst case (a single-window page with both excerpts) tops out around 500 + 63 + 63 = 626 tokens,
  comfortably inside the ceiling (research.md Decision 3).
- No public API, DTO, or database shape may change (data-model.md) — this is an internal algorithm
  change inside the existing ingestion pipeline.

**Scale/Scope**: One class modified (`Chunker.java`), one test class extended (`ChunkerTest.java`).
No new files, no new endpoints, no new persisted entities.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` v1.4.1.

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` precedes this plan; its one clarification (anchor-page rule for the double-excerpt collision case) was resolved via `/speckit-clarify` before planning started. |
| II | Test-Driven Development (Mandatory) | ✅ PASS | New `ChunkerTest` cases are unit-level, no I/O, and will be written before the `Chunker.chunk()` change per tasks.md's ordering — the existing suite already runs green with no live credentials, and this feature adds nothing that changes that. |
| III | Grounded Answers (RAG-First) | ✅ Supports, not directly measured | This feature exists to make retrieved chunks better reflect the source document's actual continuous text at page boundaries, strengthening citation accuracy indirectly; it adds no new citation mechanism itself. |
| IV | No Hallucination (Context Adherence) | ⏭️ N/A | No change to answer generation, the system prompt, or the "not in documentation" fallback. |
| V | Semantic Understanding (Meaning-Based Retrieval) | ⏭️ N/A — unaffected | No change to the embedding model, embedding call, or when embeddings are computed; chunk *text* changes slightly at boundaries, but every chunk is still embedded the same way, at the same pipeline stage, as today. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ⏭️ N/A — unaffected | No change to where vectors are stored or how Azure OpenAI is called. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ✅ Supports, re-checked not re-measured here | SC-002 requires the existing ≥80% evaluation set to keep passing after this change; running and reporting it is a Governance → Compliance Review gate for this feature's PR (constitution), not a new mechanism this plan introduces. |

**Chunking & Embedding Strategy compliance**: 500–1000 tokens, 10–15% overlap remains the governing
range (constitution; feature 011 set the concrete 500/63 values) — this feature's worst-case chunk
size (~626 tokens, both-sides excerpt case) stays inside it ✅; `source_filename`/`page_number`/
`chunk_id` are still retained on every chunk, with `page_number`'s meaning now explicitly defined
for the boundary case (FR-004/FR-010) rather than left implicit ✅; embeddings are still generated
at ingestion time from whatever `Chunker` produces, unchanged ✅.

**Code & Documentation Language Standard compliance**: this plan and all Phase 0/1 artifacts are in
English ✅; implementation-phase code, comments, and commit messages will follow the same standard.

**Technology Stack compliance**: no new dependency, no deviation from the mandated stack — this
feature only exercises `jtokkit`, already justified and present since feature 004 ✅.

**Post-Phase 1 re-check**: ✅ No change. Phase 1 design (data-model.md, quickstart.md) confirmed no
new persisted entity, no new dependency, and no API/schema shape change — the Constitution Check
above holds unchanged after design.

**Gate result**: PASS — no violations, no justifications required. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/012-cross-page-chunk-overlap/
├── plan.md                              # This file
├── research.md                          # Phase 0 — 4 decisions
├── data-model.md                        # Phase 1 — no new/changed shapes, content-semantics only
├── quickstart.md                        # Phase 1 — unit-test-driven validation + one manual DB check
├── checklists/
│   └── requirements.md                  # Spec quality checklist — all items pass
└── tasks.md                             # Phase 2 — created by /speckit-tasks, NOT by this command
```

No `contracts/` directory: this feature has no external interface of its own to document.
`POST /documents`'s request/response contract
(`specs/004-document-ingestion-endpoint/contracts/ingestion-api-contract.md`) is unchanged — this
feature is entirely internal to `Chunker`, one step inside that endpoint's existing pipeline.

### Source Code (repository root)

```text
backend/
├── src/main/java/com/epam/aihelpdesk/ingestion/
│   └── Chunker.java                     # MODIFIED — two-pass lookback/lookahead (research.md)
└── src/test/java/com/epam/aihelpdesk/ingestion/
    └── ChunkerTest.java                 # MODIFIED — new cases for FR-001/002/003/005/006/007/010;
                                          #   existing cases untouched (research.md Decision 2)

frontend/                                # UNCHANGED — no frontend work in this feature
```

**Structure Decision**: No structural change. This feature is a targeted, internal modification to
one existing class in the `ingestion` package feature 004 created; no new package, no new top-level
directory, no frontend change, no schema change.

## Complexity Tracking

*No entries — the Constitution Check gate passed with no violations requiring justification.*

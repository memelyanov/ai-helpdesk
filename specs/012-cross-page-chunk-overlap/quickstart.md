# Quickstart & Validation: Cross-Page Chunk Overlap

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-08-21

How to prove the change works. This is a validation guide, not an implementation guide — the Java
code and tests that make these steps pass are produced by `/speckit-tasks` and the implementation
phase. This feature has no new REST surface and no schema change (data-model.md), so validation is
mostly unit-level; one end-to-end step re-uses feature 004's already-running ingestion pipeline to
confirm the effect on real chunk rows.

## Prerequisites

Same as `specs/004-document-ingestion-endpoint/quickstart.md` — database up, backend running
(`backend\mvnw.cmd spring-boot:run`), Azure credentials only needed for the optional end-to-end
step (everything else works without them, same as before).

## Step 1 — Run the default test suite

```powershell
backend\mvnw.cmd test
```

Expected: green, including the new `ChunkerTest` cases this feature adds (research.md; tasks.md
enumerates them) covering:

- User Story 1 / FR-001 / FR-002: a page's last chunk carries a lead-in excerpt from the next
  page's start; a page's first chunk carries a trailing excerpt from the previous page's end.
- User Story 2 / FR-004: every chunk adjacent to a page boundary still reports exactly one
  `pageNumber` — its anchor page, not the neighbor it borrowed from.
- User Story 3 / FR-003: the borrowed excerpt on each side is exactly the same size as the existing
  same-page overlap (63 tokens), reusing `ChunkerTest`'s existing token-equality assertion style.
- Edge Cases / FR-005: a blank page between two content pages does not block the excerpt exchange —
  it still reaches the nearest page with text on each side.
- Edge Cases / FR-006: the very first chunk of a document (no preceding page) gets no trailing
  excerpt; the very last chunk of a document (no following page) gets no lead-in excerpt.
- Edge Cases / FR-007: a `.txt` document (no page structure, a single `ExtractedPage` with
  `pageNumber() == null`) is chunked exactly as before — this feature does not touch that path.
- FR-010 (Clarifications, Session 2026-08-21): a single short page sandwiched between two content
  pages gets both excerpts at once, and still reports its own page number as the anchor, even when
  the combined borrowed text outweighs its own native text.

No existing `ChunkerTest` case should need modification — every current test case exercises either
a single-page `pages` list (no neighbor to borrow from) or only asserts `pageNumber`/`chunkId`
(unaffected by excerpt content), per research.md Decision 2.

## Step 2 — Confirm the effect on a real multi-page document

```powershell
curl.exe -X POST http://localhost:8080/documents `
  -F "file=@sample-data/documents/security-policy.pdf;type=application/pdf"
```

Expected: `201 Created` as in feature 004's quickstart. Then inspect the chunks around a page
boundary:

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "
SELECT chunk_id, page_number, left(text, 60) AS starts, right(text, 60) AS ends
FROM chunks WHERE document_id = '<paste-documentId>' ORDER BY chunk_id;"
```

Expected: for a chunk that is the last one built from page *N* (immediately followed by a chunk
whose `page_number` is *N+1*), its `ends` column shows text that reads as a continuation into page
*N+1*'s opening words, not a hard cutoff mid-sentence with nothing after — this is the visible,
manual version of what `ChunkerTest` proves deterministically in Step 1. Conversely, the first
chunk of page *N+1* should `starts` with text that reads as a continuation from page *N*'s ending
words.

## Step 3 — Confirm no retrieval-accuracy regression (SC-002)

```powershell
backend\mvnw.cmd test -Pverify-db
```

Expected: green — `DocumentIngestionIT` (feature 004) still passes unchanged, confirming this
feature did not disturb the atomic write path or chunk-count reporting. Running the evaluation set
(`sample-data/evaluation-questions.csv`) against a re-ingested corpus, per the constitution's
Compliance Review gate, is the fuller SC-002 check and belongs to this feature's PR, not to this
quickstart.

## Step 4 — Clean up

```powershell
docker compose down -v
```

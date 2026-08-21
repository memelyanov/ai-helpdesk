<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/012-cross-page-chunk-overlap/plan.md](specs/012-cross-page-chunk-overlap/plan.md)

Supporting design artifacts for the active feature:
- [spec.md](specs/012-cross-page-chunk-overlap/spec.md) — three prioritized user stories (answers stay complete when the source text crosses a page break; citations stay trustworthy — exactly one anchor page per chunk — even for boundary chunks; the fix is symmetric, covering both the last chunk of a page peeking forward and the first chunk of the next page peeking back) closing the cross-page context gap `Chunker`'s per-page-independent design (feature 004) deliberately left open; one Clarifications entry (Session 2026-08-21: a short page sandwiched between two neighbors and receiving both excerpts at once always reports its own page as the anchor, even if the borrowed text outweighs its native text)
- [research.md](specs/012-cross-page-chunk-overlap/research.md) — 4 decisions: a two-pass algorithm (tokenize each non-blank page once, then iterate with lookback/lookahead by index) rather than a document-wide token stream, so `pageNumber` stays exact; only a page's first and last window are extended with a borrowed excerpt, every interior window stays byte-for-byte identical to today; the excerpt size reuses `OVERLAP_TOKENS` (63, feature 011) unchanged rather than a new tunable; the anchor-page attribution rule requires no new code, since it already falls out of the existing per-page loop structure
- [data-model.md](specs/012-cross-page-chunk-overlap/data-model.md) — no schema change, no new type; `ChunkDraft.text()` may now include a borrowed excerpt from an adjacent page, `ChunkDraft.pageNumber()` gains the explicit anchor-page definition (unchanged behavior in the ordinary case)
- [quickstart.md](specs/012-cross-page-chunk-overlap/quickstart.md) — `mvnw test` for the automated `ChunkerTest` cases, plus a real-PDF manual check of chunk text either side of a page boundary via `psql`

Prior features, still the source of truth for their own scope:
- [specs/011-retrieval-accuracy-tuning/plan.md](specs/011-retrieval-accuracy-tuning/plan.md) — retuned
  `Chunker.TARGET_TOKENS` (500) and `OVERLAP_TOKENS` (63) this feature reuses unchanged for cross-page
  excerpt sizing (research.md Decision 3); also retuned `ChatService.SIMILARITY_THRESHOLD`/`TOP_K`,
  both untouched by this feature.
- [specs/004-document-ingestion-endpoint/plan.md](specs/004-document-ingestion-endpoint/plan.md) —
  `Chunker`'s original per-page-independent design
  ([research.md](specs/004-document-ingestion-endpoint/research.md) Decision 3) this feature revises
  only at the page boundary, and the `chunks` table's `page_number`/`chunk_id` guarantees (FR-007)
  this feature's anchor-page rule (FR-004/FR-010) keeps intact; its
  [contracts/ingestion-api-contract.md](specs/004-document-ingestion-endpoint/contracts/ingestion-api-contract.md)
  is unaffected — no request/response shape changes.
- [specs/010-chat-trace-dialog/plan.md](specs/010-chat-trace-dialog/plan.md) — the prior active
  feature (frontend trace dialog); no dependency in either direction with this feature.
- [specs/009-chat-diagnostic-trace/plan.md](specs/009-chat-diagnostic-trace/plan.md) — the backend
  `POST /chat` `includeTrace`/`trace` contract.
- [specs/008-frontend-chat-ui/plan.md](specs/008-frontend-chat-ui/plan.md) — the Angular chat UI.
- [specs/001-project-scaffolding/plan.md](specs/001-project-scaffolding/plan.md) — database, backend,
  frontend skeleton; [contracts/health-api.md](specs/001-project-scaffolding/contracts/health-api.md)
  — the health endpoint response shape.
- [specs/002-frontend-health-wire/plan.md](specs/002-frontend-health-wire/plan.md) — the frontend
  connection-status indicator wired to that health endpoint.
- [specs/003-document-vector-schema/plan.md](specs/003-document-vector-schema/plan.md) — the
  `documents`/`chunks` schema underlying every endpoint this feature calls.
- [specs/005-document-listing-download/plan.md](specs/005-document-listing-download/plan.md) —
  `GET /documents` and `GET /documents/{id}/content`.
- [specs/006-document-delete/plan.md](specs/006-document-delete/plan.md) — `DELETE /documents/{id}`.
- [specs/007-chat-endpoint/plan.md](specs/007-chat-endpoint/plan.md) — `POST /chat`; its
  `ChatResponse`/`SourceCitation` shapes ([data-model.md](specs/007-chat-endpoint/data-model.md)) are
  unaffected by this feature's chunk-content-only change.

Governance: [.specify/memory/constitution.md](.specify/memory/constitution.md) v1.4.1 — seven
principles, Spec-First and TDD first among them; Azure OpenAI is the mandated inference provider;
v1.4.0 added the Code & Documentation Language Standard (English-only); v1.4.1 generalized the Error
Handling & Logging section's status-code wording (a wording fix, no new constraint).

Two constraints that shape this feature's design:
- Every chunk MUST still report exactly one **anchor page** number — the page whose own
  reading-context loop built the chunk — never a range, an average, or the neighboring page it
  merely borrowed a short excerpt from, even in the rare case where a short page's two borrowed
  excerpts combined outweigh its own native text (spec.md FR-004/FR-010, Clarifications Session
  2026-08-21).
- The cross-page excerpt size MUST equal the existing same-page overlap amount (`OVERLAP_TOKENS`,
  currently 63 tokens / ~12.6%, feature 011) — no new, independently-tuned constant, and no
  NLP-based sentence-boundary detection (spec.md FR-003, Assumptions).
<!-- SPECKIT END -->

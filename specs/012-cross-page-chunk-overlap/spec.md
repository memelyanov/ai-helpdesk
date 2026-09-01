# Feature Specification: Cross-Page Chunk Overlap

**Feature Branch**: `012-cross-page-chunk-overlap`

**Created**: 2026-08-21

**Status**: Draft

**Input**: User description: "token-level carry-over между страницами. Ключевая мысль: overlap уже работает как "хвост предыдущего окна становится головой следующего" — внутри страницы. Нужно распространить этот же принцип на границу страниц: хвост токенов страницы N становится "затравкой" в начале токен-потока страницы N+1, перед тем как строятся окна. При этом pageNumber каждого чанка остаётся однозначным — это страница, чей текст составляет подавляющее большинство (~87%+) чанка. Мы просто разрешаем первому чанку новой страницы "помнить" последние ~63 токена предыдущей. Аналогично и последний чанк должен знать начало следующей страницы используя тот же принцип"

## Clarifications

### Session 2026-08-21

- Q: When a page's own text is short enough that its single chunk receives both a trailing excerpt (from the preceding page) and a lead-in excerpt (from the following page) at once, and the combined borrowed text outweighs the page's own native text, which page number should that chunk report? → A: Always report the anchor page — the page whose reading-context loop built the chunk — regardless of the actual borrowed-vs-native token ratio in that rare case.

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.

  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Answers stay complete when the source text crosses a page break (Priority: P1)

A helpdesk user asks a question whose answer, in the original PDF, is a sentence, list, or
paragraph that starts near the bottom of one page and continues at the top of the next. Today,
because chunking treats each page as an independent reading context, the retrieved chunk may
contain only the fragment before or after the break, and the user receives an incomplete or
confusing answer even though the full answer exists in the document.

**Why this priority**: This is the core problem being solved. Without it, the feature has no
user-visible effect, and the retrieval-accuracy risk identified for multi-page source documents
remains.

**Independent Test**: Ingest a two-page test document where a single sentence/list is deliberately
split across the page boundary, ask a question whose answer depends on both halves, and confirm
the answer synthesized by the system uses the complete, unbroken passage rather than a truncated
half.

**Acceptance Scenarios**:

1. **Given** a PDF where page 1 ends mid-sentence and page 2 begins with the rest of that
   sentence, **When** the document is ingested, **Then** at least one chunk contains the complete
   sentence, not just the portion native to a single page.
2. **Given** a user question whose correct answer depends on text spanning that page boundary,
   **When** the question is asked, **Then** the answer reflects the complete passage.

---

### User Story 2 - Citations stay trustworthy even for boundary chunks (Priority: P2)

A helpdesk user reads the source citation ("filename, p. N") attached to an answer and expects to
find the cited content by opening that exact page. A chunk that borrows a little text from a
neighboring page must not become ambiguous about which page it "belongs to."

**Why this priority**: Citation accuracy is a constitutional requirement (Grounded Answers,
Principle III) and must not be weakened while fixing the boundary-context problem; this story
protects that guarantee while User Story 1 is implemented.

**Independent Test**: Inspect the chunks produced for a multi-page test document and confirm every
chunk — including ones adjacent to a page boundary — reports exactly one page number: its anchor
page (FR-004), which in the ordinary case is also where the majority of the chunk's own text
appears (see FR-010 for the rare short-page exception where borrowed text can outweigh native
text).

**Acceptance Scenarios**:

1. **Given** a chunk that starts a new page and borrows a short lead-in from the previous page,
   **When** its page number is inspected, **Then** it reports the new page, not the previous one
   and not a range.
2. **Given** a chunk that ends a page and borrows a short excerpt from the next page, **When** its
   page number is inspected, **Then** it reports the page it ends on, not the next one.

---

### User Story 3 - Boundary context works in both reading directions (Priority: P3)

Cross-page context loss is not one-directional: a chunk can be the last chunk built from a page
(missing what comes next) just as easily as it can be the first chunk of a page (missing what came
before). Both situations should be covered the same way, using the same overlap size already used
between chunks on the same page.

**Why this priority**: This completes the symmetry the first two stories rely on but is scoped
last because User Story 1 already delivers the primary user value for the more common case (a
first chunk missing prior context); this story closes the remaining gap for the boundary chunk on
the other side.

**Independent Test**: For the same two-page test document, confirm that both the last chunk built
from page 1 and the first chunk built from page 2 each carry a short excerpt from the other side of
the boundary, and that the size of each excerpt matches the overlap already used between
consecutive chunks within a single page.

**Acceptance Scenarios**:

1. **Given** the last chunk generated from a page that is followed by another page, **When** the
   chunk is inspected, **Then** it includes a short excerpt from the start of the following page.
2. **Given** the first chunk generated from a page that follows another page, **When** the chunk
   is inspected, **Then** it includes a short excerpt from the end of the preceding page.

---

### Edge Cases

- What happens for the very first page of a document (no preceding page)? Its first chunk MUST NOT
  be extended with a fabricated trailing excerpt — there is nothing to borrow.
- What happens for the very last page of a document (no following page)? Its last chunk MUST NOT
  be extended with a fabricated lead-in excerpt — there is nothing to borrow.
- What happens when one or more blank/divider pages sit between two pages that do have text? The
  excerpt exchange MUST reach past the blank pages to the nearest page with text on each side,
  consistent with blank pages already contributing zero chunks.
- What happens when a page's own text is shorter than the excerpt size normally borrowed? The
  system MUST borrow only as much as actually exists, never fabricate or pad content.
- What happens for a document format without page structure (e.g. plain text)? There is no page
  boundary to bridge, so this feature does not change how such documents are chunked.
- What happens when a single page produces only one chunk (shorter than the target chunk size) and
  that page has both a preceding and a following page? That one chunk is simultaneously the page's
  first and last chunk, so it MUST receive both the trailing excerpt from the previous page and the
  lead-in excerpt from the next page, and it MUST still report that page as its anchor page (FR-010)
  even if the combined borrowed excerpts outweigh its own native text.
- What happens when many or all pages in a document are short enough to each be a single-chunk page
  with neighbors on both sides? Each such page's chunk independently follows the same FR-010 rule —
  there is no cumulative or document-wide exception; every page's anchor-page attribution is decided
  independently of how many other pages in the document are in the same situation.
- What happens when a document begins or ends with one or more blank pages before its first/last
  page with text? Those leading/trailing blank pages contribute no chunks, as already true today,
  and are not treated as a phantom neighbor — the document's first page-with-text still follows
  FR-006 (no trailing excerpt, since there is no preceding page with text), and likewise for the
  last page-with-text.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When a page in a document has a following page, the last chunk built from that
  page's own text MUST include a lead-in excerpt (sized per FR-003) taken from the start of the
  nearest following page that has text (FR-005) — using only that page's own native text, never an
  excerpt it may itself have borrowed from a page beyond it, since excerpts do not compound across
  more than one page boundary.
- **FR-002**: When a page in a document has a preceding page, the first chunk built from that
  page's own text MUST include a trailing excerpt (sized per FR-003) taken from the end of the
  nearest preceding page that has text (FR-005) — using only that page's own native text, never an
  excerpt it may itself have borrowed from a page before it, since excerpts do not compound across
  more than one page boundary.
- **FR-003**: The size of each cross-page excerpt (both the lead-in borrowed from a following page
  and the trailing excerpt borrowed from a preceding page) MUST equal the same overlap amount
  already used between consecutive chunks within a single page, so cross-page context is treated
  consistently with same-page context rather than as a separate, differently-tuned mechanism.
- **FR-004**: Every chunk the system produces, including one extended with a cross-page excerpt,
  MUST report exactly one source page number for citation purposes — its **anchor page**: the page
  whose own reading-context the chunk was built from. In the ordinary case (at most one cross-page
  excerpt) this is also the page contributing the majority of the chunk's text. A chunk MUST NOT
  report a page range, an averaged page, or any page other than its anchor page; see FR-010 for the
  rare case where this could otherwise be ambiguous.
- **FR-005**: When locating a neighboring page to borrow a cross-page excerpt from, the system MUST
  skip over any blank/empty intervening pages and reach the nearest page that has text, on either
  side of the boundary.
- **FR-006**: The first chunk of a document's first page-with-text MUST NOT be extended with a
  trailing excerpt (no preceding page exists), and the last chunk of a document's last
  page-with-text MUST NOT be extended with a lead-in excerpt (no following page exists).
- **FR-007**: This cross-page excerpt behavior MUST NOT apply to document formats that have no page
  structure; such documents continue to be chunked as one continuous stream, unaffected by this
  feature.
- **FR-008**: Adding cross-page excerpts MUST NOT change the existing chunk-sequence numbering
  guarantee — chunk sequence positions remain unique and 0-indexed within their document.
- **FR-009**: A chunk extended with one or two cross-page excerpts MUST still fall within the
  document's established chunk-size range (500–1000 tokens for an interior chunk, with the existing
  documented exception that a page's own trailing remainder chunk may be shorter); the excerpts add
  a small, bounded amount of borrowed context, they do not make a chunk's size unbounded.
- **FR-010**: When a page's own text is short enough that its single chunk carries both a trailing
  excerpt (borrowed from the preceding page) and a lead-in excerpt (borrowed from the following
  page) at the same time, that chunk MUST still report its anchor page's number (FR-004), even in
  the rare case where the combined borrowed excerpts outweigh the page's own native text in size.
  The system MUST NOT shrink, drop, or reassign either excerpt to preserve a literal token-count
  majority in this case — the anchor-page rule is authoritative regardless of ratio. This is not a
  conditional check the system must perform — the anchor-page rule applies unconditionally, with no
  requirement to ever compare borrowed-vs-native token counts at runtime.

### Key Entities *(include if feature involves data)*

- **Chunk**: A candidate unit of a document's extracted text prepared for indexing. Carries the
  text that will be searched and cited, and a single source page number. This feature adds the
  possibility that a chunk's text includes a short excerpt borrowed from an adjacent page, in
  addition to the page's own text, while its reported page number stays singular: always its anchor
  page (FR-004) — the page whose reading-context loop built it — the same rule as before this
  feature, now made explicit rather than left as an unstated majority assumption (FR-010).
- **Page boundary**: The transition between the end of one page's extracted text and the start of
  the next page's extracted text within the same document. This feature is only about the
  boundaries between pages that both contain text (directly or through intervening blank pages).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a test corpus where a single logical passage is deliberately split across a page
  boundary, 100% of the chunk pairs adjacent to that boundary contain the bounded cross-page
  excerpt defined by FR-003 from both sides — no chunk ends or begins at a page boundary without
  the neighboring page's excerpt attached, though a split passage longer than that bounded excerpt
  is not guaranteed to be captured in full.
- **SC-002**: The existing retrieval-accuracy evaluation (≥80% of test questions retrieving the
  correct source document in the top-K results) continues to pass after this change, with no
  regression attributable to it.
- **SC-003**: 100% of chunks in the ingested corpus report exactly one page number that matches a
  real page of their source document; zero chunks report an ambiguous, averaged, or incorrect page
  attribution.
- **SC-004**: The total chunk count for a re-ingested corpus is unchanged by this feature (FR-008);
  stored/embedded text volume grows by at most two excerpt-sized additions (FR-003) per page — one
  on its first chunk, one on its last — never by a multiple of corpus size.
- **SC-005**: For a test document containing a short page flanked on both sides by pages with text
  (the FR-010 case), that page's chunk reports its own page number as the anchor in 100% of such
  cases in the test corpus.

## Assumptions

- The cross-page excerpt is added mechanically, by borrowed amount, the same way same-page overlap
  already works today — it is not based on detecting whether a sentence or paragraph actually
  continues across the boundary. Every page boundary between two pages with text gets the same
  treatment.
- The excerpt size matches the existing same-page overlap amount (already established at roughly
  10–15% of the target chunk size elsewhere in this system), reused as-is for cross-page excerpts
  rather than introducing a separately tuned value.
- A chunk extended with a cross-page excerpt on one or both sides is still attributed to a single
  page: its anchor page, the page whose reading-context loop built it (FR-004). This coincides with
  majority-of-own-text in the ordinary single-excerpt case; FR-010 makes it authoritative even in
  the rare case where excerpts from both neighbors combined outweigh a short page's own text.
- This feature changes how chunks are built during document ingestion only; it does not change
  anything about how existing already-ingested documents are stored until they are re-ingested.

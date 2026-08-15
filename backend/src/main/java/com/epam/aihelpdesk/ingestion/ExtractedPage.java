package com.epam.aihelpdesk.ingestion;

/**
 * One page's text ({@code .pdf}) or the whole document's text as a single instance ({@code .txt},
 * no page structure) — output of {@link TextExtractor} (data-model.md, research Decision 2).
 *
 * @param pageNumber 1-indexed source page number, or {@code null} for formats without page
 *                   structure (FR-007)
 * @param text       the page's extracted text; may be blank (a divider page contributes no
 *                   chunks, spec Edge Cases)
 */
public record ExtractedPage(Integer pageNumber, String text) {
}

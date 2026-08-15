package com.epam.aihelpdesk.ingestion;

import java.util.List;

/**
 * {@link TextExtractor}'s full output: the content type it detected — exactly {@code text/plain}
 * or {@code application/pdf}, the same value the {@code documents.content_type} column's CHECK
 * constraint accepts (specs/003-document-vector-schema) — plus the page-by-page extracted text.
 *
 * @param contentType the detected MIME type (FR-002), never trusted from the caller
 * @param pages       the document's pages in original reading order (FR-004)
 */
public record TextExtractionResult(String contentType, List<ExtractedPage> pages) {
}

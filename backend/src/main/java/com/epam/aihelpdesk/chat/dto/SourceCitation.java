package com.epam.aihelpdesk.chat.dto;

import java.util.UUID;

/**
 * One entry in a {@link ChatResponse}'s {@code sources} list — a distinct
 * {@code (documentId, page)} that genuinely contributed a retrieved passage to the generated
 * answer (FR-008/FR-009). Computed deterministically from retrieval results, never parsed from the
 * model's answer text (research Decision 6) — this is what makes SC-005's "zero fabricated
 * citations" true by construction.
 *
 * @param documentId identifies the source document the same way features 005/006 already do — a
 *                    caller can pass this straight to {@code GET /documents/{id}/content} to fetch
 *                    the original file.
 * @param filename    the contributing document's name at ingestion time.
 * @param page        either the 1-indexed page number as a string (e.g. {@code "3"}), or the fixed
 *                     string {@code "no page structure"} when the source chunk has no page number
 *                     (spec.md Clarifications Session 2026-08-16, FR-009) — never {@code null},
 *                     never a numeric placeholder.
 * @param score       retrieval confidence — {@code 1 - distance}, rounded to two decimal places;
 *                    always &ge; 0.5 (the similarity threshold) for any citation that appears here.
 */
public record SourceCitation(UUID documentId, String filename, String page, double score) {

    /** The fixed indicator shown in {@link #page} for a source with no page structure. */
    public static final String NO_PAGE_STRUCTURE = "no page structure";
}

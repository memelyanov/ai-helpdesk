package com.epam.aihelpdesk.ingestion.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One entry in {@code GET /documents}'s JSON array response (FR-002). {@code chunkCount} is
 * legitimately {@code 0} for a document that produced no extractable text (feature 004 FR-015) —
 * that is a valid, expected value, never omitted or treated as an error (FR-003). This response
 * never carries chunk {@code text} or {@code embedding} content (FR-012) — see
 * {@code specs/005-document-listing-download/contracts/document-query-api-contract.md} for the
 * full response contract.
 */
public record DocumentSummaryResponse(UUID documentId, String filename, String contentType,
        OffsetDateTime uploadedAt, long chunkCount) {
}

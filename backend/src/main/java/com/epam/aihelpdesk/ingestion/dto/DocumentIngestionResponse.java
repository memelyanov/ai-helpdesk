package com.epam.aihelpdesk.ingestion.dto;

import java.util.UUID;

/**
 * {@code POST /documents}'s success response body (FR-010): the identifier assigned to the newly
 * stored document and the number of chunks it was split into. {@code chunkCount} is legitimately
 * {@code 0} for a document that parsed successfully but yielded no extractable text (FR-015) — that
 * is a valid, non-error outcome, not a placeholder. See
 * {@code specs/004-document-ingestion-endpoint/contracts/ingestion-api-contract.md} for the full
 * response contract.
 */
public record DocumentIngestionResponse(UUID documentId, int chunkCount) {
}

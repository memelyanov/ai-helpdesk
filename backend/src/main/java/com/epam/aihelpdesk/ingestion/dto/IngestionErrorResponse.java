package com.epam.aihelpdesk.ingestion.dto;

/**
 * {@code POST /documents}'s shared error response body, for every {@code 400} and {@code 503}
 * outcome alike (FR-011). {@code error} is one of {@code unsupported_type}, {@code invalid_file},
 * {@code unparseable}, {@code provider_unconfigured}, or {@code processing_failed} — see
 * {@code specs/004-document-ingestion-endpoint/contracts/ingestion-api-contract.md} for the full
 * status-code mapping. {@code message} MUST NOT contain a credential value (FR-014).
 */
public record IngestionErrorResponse(String error, String message) {
}

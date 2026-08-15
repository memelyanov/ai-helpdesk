package com.epam.aihelpdesk.ingestion.dto;

/**
 * The shared error response body for every {@code 4xx}/{@code 5xx} outcome across all three
 * {@code /documents} endpoints. {@code error} is one of {@code unsupported_type},
 * {@code invalid_file}, {@code unparseable}, {@code provider_unconfigured}, or
 * {@code processing_failed} (feature 004's {@code POST /documents}; see
 * {@code specs/004-document-ingestion-endpoint/contracts/ingestion-api-contract.md}), or
 * {@code document_not_found} (this feature's {@code GET /documents/{id}/content}; see
 * {@code specs/005-document-listing-download/contracts/document-query-api-contract.md}).
 * {@code message} MUST NOT contain a credential value (FR-014) and is informational only — a caller
 * MUST NOT parse it to decide anything; the HTTP status and {@code error} code together are the
 * stable contract.
 */
public record DocumentErrorResponse(String error, String message) {
}

package com.epam.aihelpdesk.ingestion;

/**
 * An identifier names an existing document, but an unexpected server-side failure (for example,
 * the database became unreachable mid-operation) prevented its deletion from completing. Nothing is
 * deleted when this is thrown — a single {@code DELETE} statement's own atomicity guarantees that
 * structurally (research Decision 5) — and a caller MAY safely retry the identical request once the
 * underlying condition has cleared (spec.md FR-010). {@link DocumentErrorHandler} maps this to
 * {@code 503 Service Unavailable} with the fixed {@code deletion_failed} error code.
 *
 * <p>Deliberately a sibling of {@link IngestionException}, not a subtype, and deliberately not a
 * reuse of {@link IngestionProcessingException} — that class's own Javadoc is scoped to the
 * ingestion pipeline's own two {@code errorCode} values ({@code provider_unconfigured},
 * {@code processing_failed}); neither describes an existing document's deletion failing (research
 * Decision 6).
 */
public class DocumentDeletionException extends RuntimeException {

    public DocumentDeletionException(String message) {
        super(message);
    }

    public DocumentDeletionException(String message, Throwable cause) {
        super(message, cause);
    }
}

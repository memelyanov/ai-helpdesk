package com.epam.aihelpdesk.ingestion;

/**
 * The upload's input was fine, but the system could not currently process it — a retry of the
 * identical upload may succeed once the underlying condition clears (FR-011's "input was valid but
 * processing failed" category). Maps to {@code 503 Service Unavailable} via
 * {@link IngestionErrorHandler}. Valid {@code errorCode} values:
 *
 * <ul>
 *   <li>{@code provider_unconfigured} — the Azure OpenAI embedding configuration is absent or
 *       incomplete; no network call was attempted (research Decision 6).</li>
 *   <li>{@code processing_failed} — the embedding call or the database write failed for an
 *       otherwise-valid document (FR-009's failure case).</li>
 * </ul>
 */
public class IngestionProcessingException extends IngestionException {

    public IngestionProcessingException(String errorCode, String message) {
        super(errorCode, message);
    }

    public IngestionProcessingException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

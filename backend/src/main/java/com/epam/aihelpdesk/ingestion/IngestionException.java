package com.epam.aihelpdesk.ingestion;

/**
 * Base type for every failure the ingestion pipeline can raise. Carries the machine-readable
 * {@code error} code that ends up in {@link com.epam.aihelpdesk.ingestion.dto.DocumentErrorResponse}
 * — never a message alone, so {@link DocumentErrorHandler} never has to infer the code from
 * exception type or text. Not thrown directly; use one of the two subclasses so the HTTP status
 * (FR-011) is unambiguous at the throw site:
 *
 * <ul>
 *   <li>{@link InvalidDocumentException} — the input itself was invalid ({@code 400}).</li>
 *   <li>{@link IngestionProcessingException} — the input was valid but processing failed
 *       ({@code 503}).</li>
 * </ul>
 */
public abstract class IngestionException extends RuntimeException {

    private final String errorCode;

    protected IngestionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected IngestionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}

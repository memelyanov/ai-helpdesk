package com.epam.aihelpdesk.ingestion;

/**
 * The upload itself was the problem — the caller must change the file before retrying, retrying
 * the identical upload will not help (FR-011's "input itself was invalid" category). Maps to
 * {@code 400 Bad Request} via {@link DocumentErrorHandler}. Valid {@code errorCode} values:
 *
 * <ul>
 *   <li>{@code unsupported_type} — content is neither {@code .txt} nor {@code .pdf} (FR-002).</li>
 *   <li>{@code invalid_file} — empty, oversized, or the multipart request itself is malformed
 *       (no/duplicate {@code file} part, no filename) (FR-003).</li>
 *   <li>{@code unparseable} — an accepted-format file Tika cannot actually parse, including a
 *       {@code .txt} file whose bytes cannot be decoded as valid text (FR-005).</li>
 * </ul>
 */
public class InvalidDocumentException extends IngestionException {

    public InvalidDocumentException(String errorCode, String message) {
        super(errorCode, message);
    }

    public InvalidDocumentException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

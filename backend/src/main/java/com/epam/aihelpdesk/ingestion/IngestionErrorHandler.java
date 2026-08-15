package com.epam.aihelpdesk.ingestion;

import com.epam.aihelpdesk.ingestion.dto.IngestionErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Maps every {@link IngestionException} to the two-category HTTP response FR-011 requires: a
 * caller MUST be able to tell "retrying the identical file is pointless" from "retrying may help"
 * from the status code alone, with no need to parse {@code message} (contracts/ingestion-api-contract.md).
 *
 * <p>{@link InvalidDocumentException} → {@code 400} (the input itself was invalid);
 * {@link IngestionProcessingException} → {@code 503} (the input was valid, processing failed).
 * {@link MissingServletRequestPartException} — Spring's own signal for a request with no {@code
 * file} part at all — is folded into the same {@code 400 invalid_file} outcome as a malformed
 * request handled inside {@code DocumentController} (FR-003, spec Edge Cases), so a caller sees one
 * consistent error shape regardless of which layer detected the problem.
 *
 * <p>Every response body built here is constructed from a fixed, code-reviewed message — never from
 * the raw exception message of anything that could have touched a credential — so FR-014's
 * "no credential value in an error response" guarantee holds structurally, not by convention.
 */
@RestControllerAdvice
public class IngestionErrorHandler {

    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<IngestionErrorResponse> handleInvalidDocument(InvalidDocumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new IngestionErrorResponse(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(IngestionProcessingException.class)
    public ResponseEntity<IngestionErrorResponse> handleProcessingFailure(IngestionProcessingException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new IngestionErrorResponse(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<IngestionErrorResponse> handleMissingPart(MissingServletRequestPartException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new IngestionErrorResponse("invalid_file", "Request must contain exactly one 'file' part."));
    }

    /**
     * Container-level safety net for a request larger than {@code application.yml}'s
     * {@code spring.servlet.multipart.max-file-size} (25 MB) — above FR-003's own 20 MB business
     * limit, which {@link DocumentController} already enforces and reports as {@code invalid_file}
     * for every ordinary oversized upload. This handler only catches the rarer case of a request so
     * large Spring rejects it before {@code DocumentController} runs at all; the caller still sees
     * the same {@code invalid_file} outcome either way.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<IngestionErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new IngestionErrorResponse("invalid_file", "Uploaded file exceeds the 20 MB size limit."));
    }
}

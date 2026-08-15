package com.epam.aihelpdesk.ingestion;

import com.epam.aihelpdesk.ingestion.dto.DocumentErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * The shared {@code {error, message}} error surface for all three {@code /documents} endpoints —
 * {@code POST /documents} (feature 004) and this feature's {@code GET /documents} /
 * {@code GET /documents/{id}/content} — so a caller sees one consistent error shape no matter
 * which endpoint or failure it hits.
 *
 * <p>{@link InvalidDocumentException} → {@code 400} (the input itself was invalid);
 * {@link IngestionProcessingException} → {@code 503} (the input was valid, processing failed);
 * {@link DocumentNotFoundException} → {@code 404} (no stored document matches the requested id,
 * research Decision 4); {@link DocumentDeletionException} → {@code 503} (the id names an existing
 * document, but an unexpected server-side failure prevented its deletion, research Decision 6). A
 * caller can tell these categories apart from the status code alone, with no need to parse
 * {@code message}
 * (contracts/ingestion-api-contract.md, contracts/document-query-api-contract.md,
 * contracts/document-delete-api-contract.md).
 *
 * <p>{@link MissingServletRequestPartException} — Spring's own signal for a request with no {@code
 * file} part at all — is folded into the same {@code 400 invalid_file} outcome as a malformed
 * request handled inside {@code DocumentController} (FR-003, spec Edge Cases), so a caller sees one
 * consistent error shape regardless of which layer detected the problem.
 *
 * <p>Every response body built here is constructed from a fixed, code-reviewed message — never from
 * the raw exception message of anything that could have touched a credential — so FR-014's
 * "no credential value in an error response" guarantee holds structurally, not by convention.
 */
@RestControllerAdvice
public class DocumentErrorHandler {

    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<DocumentErrorResponse> handleInvalidDocument(InvalidDocumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new DocumentErrorResponse(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(IngestionProcessingException.class)
    public ResponseEntity<DocumentErrorResponse> handleProcessingFailure(IngestionProcessingException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new DocumentErrorResponse(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<DocumentErrorResponse> handleDocumentNotFound(DocumentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new DocumentErrorResponse("document_not_found", exception.getMessage()));
    }

    @ExceptionHandler(DocumentDeletionException.class)
    public ResponseEntity<DocumentErrorResponse> handleDocumentDeletionFailure(DocumentDeletionException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new DocumentErrorResponse("deletion_failed", exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<DocumentErrorResponse> handleMissingPart(MissingServletRequestPartException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new DocumentErrorResponse("invalid_file", "Request must contain exactly one 'file' part."));
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
    public ResponseEntity<DocumentErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new DocumentErrorResponse("invalid_file", "Uploaded file exceeds the 20 MB size limit."));
    }
}

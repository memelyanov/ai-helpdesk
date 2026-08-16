package com.epam.aihelpdesk.chat;

import com.epam.aihelpdesk.chat.dto.ChatErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The shared {@code {error, message}} error surface for {@code POST /chat} — a new, chat-scoped
 * error handler (research Decision 8), not a reuse of
 * {@code com.epam.aihelpdesk.ingestion.DocumentErrorHandler}.
 *
 * <p>{@link InvalidChatRequestException} → {@code 400} (a validation failure {@link ChatController}
 * itself detected); {@link ChatProcessingException} → {@code 503} (the request was valid, but
 * processing failed). {@link HttpMessageNotReadableException} — Spring's own signal for a request
 * body that could not be parsed at all (empty/invalid JSON, or a non-UUID {@code documentIds}
 * entry) — is mapped directly to {@code 400 malformed_request} here, without ever constructing an
 * {@link InvalidChatRequestException} instance, since Jackson fails before {@code ChatController}'s
 * own method body — and therefore before {@code ChatRequest} — ever runs (FR-016, mirroring
 * {@code DocumentErrorHandler}'s {@code MissingServletRequestPartException} handler).
 *
 * <p>Every handler method logs one structured line naming the outcome's {@code errorCode} — never
 * the question text, which this class never has access to for the {@code malformed_request} case
 * and must not be made to carry for the others either — so every rejection is diagnosable (FR-017;
 * {@code ChatService}'s own logging never runs for a {@code 400}, since validation fails before
 * {@code ChatService.answer} is ever called, so this is the only place these three outcomes can be
 * logged).
 */
@RestControllerAdvice
public class ChatErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatErrorHandler.class);

    @ExceptionHandler(InvalidChatRequestException.class)
    public ResponseEntity<ChatErrorResponse> handleInvalidRequest(InvalidChatRequestException exception) {
        log.info("chat request rejected: errorCode={}", exception.errorCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ChatErrorResponse(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(ChatProcessingException.class)
    public ResponseEntity<ChatErrorResponse> handleProcessingFailure(ChatProcessingException exception) {
        log.warn("chat request failed: errorCode={}", exception.errorCode());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ChatErrorResponse(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ChatErrorResponse> handleMalformedRequest(HttpMessageNotReadableException exception) {
        log.info("chat request rejected: errorCode=malformed_request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ChatErrorResponse("malformed_request",
                        "Request body could not be parsed as a valid chat request."));
    }
}

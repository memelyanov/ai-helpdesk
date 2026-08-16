package com.epam.aihelpdesk.chat;

/**
 * Base type for every failure {@code POST /chat} can raise. Carries the machine-readable
 * {@code error} code that ends up in {@link com.epam.aihelpdesk.chat.dto.ChatErrorResponse} —
 * never a message alone, so {@link ChatErrorHandler} never has to infer the code from exception
 * type or text. A sibling of {@code com.epam.aihelpdesk.ingestion.IngestionException}, not a
 * subtype or a reuse of it — {@code ingestion}'s exception hierarchy is deliberately scoped to the
 * ingestion pipeline (research Decision 8, the same reasoning feature 006's Decision 6 already
 * established for {@code DocumentDeletionException}). Not thrown directly; use one of the two
 * subclasses so the HTTP status (data-model.md) is unambiguous at the throw site:
 *
 * <ul>
 *   <li>{@link InvalidChatRequestException} — the request itself was invalid ({@code 400}).</li>
 *   <li>{@link ChatProcessingException} — the request was valid but processing failed
 *       ({@code 503}).</li>
 * </ul>
 */
public abstract class ChatException extends RuntimeException {

    private final String errorCode;

    protected ChatException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected ChatException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}

package com.epam.aihelpdesk.chat;

/**
 * The request's input was fine, but the system could not currently process it — a retry of the
 * identical question may succeed once the underlying condition clears (FR-013). Maps to
 * {@code 503 Service Unavailable} via {@link ChatErrorHandler}. Valid {@code errorCode} values:
 *
 * <ul>
 *   <li>{@code provider_unconfigured} — the Azure OpenAI embedding or chat configuration is
 *       incomplete; no network call was attempted.</li>
 *   <li>{@code processing_failed} — the embedding call, the retrieval query, or the chat
 *       completion call failed for an otherwise-valid question.</li>
 * </ul>
 */
public class ChatProcessingException extends ChatException {

    public ChatProcessingException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ChatProcessingException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

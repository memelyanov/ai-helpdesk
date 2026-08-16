package com.epam.aihelpdesk.chat;

/**
 * The request itself was invalid — a retry of the identical request will never succeed without
 * changing it (FR-011/FR-012/FR-016). Maps to {@code 400 Bad Request} via {@link ChatErrorHandler}.
 * Valid {@code errorCode} values:
 *
 * <ul>
 *   <li>{@code blank_question} — {@code question} is missing, empty, or all whitespace
 *       (FR-011).</li>
 *   <li>{@code question_too_long} — {@code question} exceeds 1000 characters after trimming
 *       (FR-012).</li>
 *   <li>{@code malformed_request} — the request body could not be parsed at all: invalid/empty
 *       JSON, or a {@code documentIds} entry that is not a well-formed UUID (FR-016). This value is
 *       set directly by {@link ChatErrorHandler}'s {@code HttpMessageNotReadableException} mapping,
 *       not thrown from an {@code InvalidChatRequestException} instance — see
 *       data-model.md's {@code ChatErrorResponse} table.</li>
 * </ul>
 */
public class InvalidChatRequestException extends ChatException {

    public InvalidChatRequestException(String errorCode, String message) {
        super(errorCode, message);
    }
}

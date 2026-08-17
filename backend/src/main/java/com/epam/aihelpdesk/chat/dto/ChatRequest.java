package com.epam.aihelpdesk.chat.dto;

import java.util.List;
import java.util.UUID;

/**
 * The {@code POST /chat} request body. No bean-validation annotations — this codebase validates
 * request bodies manually in the controller ({@code DocumentController.validate()} precedent), not
 * via {@code @Valid} (FR-011/FR-012, {@link com.epam.aihelpdesk.chat.ChatController}).
 *
 * <p>Binding {@code documentIds} as {@code List<UUID>} means Jackson itself rejects a non-UUID
 * entry with {@code HttpMessageNotReadableException} — mapped by
 * {@link com.epam.aihelpdesk.chat.ChatErrorHandler} to {@code 400 malformed_request} — before this
 * record is ever constructed (FR-016, research Decision 8); no manual UUID-format check is needed
 * here.
 *
 * @param question     the plain-text question, 1-1000 characters after trimming (FR-011/FR-012).
 * @param documentIds  when present and non-empty, restricts retrieval to chunks belonging to these
 *                     documents only (FR-010). Absent, {@code null}, or empty means "search the
 *                     whole corpus" — this field has no way to express "search nothing."
 * @param includeTrace feature 009: when {@code true}, the response's {@code trace} field carries the
 *                     ordered diagnostic trace for this request. {@code null}, absent, or
 *                     {@code false} are all equivalent to "no trace" — the response is then
 *                     byte-identical to this record's original, pre-009 contract (spec.md FR-010,
 *                     User Story 3). A boxed {@link Boolean}, not a primitive {@code boolean},
 *                     matching {@code documentIds}' existing nullable-optional style and keeping the
 *                     three-way "explicitly true / explicitly false / not sent" distinction visible
 *                     in code even though only the first is ever treated differently.
 */
public record ChatRequest(String question, List<UUID> documentIds, Boolean includeTrace) {

    /**
     * Convenience constructor for the pre-009 two-field shape — equivalent to
     * {@code includeTrace=null} ("no trace").
     */
    public ChatRequest(String question, List<UUID> documentIds) {
        this(question, documentIds, null);
    }
}

package com.epam.aihelpdesk.chat.dto;

import java.util.List;

/**
 * The {@code POST /chat} response body — the single shape both outcomes use, at the same
 * {@code 200 OK} status (research Decision 7):
 *
 * <ul>
 *   <li>A grounded answer (FR-006): {@code answer} is the generated text, {@code sources} is
 *       non-empty.</li>
 *   <li>"Not covered" (FR-007): {@code answer} is the fixed string
 *       {@code "I don't have this information in the documentation."}, {@code sources} is empty.</li>
 * </ul>
 *
 * <p>{@code sources} is empty if and only if {@code answer} is the fixed not-covered string — never
 * empty alongside a generated answer, never non-empty alongside the fixed string (FR-008).
 *
 * @param answer  never blank.
 * @param sources every distinct {@code (documentId, page)} that contributed a retrieved passage to
 *                {@code answer}, in descending similarity order.
 */
public record ChatResponse(String answer, List<SourceCitation> sources) {

    /** The fixed answer text for the "documentation does not cover this" outcome (FR-007). */
    public static final String NOT_COVERED_ANSWER = "I don't have this information in the documentation.";
}

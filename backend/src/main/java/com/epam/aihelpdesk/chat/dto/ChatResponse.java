package com.epam.aihelpdesk.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 * @param trace   feature 009: present, and non-{@code null}, only when the request had
 *                {@code includeTrace=true} — one {@link ChatTraceStep} per pipeline stage that
 *                actually ran, in execution order (FR-010/FR-011). {@code null} otherwise, and
 *                omitted from the JSON body entirely rather than serialized as
 *                {@code "trace": null} ({@link JsonInclude.Include#NON_NULL} below) — so a caller
 *                that never sets {@code includeTrace} sees a response byte-identical to this
 *                record's original, pre-009 two-field contract (FR-010, User Story 3). Never changes
 *                {@code answer} or {@code sources}' value (FR-016) — purely observational.
 */
public record ChatResponse(String answer, List<SourceCitation> sources,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ChatTraceStep> trace) {

    /** The fixed answer text for the "documentation does not cover this" outcome (FR-007). */
    public static final String NOT_COVERED_ANSWER = "I don't have this information in the documentation.";

    /**
     * Convenience constructor for the pre-009 two-field shape — equivalent to {@code trace=null}
     * ("no trace"), used by every call site this feature does not need to attach a trace to.
     */
    public ChatResponse(String answer, List<SourceCitation> sources) {
        this(answer, sources, null);
    }
}

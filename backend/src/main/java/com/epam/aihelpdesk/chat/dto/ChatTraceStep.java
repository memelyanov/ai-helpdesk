package com.epam.aihelpdesk.chat.dto;

import java.util.Map;

/**
 * One recorded stage of processing a single chat request (spec.md's "Chat Trace Step" entity),
 * built by {@code ChatService.answer(...)} as it executes (research Decision 2). At most six per
 * request, in the order the stages actually ran; fewer when the pipeline stops early (FR-013).
 *
 * <p>{@code stage} is one of six fixed, closed string values — a plain {@code String} constant, the
 * same convention {@link ChatErrorResponse#error()} already established, not a Java {@code enum}:
 *
 * <ul>
 *   <li>{@code request_received} (FR-001) — {@code detail}: {@code question} (String, full text),
 *       {@code documentIds} (List&lt;String&gt;).</li>
 *   <li>{@code question_embedded} (FR-002) — {@code detail}: {@code vectorDimensions} (int).</li>
 *   <li>{@code vector_search_completed} (FR-003) — {@code detail}: {@code candidateCount} (int),
 *       {@code candidates} (full per-row detail, including passage text, FR-012).</li>
 *   <li>{@code results_filtered} (FR-004) — {@code detail}: {@code survivorCount} (int),
 *       {@code discardedCount} (int), {@code threshold} (double), {@code survivors} (same per-row
 *       shape as {@code candidates}, FR-012).</li>
 *   <li>{@code prompt_assembled} (FR-005) — {@code detail}: {@code systemPrompt} (String),
 *       {@code prompt} (String, full text, FR-012), {@code passageCount} (int).</li>
 *   <li>{@code model_response_received} (FR-006) — {@code detail}: {@code rawResponse} (String,
 *       full text, FR-012), {@code completionLength} (int), {@code outcome} ({@code "answered"} or
 *       {@code "not_covered"}).</li>
 * </ul>
 *
 * See data-model.md's per-stage {@code detail} key table for the authoritative field list.
 *
 * @param stage       one of the six fixed values documented above.
 * @param durationMs  wall-clock time this stage took.
 * @param detail      stage-specific fields, serialized as a plain nested JSON object.
 */
public record ChatTraceStep(String stage, long durationMs, Map<String, Object> detail) {

    public static final String REQUEST_RECEIVED = "request_received";
    public static final String QUESTION_EMBEDDED = "question_embedded";
    public static final String VECTOR_SEARCH_COMPLETED = "vector_search_completed";
    public static final String RESULTS_FILTERED = "results_filtered";
    public static final String PROMPT_ASSEMBLED = "prompt_assembled";
    public static final String MODEL_RESPONSE_RECEIVED = "model_response_received";
}

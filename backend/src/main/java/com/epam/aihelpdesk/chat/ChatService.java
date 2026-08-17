package com.epam.aihelpdesk.chat;

import com.epam.aihelpdesk.chat.dto.ChatRequest;
import com.epam.aihelpdesk.chat.dto.ChatResponse;
import com.epam.aihelpdesk.chat.dto.ChatTraceStep;
import com.epam.aihelpdesk.chat.dto.SourceCitation;
import com.epam.aihelpdesk.ingestion.EmbeddingClient;
import com.epam.aihelpdesk.ingestion.IngestionProcessingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the retrieve → threshold → augment → generate pipeline (constitution Query Pipeline
 * section): embed the question with the same deployment used at ingestion, retrieve the top-K
 * nearest chunks, discard any below the similarity threshold, and — only when at least one
 * survives — call the chat deployment and return a generated answer with deterministic citations.
 * When nothing survives, return the fixed "not covered" response directly, without ever calling
 * {@link ChatCompletionClient} (FR-007, research Decision 7).
 *
 * <p>Feature 009: also builds an ordered {@code List<ChatTraceStep>} as it executes — one entry per
 * stage that actually runs, never a step for a stage that didn't (FR-013) — and logs exactly one
 * summary-level line per appended step, correlated across lines by the MDC {@code chatRequestId}
 * {@link ChatController} sets around the whole request (FR-001 through FR-008; research Decisions 1,
 * 2, 5). Full raw content — retrieved passage text, the exact prompt, the raw model response — lives
 * only in each step's {@code detail}, never in the log line itself (FR-017); the trace is attached to
 * the returned {@code ChatResponse} only when {@code request.includeTrace()} is {@code true}
 * (FR-010/FR-011), otherwise the response is byte-identical to this feature's pre-existing contract
 * (FR-016).
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Bounded number of the most relevant passages retrieved per question (FR-004). */
    static final int TOP_K = 4;

    /** Minimum cosine similarity a retrieved passage must meet to be used (FR-005, inclusive). */
    static final double SIMILARITY_THRESHOLD = 0.5;

    /** Maximum accepted question length, in characters, after trimming (FR-012). */
    public static final int MAX_QUESTION_LENGTH = 1000;

    private final EmbeddingClient embeddingClient;
    private final ChatRetrievalRepository chatRetrievalRepository;
    private final ChatCompletionClient chatCompletionClient;

    public ChatService(EmbeddingClient embeddingClient, ChatRetrievalRepository chatRetrievalRepository,
            ChatCompletionClient chatCompletionClient) {
        this.embeddingClient = embeddingClient;
        this.chatRetrievalRepository = chatRetrievalRepository;
        this.chatCompletionClient = chatCompletionClient;
    }

    /**
     * Answers one question, independently of any other request (FR-014, no conversation memory).
     *
     * @throws ChatProcessingException {@code provider_unconfigured} or {@code processing_failed} if
     *                                  the embedding call, the retrieval query, or the chat
     *                                  completion call fails (FR-013)
     */
    public ChatResponse answer(ChatRequest request) {
        List<ChatTraceStep> steps = new ArrayList<>();

        appendRequestReceived(steps, request);

        float[] queryVector = embedQuestion(request.question(), steps);

        List<RetrievedChunk> retrieved = retrieveCandidates(queryVector, request.documentIds(), steps);

        List<RetrievedChunk> survivors = filterSurvivors(retrieved, steps);

        if (survivors.isEmpty()) {
            return new ChatResponse(ChatResponse.NOT_COVERED_ANSWER, List.of(), traceIfRequested(request, steps));
        }

        List<SourceCitation> sources = toSources(survivors);
        ChatCompletionResult result = chatCompletionClient.complete(request.question(), survivors);
        appendPromptAssembledAndModelResponse(steps, result, survivors.size());

        String completion = result.completion();
        if (completion == null || completion.isBlank()) {
            // A reachable, correctly configured provider that completes the request but returns
            // nothing usable is not a system failure (spec.md Edge Cases) — treated identically to
            // the threshold short-circuit above, not reported as FR-013's processing failure.
            return new ChatResponse(ChatResponse.NOT_COVERED_ANSWER, List.of(), traceIfRequested(request, steps));
        }

        return new ChatResponse(completion, sources, traceIfRequested(request, steps));
    }

    /**
     * Attaches the built trace only when the caller opted in — {@code null} otherwise, so
     * {@code ChatResponse}'s {@code @JsonInclude(NON_NULL)} omits the {@code "trace"} key entirely
     * (FR-010, FR-016).
     */
    private static List<ChatTraceStep> traceIfRequested(ChatRequest request, List<ChatTraceStep> steps) {
        return Boolean.TRUE.equals(request.includeTrace()) ? steps : null;
    }

    private static void appendRequestReceived(List<ChatTraceStep> steps, ChatRequest request) {
        List<String> documentIds = request.documentIds() == null ? List.of()
                : request.documentIds().stream().map(UUID::toString).toList();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("question", request.question());
        detail.put("documentIds", documentIds);
        steps.add(new ChatTraceStep(ChatTraceStep.REQUEST_RECEIVED, 0L, detail));
        // FR-001 explicitly requires the question text in this one log line — the sole, deliberate
        // exception to FR-017's "no full raw content in the log" rule (data-model.md's logging table).
        log.info("chat request received: question={}, documentIds={}", request.question(), documentIds);
    }

    private float[] embedQuestion(String question, List<ChatTraceStep> steps) {
        long start = System.currentTimeMillis();
        float[] queryVector;
        try {
            queryVector = embeddingClient.embedQuery(question);
        } catch (IngestionProcessingException e) {
            log.warn("chat request failed: errorCode={}, stage=embedding", e.errorCode());
            throw new ChatProcessingException(e.errorCode(), e.getMessage(), e);
        }
        long durationMs = System.currentTimeMillis() - start;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("vectorDimensions", queryVector.length);
        steps.add(new ChatTraceStep(ChatTraceStep.QUESTION_EMBEDDED, durationMs, detail));
        log.info("question embedded: vectorDimensions={}", queryVector.length);
        return queryVector;
    }

    private List<RetrievedChunk> retrieveCandidates(float[] queryVector, List<UUID> documentIds,
            List<ChatTraceStep> steps) {
        long start = System.currentTimeMillis();
        List<RetrievedChunk> retrieved = chatRetrievalRepository.findTopSimilarChunks(queryVector, TOP_K,
                documentIds);
        long durationMs = System.currentTimeMillis() - start;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("candidateCount", retrieved.size());
        detail.put("candidates", retrieved.stream().map(ChatService::rowDetail).toList());
        steps.add(new ChatTraceStep(ChatTraceStep.VECTOR_SEARCH_COMPLETED, durationMs, detail));
        log.info("vector search completed: candidateCount={}", retrieved.size());
        return retrieved;
    }

    private static List<RetrievedChunk> filterSurvivors(List<RetrievedChunk> retrieved, List<ChatTraceStep> steps) {
        long start = System.currentTimeMillis();
        List<RetrievedChunk> survivors = retrieved.stream()
                .filter(chunk -> chunk.distance() <= (1 - SIMILARITY_THRESHOLD))
                .toList();
        long durationMs = System.currentTimeMillis() - start;
        int discardedCount = retrieved.size() - survivors.size();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("survivorCount", survivors.size());
        detail.put("discardedCount", discardedCount);
        detail.put("threshold", SIMILARITY_THRESHOLD);
        detail.put("survivors", survivors.stream().map(ChatService::rowDetail).toList());
        steps.add(new ChatTraceStep(ChatTraceStep.RESULTS_FILTERED, durationMs, detail));
        log.info("results filtered: survivorCount={}, discardedCount={}, threshold={}", survivors.size(),
                discardedCount, SIMILARITY_THRESHOLD);
        return survivors;
    }

    /**
     * Appends {@code prompt_assembled} and {@code model_response_received} together, sourced from
     * the same {@link ChatCompletionResult} — the client call that produces the prompt and the one
     * that produces the response are the same call, split into two steps only because they answer
     * two distinct requirements, FR-005 and FR-006 (data-model.md).
     */
    private static void appendPromptAssembledAndModelResponse(List<ChatTraceStep> steps, ChatCompletionResult result,
            int passageCount) {
        Map<String, Object> promptDetail = new LinkedHashMap<>();
        promptDetail.put("systemPrompt", result.systemPrompt());
        promptDetail.put("prompt", result.prompt());
        promptDetail.put("passageCount", passageCount);
        steps.add(new ChatTraceStep(ChatTraceStep.PROMPT_ASSEMBLED, 0L, promptDetail));
        log.info("prompt assembled: passageCount={}", passageCount);

        String completion = result.completion();
        int completionLength = completion == null ? 0 : completion.length();
        String outcome = (completion == null || completion.isBlank()) ? "not_covered" : "answered";
        Map<String, Object> responseDetail = new LinkedHashMap<>();
        responseDetail.put("rawResponse", completion);
        responseDetail.put("completionLength", completionLength);
        responseDetail.put("outcome", outcome);
        steps.add(new ChatTraceStep(ChatTraceStep.MODEL_RESPONSE_RECEIVED, 0L, responseDetail));
        log.info("model response received: outcome={}, completionLength={}", outcome, completionLength);
    }

    private static Map<String, Object> rowDetail(RetrievedChunk chunk) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("documentId", chunk.documentId().toString());
        row.put("chunkId", chunk.chunkId());
        row.put("sourceFilename", chunk.sourceFilename());
        row.put("page", chunk.pageNumber());
        row.put("text", chunk.text());
        row.put("distance", chunk.distance());
        row.put("similarity", 1 - chunk.distance());
        return row;
    }

    /**
     * Groups surviving chunks by {@code (documentId, pageNumber)}, keeping the lowest-distance
     * (highest-similarity) row per group, sorted by similarity descending (research Decision 6) —
     * computed from retrieval results, never parsed from the model's answer text.
     */
    private static List<SourceCitation> toSources(List<RetrievedChunk> survivors) {
        Map<String, RetrievedChunk> bestPerGroup = new LinkedHashMap<>();
        for (RetrievedChunk chunk : survivors) {
            String key = chunk.documentId() + "|" + chunk.pageNumber();
            RetrievedChunk existing = bestPerGroup.get(key);
            if (existing == null || chunk.distance() < existing.distance()) {
                bestPerGroup.put(key, chunk);
            }
        }
        List<SourceCitation> sources = new ArrayList<>();
        for (RetrievedChunk chunk : bestPerGroup.values()) {
            String page = chunk.pageNumber() == null ? SourceCitation.NO_PAGE_STRUCTURE
                    : String.valueOf(chunk.pageNumber());
            double score = Math.round((1 - chunk.distance()) * 100.0) / 100.0;
            sources.add(new SourceCitation(chunk.documentId(), chunk.sourceFilename(), page, score));
        }
        sources.sort(Comparator.comparingDouble(SourceCitation::score).reversed());
        return sources;
    }
}

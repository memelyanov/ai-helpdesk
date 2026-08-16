package com.epam.aihelpdesk.chat;

import com.epam.aihelpdesk.chat.dto.ChatRequest;
import com.epam.aihelpdesk.chat.dto.ChatResponse;
import com.epam.aihelpdesk.chat.dto.SourceCitation;
import com.epam.aihelpdesk.ingestion.EmbeddingClient;
import com.epam.aihelpdesk.ingestion.IngestionProcessingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        float[] queryVector = embedQuestion(request.question());

        List<RetrievedChunk> retrieved =
                chatRetrievalRepository.findTopSimilarChunks(queryVector, TOP_K, request.documentIds());
        List<RetrievedChunk> survivors = retrieved.stream()
                .filter(chunk -> chunk.distance() <= (1 - SIMILARITY_THRESHOLD))
                .toList();

        if (survivors.isEmpty()) {
            log.info("chat request answered: outcome=not_covered, reason=no_passage_above_threshold");
            return notCovered();
        }

        List<SourceCitation> sources = toSources(survivors);
        String completion = chatCompletionClient.complete(request.question(), survivors);
        if (completion == null || completion.isBlank()) {
            // A reachable, correctly configured provider that completes the request but returns
            // nothing usable is not a system failure (spec.md Edge Cases) — treated identically to
            // the threshold short-circuit above, not reported as FR-013's processing failure.
            log.info("chat request answered: outcome=not_covered, reason=empty_completion");
            return notCovered();
        }

        log.info("chat request answered: outcome=answered, sourceCount={}", sources.size());
        return new ChatResponse(completion, sources);
    }

    private float[] embedQuestion(String question) {
        try {
            return embeddingClient.embedQuery(question);
        } catch (IngestionProcessingException e) {
            log.warn("chat request failed: errorCode={}, stage=embedding", e.errorCode());
            throw new ChatProcessingException(e.errorCode(), e.getMessage(), e);
        }
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

    private static ChatResponse notCovered() {
        return new ChatResponse(ChatResponse.NOT_COVERED_ANSWER, List.of());
    }
}

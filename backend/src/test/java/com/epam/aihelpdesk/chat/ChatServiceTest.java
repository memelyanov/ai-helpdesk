package com.epam.aihelpdesk.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.epam.aihelpdesk.chat.dto.ChatRequest;
import com.epam.aihelpdesk.chat.dto.ChatResponse;
import com.epam.aihelpdesk.chat.dto.ChatTraceStep;
import com.epam.aihelpdesk.ingestion.EmbeddingClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Direct, mocked-collaborator unit test of {@link ChatService} — no {@code @SpringBootTest}, no
 * {@code MockMvc}, no database, no Azure call (constitution Principle II). First test in this
 * codebase to exercise {@code ChatService.answer(...)}'s own logic directly (research Decision 6).
 *
 * <p>User Story 1 (T010): every log line {@code ChatService} emits, asserted via a Logback
 * {@link ListAppender} attached directly to its logger — proving FR-001 through FR-007 (six
 * summary-level log lines, correctly ordered, correctly truncated on early stop) and FR-017 (no
 * full raw content ever reaches the log). User Story 2/3 additions land later, once
 * {@code ChatRequest}/{@code ChatResponse} carry {@code includeTrace}/{@code trace} (T013/T014).
 */
class ChatServiceTest {

    private static final float[] QUERY_VECTOR = new float[1536];

    private EmbeddingClient embeddingClient;
    private ChatRetrievalRepository chatRetrievalRepository;
    private ChatCompletionClient chatCompletionClient;
    private ChatService chatService;

    private Logger chatServiceLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        embeddingClient = mock(EmbeddingClient.class);
        chatRetrievalRepository = mock(ChatRetrievalRepository.class);
        chatCompletionClient = mock(ChatCompletionClient.class);
        chatService = new ChatService(embeddingClient, chatRetrievalRepository, chatCompletionClient);

        chatServiceLogger = (Logger) LoggerFactory.getLogger(ChatService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        chatServiceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        chatServiceLogger.detachAppender(logAppender);
    }

    private List<String> loggedMessages() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static RetrievedChunk chunk(UUID documentId, int chunkId, String filename, int page, String text,
            double distance) {
        return new RetrievedChunk(documentId, chunkId, filename, page, text, distance);
    }

    // -----------------------------------------------------------------------------------------
    // User Story 1 — logging (FR-001 through FR-007, FR-017)
    // -----------------------------------------------------------------------------------------

    @Test
    void aNormalAnsweredFlowLogsExactlySixLinesInOrderWithNoFullContentInAnyOfThem() {
        UUID documentId = UUID.randomUUID();
        // Two candidates, only one survives — deliberately so the log-line count cannot coincide
        // with a naive "one line per retrieved/survivor row" implementation; a correct
        // one-line-per-pipeline-stage implementation always logs 6 lines here, regardless of row
        // counts.
        RetrievedChunk survivor = chunk(documentId, 1, "policy.pdf", 2,
                "MOCKED_PASSAGE_TEXT_SHOULD_NEVER_APPEAR_IN_A_LOG_LINE", 0.1);
        RetrievedChunk discarded = chunk(documentId, 2, "policy.pdf", 3, "a weak candidate", 0.9);
        when(embeddingClient.embedQuery(anyString())).thenReturn(QUERY_VECTOR);
        when(chatRetrievalRepository.findTopSimilarChunks(any(), anyInt(), any()))
                .thenReturn(List.of(survivor, discarded));
        when(chatCompletionClient.complete(anyString(), anyList())).thenReturn(new ChatCompletionResult(
                "MOCKED_SYSTEM_PROMPT_SHOULD_NEVER_APPEAR_IN_A_LOG_LINE",
                "MOCKED_PROMPT_TEXT_SHOULD_NEVER_APPEAR_IN_A_LOG_LINE",
                "MOCKED_RAW_RESPONSE_SHOULD_NEVER_APPEAR_IN_A_LOG_LINE"));

        chatService.answer(new ChatRequest("Can I expense a taxi?", null));

        List<String> messages = loggedMessages();
        assertThat(messages).hasSize(6);
        assertThat(messages).noneMatch(m -> m.contains("MOCKED_PASSAGE_TEXT_SHOULD_NEVER_APPEAR_IN_A_LOG_LINE")
                || m.contains("MOCKED_SYSTEM_PROMPT_SHOULD_NEVER_APPEAR_IN_A_LOG_LINE")
                || m.contains("MOCKED_PROMPT_TEXT_SHOULD_NEVER_APPEAR_IN_A_LOG_LINE")
                || m.contains("MOCKED_RAW_RESPONSE_SHOULD_NEVER_APPEAR_IN_A_LOG_LINE"));
    }

    @Test
    void aBelowThresholdFlowLogsExactlyFourLinesAndNeverInvokesTheCompletionClient() {
        UUID documentId = UUID.randomUUID();
        RetrievedChunk weak1 = chunk(documentId, 1, "policy.pdf", 2, "too weak", 0.9);
        RetrievedChunk weak2 = chunk(documentId, 2, "policy.pdf", 3, "also too weak", 0.95);
        when(embeddingClient.embedQuery(anyString())).thenReturn(QUERY_VECTOR);
        when(chatRetrievalRepository.findTopSimilarChunks(any(), anyInt(), any()))
                .thenReturn(List.of(weak1, weak2));

        chatService.answer(new ChatRequest("Question with only weak matches", null));

        assertThat(loggedMessages()).hasSize(4);
        verify(chatCompletionClient, never()).complete(anyString(), anyList());
    }

    @Test
    void aBlankCompletionStillLogsAllSixLinesWithANotCoveredOutcome() {
        UUID documentId = UUID.randomUUID();
        RetrievedChunk survivor = chunk(documentId, 1, "policy.pdf", 2, "a relevant passage", 0.1);
        RetrievedChunk discarded = chunk(documentId, 2, "policy.pdf", 3, "a weak candidate", 0.9);
        when(embeddingClient.embedQuery(anyString())).thenReturn(QUERY_VECTOR);
        when(chatRetrievalRepository.findTopSimilarChunks(any(), anyInt(), any()))
                .thenReturn(List.of(survivor, discarded));
        when(chatCompletionClient.complete(anyString(), anyList()))
                .thenReturn(new ChatCompletionResult("system prompt", "prompt", ""));

        ChatResponse response = chatService.answer(new ChatRequest("A question", null));

        assertThat(loggedMessages()).hasSize(6);
        assertThat(response.answer()).isEqualTo(ChatResponse.NOT_COVERED_ANSWER);
    }

    // -----------------------------------------------------------------------------------------
    // User Story 2 — trace assembly (FR-011, FR-012, FR-013)
    // -----------------------------------------------------------------------------------------

    @Test
    void includeTraceTrueOnANormalAnsweredFlowReturnsTheFullSixStepTraceWithFullRawContent() {
        UUID documentId = UUID.randomUUID();
        RetrievedChunk survivor = chunk(documentId, 1, "policy.pdf", 2, "the full passage text", 0.1);
        RetrievedChunk discarded = chunk(documentId, 2, "policy.pdf", 3, "a weak candidate", 0.9);
        when(embeddingClient.embedQuery(anyString())).thenReturn(QUERY_VECTOR);
        when(chatRetrievalRepository.findTopSimilarChunks(any(), anyInt(), any()))
                .thenReturn(List.of(survivor, discarded));
        when(chatCompletionClient.complete(anyString(), anyList()))
                .thenReturn(new ChatCompletionResult("the system prompt", "the exact prompt", "the raw response"));

        ChatResponse response = chatService.answer(new ChatRequest("Can I expense a taxi?", null, true));

        assertThat(response.trace()).hasSize(6);
        List<String> stages = response.trace().stream().map(ChatTraceStep::stage).toList();
        assertThat(stages).containsExactly(ChatTraceStep.REQUEST_RECEIVED, ChatTraceStep.QUESTION_EMBEDDED,
                ChatTraceStep.VECTOR_SEARCH_COMPLETED, ChatTraceStep.RESULTS_FILTERED,
                ChatTraceStep.PROMPT_ASSEMBLED, ChatTraceStep.MODEL_RESPONSE_RECEIVED);

        ChatTraceStep vectorSearch = response.trace().get(2);
        assertThat(vectorSearch.detail().get("candidates").toString()).contains("the full passage text");
        ChatTraceStep resultsFiltered = response.trace().get(3);
        assertThat(resultsFiltered.detail().get("survivors").toString()).contains("the full passage text")
                .doesNotContain("a weak candidate");
        ChatTraceStep promptAssembled = response.trace().get(4);
        assertThat(promptAssembled.detail().get("prompt")).isEqualTo("the exact prompt");
        assertThat(promptAssembled.detail().get("systemPrompt")).isEqualTo("the system prompt");
        ChatTraceStep modelResponse = response.trace().get(5);
        assertThat(modelResponse.detail().get("rawResponse")).isEqualTo("the raw response");
        assertThat(modelResponse.detail().get("outcome")).isEqualTo("answered");
    }

    @Test
    void includeTraceTrueOnABelowThresholdFlowReturnsOnlyTheFourStepTruncatedTrace() {
        UUID documentId = UUID.randomUUID();
        RetrievedChunk weak1 = chunk(documentId, 1, "policy.pdf", 2, "too weak", 0.9);
        RetrievedChunk weak2 = chunk(documentId, 2, "policy.pdf", 3, "also too weak", 0.95);
        when(embeddingClient.embedQuery(anyString())).thenReturn(QUERY_VECTOR);
        when(chatRetrievalRepository.findTopSimilarChunks(any(), anyInt(), any()))
                .thenReturn(List.of(weak1, weak2));

        ChatResponse response = chatService.answer(new ChatRequest("Question with only weak matches", null, true));

        assertThat(response.trace()).hasSize(4);
        List<String> stages = response.trace().stream().map(ChatTraceStep::stage).toList();
        assertThat(stages).containsExactly(ChatTraceStep.REQUEST_RECEIVED, ChatTraceStep.QUESTION_EMBEDDED,
                ChatTraceStep.VECTOR_SEARCH_COMPLETED, ChatTraceStep.RESULTS_FILTERED);
    }

    @Test
    void includeTraceAbsentNullOrFalseAllProduceANullTraceEvenThoughTheSameLogLinesStillFire() {
        UUID documentId = UUID.randomUUID();
        RetrievedChunk survivor = chunk(documentId, 1, "policy.pdf", 2, "a relevant passage", 0.1);
        RetrievedChunk discarded = chunk(documentId, 2, "policy.pdf", 3, "a weak candidate", 0.9);
        when(embeddingClient.embedQuery(anyString())).thenReturn(QUERY_VECTOR);
        when(chatRetrievalRepository.findTopSimilarChunks(any(), anyInt(), any()))
                .thenReturn(List.of(survivor, discarded));
        when(chatCompletionClient.complete(anyString(), anyList()))
                .thenReturn(new ChatCompletionResult("system prompt", "prompt", "an answer"));

        ChatResponse absent = chatService.answer(new ChatRequest("A question", null));
        ChatResponse explicitNull = chatService.answer(new ChatRequest("A question", null, null));
        ChatResponse explicitFalse = chatService.answer(new ChatRequest("A question", null, false));

        assertThat(absent.trace()).isNull();
        assertThat(explicitNull.trace()).isNull();
        assertThat(explicitFalse.trace()).isNull();
        assertThat(loggedMessages()).hasSize(18);
    }

    // -----------------------------------------------------------------------------------------
    // User Story 3 — trace never changes answer/sources (FR-016)
    // -----------------------------------------------------------------------------------------

    @Test
    void answerAndSourcesAreIdenticalRegardlessOfIncludeTrace() {
        UUID documentId = UUID.randomUUID();
        RetrievedChunk survivor = chunk(documentId, 1, "policy.pdf", 2, "a relevant passage", 0.1);
        RetrievedChunk discarded = chunk(documentId, 2, "policy.pdf", 3, "a weak candidate", 0.9);
        when(embeddingClient.embedQuery(anyString())).thenReturn(QUERY_VECTOR);
        when(chatRetrievalRepository.findTopSimilarChunks(any(), anyInt(), any()))
                .thenReturn(List.of(survivor, discarded));
        when(chatCompletionClient.complete(anyString(), anyList()))
                .thenReturn(new ChatCompletionResult("system prompt", "prompt", "an answer"));

        ChatResponse withTrace = chatService.answer(new ChatRequest("A question", null, true));
        ChatResponse withoutTrace = chatService.answer(new ChatRequest("A question", null, false));
        ChatResponse nullTrace = chatService.answer(new ChatRequest("A question", null, null));

        assertThat(withTrace.answer()).isEqualTo(withoutTrace.answer()).isEqualTo(nullTrace.answer());
        assertThat(withTrace.sources()).isEqualTo(withoutTrace.sources()).isEqualTo(nullTrace.sources());
    }
}

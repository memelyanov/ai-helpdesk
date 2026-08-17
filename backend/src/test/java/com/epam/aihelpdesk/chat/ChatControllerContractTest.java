package com.epam.aihelpdesk.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epam.aihelpdesk.chat.dto.ChatRequest;
import com.epam.aihelpdesk.chat.dto.ChatResponse;
import com.epam.aihelpdesk.chat.dto.ChatTraceStep;
import com.epam.aihelpdesk.chat.dto.SourceCitation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code MockMvc} contract test for {@code POST /chat} — request/response shape and status codes,
 * against a stubbed {@link ChatService} (constitution Principle II). No live database or Azure
 * call is ever touched. Runs in the default suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatService chatService;

    // -----------------------------------------------------------------------------------------
    // User Story 1 — grounded, cited answer (FR-001, FR-004, FR-008, FR-009, FR-010,
    // Acceptance Scenarios 1, 2, 4, 5)
    // -----------------------------------------------------------------------------------------

    @Test
    void aValidQuestionReturnsTheStubbedGroundedAnswerWithMultipleSources() throws Exception {
        UUID documentIdA = UUID.randomUUID();
        UUID documentIdB = UUID.randomUUID();
        ChatResponse stubbed = new ChatResponse("Yes, taxis are reimbursable within policy limits.",
                List.of(new SourceCitation(documentIdA, "travel-expense-policy.pdf", "2", 0.81),
                        new SourceCitation(documentIdB, "corporate-card-rules.txt",
                                SourceCitation.NO_PAGE_STRUCTURE, 0.63)));
        when(chatService.answer(any())).thenReturn(stubbed);

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Can I expense a taxi from the airport?\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.answer").value(stubbed.answer()))
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[0].filename").value("travel-expense-policy.pdf"))
                .andExpect(jsonPath("$.sources[0].page").value("2"))
                .andExpect(jsonPath("$.sources[0].score").value(0.81))
                .andExpect(jsonPath("$.sources[1].page").value("no page structure"));
    }

    @Test
    void documentIdsInTheRequestBodyReachesChatServiceUnchanged() throws Exception {
        UUID filterId = UUID.randomUUID();
        when(chatService.answer(any())).thenReturn(new ChatResponse("An answer.", List.of()));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Can I expense a taxi?\", \"documentIds\": [\"" + filterId + "\"]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).answer(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().documentIds()).containsExactly(filterId);
    }

    // -----------------------------------------------------------------------------------------
    // Validation — FR-011 (blank), FR-012 (over-length), FR-016 (malformed) — always run before
    // any call to ChatService (spec.md FR-011's ordering guarantee)
    // -----------------------------------------------------------------------------------------

    @Test
    void aBlankQuestionReturnsFourHundredBlankQuestionWithNoServiceCall() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("blank_question"));

        verify(chatService, never()).answer(any());
    }

    @Test
    void aMissingQuestionFieldReturnsFourHundredBlankQuestion() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("blank_question"));

        verify(chatService, never()).answer(any());
    }

    @Test
    void aQuestionOfExactlyOneThousandCharactersIsAccepted() throws Exception {
        when(chatService.answer(any())).thenReturn(new ChatResponse("An answer.", List.of()));
        String question = "a".repeat(1000);

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + question + "\"}"))
                .andExpect(status().isOk());

        verify(chatService).answer(any());
    }

    @Test
    void aQuestionOfOneThousandAndOneCharactersReturnsFourHundredQuestionTooLong() throws Exception {
        String question = "a".repeat(1001);

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + question + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("question_too_long"));

        verify(chatService, never()).answer(any());
    }

    @Test
    void aMalformedJsonBodyReturnsFourHundredMalformedRequest() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("malformed_request"));

        verify(chatService, never()).answer(any());
    }

    @Test
    void aNonUuidDocumentIdsEntryReturnsFourHundredMalformedRequest() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Can I expense a taxi?\", \"documentIds\": [\"not-a-uuid\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("malformed_request"));

        verify(chatService, never()).answer(any());
    }

    // -----------------------------------------------------------------------------------------
    // User Story 2 — the "not covered" outcome is still a 200, never confused with an error
    // (FR-007, research Decision 7) — see also T019's extension of this file
    // -----------------------------------------------------------------------------------------

    @Test
    void aNotCoveredResponseFromChatServiceIsStillReturnedAsTwoHundred() throws Exception {
        when(chatService.answer(any())).thenReturn(new ChatResponse(ChatResponse.NOT_COVERED_ANSWER, List.of()));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"What's the CEO's personal cell phone number?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(ChatResponse.NOT_COVERED_ANSWER))
                .andExpect(jsonPath("$.sources.length()").value(0));
    }

    // -----------------------------------------------------------------------------------------
    // User Story 3 — a genuine processing failure is a distinct 503, never confused with the
    // not-covered response or a raw stack trace (FR-013, FR-015)
    // -----------------------------------------------------------------------------------------

    @Test
    void anUnconfiguredProviderReturnsServiceUnavailableWithProviderUnconfigured() throws Exception {
        when(chatService.answer(any())).thenThrow(new ChatProcessingException("provider_unconfigured",
                "Azure OpenAI chat configuration is incomplete; no request was attempted."));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Can I expense a taxi?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("provider_unconfigured"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(ChatResponse.NOT_COVERED_ANSWER)));
    }

    @Test
    void aProcessingFailureReturnsServiceUnavailableWithProcessingFailed() throws Exception {
        when(chatService.answer(any()))
                .thenThrow(new ChatProcessingException("processing_failed", "Chat completion request failed."));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Can I expense a taxi?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("processing_failed"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(ChatResponse.NOT_COVERED_ANSWER)));
    }

    // -----------------------------------------------------------------------------------------
    // Feature 009, User Story 1 — every request is correlated via MDC, set before validation and
    // always cleared afterward (FR-008, research Decision 1)
    // -----------------------------------------------------------------------------------------

    @Test
    void theCorrelationIdIsSetDuringTheServiceCallAndClearedAfterASuccessfulRequest() throws Exception {
        when(chatService.answer(any())).thenAnswer(invocation -> {
            Assertions.assertThat(MDC.get("chatRequestId"))
                    .as("chatRequestId must be set on the MDC while ChatService.answer(...) runs")
                    .isNotNull();
            return new ChatResponse("An answer.", List.of());
        });

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Can I expense a taxi?\"}"))
                .andExpect(status().isOk());

        Assertions.assertThat(MDC.get("chatRequestId"))
                .as("chatRequestId must be cleared once the request completes")
                .isNull();
    }

    @Test
    void theCorrelationIdIsStillClearedAfterAValidationRejectionEvenThoughChatServiceIsNeverCalled()
            throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"   \"}"))
                .andExpect(status().isBadRequest());

        verify(chatService, never()).answer(any());
        Assertions.assertThat(MDC.get("chatRequestId"))
                .as("chatRequestId must be cleared even when validation rejects the request")
                .isNull();
    }

    // -----------------------------------------------------------------------------------------
    // Feature 009, User Story 2 — includeTrace passes through to ChatService, and a trace/no-trace
    // ChatResponse serializes with/without the "trace" key accordingly (FR-010, FR-011)
    // -----------------------------------------------------------------------------------------

    @Test
    void includeTraceOnTheRequestBodyReachesChatServiceUnchanged() throws Exception {
        when(chatService.answer(any())).thenReturn(new ChatResponse("An answer.", List.of()));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Can I expense a taxi?\", \"includeTrace\": true}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).answer(captor.capture());
        Assertions.assertThat(captor.getValue().includeTrace()).isTrue();
    }

    @Test
    void aStubbedResponseWithANonNullTraceSerializesTheTraceArrayInStageOrder() throws Exception {
        ChatTraceStep step1 = new ChatTraceStep(ChatTraceStep.REQUEST_RECEIVED, 0L,
                Map.of("question", "Can I expense a taxi?", "documentIds", List.of()));
        ChatTraceStep step2 = new ChatTraceStep(ChatTraceStep.QUESTION_EMBEDDED, 5L,
                Map.of("vectorDimensions", 1536));
        when(chatService.answer(any()))
                .thenReturn(new ChatResponse("An answer.", List.of(), List.of(step1, step2)));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Can I expense a taxi?\", \"includeTrace\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace.length()").value(2))
                .andExpect(jsonPath("$.trace[0].stage").value("request_received"))
                .andExpect(jsonPath("$.trace[1].stage").value("question_embedded"));
    }

    @Test
    void aStubbedResponseWithANullTraceProducesNoTraceKeyAtAll() throws Exception {
        when(chatService.answer(any())).thenReturn(new ChatResponse("An answer.", List.of()));

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Can I expense a taxi?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    // -----------------------------------------------------------------------------------------
    // Feature 009, User Story 3 — omitting includeTrace and explicitly sending it false are
    // identical, byte-for-byte, in both directions (FR-010, User Story 3 Acceptance Scenarios 1-2)
    // -----------------------------------------------------------------------------------------

    @Test
    void omittingIncludeTraceAndExplicitlySettingItFalseBothProduceNoTraceKeyAndIdenticalBodies() throws Exception {
        ChatResponse stubbed = new ChatResponse("Yes, taxis are reimbursable.",
                List.of(new SourceCitation(UUID.randomUUID(), "travel-expense-policy.pdf", "2", 0.81)));
        when(chatService.answer(any())).thenReturn(stubbed);

        String omittedBody = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Can I expense a taxi?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String explicitFalseBody = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Can I expense a taxi?\", \"includeTrace\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertThat(omittedBody).isEqualTo(explicitFalseBody);
    }
}

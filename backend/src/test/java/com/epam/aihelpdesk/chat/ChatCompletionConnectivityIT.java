package com.epam.aihelpdesk.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epam.aihelpdesk.chat.dto.ChatResponse;
import com.epam.aihelpdesk.health.AzureOpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Opt-in Azure connectivity check for {@code POST /chat}'s full pipeline, extending
 * {@code AzureOpenAiConnectivityIT}'s pattern (research Decision 9). Excluded from the default
 * suite by the {@code azure} tag; runs only via {@code mvnw test -Pverify-ai}. Unlike the
 * {@code db}-tagged {@link ChatRetrievalIT}, this class stubs nothing — it ingests a real sample
 * document and asks real questions against the actually-configured Azure OpenAI deployments, so it
 * needs both a reachable database (the project's own {@code docker-compose} Postgres, per
 * {@code quickstart.md}'s Prerequisites — no Testcontainers here, matching
 * {@code AzureOpenAiConnectivityIT}/{@code EmbeddingClientAzureIT}'s existing "use whatever this
 * environment already has configured" opt-in style) and a complete {@code AZURE_OPEN_AI_*}
 * environment.
 */
@Tag("azure")
@SpringBootTest
@AutoConfigureMockMvc
class ChatCompletionConnectivityIT {

    private static final Path SAMPLE_DOCUMENTS = Path.of("../sample-data/documents");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AzureOpenAiProperties properties;

    // -----------------------------------------------------------------------------------------
    // User Story 1 — a real grounded, cited answer (Acceptance Scenario 1)
    // -----------------------------------------------------------------------------------------

    @Test
    void aQuestionWithAKnownAnswerInTheIngestedCorpusReturnsANonBlankCitedAnswer() throws Exception {
        assumeConfigured();
        ingest("travel-expense-policy.pdf", "application/pdf");

        JsonNode response =
                postChat("Can I expense a taxi from the airport when travelling for work?");

        assertThat(response.get("answer").asText()).isNotBlank();
        List<String> filenames = filenamesOf(response.get("sources"));
        assertThat(filenames).as("the answer cites the document it was actually grounded in")
                .contains("travel-expense-policy.pdf");
    }

    // -----------------------------------------------------------------------------------------
    // User Story 2 — a real "not covered" answer for a genuinely unrelated question
    // (Acceptance Scenario 1)
    // -----------------------------------------------------------------------------------------

    @Test
    void aQuestionUnrelatedToTheCorpusReturnsTheFixedNotCoveredResponse() throws Exception {
        assumeConfigured();
        ingest("travel-expense-policy.pdf", "application/pdf");

        JsonNode response = postChat("What's the CEO's personal cell phone number?");

        assertThat(response.get("answer").asText()).isEqualTo(ChatResponse.NOT_COVERED_ANSWER);
        assertThat(response.get("sources")).isEmpty();
    }

    // -----------------------------------------------------------------------------------------
    // User Story 3 — a genuine Azure configuration/reachability failure is reported as a distinct
    // 503, never the not-covered response (Acceptance Scenario 1) — tested directly against
    // ChatCompletionClient, the same "test the hand-built client directly" style
    // AzureOpenAiConnectivityIT/EmbeddingClientAzureIT already use, since the real, Spring-managed
    // AzureOpenAiProperties singleton cannot be reconfigured mid-test through a running context.
    // -----------------------------------------------------------------------------------------

    @Test
    void anUnconfiguredChatDeploymentFailsImmediatelyWithoutAnyRequest() {
        AzureOpenAiProperties incomplete = new AzureOpenAiProperties("", "", "", "");
        ChatCompletionClient client = new ChatCompletionClient(incomplete);

        assertThatThrownBy(() -> client.complete("Say hello.", List.of()))
                .isInstanceOf(ChatProcessingException.class)
                .satisfies(e -> assertThat(((ChatProcessingException) e).errorCode())
                        .isEqualTo("provider_unconfigured"));
    }

    @Test
    void anUnreachableChatEndpointFailsWithProcessingFailedNeverTheNotCoveredResponse() {
        AzureOpenAiProperties unreachable = new AzureOpenAiProperties("dummy-key",
                "https://this-host-does-not-exist.invalid.example/", "dummy-deployment", "dummy-deployment");
        ChatCompletionClient client = new ChatCompletionClient(unreachable);

        assertThatThrownBy(() -> client.complete("Say hello.", List.of()))
                .isInstanceOf(ChatProcessingException.class)
                .satisfies(e -> assertThat(((ChatProcessingException) e).errorCode())
                        .isEqualTo("processing_failed"));
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private void assumeConfigured() {
        if (!properties.isComplete() || !properties.isEmbeddingComplete()) {
            fail("Azure OpenAI configuration incomplete (api-key/endpoint/chat-deployment-name/"
                    + "embedding-deployment-name). No request was made.");
        }
    }

    private UUID ingest(String filename, String contentType) throws Exception {
        byte[] content = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve(filename));
        MvcResult result = mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", filename, contentType, content)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JSON.readTree(result.getResponse().getContentAsString()).get("documentId").asText());
    }

    private JsonNode postChat(String question) throws Exception {
        MvcResult result = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": " + JSON.writeValueAsString(question) + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> filenamesOf(JsonNode sources) {
        return sources.findValuesAsText("filename");
    }
}

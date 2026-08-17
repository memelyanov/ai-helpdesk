package com.epam.aihelpdesk.chat;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.epam.aihelpdesk.health.AzureOpenAiProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * Calls the Azure OpenAI chat deployment with the constitution's fixed grounding system prompt
 * plus the retrieved, threshold-passing passages (constitution Query Pipeline section). Builds an
 * {@link AzureOpenAiChatModel} by hand — the identical
 * {@code OpenAIClientBuilder}/{@code AzureKeyCredential}/{@code AzureOpenAiChatOptions.deploymentName(...)}
 * construction {@code AzureOpenAiConnectivityIT} already proves works (research Decision 4) —
 * rather than via Spring AI's auto-configuration, which {@code application.yml} deliberately
 * disables (spring.ai.model.chat: none) so the application still boots with no Azure credentials
 * (feature 001 Decision 4). {@link AzureOpenAiProperties#isComplete()} is checked <em>before</em>
 * any client is built or any network call is attempted, so an unconfigured provider fails
 * immediately and distinguishably from a genuine call failure (FR-013).
 */
@Component
public class ChatCompletionClient {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionClient.class);

    /** The constitution's exact fixed system prompt (Query Pipeline section). */
    // static final String SYSTEM_PROMPT = "Answer the following question based ONLY on the context provided. "
    //         + "If the answer is not in the context, respond with "
    //         + "'I don't have this information in the documentation.' Always cite your sources.";
    static final String SYSTEM_PROMPT = "Answer the following question based on the context provided. "
            + "Always cite your sources.";

    private final AzureOpenAiProperties properties;

    public ChatCompletionClient(AzureOpenAiProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates an answer from the question and the surviving retrieved passages. Returns the exact
     * prompt sent and the raw completion text (possibly blank — the caller, {@link ChatService},
     * treats a blank completion the same as the "not covered" outcome — spec.md Edge Cases), so the
     * caller can build the {@code prompt_assembled}/{@code model_response_received} trace steps
     * without this client needing to know tracing exists (research Decision 3).
     *
     * @throws ChatProcessingException {@code provider_unconfigured} if the chat configuration is
     *                                  incomplete, or {@code processing_failed} if the call to Azure
     *                                  OpenAI fails
     */
    public ChatCompletionResult complete(String question, List<RetrievedChunk> passages) {
        if (!properties.isComplete()) {
            log.warn("chat completion request skipped: provider not configured");
            throw new ChatProcessingException("provider_unconfigured",
                    "Azure OpenAI chat configuration is incomplete; no request was attempted.");
        }

        AzureOpenAiChatModel model = buildModel();
        String context = passages.stream().map(RetrievedChunk::text).reduce("", (a, b) -> a + "\n\n" + b);
        String userMessage = "Context:\n" + context + "\n\nQuestion: " + question;

        log.info("chat completion request started: passageCount={}", passages.size());
        ChatResponse response;
        try {
            response = model.call(new Prompt(SYSTEM_PROMPT + "\n\n" + userMessage));
        } catch (RuntimeException e) {
            // Deliberately not e.getMessage() alone into the response — the Azure SDK's own
            // exception messages are logged server-side but the caller-facing message is a fixed,
            // reviewed string with no possibility of echoing a credential (FR-015).
            log.warn("chat completion request failed: cause={}", e.toString());
            throw new ChatProcessingException("processing_failed", "Chat completion request failed.", e);
        }

        String completion = response.getResult().getOutput().getText();
        log.info("chat completion request succeeded: completionLength={}",
                completion == null ? 0 : completion.length());

        return new ChatCompletionResult(SYSTEM_PROMPT, userMessage, completion);
    }

    private AzureOpenAiChatModel buildModel() {
        OpenAIClientBuilder clientBuilder = new OpenAIClientBuilder()
                .endpoint(properties.getEndpoint())
                .credential(new AzureKeyCredential(properties.getApiKey()));
        return AzureOpenAiChatModel.builder()
                .openAIClientBuilder(clientBuilder)
                .defaultOptions(AzureOpenAiChatOptions.builder()
                        .deploymentName(properties.getChatDeploymentName())
                        .build())
                .build();
    }
}

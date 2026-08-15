package com.epam.aihelpdesk.health;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads the four Azure OpenAI values from Spring AI's own (nested) property paths — verified
 * against {@code spring-configuration-metadata.json}, see ai-provider.md — rather than
 * Spring AI's {@code AzureOpenAiChatProperties}, whose deployment name carries a non-blank
 * library default ("gpt-4o") and could never report as missing (FR-018, FR-021, FR-023).
 *
 * <p>A flat {@code @ConfigurationProperties(prefix = "spring.ai.azure.openai")} class cannot bind
 * these: the chat and embedding deployment names live three levels deep
 * ({@code chat.options.deployment-name}, {@code embedding.options.deployment-name}), not at
 * {@code chat-deployment-name} directly under the prefix. {@code @Value} reads each real path
 * explicitly instead.
 */
@Component
public class AzureOpenAiProperties {

    private final String apiKey;
    private final String endpoint;
    private final String chatDeploymentName;
    private final String embeddingDeploymentName;

    public AzureOpenAiProperties(
            @Value("${spring.ai.azure.openai.api-key:}") String apiKey,
            @Value("${spring.ai.azure.openai.endpoint:}") String endpoint,
            @Value("${spring.ai.azure.openai.chat.options.deployment-name:}") String chatDeploymentName,
            @Value("${spring.ai.azure.openai.embedding.options.deployment-name:}") String embeddingDeploymentName) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.chatDeploymentName = chatDeploymentName;
        this.embeddingDeploymentName = embeddingDeploymentName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getChatDeploymentName() {
        return chatDeploymentName;
    }

    public String getEmbeddingDeploymentName() {
        return embeddingDeploymentName;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Complete only when apiKey, endpoint and chatDeploymentName are all present and non-blank
     * (FR-021). embeddingDeploymentName is deliberately excluded (FR-023).
     */
    public boolean isComplete() {
        return !blank(apiKey) && !blank(endpoint) && !blank(chatDeploymentName);
    }

    /**
     * Complete only when apiKey, endpoint and embeddingDeploymentName are all present and
     * non-blank — a distinct check from {@link #isComplete()}, which intentionally excludes the
     * embedding deployment name and checks the chat deployment name instead (feature 001, FR-023).
     * Ingestion (feature 004) needs its own gate here: a chat-only configuration must not be
     * treated as ready for embedding calls, and vice versa (research Decision 6).
     */
    public boolean isEmbeddingComplete() {
        return !blank(apiKey) && !blank(endpoint) && !blank(embeddingDeploymentName);
    }

    /**
     * Names of the required settings (api-key, endpoint, chat-deployment-name) that are currently
     * blank, in the wire-format naming used by the health response.
     */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (blank(apiKey)) {
            missing.add("api-key");
        }
        if (blank(endpoint)) {
            missing.add("endpoint");
        }
        if (blank(chatDeploymentName)) {
            missing.add("chat-deployment-name");
        }
        return missing;
    }
}

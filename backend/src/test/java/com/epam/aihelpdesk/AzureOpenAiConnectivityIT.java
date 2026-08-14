package com.epam.aihelpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.epam.aihelpdesk.health.AzureOpenAiProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;

/**
 * Opt-in Azure connectivity check (FR-022, SC-008). Excluded from the default suite by the
 * "azure" tag and pom.xml's excludedGroups; runs only via {@code mvnw test -Pverify-ai}. Makes
 * exactly one minimal completion request against the chat deployment when configuration is
 * complete; fails immediately, without a request, when it is not.
 */
@Tag("azure")
@SpringBootTest
class AzureOpenAiConnectivityIT {

    @Autowired
    AzureOpenAiProperties properties;

    @Test
    void verifiesChatDeploymentReachable() {
        List<String> missingForChat = new ArrayList<>();
        if (isBlank(properties.getApiKey())) {
            missingForChat.add("api-key");
        }
        if (isBlank(properties.getEndpoint())) {
            missingForChat.add("endpoint");
        }
        if (isBlank(properties.getChatDeploymentName())) {
            missingForChat.add("chat-deployment-name");
        }
        if (isBlank(properties.getEmbeddingDeploymentName())) {
            // Reported, per FR-023, but does not fail the chat check on its own.
            System.out.println("AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME missing — not required here");
        }

        if (!missingForChat.isEmpty()) {
            fail("Azure OpenAI configuration incomplete, missing: " + missingForChat + ". No request was made.");
            return;
        }

        OpenAIClientBuilder clientBuilder = new OpenAIClientBuilder()
                .endpoint(properties.getEndpoint())
                .credential(new AzureKeyCredential(properties.getApiKey()));

        AzureOpenAiChatModel chatModel = AzureOpenAiChatModel.builder()
                .openAIClientBuilder(clientBuilder)
                .defaultOptions(AzureOpenAiChatOptions.builder()
                        .deploymentName(properties.getChatDeploymentName())
                        .maxTokens(8)
                        .build())
                .build();

        try {
            ChatResponse response = chatModel.call(new Prompt("Say \"ok\"."));
            assertThat(response.getResult().getOutput().getText()).isNotBlank();
        } catch (RuntimeException e) {
            fail("Azure OpenAI chat verification failed: " + e.getMessage(), e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

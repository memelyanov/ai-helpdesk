package com.epam.aihelpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import com.epam.aihelpdesk.health.AzureOpenAiConfigHealthIndicator;
import com.epam.aihelpdesk.health.AzureOpenAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Four cases over the FR-021 completeness rule. The indicator MUST never return DOWN (FR-020).
 */
class AzureOpenAiConfigHealthIndicatorTest {

    private static AzureOpenAiProperties propertiesOf(String apiKey, String endpoint, String chat, String embedding) {
        return new AzureOpenAiProperties(apiKey, endpoint, chat, embedding);
    }

    @Test
    void allThreeRequiredValuesPresent_reportsUp() {
        AzureOpenAiProperties properties = propertiesOf("key", "https://example.com", "chat-dep", "");
        Health health = new AzureOpenAiConfigHealthIndicator(properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void allAbsent_reportsUnknownNamingAllThreeMissing() {
        AzureOpenAiProperties properties = propertiesOf("", "", "", "");
        Health health = new AzureOpenAiConfigHealthIndicator(properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        @SuppressWarnings("unchecked")
        java.util.List<String> missing = (java.util.List<String>) health.getDetails().get("missing");
        assertThat(missing).containsExactlyInAnyOrder("api-key", "endpoint", "chat-deployment-name");
    }

    @Test
    void endpointPresentKeyBlank_reportsUnknownNeverUp() {
        AzureOpenAiProperties properties = propertiesOf("", "https://example.com", "chat-dep", "");
        Health health = new AzureOpenAiConfigHealthIndicator(properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void embeddingDeploymentNameUnset_stillReportsUp() {
        AzureOpenAiProperties properties = propertiesOf("key", "https://example.com", "chat-dep", "");
        Health health = new AzureOpenAiConfigHealthIndicator(properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void neverReturnsDown() {
        Health health = new AzureOpenAiConfigHealthIndicator(propertiesOf("", "", "", "")).health();

        assertThat(health.getStatus()).isNotEqualTo(Status.DOWN);
    }
}

package com.epam.aihelpdesk.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.epam.aihelpdesk.health.AzureOpenAiProperties;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Opt-in Azure connectivity check for the embedding deployment, extending
 * {@code AzureOpenAiConnectivityIT}'s construction pattern (research Decision 9). Excluded from the
 * default suite by the {@code azure} tag; runs only via {@code mvnw test -Pverify-ai}. Makes exactly
 * one real batched embedding call when configuration is complete; fails immediately, without a
 * request, when it is not — proving the hand-built {@link EmbeddingClient} actually reaches Azure.
 */
@Tag("azure")
@SpringBootTest
class EmbeddingClientAzureIT {

    @Autowired
    EmbeddingClient embeddingClient;

    @Autowired
    AzureOpenAiProperties properties;

    @Test
    void verifiesEmbeddingDeploymentReachable() {
        if (!properties.isEmbeddingComplete()) {
            fail("Azure OpenAI embedding configuration incomplete (api-key/endpoint/"
                    + "embedding-deployment-name). No request was made.");
            return;
        }

        List<ChunkDraft> chunks = List.of(new ChunkDraft(0, null, "Say hello."));

        List<EmbeddedChunk> result = embeddingClient.embed(chunks);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).embedding()).as("text-embedding-3-small is 1536-dimensional")
                .hasSize(1536);
    }
}

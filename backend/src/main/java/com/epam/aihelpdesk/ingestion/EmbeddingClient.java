package com.epam.aihelpdesk.ingestion;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.epam.aihelpdesk.health.AzureOpenAiProperties;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

/**
 * Embeds every chunk of a document in as few Azure OpenAI requests as possible — one batched call
 * per document, sub-batched only if the chunk count exceeds the provider's per-request input
 * ceiling (research Decision 4).
 *
 * <p>The {@link AzureOpenAiEmbeddingModel} is built by hand, the same construction pattern
 * {@code AzureOpenAiConnectivityIT} already uses for chat, rather than via Spring AI's
 * auto-configuration — {@code application.yml} deliberately pins
 * {@code spring.ai.model.embedding: none} so the application still boots with no Azure credentials
 * (feature 001, Decision 4; research Decision 6). {@link AzureOpenAiProperties#isEmbeddingComplete()}
 * is checked <em>before</em> any client is built or any network call is attempted, so an
 * unconfigured provider fails immediately and distinguishably from a genuine call failure
 * (FR-009/011).
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    /** Azure OpenAI's documented per-request input ceiling for {@code text-embedding-3-small}. */
    private static final int MAX_BATCH_SIZE = 2048;

    private final AzureOpenAiProperties properties;

    public EmbeddingClient(AzureOpenAiProperties properties) {
        this.properties = properties;
    }

    /**
     * Embeds every chunk, in order, returning one {@link EmbeddedChunk} per input chunk in the same
     * order. An empty input returns an empty result with no network call — vacuously satisfies
     * "every chunk embedded" for a document with zero chunks (FR-009, FR-015).
     *
     * @throws IngestionProcessingException {@code provider_unconfigured} if the embedding
     *                                       configuration is incomplete, or {@code processing_failed}
     *                                       if any sub-batch call to Azure OpenAI fails
     */
    public List<EmbeddedChunk> embed(List<ChunkDraft> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        if (!properties.isEmbeddingComplete()) {
            log.warn("embedding request skipped: provider not configured, chunkCount={}", chunks.size());
            throw new IngestionProcessingException("provider_unconfigured",
                    "Azure OpenAI embedding configuration is incomplete; no request was attempted.");
        }

        AzureOpenAiEmbeddingModel model = buildModel();
        List<EmbeddedChunk> embedded = new ArrayList<>(chunks.size());
        for (int batchStart = 0; batchStart < chunks.size(); batchStart += MAX_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + MAX_BATCH_SIZE, chunks.size());
            List<ChunkDraft> subBatch = chunks.subList(batchStart, batchEnd);
            embedded.addAll(embedBatch(model, subBatch));
        }
        return embedded;
    }

    private List<EmbeddedChunk> embedBatch(AzureOpenAiEmbeddingModel model, List<ChunkDraft> subBatch) {
        List<String> texts = subBatch.stream().map(ChunkDraft::text).toList();
        log.info("embedding request started: chunkCount={}", texts.size());
        EmbeddingResponse response;
        try {
            AzureOpenAiEmbeddingOptions options = AzureOpenAiEmbeddingOptions.builder()
                    .deploymentName(properties.getEmbeddingDeploymentName())
                    .build();
            response = model.call(new EmbeddingRequest(texts, options));
        } catch (RuntimeException e) {
            // Deliberately not e.getMessage() alone into the response — the Azure SDK's own
            // exception messages are logged server-side (FR-016) but the caller-facing message here
            // is a fixed, reviewed string with no possibility of echoing a credential (FR-014).
            log.warn("embedding request failed: chunkCount={}, cause={}", texts.size(), e.toString());
            throw new IngestionProcessingException("processing_failed", "Embedding request failed.", e);
        }

        List<Embedding> results = response.getResults();
        List<EmbeddedChunk> batchResult = new ArrayList<>(subBatch.size());
        for (int i = 0; i < subBatch.size(); i++) {
            batchResult.add(new EmbeddedChunk(subBatch.get(i), results.get(i).getOutput()));
        }
        log.info("embedding request succeeded: chunkCount={}", texts.size());
        return batchResult;
    }

    private AzureOpenAiEmbeddingModel buildModel() {
        OpenAIClientBuilder clientBuilder = new OpenAIClientBuilder()
                .endpoint(properties.getEndpoint())
                .credential(new AzureKeyCredential(properties.getApiKey()));
        AzureOpenAiEmbeddingOptions defaultOptions = AzureOpenAiEmbeddingOptions.builder()
                .deploymentName(properties.getEmbeddingDeploymentName())
                .build();
        return new AzureOpenAiEmbeddingModel(clientBuilder.buildClient(), MetadataMode.EMBED, defaultOptions);
    }
}

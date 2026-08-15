package com.epam.aihelpdesk.ingestion;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.epam.aihelpdesk.ingestion.dto.DocumentIngestionResponse;

/**
 * Orchestrates the ingestion pipeline: parse (Tika) → chunk (jtokkit) → embed (Azure OpenAI) →
 * write (JDBC, one transaction). No database access happens until every chunk already has its
 * embedding in hand (FR-008/009, research Decision 5) — everything before the
 * {@link DocumentRepository#save} call is in-memory only.
 *
 * <p>Logs one structured outcome record per upload attempt — accepted, rejected (FR-002/003/005),
 * or failed (FR-009's failure case) — satisfying FR-016 at the single call site every request
 * passes through, regardless of which pipeline stage produced the outcome.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final TextExtractor textExtractor;
    private final Chunker chunker;
    private final EmbeddingClient embeddingClient;
    private final DocumentRepository documentRepository;

    public IngestionService(TextExtractor textExtractor, Chunker chunker, EmbeddingClient embeddingClient,
            DocumentRepository documentRepository) {
        this.textExtractor = textExtractor;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.documentRepository = documentRepository;
    }

    public DocumentIngestionResponse ingest(String filename, byte[] content) {
        try {
            TextExtractionResult extraction = textExtractor.extract(content);
            List<ChunkDraft> chunkDrafts = chunker.chunk(extraction.pages());
            List<EmbeddedChunk> embeddedChunks = embeddingClient.embed(chunkDrafts);
            UUID documentId = documentRepository.save(filename, extraction.contentType(), content, embeddedChunks);

            log.info("upload accepted: filename={}, documentId={}, chunkCount={}", filename, documentId,
                    embeddedChunks.size());
            return new DocumentIngestionResponse(documentId, embeddedChunks.size());
        } catch (InvalidDocumentException e) {
            log.info("upload rejected: filename={}, errorCode={}", filename, e.errorCode());
            throw e;
        } catch (IngestionProcessingException e) {
            log.warn("upload failed: filename={}, errorCode={}", filename, e.errorCode());
            throw e;
        }
    }
}

package com.epam.aihelpdesk.ingestion;

import com.pgvector.PGvector;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes a document and its full chunk set in exactly one transaction (FR-009, research
 * Decision 5) — plain {@link JdbcTemplate}, no JPA/Hibernate (research Decision 7), consistent
 * with feature 001/003. The transaction is opened only once every chunk already carries its
 * embedding ({@link EmbeddedChunk}); this method never sees a chunk without one, so there is no
 * partial-write state to guard against inside it.
 *
 * <p>{@code source_filename} and {@code page_number} are written per chunk row alongside its
 * {@code embedding} — {@code source_filename} is a denormalized copy of the owning document's
 * filename (db/init/02-documents-and-chunks.sql), not re-derived or left null (FR-007).
 */
@Repository
public class DocumentRepository {

    private static final Logger log = LoggerFactory.getLogger(DocumentRepository.class);

    private static final String INSERT_DOCUMENT =
            "INSERT INTO documents (filename, content_type, content) VALUES (?, ?, ?) RETURNING id";
    private static final String INSERT_CHUNK = "INSERT INTO chunks "
            + "(document_id, chunk_id, source_filename, page_number, text, embedding) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String DELETE_DOCUMENT = "DELETE FROM documents WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * @throws IngestionProcessingException {@code processing_failed} if the transaction fails for
     *                                       any reason — no partial rows survive (FR-009)
     */
    public UUID save(String filename, String contentType, byte[] content, List<EmbeddedChunk> chunks) {
        log.info("document write started: filename={}, chunkCount={}", filename, chunks.size());
        try {
            UUID documentId = transactionTemplate.execute(status -> {
                UUID id = insertDocument(filename, contentType, content);
                for (EmbeddedChunk embeddedChunk : chunks) {
                    insertChunk(id, filename, embeddedChunk);
                }
                return id;
            });
            log.info("document write succeeded: documentId={}, filename={}, chunkCount={}", documentId, filename,
                    chunks.size());
            return documentId;
        } catch (RuntimeException e) {
            log.warn("document write failed: filename={}, chunkCount={}, cause={}", filename, chunks.size(),
                    e.toString());
            throw new IngestionProcessingException("processing_failed", "Failed to persist the document.", e);
        }
    }

    private UUID insertDocument(String filename, String contentType, byte[] content) {
        return jdbcTemplate.queryForObject(INSERT_DOCUMENT,
                (resultSet, rowNum) -> (UUID) resultSet.getObject("id"), filename, contentType, content);
    }

    private void insertChunk(UUID documentId, String sourceFilename, EmbeddedChunk embeddedChunk) {
        ChunkDraft chunk = embeddedChunk.chunk();
        jdbcTemplate.update(INSERT_CHUNK, documentId, chunk.chunkId(), sourceFilename, chunk.pageNumber(),
                chunk.text(), new PGvector(embeddedChunk.embedding()));
    }

    /**
     * One {@code DELETE FROM documents WHERE id = ?} statement — its own affected-row count is the
     * only signal needed to decide "deleted" from "nothing to delete" (research Decision 4): no
     * prior existence check runs first, so there is no check-then-act race window. Every one of
     * {@code id}'s {@code chunks} rows is removed in the same statement via feature 003's
     * {@code ON DELETE CASCADE} (FR-011) — no explicit {@link TransactionTemplate} is needed, since a
     * single statement (cascading actions included) is already atomic in PostgreSQL (research
     * Decision 5).
     *
     * @return {@code true} if exactly one row (and its cascaded chunks) was deleted; {@code false} if
     *         no row matched {@code id} — it never existed or was already deleted (FR-008)
     * @throws DocumentDeletionException {@code deletion_failed} if the statement fails unexpectedly —
     *                                    no row is deleted (FR-010, statement-level atomicity)
     */
    public boolean deleteById(UUID id) {
        try {
            int rowsAffected = jdbcTemplate.update(DELETE_DOCUMENT, id);
            boolean deleted = rowsAffected == 1;
            if (deleted) {
                log.info("document delete succeeded: documentId={}", id);
            } else {
                log.info("document delete found nothing to delete: documentId={}", id);
            }
            return deleted;
        } catch (RuntimeException e) {
            log.warn("document delete failed: documentId={}, cause={}", id, e.toString());
            throw new DocumentDeletionException("Failed to delete the document.", e);
        }
    }
}

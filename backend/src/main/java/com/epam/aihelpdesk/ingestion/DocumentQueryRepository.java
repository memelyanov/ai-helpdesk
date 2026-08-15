package com.epam.aihelpdesk.ingestion;

import com.epam.aihelpdesk.ingestion.dto.DocumentSummaryResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries against the {@code documents}/{@code chunks} schema (feature 003) for
 * {@code GET /documents} and {@code GET /documents/{id}/content}. Kept separate from
 * {@link DocumentRepository} — that class is documented and tested narrowly around the
 * write/transaction path {@code POST /documents} needs; these reads are plain, non-transactional
 * {@code SELECT}s with no overlapping concern (research Decision 5).
 */
@Repository
public class DocumentQueryRepository {

    private static final String SELECT_ALL_WITH_CHUNK_COUNT =
            "SELECT d.id, d.filename, d.content_type, d.uploaded_at, count(c.id) AS chunk_count "
                    + "FROM documents d LEFT JOIN chunks c ON c.document_id = d.id "
                    + "GROUP BY d.id, d.filename, d.content_type, d.uploaded_at "
                    + "ORDER BY d.uploaded_at DESC, d.id DESC";

    private static final String SELECT_CONTENT_BY_ID =
            "SELECT filename, content_type, content FROM documents WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;

    public DocumentQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Every ingested document, newest-first (FR-005), including zero-chunk documents (FR-003) —
     * a {@code LEFT JOIN}, never an {@code INNER JOIN}, so a document with no matching {@code
     * chunks} row still appears with {@code chunkCount: 0} rather than being silently dropped.
     * {@code ORDER BY ... , d.id DESC} makes ordering deterministic even when two documents share
     * an identical {@code uploaded_at} timestamp (FR-005).
     */
    public List<DocumentSummaryResponse> findAll() {
        return jdbcTemplate.query(SELECT_ALL_WITH_CHUNK_COUNT, DocumentQueryRepository::mapSummary);
    }

    /**
     * A document's original stored content by id, or {@link Optional#empty()} when no
     * {@code documents} row matches — {@link DocumentController} maps an empty result to
     * {@link DocumentNotFoundException} (FR-010).
     */
    public Optional<DocumentContent> findContentById(UUID id) {
        try {
            DocumentContent content = jdbcTemplate.queryForObject(SELECT_CONTENT_BY_ID,
                    DocumentQueryRepository::mapContent, id);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static DocumentSummaryResponse mapSummary(ResultSet resultSet, int rowNum) throws SQLException {
        UUID documentId = (UUID) resultSet.getObject("id");
        String filename = resultSet.getString("filename");
        String contentType = resultSet.getString("content_type");
        OffsetDateTime uploadedAt = toOffsetDateTime(resultSet.getTimestamp("uploaded_at"));
        long chunkCount = resultSet.getLong("chunk_count");
        return new DocumentSummaryResponse(documentId, filename, contentType, uploadedAt, chunkCount);
    }

    private static DocumentContent mapContent(ResultSet resultSet, int rowNum) throws SQLException {
        String filename = resultSet.getString("filename");
        String contentType = resultSet.getString("content_type");
        byte[] content = resultSet.getBytes("content");
        return new DocumentContent(filename, contentType, content);
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
}

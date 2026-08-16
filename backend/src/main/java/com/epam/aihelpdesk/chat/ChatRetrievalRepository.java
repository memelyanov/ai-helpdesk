package com.epam.aihelpdesk.chat;

import com.pgvector.PGvector;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.stereotype.Repository;

/**
 * Issues feature 003's {@code similarity-search-contract.md} query verbatim — top-{@code K}
 * nearest {@code chunks} rows by pgvector cosine distance (research Decision 5), optionally
 * restricted to a caller-supplied set of document ids (FR-010). No similarity threshold appears in
 * this SQL; {@code ChatService} applies it afterward, in application code, against this
 * already-limited result set (research Decision 5 — matches the constitution's "if top-K
 * similarity scores are all below threshold" wording, a check performed on the top-K set, not a
 * filter that changes the candidate pool itself).
 */
@Repository
public class ChatRetrievalRepository {

    private static final Logger log = LoggerFactory.getLogger(ChatRetrievalRepository.class);

    private static final String SELECT_TOP_K = "SELECT c.document_id, c.chunk_id, c.source_filename, "
            + "c.page_number, c.text, c.embedding <=> ? AS distance "
            + "FROM chunks c "
            + "ORDER BY c.embedding <=> ? "
            + "LIMIT ?";

    private static final String SELECT_TOP_K_FILTERED = "SELECT c.document_id, c.chunk_id, c.source_filename, "
            + "c.page_number, c.text, c.embedding <=> ? AS distance "
            + "FROM chunks c "
            + "WHERE c.document_id = ANY(?) "
            + "ORDER BY c.embedding <=> ? "
            + "LIMIT ?";

    private final JdbcTemplate jdbcTemplate;

    public ChatRetrievalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The {@code topK} chunks most similar to {@code queryVector}, closest first, optionally
     * restricted to {@code documentIds} (non-{@code null}, non-empty means "filter"; {@code null}
     * or empty means "search the whole corpus," FR-010). An empty or non-matching filter simply
     * yields an empty result — no special-casing needed, since that is exactly FR-007's required
     * "nothing relevant" outcome once {@code ChatService} sees zero surviving rows.
     *
     * @throws ChatProcessingException {@code processing_failed} if the query fails
     */
    public List<RetrievedChunk> findTopSimilarChunks(float[] queryVector, int topK, List<UUID> documentIds) {
        boolean filtered = documentIds != null && !documentIds.isEmpty();
        log.info("retrieval query started: topK={}, filtered={}", topK, filtered);
        PGvector vector = new PGvector(queryVector);
        try {
            List<RetrievedChunk> results = filtered
                    ? jdbcTemplate.query(SELECT_TOP_K_FILTERED, filteredSetter(vector, documentIds, topK),
                            ChatRetrievalRepository::mapRow)
                    : jdbcTemplate.query(SELECT_TOP_K, unfilteredSetter(vector, topK),
                            ChatRetrievalRepository::mapRow);
            log.info("retrieval query succeeded: rowCount={}", results.size());
            return results;
        } catch (RuntimeException e) {
            log.warn("retrieval query failed: cause={}", e.toString());
            throw new ChatProcessingException("processing_failed", "Failed to search the document corpus.", e);
        }
    }

    private static PreparedStatementSetter unfilteredSetter(PGvector vector, int topK) {
        return ps -> {
            ps.setObject(1, vector);
            ps.setObject(2, vector);
            ps.setInt(3, topK);
        };
    }

    private static PreparedStatementSetter filteredSetter(PGvector vector, List<UUID> documentIds, int topK) {
        return ps -> {
            ps.setObject(1, vector);
            Array idsArray = ps.getConnection().createArrayOf("uuid", documentIds.toArray());
            ps.setArray(2, idsArray);
            ps.setObject(3, vector);
            ps.setInt(4, topK);
        };
    }

    private static RetrievedChunk mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        UUID documentId = (UUID) resultSet.getObject("document_id");
        int chunkId = resultSet.getInt("chunk_id");
        String sourceFilename = resultSet.getString("source_filename");
        int pageNumberValue = resultSet.getInt("page_number");
        Integer pageNumber = resultSet.wasNull() ? null : pageNumberValue;
        String text = resultSet.getString("text");
        double distance = resultSet.getDouble("distance");
        return new RetrievedChunk(documentId, chunkId, sourceFilename, pageNumber, text, distance);
    }
}

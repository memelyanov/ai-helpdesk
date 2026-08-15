package com.epam.aihelpdesk.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Schema-verification tests for the {@code documents} and {@code chunks} tables
 * (specs/003-document-vector-schema). Applies the real init scripts — the same ones
 * {@code docker-compose.yml} runs — against a disposable Testcontainers instance and asserts the
 * guarantees recorded in data-model.md and contracts/: referential integrity, cascade delete,
 * per-document {@code chunk_id} uniqueness, the {@code page_number} "no page" convention, and
 * embedding-dimension enforcement.
 *
 * <p>Excluded from the default suite by the {@code db} tag and pom.xml's {@code excludedGroups}
 * (feature 001 research Decision 11's named exception, feature 003 research Decision 9); runs only
 * via {@code mvnw test -Pverify-db}, which requires a running Docker daemon. Uses plain JDBC, no
 * JPA/ORM, consistent with feature 001 research Decision 7.
 *
 * <p>Test methods are added incrementally per user story (specs/003-document-vector-schema/tasks.md);
 * each test inserts and asserts only against the rows it creates, so no cross-test table reset is
 * needed — every assertion is scoped by the {@code id}/{@code document_id} returned from its own
 * insert.
 */
@Tag("db")
@Testcontainers
class DocumentsAndChunksSchemaIT {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("aihelpdesk")
            .withUsername("aihelpdesk")
            .withPassword("aihelpdesk");

    /**
     * Applies the init scripts once, in the same order {@code docker-compose.yml}'s
     * {@code /docker-entrypoint-initdb.d/} mechanism would run them. Runs once for the whole class
     * (single disposable container), not per test method.
     */
    @BeforeAll
    static void applySchema() throws IOException, SQLException {
        runScript("../db/init/01-init-vector.sql");
        runScript("../db/init/02-documents-and-chunks.sql");
    }

    // ---------------------------------------------------------------------
    // User Story 1 — store and retrieve the original document (FR-001–FR-005, FR-014, FR-015)
    // ---------------------------------------------------------------------

    @Test
    void documentRoundTripIsByteIdentical() throws SQLException {
        try (Connection connection = connect()) {
            byte[] txtContent = "hello world".getBytes(StandardCharsets.UTF_8);
            UUID txtId = insertDocument(connection, "sample.txt", "text/plain", txtContent);

            // Not a real PDF — just non-UTF-8-safe binary bytes, enough to prove BYTEA round-trips
            // arbitrary binary content unchanged (FR-001/FR-003), not merely text.
            byte[] pdfContent = {0x25, 0x50, 0x44, 0x46, (byte) 0xFF, 0x00, 0x01, 0x7E};
            UUID pdfId = insertDocument(connection, "sample.pdf", "application/pdf", pdfContent);

            assertDocumentMatches(connection, txtId, "sample.txt", "text/plain", txtContent);
            assertDocumentMatches(connection, pdfId, "sample.pdf", "application/pdf", pdfContent);
        }
    }

    @Test
    void rejectsDisallowedContentTypeAndEmptyContent() throws SQLException {
        try (Connection connection = connect()) {
            assertThatThrownBy(() -> insertDocument(connection, "sample.docx", "application/msword",
                    "x".getBytes(StandardCharsets.UTF_8)))
                    .asInstanceOf(throwable(SQLException.class))
                    .extracting(SQLException::getSQLState)
                    .isEqualTo("23514"); // check_violation

            assertThatThrownBy(() -> insertDocument(connection, "empty.txt", "text/plain", new byte[0]))
                    .asInstanceOf(throwable(SQLException.class))
                    .extracting(SQLException::getSQLState)
                    .isEqualTo("23514"); // check_violation
        }
    }

    @Test
    void deletingDocumentMakesItUnfindableSameAsNeverExisting() throws SQLException {
        try (Connection connection = connect()) {
            UUID id = insertDocument(connection, "to-delete.txt", "text/plain",
                    "bye".getBytes(StandardCharsets.UTF_8));

            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM documents WHERE id = ?")) {
                delete.setObject(1, id);
                assertThat(delete.executeUpdate()).as("one row deleted").isEqualTo(1);
            }

            assertThat(documentExists(connection, id)).as("deleted document no longer found").isFalse();
            assertThat(documentExists(connection, UUID.randomUUID()))
                    .as("never-existed document also not found — same outcome as deleted")
                    .isFalse();
        }
    }

    // ---------------------------------------------------------------------
    // User Story 2 — store searchable chunks with vector, text, and metadata
    // (FR-006–FR-008, FR-011, FR-012, FR-016)
    // ---------------------------------------------------------------------

    @Test
    void chunkRoundTripPersistsVectorTextAndMetadata() throws SQLException {
        try (Connection connection = connect()) {
            UUID documentId = insertDocument(connection, "policy.pdf", "application/pdf",
                    "policy body".getBytes(StandardCharsets.UTF_8));
            float[] embedding = sampleEmbedding(1536);

            insertChunk(connection, documentId, 0, "policy.pdf", 5, "chunk zero text", embedding);

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT document_id, chunk_id, source_filename, page_number, text, "
                            + "embedding::text AS embedding_text FROM chunks "
                            + "WHERE document_id = ? AND chunk_id = 0")) {
                statement.setObject(1, documentId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).as("chunk row exists").isTrue();
                    assertThat(resultSet.getObject("document_id", UUID.class)).isEqualTo(documentId);
                    assertThat(resultSet.getInt("chunk_id")).isZero();
                    assertThat(resultSet.getString("source_filename")).isEqualTo("policy.pdf");
                    assertThat(resultSet.getInt("page_number")).isEqualTo(5);
                    assertThat(resultSet.getString("text")).isEqualTo("chunk zero text");
                    assertThat(parseVector(resultSet.getString("embedding_text"))).containsExactly(embedding);
                }
            }
        }
    }

    @Test
    void pageNumberNullConventionAndPositivityCheck() throws SQLException {
        try (Connection connection = connect()) {
            UUID documentId = insertDocument(connection, "notes.txt", "text/plain",
                    "no pages here".getBytes(StandardCharsets.UTF_8));

            insertChunk(connection, documentId, 0, "notes.txt", null, "chunk text", sampleEmbedding(1536));

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT page_number FROM chunks WHERE document_id = ? AND chunk_id = 0")) {
                statement.setObject(1, documentId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    resultSet.getInt("page_number");
                    assertThat(resultSet.wasNull()).as("page_number is NULL, never a numeric placeholder").isTrue();
                }
            }

            assertThatThrownBy(() -> insertChunk(connection, documentId, 1, "notes.txt", 0, "x",
                    sampleEmbedding(1536)))
                    .asInstanceOf(throwable(SQLException.class))
                    .extracting(SQLException::getSQLState)
                    .isEqualTo("23514"); // check_violation — page_number = 0 rejected

            assertThatThrownBy(() -> insertChunk(connection, documentId, 2, "notes.txt", -1, "x",
                    sampleEmbedding(1536)))
                    .asInstanceOf(throwable(SQLException.class))
                    .extracting(SQLException::getSQLState)
                    .isEqualTo("23514"); // check_violation — negative page_number rejected
        }
    }

    @Test
    void rejectsOrphanChunkAndDuplicateChunkIdWithinDocumentButNotAcrossDocuments() throws SQLException {
        try (Connection connection = connect()) {
            assertThatThrownBy(() -> insertChunk(connection, UUID.randomUUID(), 0, "ghost.txt", null, "x",
                    sampleEmbedding(1536)))
                    .asInstanceOf(throwable(SQLException.class))
                    .extracting(SQLException::getSQLState)
                    .isEqualTo("23503"); // foreign_key_violation — FR-007

            UUID documentA = insertDocument(connection, "a.txt", "text/plain", "a".getBytes(StandardCharsets.UTF_8));
            UUID documentB = insertDocument(connection, "b.txt", "text/plain", "b".getBytes(StandardCharsets.UTF_8));

            insertChunk(connection, documentA, 0, "a.txt", null, "a chunk 0", sampleEmbedding(1536));

            assertThatThrownBy(() -> insertChunk(connection, documentA, 0, "a.txt", null, "dup",
                    sampleEmbedding(1536)))
                    .asInstanceOf(throwable(SQLException.class))
                    .extracting(SQLException::getSQLState)
                    .isEqualTo("23505"); // unique_violation — FR-012, same document

            long chunkBId = insertChunk(connection, documentB, 0, "b.txt", null, "b chunk 0",
                    sampleEmbedding(1536));
            assertThat(chunkBId).as("same chunk_id succeeds for a different document").isPositive();
        }
    }

    @Test
    void deletingDocumentCascadesToItsChunks() throws SQLException {
        try (Connection connection = connect()) {
            UUID documentId = insertDocument(connection, "cascade.txt", "text/plain",
                    "content".getBytes(StandardCharsets.UTF_8));
            insertChunk(connection, documentId, 0, "cascade.txt", null, "chunk 0", sampleEmbedding(1536));
            insertChunk(connection, documentId, 1, "cascade.txt", null, "chunk 1", sampleEmbedding(1536));

            assertThat(countChunks(connection, documentId)).isEqualTo(2);

            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM documents WHERE id = ?")) {
                delete.setObject(1, documentId);
                delete.executeUpdate();
            }

            assertThat(countChunks(connection, documentId)).as("chunks removed with their document").isZero();
        }
    }

    @Test
    void rejectsEmbeddingWithWrongDimensionality() throws SQLException {
        try (Connection connection = connect()) {
            UUID documentId = insertDocument(connection, "dim.txt", "text/plain",
                    "x".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> insertChunk(connection, documentId, 0, "dim.txt", null, "x",
                    sampleEmbedding(3)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("dimension");
        }
    }

    // ---------------------------------------------------------------------
    // User Story 3 — trace a search hit back to a downloadable document (FR-009, SC-003)
    // ---------------------------------------------------------------------

    @Test
    void similaritySearchResultResolvesToItsSourceDocument() throws SQLException {
        try (Connection connection = connect()) {
            UUID documentId = insertDocument(connection, "travel-expense-policy.pdf", "application/pdf",
                    "reimbursement rules".getBytes(StandardCharsets.UTF_8));
            // A pattern distinct from sampleEmbedding()'s (used by every other test in this shared,
            // non-truncated table), so this chunk is the unique exact (distance-0) match for its own
            // query vector regardless of what other tests have already inserted — an unscoped top-K
            // search is otherwise not guaranteed to rank it first among unrelated ties.
            float[] embedding = distinctEmbedding(1536);
            insertChunk(connection, documentId, 0, "travel-expense-policy.pdf", 5,
                    "taxi reimbursement text", embedding);

            // The exact query shape from contracts/similarity-search-contract.md, unscoped.
            List<UUID> hits = similaritySearchDocumentIds(connection, embedding, 4);

            assertThat(hits).as("the known document's chunk is the top result").isNotEmpty();
            UUID topHit = hits.get(0);
            assertThat(topHit).isEqualTo(documentId);

            assertThat(documentExists(connection, topHit))
                    .as("the search result's document_id resolves to a downloadable document (US1)")
                    .isTrue();
        }
    }

    @Test
    void multipleChunksFromSameDocumentAllReportItAndAnUnmatchedDocumentReturnsNoRows() throws SQLException {
        try (Connection connection = connect()) {
            UUID documentId = insertDocument(connection, "vacation-policy.pdf", "application/pdf",
                    "vacation rules".getBytes(StandardCharsets.UTF_8));
            float[] embedding = sampleEmbedding(1536);
            insertChunk(connection, documentId, 0, "vacation-policy.pdf", 1, "chunk zero", embedding);
            insertChunk(connection, documentId, 1, "vacation-policy.pdf", 2, "chunk one", embedding);

            List<UUID> hits = similaritySearchDocumentIdsForDocument(connection, embedding, 10, documentId);

            assertThat(hits).as("both chunks resolve to the same source document")
                    .hasSize(2)
                    .containsOnly(documentId);

            // Edge Case: a search scoped to a document with zero chunks returns zero rows, not an
            // error — equivalent in effect to running the search before any chunk has ever been
            // ingested (spec.md Edge Case), without depending on the whole table being empty, which
            // this shared, non-truncated test class cannot guarantee across test execution order.
            UUID chunklessDocumentId = insertDocument(connection, "empty.txt", "text/plain",
                    "no chunks yet".getBytes(StandardCharsets.UTF_8));

            List<UUID> noHits = similaritySearchDocumentIdsForDocument(connection, embedding, 10,
                    chunklessDocumentId);

            assertThat(noHits).as("no matching chunks — empty result, not an error").isEmpty();
        }
    }

    // ---------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static UUID insertDocument(Connection connection, String filename, String contentType, byte[] content)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO documents (filename, content_type, content) VALUES (?, ?, ?) RETURNING id")) {
            statement.setString(1, filename);
            statement.setString(2, contentType);
            statement.setBytes(3, content);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("id");
            }
        }
    }

    private static void assertDocumentMatches(Connection connection, UUID id, String expectedFilename,
            String expectedContentType, byte[] expectedContent) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT filename, content_type, content, uploaded_at FROM documents WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("document row exists").isTrue();
                assertThat(resultSet.getString("filename")).isEqualTo(expectedFilename);
                assertThat(resultSet.getString("content_type")).isEqualTo(expectedContentType);
                assertThat(resultSet.getBytes("content")).isEqualTo(expectedContent);
                assertThat(resultSet.getTimestamp("uploaded_at")).isNotNull();
            }
        }
    }

    private static boolean documentExists(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM documents WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static long insertChunk(Connection connection, UUID documentId, int chunkId, String sourceFilename,
            Integer pageNumber, String text, float[] embedding) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO chunks (document_id, chunk_id, source_filename, page_number, text, embedding) "
                        + "VALUES (?, ?, ?, ?, ?, ?::vector) RETURNING id")) {
            statement.setObject(1, documentId);
            statement.setInt(2, chunkId);
            statement.setString(3, sourceFilename);
            if (pageNumber == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, pageNumber);
            }
            statement.setString(5, text);
            statement.setString(6, vectorLiteral(embedding));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("id");
            }
        }
    }

    /**
     * The exact query shape from contracts/similarity-search-contract.md, unfiltered — a real
     * caller would apply its own {@code LIMIT} (top-K); this returns every matched
     * {@code document_id} in distance order.
     */
    private static List<UUID> similaritySearchDocumentIds(Connection connection, float[] queryVector, int k)
            throws SQLException {
        String literal = vectorLiteral(queryVector);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT document_id, chunk_id, source_filename, page_number, text, "
                        + "embedding <=> ?::vector AS distance FROM chunks "
                        + "ORDER BY embedding <=> ?::vector LIMIT ?")) {
            statement.setString(1, literal);
            statement.setString(2, literal);
            statement.setInt(3, k);
            return collectDocumentIds(statement);
        }
    }

    /**
     * The same contract query, additionally scoped to one {@code document_id} — the
     * "exact-match column filtering" the contract calls out as possible with no join. Used here so
     * an "unmatched query" assertion doesn't depend on the whole (shared, non-truncated) table being
     * empty.
     */
    private static List<UUID> similaritySearchDocumentIdsForDocument(Connection connection, float[] queryVector,
            int k, UUID documentId) throws SQLException {
        String literal = vectorLiteral(queryVector);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT document_id, chunk_id, source_filename, page_number, text, "
                        + "embedding <=> ?::vector AS distance FROM chunks "
                        + "WHERE document_id = ? ORDER BY embedding <=> ?::vector LIMIT ?")) {
            statement.setString(1, literal);
            statement.setObject(2, documentId);
            statement.setString(3, literal);
            statement.setInt(4, k);
            return collectDocumentIds(statement);
        }
    }

    private static List<UUID> collectDocumentIds(PreparedStatement statement) throws SQLException {
        List<UUID> results = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(resultSet.getObject("document_id", UUID.class));
            }
        }
        return results;
    }

    private static int countChunks(Connection connection, UUID documentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM chunks WHERE document_id = ?")) {
            statement.setObject(1, documentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    /**
     * A deterministic embedding vector whose values ({@code 0}, {@code 0.25}, {@code 0.5},
     * {@code 0.75} cycling) are all exactly representable in binary floating point, so the
     * text-round-trip comparison in {@link #chunkRoundTripPersistsVectorTextAndMetadata()} is not
     * sensitive to formatting/precision noise.
     */
    private static float[] sampleEmbedding(int dimensions) {
        float[] values = new float[dimensions];
        for (int i = 0; i < values.length; i++) {
            values[i] = (i % 4) * 0.25f;
        }
        return values;
    }

    /**
     * A different exactly-representable pattern ({@code 1}, {@code 0.75}, {@code 0.5},
     * {@code 0.25} cycling) from {@link #sampleEmbedding(int)}'s, so a chunk written with this
     * embedding is never a distance-0 tie with a chunk written using the other one — see
     * {@link #similaritySearchResultResolvesToItsSourceDocument()}.
     */
    private static float[] distinctEmbedding(int dimensions) {
        float[] values = new float[dimensions];
        for (int i = 0; i < values.length; i++) {
            values[i] = 1.0f - (i % 4) * 0.25f;
        }
        return values;
    }

    private static String vectorLiteral(float[] values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.append(']').toString();
    }

    private static float[] parseVector(String literal) {
        String trimmed = literal.substring(1, literal.length() - 1);
        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }

    /**
     * Runs a {@code .sql} file's full contents as one JDBC {@link Statement#execute(String)} call.
     * The PostgreSQL JDBC driver sends a plain {@link Statement}'s SQL text via the simple query
     * protocol, which accepts multiple {@code ;}-separated statements in one call — exactly what an
     * init script is.
     *
     * @param relativePathFromModuleBasedir path relative to the {@code backend/} module directory
     *                                      (surefire's working directory), e.g.
     *                                      {@code "../db/init/01-init-vector.sql"}
     */
    private static void runScript(String relativePathFromModuleBasedir) throws IOException, SQLException {
        String sql = Files.readString(Path.of(relativePathFromModuleBasedir));
        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}

package com.epam.aihelpdesk.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epam.aihelpdesk.chat.dto.ChatResponse;
import com.epam.aihelpdesk.ingestion.EmbeddingClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Full-pipeline integration test against a real Testcontainers {@code pgvector/pgvector:pg18}
 * database for {@code POST /chat}'s retrieval half — proves the real pgvector {@code <=>} query
 * ranks and caps chunks correctly (FR-004), that the 0.5 similarity threshold is genuinely
 * inclusive (FR-005), that a {@code documentIds} filter actually narrows the candidate set
 * (FR-010, Acceptance Scenario 5), and that a document-store failure surfaces as
 * {@code processing_failed} rather than a silent empty result (User Story 3 Scenario 2) — with
 * {@link com.epam.aihelpdesk.chat.ChatCompletionClient} stubbed via {@code @MockitoBean}
 * (constitution Principle II). Excluded from the default suite by the {@code db} tag; runs only
 * via {@code mvnw test -Pverify-db}.
 *
 * <p>Reuses {@code DocumentIngestionIT}/{@code DocumentQueryIT}'s exact container/schema bring-up
 * pattern. Every test seeds chunks in an orthogonal slice of the 1536-dimensional embedding space
 * (a distinct pair/run of dimension indices per test) so that, since {@link #POSTGRES} is a single
 * container shared across every {@code @Test} method in this class, one test's seeded vectors can
 * never accidentally rank into another test's top-K results — their cosine similarity to a query
 * vector outside their own dimension slice is always exactly {@code 0}.
 */
@Tag("db")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ChatRetrievalIT {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("aihelpdesk")
            .withUsername("aihelpdesk")
            .withPassword("aihelpdesk");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void applySchema() throws IOException, SQLException {
        runScript("../db/init/01-init-vector.sql");
        runScript("../db/init/02-documents-and-chunks.sql");
    }

    private static final int EMBEDDING_DIMENSIONS = 1536;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EmbeddingClient embeddingClient;

    @MockitoBean
    ChatCompletionClient chatCompletionClient;

    // -----------------------------------------------------------------------------------------
    // User Story 1 — real pgvector ranking, TOP_K cap, inclusive threshold boundary (FR-004/005)
    // -----------------------------------------------------------------------------------------

    @Test
    void ranksChunksBySimilarityCapsAtTopKAndIncludesTheInclusiveThresholdBoundary() throws Exception {
        UUID documentId = insertDocument("rank-it.txt");
        // Query vector: unit vector along dim 100. Five chunks in the same orthogonal slice, at
        // decreasing similarity: 1.0, 1/sqrt(2)~=0.707, 1/sqrt(3)~=0.577, 1/2=0.5 (exact — both the
        // query and this chunk use only integer 0/1 components, so pgvector's float32 division
        // 1/2 is bit-exact, making this a genuine, non-flaky boundary case), 1/sqrt(5)~=0.447.
        float[] query = unitVector(100);
        insertChunk(documentId, 1, "rank-it.txt", 1, "closest passage", axisSum(100));
        insertChunk(documentId, 2, "rank-it.txt", 2, "second passage", axisSum(100, 101));
        insertChunk(documentId, 3, "rank-it.txt", 3, "third passage", axisSum(100, 101, 102));
        insertChunk(documentId, 4, "rank-it.txt", 4, "fourth passage, exactly at threshold",
                axisSum(100, 101, 102, 103));
        insertChunk(documentId, 5, "rank-it.txt", 5, "fifth passage, excluded by TOP_K cap",
                axisSum(100, 101, 102, 103, 104));
        when(embeddingClient.embedQuery(anyString())).thenReturn(query);
        when(chatCompletionClient.complete(any(), any()))
                .thenReturn(new ChatCompletionResult("system prompt", "prompt", "A grounded answer."));

        JsonNode body = postChat("Question scoped to the rank-it corpus", null);

        JsonNode sources = body.get("sources");
        assertThat(sources).as("TOP_K=4 caps the result even though 5 chunks are above threshold")
                .hasSize(4);
        List<String> pages = List.of(sources.get(0).get("page").asText(), sources.get(1).get("page").asText(),
                sources.get(2).get("page").asText(), sources.get(3).get("page").asText());
        assertThat(pages).as("closest-first order, and the 5th (weakest) chunk never reaches the app layer")
                .containsExactly("1", "2", "3", "4");
        assertThat(sources.get(3).get("score").asDouble())
                .as("the exact-0.5 chunk is included, not discarded — the threshold is inclusive (FR-005)")
                .isEqualTo(0.5);
    }

    // -----------------------------------------------------------------------------------------
    // User Story 1 — a document filter narrows retrieval even when a more relevant passage exists
    // elsewhere (FR-010, Acceptance Scenario 5)
    // -----------------------------------------------------------------------------------------

    @Test
    void documentIdsFilterNarrowsRetrievalToOnlyTheNamedDocumentEvenWhenAnotherIsMoreRelevant() throws Exception {
        UUID allowedDocumentId = insertDocument("filter-it-allowed.txt");
        UUID otherDocumentId = insertDocument("filter-it-other.txt");
        float[] query = unitVector(110);
        insertChunk(allowedDocumentId, 1, "filter-it-allowed.txt", 1, "allowed passage", axisSum(110, 111));
        insertChunk(otherDocumentId, 1, "filter-it-other.txt", 1, "more relevant but filtered out",
                axisSum(110));
        when(embeddingClient.embedQuery(anyString())).thenReturn(query);
        when(chatCompletionClient.complete(any(), any())).thenReturn(
                new ChatCompletionResult("system prompt", "prompt", "A grounded answer scoped to one document."));

        JsonNode filtered = postChat("Question scoped to the filter-it corpus", List.of(allowedDocumentId));

        assertThat(filtered.get("sources")).hasSize(1);
        assertThat(filtered.get("sources").get(0).get("filename").asText()).isEqualTo("filter-it-allowed.txt");
    }

    // -----------------------------------------------------------------------------------------
    // User Story 2 — every candidate below threshold, and a filter matching nothing, both resolve
    // to the fixed not-covered response without ever calling the chat deployment (FR-005/FR-007,
    // spec Edge Cases)
    // -----------------------------------------------------------------------------------------

    @Test
    void everyCandidateBelowThresholdReturnsTheFixedNotCoveredResponseWithoutCallingCompletion() throws Exception {
        UUID documentId = insertDocument("below-threshold-it.txt");
        float[] query = unitVector(130);
        // cos = 1/sqrt(5) ~= 0.447, safely below the 0.5 threshold with no float-precision risk.
        insertChunk(documentId, 1, "below-threshold-it.txt", 1, "too weak to count",
                axisSum(130, 131, 132, 133, 134));
        when(embeddingClient.embedQuery(anyString())).thenReturn(query);

        JsonNode body = postChat("Question with only weak matches", null);

        assertThat(body.get("answer").asText()).isEqualTo(ChatResponse.NOT_COVERED_ANSWER);
        assertThat(body.get("sources")).isEmpty();
        verify(chatCompletionClient, never()).complete(any(), any());
    }

    @Test
    void aDocumentFilterMatchingNoIngestedDocumentReturnsNotCoveredEvenThoughTheSameQuestionSucceedsUnfiltered()
            throws Exception {
        UUID documentId = insertDocument("filter-mismatch-it.txt");
        float[] query = unitVector(140);
        insertChunk(documentId, 1, "filter-mismatch-it.txt", 1, "a genuinely relevant passage", axisSum(140));
        when(embeddingClient.embedQuery(anyString())).thenReturn(query);
        when(chatCompletionClient.complete(any(), any()))
                .thenReturn(new ChatCompletionResult("system prompt", "prompt", "A grounded answer."));

        JsonNode unfiltered = postChat("Question that matches the filter-mismatch corpus", null);
        assertThat(unfiltered.get("sources")).as("the same question succeeds without a filter").isNotEmpty();

        JsonNode filtered =
                postChat("Question that matches the filter-mismatch corpus", List.of(UUID.randomUUID()));
        assertThat(filtered.get("answer").asText())
                .as("a filter naming only a never-ingested id narrows the candidate set to zero rows")
                .isEqualTo(ChatResponse.NOT_COVERED_ANSWER);
        assertThat(filtered.get("sources")).isEmpty();
    }

    // -----------------------------------------------------------------------------------------
    // User Story 3 — a document-store failure is reported as processing_failed, never silently
    // treated as "nothing relevant found" (Acceptance Scenario 2)
    // -----------------------------------------------------------------------------------------

    @Test
    void aDocumentStoreFailureIsReportedAsProcessingFailedNeverSilentlyTreatedAsNotCovered() {
        JdbcTemplate brokenJdbcTemplate = mock(JdbcTemplate.class);
        when(brokenJdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.PreparedStatementSetter.class),
                any(org.springframework.jdbc.core.RowMapper.class)))
                .thenThrow(new DataAccessResourceFailureException("simulated document-store failure"));
        ChatRetrievalRepository brokenRepository = new ChatRetrievalRepository(brokenJdbcTemplate);

        assertThatThrownBy(() -> brokenRepository.findTopSimilarChunks(new float[EMBEDDING_DIMENSIONS], 4, null))
                .isInstanceOf(ChatProcessingException.class)
                .satisfies(e -> assertThat(((ChatProcessingException) e).errorCode()).isEqualTo("processing_failed"));
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private JsonNode postChat(String question, List<UUID> documentIds) throws Exception {
        String body = documentIds == null
                ? "{\"question\": " + JSON.writeValueAsString(question) + "}"
                : "{\"question\": " + JSON.writeValueAsString(question) + ", \"documentIds\": "
                        + JSON.writeValueAsString(documentIds) + "}";
        MvcResult result = mockMvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    /** A unit vector with a single {@code 1.0} at {@code dimension}, zero elsewhere. */
    private static float[] unitVector(int dimension) {
        float[] v = new float[EMBEDDING_DIMENSIONS];
        v[dimension] = 1.0f;
        return v;
    }

    /** A vector with {@code 1.0} at every listed dimension, zero elsewhere. */
    private static float[] axisSum(int... dimensions) {
        float[] v = new float[EMBEDDING_DIMENSIONS];
        for (int dimension : dimensions) {
            v[dimension] = 1.0f;
        }
        return v;
    }

    private static UUID insertDocument(String filename) throws SQLException {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO documents (filename, content_type, content) VALUES (?, ?, ?) RETURNING id")) {
            statement.setString(1, filename);
            statement.setString(2, "text/plain");
            statement.setBytes(3, "placeholder content".getBytes(StandardCharsets.UTF_8));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("id");
            }
        }
    }

    private static void insertChunk(UUID documentId, int chunkId, String sourceFilename, Integer pageNumber,
            String text, float[] embedding) throws SQLException {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement("INSERT INTO chunks "
                        + "(document_id, chunk_id, source_filename, page_number, text, embedding) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, documentId);
            statement.setInt(2, chunkId);
            statement.setString(3, sourceFilename);
            if (pageNumber == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, pageNumber);
            }
            statement.setString(5, text);
            statement.setObject(6, new PGvector(embedding));
            statement.executeUpdate();
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void runScript(String relativePathFromModuleBasedir) throws IOException, SQLException {
        String sql = Files.readString(Path.of(relativePathFromModuleBasedir));
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}

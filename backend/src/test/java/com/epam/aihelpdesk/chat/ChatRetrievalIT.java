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
 * ranks and caps chunks correctly (FR-004), that the 0.35 similarity threshold is genuinely
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
    // User Story 1/2 — real pgvector ranking and the TOP_K cap (FR-004; the inclusive-threshold
    // boundary concern moved to aCandidateBetweenTheOldAndNewThresholdIsNowIncludedInclusively)
    // -----------------------------------------------------------------------------------------

    @Test
    void ranksChunksBySimilarityAndCapsAtTopKWhenAllCandidatesClearTheRelevanceBar() throws Exception {
        UUID documentId = insertDocument("rank-it.txt");
        // Query vector: unit vector along dim 100. Six chunks in the same orthogonal slice, at
        // decreasing similarity: 1.0, 1/sqrt(2)~=0.707, 1/sqrt(3)~=0.577, 1/2=0.5, 1/sqrt(5)~=0.447,
        // 1/sqrt(6)~=0.408 — every one comfortably above the new 0.35 relevance bar, so none is
        // excluded by relevance, only the weakest (6th) by the TOP_K=5 cap.
        float[] query = unitVector(100);
        insertChunk(documentId, 1, "rank-it.txt", 1, "closest passage", axisSum(100));
        insertChunk(documentId, 2, "rank-it.txt", 2, "second passage", axisSum(100, 101));
        insertChunk(documentId, 3, "rank-it.txt", 3, "third passage", axisSum(100, 101, 102));
        insertChunk(documentId, 4, "rank-it.txt", 4, "fourth passage", axisSum(100, 101, 102, 103));
        insertChunk(documentId, 5, "rank-it.txt", 5, "fifth passage", axisSum(100, 101, 102, 103, 104));
        insertChunk(documentId, 6, "rank-it.txt", 6, "sixth passage, excluded by TOP_K cap",
                axisSum(100, 101, 102, 103, 104, 105));
        when(embeddingClient.embedQuery(anyString())).thenReturn(query);
        when(chatCompletionClient.complete(any(), any()))
                .thenReturn(new ChatCompletionResult("system prompt", "prompt", "A grounded answer."));

        JsonNode body = postChat("Question scoped to the rank-it corpus", null);

        JsonNode sources = body.get("sources");
        assertThat(sources).as("TOP_K=5 caps the result even though 6 chunks are above the relevance bar")
                .hasSize(5);
        List<String> pages = List.of(sources.get(0).get("page").asText(), sources.get(1).get("page").asText(),
                sources.get(2).get("page").asText(), sources.get(3).get("page").asText(),
                sources.get(4).get("page").asText());
        assertThat(pages).as("closest-first order, and the 6th (weakest) chunk never reaches the app layer")
                .containsExactly("1", "2", "3", "4", "5");
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
        // cos = 1/sqrt(10) ~= 0.316, safely below the 0.35 threshold with no float-precision risk.
        // (Ten axis terms, not five: five terms gives 1/sqrt(5) ~= 0.447, which was safely below the
        // *old* 0.5 threshold but sits *above* the *new* 0.35 one — this fixture must clear the new
        // bar with room to spare, research Decision 6.)
        insertChunk(documentId, 1, "below-threshold-it.txt", 1, "too weak to count",
                axisSum(130, 131, 132, 133, 134, 135, 136, 137, 138, 139));
        when(embeddingClient.embedQuery(anyString())).thenReturn(query);

        JsonNode body = postChat("Question with only weak matches", null);

        assertThat(body.get("answer").asText()).isEqualTo(ChatResponse.NOT_COVERED_ANSWER);
        assertThat(body.get("sources")).isEmpty();
        verify(chatCompletionClient, never()).complete(any(), any());
    }

    @Test
    void aCandidateBetweenTheOldAndNewThresholdIsNowIncludedInclusively() throws Exception {
        UUID documentId = insertDocument("boundary-it.txt");
        float[] query = unitVector(160);
        // 0.35 isn't 1/sqrt(k) for any small integer k the way 0.5 = 1/sqrt(4) is, and — unlike 0.5 —
        // it isn't a power-of-two fraction either, so no float32/float64 construction can hit it
        // bit-exactly at all. Building the vector at cosine 0.35 exactly was tried and found to be
        // genuinely flaky: pgvector's float32 <=> computation lands a few ulps to either side of the
        // mathematical value, and production's strict (non-epsilon) `distance <= 1 - threshold`
        // comparison sometimes excluded it. Using 0.351 instead gives a safety margin (~1e-3) far
        // larger than that float noise (~1e-7), so the chunk reliably survives the strict filter,
        // while still rounding to the same displayed score (0.35, the threshold itself — production
        // rounds score to two decimals) that the assertion below checks.
        insertChunk(documentId, 1, "boundary-it.txt", 1, "right at the new relevance bar",
                cosineVector(160, 0.351f));
        // Comfortably below 0.35 (reuses the below-threshold test's ten-term construction on a
        // disjoint axis slice): 1/sqrt(10) ~= 0.316.
        insertChunk(documentId, 2, "boundary-it.txt", 2, "still too weak even under the new bar",
                axisSum(161, 162, 163, 164, 165, 166, 167, 168, 169, 170));
        when(embeddingClient.embedQuery(anyString())).thenReturn(query);
        when(chatCompletionClient.complete(any(), any()))
                .thenReturn(new ChatCompletionResult("system prompt", "prompt", "A grounded answer."));

        JsonNode body = postChat("Question scoped to the boundary-it corpus", null);

        JsonNode sources = body.get("sources");
        assertThat(sources).as("only the exactly-0.35 chunk survives — the weaker one stays excluded")
                .hasSize(1);
        assertThat(sources.get(0).get("page").asText()).isEqualTo("1");
        assertThat(sources.get(0).get("score").asDouble())
                .as("the new 0.35 threshold is inclusive, exactly like the old 0.5 one was (FR-005)")
                .isCloseTo(0.35, org.assertj.core.data.Offset.offset(0.0001));
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

    /**
     * A unit-length vector whose cosine similarity to {@code unitVector(dimension)} is exactly
     * {@code cosine}: {@code v[dimension] = cosine}, {@code v[dimension + 1] = sqrt(1 - cosine^2)},
     * zero elsewhere. Used only where the target similarity isn't {@code 1/sqrt(k)} for any small
     * integer {@code k} (e.g. {@code 0.35}), so the bit-exact integer-only {@link #axisSum} technique
     * doesn't apply.
     */
    private static float[] cosineVector(int dimension, float cosine) {
        float[] v = new float[EMBEDDING_DIMENSIONS];
        v[dimension] = cosine;
        v[dimension + 1] = (float) Math.sqrt(1 - cosine * cosine);
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

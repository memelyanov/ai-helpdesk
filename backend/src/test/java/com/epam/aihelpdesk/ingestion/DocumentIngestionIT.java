package com.epam.aihelpdesk.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
 * database, with a <strong>stubbed</strong> {@link EmbeddingClient} (fixed-length fake vectors) —
 * proves the real transaction/atomicity/cascade behavior and actual row shape without needing Azure
 * credentials (constitution Principle II; research Decision 9). Excluded from the default suite by
 * the {@code db} tag; runs only via {@code mvnw test -Pverify-db}.
 *
 * <p>Applies the real {@code db/init/} scripts once for the whole class, the same pattern
 * {@code DocumentsAndChunksSchemaIT} (feature 003) uses. Each test method scopes its own assertions
 * by the {@code documentId}/filename it created, since the table is shared and not truncated between
 * tests.
 */
@Tag("db")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DocumentIngestionIT {

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

    private static final Path SAMPLE_DOCUMENTS = Path.of("../sample-data/documents");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    EmbeddingClient embeddingClient;

    // -----------------------------------------------------------------------------------------
    // User Story 1 — full happy-path pipeline through a real database (FR-004/007/008/009/010)
    // -----------------------------------------------------------------------------------------

    @Test
    void ingestingAPdfWritesTheDocumentAndEveryChunkWithPageNumbersInOneTransaction() throws Exception {
        stubEmbeddingSuccess();
        byte[] content = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("security-policy.pdf"));

        UUID documentId = upload("security-policy.pdf", "application/pdf", content);

        assertThat(documentExists(documentId)).isTrue();
        List<Integer> pageNumbers = jdbcTemplate.queryForList(
                "SELECT page_number FROM chunks WHERE document_id = ? ORDER BY chunk_id", Integer.class, documentId);
        assertThat(pageNumbers).as("every chunk of a PDF carries a real page number").isNotEmpty()
                .allSatisfy(pageNumber -> assertThat(pageNumber).isNotNull().isPositive());
    }

    // -----------------------------------------------------------------------------------------
    // FR-015 — zero-extractable-text success path persists with zero chunks
    // -----------------------------------------------------------------------------------------

    @Test
    void aBlankDocumentIsStoredWithZeroChunksAndIsFullyRetrievable() throws Exception {
        stubEmbeddingSuccess();
        byte[] blankContent = "   \n\t  ".getBytes(StandardCharsets.UTF_8);

        UUID documentId = upload("blank.txt", "text/plain", blankContent);

        assertThat(documentExists(documentId)).isTrue();
        Integer chunkCount = jdbcTemplate.queryForObject("SELECT count(*) FROM chunks WHERE document_id = ?",
                Integer.class, documentId);
        assertThat(chunkCount).isZero();
    }

    // -----------------------------------------------------------------------------------------
    // FR-012 / SC-005 — re-uploading the same file produces two independent documents
    // -----------------------------------------------------------------------------------------

    @Test
    void reuploadingTheSameFileProducesTwoIndependentDocuments() throws Exception {
        stubEmbeddingSuccess();
        byte[] content = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("expense-tool-faq.txt"));

        UUID firstId = upload("expense-tool-faq.txt", "text/plain", content);
        UUID secondId = upload("expense-tool-faq.txt", "text/plain", content);

        assertThat(firstId).as("two distinct document identifiers (SC-005 point 1)").isNotEqualTo(secondId);

        int firstChunkCount = countChunks(firstId);
        int secondChunkCount = countChunks(secondId);
        assertThat(firstChunkCount).as("each identifier's chunks are independently complete (SC-005 point 2)")
                .isGreaterThan(0).isEqualTo(secondChunkCount);

        // No delete endpoint exists in this feature — a direct SQL delete is the only way to
        // exercise "deleting one document leaves the other's row and chunks unaffected" (SC-005
        // point 3), relying on feature 003's ON DELETE CASCADE contract.
        jdbcTemplate.update("DELETE FROM documents WHERE id = ?", firstId);

        assertThat(documentExists(firstId)).isFalse();
        assertThat(countChunks(firstId)).isZero();
        assertThat(documentExists(secondId)).as("the other document's row is completely unaffected").isTrue();
        assertThat(countChunks(secondId)).as("the other document's chunks are completely unaffected")
                .isEqualTo(secondChunkCount);
    }

    // -----------------------------------------------------------------------------------------
    // User Story 3 — no partial results on a mid-pipeline failure, and a clean retry (FR-009, SC-003)
    // -----------------------------------------------------------------------------------------

    @Test
    void aFailedEmbeddingLeavesNoRowsBehindAndAnIdenticalRetrySucceedsCleanly() throws Exception {
        String filename = "retry-after-failure.txt";
        byte[] content = "content that will fail to embed on the first attempt".getBytes(StandardCharsets.UTF_8);

        doThrow(new IngestionProcessingException("processing_failed", "Embedding request failed."))
                .when(embeddingClient).embed(anyList());

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", filename, "text/plain", content)))
                .andExpect(status().isServiceUnavailable());

        assertThat(countDocumentsByFilename(filename)).as("no document row survives the failed attempt").isZero();

        stubEmbeddingSuccess();

        UUID retryDocumentId = upload(filename, "text/plain", content);

        assertThat(documentExists(retryDocumentId)).isTrue();
        assertThat(countChunks(retryDocumentId)).isGreaterThan(0);
        assertThat(countDocumentsByFilename(filename))
                .as("exactly one complete document exists after the retry, not a partial leftover plus a new one")
                .isEqualTo(1);
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private void stubEmbeddingSuccess() {
        doAnswer(invocation -> {
            List<ChunkDraft> drafts = invocation.getArgument(0);
            return drafts.stream().map(draft -> new EmbeddedChunk(draft, new float[1536])).toList();
        }).when(embeddingClient).embed(anyList());
    }

    private UUID upload(String filename, String contentType, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", filename, contentType, content)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("documentId").asText());
    }

    private boolean documentExists(UUID id) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM documents WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private int countChunks(UUID documentId) {
        Integer count =
                jdbcTemplate.queryForObject("SELECT count(*) FROM chunks WHERE document_id = ?", Integer.class,
                        documentId);
        return count == null ? 0 : count;
    }

    private int countDocumentsByFilename(String filename) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM documents WHERE filename = ?",
                Integer.class, filename);
        return count == null ? 0 : count;
    }

    private static void runScript(String relativePathFromModuleBasedir) throws IOException, SQLException {
        String sql = Files.readString(Path.of(relativePathFromModuleBasedir));
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}

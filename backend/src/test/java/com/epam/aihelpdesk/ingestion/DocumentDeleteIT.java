package com.epam.aihelpdesk.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * database for {@code DELETE /documents/{id}} — proves the real single-statement
 * {@code DELETE FROM documents WHERE id = ?} and feature 003's real {@code ON DELETE CASCADE}
 * actually remove {@code chunks} rows, with a <strong>stubbed</strong> {@link EmbeddingClient}
 * (constitution Principle II; research Decision 7). Excluded from the default suite by the
 * {@code db} tag; runs only via {@code mvnw test -Pverify-db}.
 *
 * <p>Reuses {@link DocumentIngestionIT}/{@link DocumentQueryIT}'s exact container/schema bring-up
 * pattern. Kept as a separate sibling file per tasks.md's test-file split decision — scoped to this
 * feature's one {@code DELETE} endpoint.
 */
@Tag("db")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DocumentDeleteIT {

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

    @MockitoBean
    EmbeddingClient embeddingClient;

    // -----------------------------------------------------------------------------------------
    // User Story 1 — deleting a real document removes its chunks via the real cascade (SC-002)
    // -----------------------------------------------------------------------------------------

    @Test
    void deletingAnIngestedDocumentReturnsTwoOhFourAndRemovesItsChunksViaTheRealCascade() throws Exception {
        stubEmbeddingSuccess();
        byte[] pdfContent = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("security-policy.pdf"));
        UUID documentId = upload("delete-it-with-chunks.pdf", "application/pdf", pdfContent);
        assertThat(chunkCount(documentId)).as("the document has chunks before deletion").isGreaterThan(0);

        mockMvc.perform(delete("/documents/{id}", documentId)).andExpect(status().isNoContent());

        assertThat(chunkCount(documentId)).as("every chunk is gone via ON DELETE CASCADE (SC-002)").isZero();
        mockMvc.perform(get("/documents/{id}/content", documentId)).andExpect(status().isNotFound());
    }

    @Test
    void deletingAZeroChunkDocumentSucceedsRegardlessOfChunkCount() throws Exception {
        stubEmbeddingSuccess();
        byte[] blankContent = "   \n\t  ".getBytes(StandardCharsets.UTF_8);
        UUID zeroChunkId = upload("delete-it-zero-chunks.txt", "text/plain", blankContent);
        assertThat(chunkCount(zeroChunkId)).isZero();

        mockMvc.perform(delete("/documents/{id}", zeroChunkId)).andExpect(status().isNoContent());

        mockMvc.perform(get("/documents/{id}/content", zeroChunkId)).andExpect(status().isNotFound());
    }

    @Test
    void deletingOneDocumentDoesNotAffectAnotherDocumentOrItsChunks() throws Exception {
        stubEmbeddingSuccess();
        byte[] pdfContent = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("security-policy.pdf"));
        byte[] txtContent = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("expense-tool-faq.txt"));
        UUID toDeleteId = upload("delete-it-isolation-target.pdf", "application/pdf", pdfContent);
        UUID untouchedId = upload("delete-it-isolation-untouched.txt", "text/plain", txtContent);
        int untouchedChunkCountBefore = chunkCount(untouchedId);

        mockMvc.perform(delete("/documents/{id}", toDeleteId)).andExpect(status().isNoContent());

        mockMvc.perform(get("/documents/{id}/content", untouchedId)).andExpect(status().isOk());
        assertThat(chunkCount(untouchedId)).as("an unrelated document's chunks are untouched (FR-007)")
                .isEqualTo(untouchedChunkCountBefore);
    }

    // -----------------------------------------------------------------------------------------
    // User Story 2 — every way a delete can't succeed, against the real database (FR-008, SC-003)
    // -----------------------------------------------------------------------------------------

    @Test
    void deletingTheSameDocumentTwiceReturnsFourOhFourOnTheSecondCall() throws Exception {
        stubEmbeddingSuccess();
        byte[] txtContent = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("expense-tool-faq.txt"));
        UUID documentId = upload("delete-it-twice.txt", "text/plain", txtContent);

        mockMvc.perform(delete("/documents/{id}", documentId)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/documents/{id}", documentId)).andExpect(status().isNotFound());
    }

    @Test
    void deletingARandomNeverIssuedIdReturnsFourOhFourAgainstTheRealDatabase() throws Exception {
        mockMvc.perform(delete("/documents/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
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

    private static int chunkCount(UUID documentId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
                PreparedStatement statement =
                        connection.prepareStatement("SELECT count(*) FROM chunks WHERE document_id = ?")) {
            statement.setObject(1, documentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
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

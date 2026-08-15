package com.epam.aihelpdesk.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
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
 * database for {@code GET /documents} and {@code GET /documents/{id}/content} — proves the real
 * {@code LEFT JOIN}/{@code GROUP BY} list query and a real byte-for-byte download round-trip
 * against actual inserted rows, with a <strong>stubbed</strong> {@link EmbeddingClient} (constitution
 * Principle II; research Decision 8). Excluded from the default suite by the {@code db} tag; runs
 * only via {@code mvnw test -Pverify-db}.
 *
 * <p>Reuses {@link DocumentIngestionIT}'s exact container/schema bring-up pattern. Kept as a
 * separate sibling file per tasks.md's test-file split decision — scoped to the two read-only
 * {@code GET} endpoints, not {@code POST /documents}'s own pipeline behavior.
 */
@Tag("db")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DocumentQueryIT {

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
    // User Story 1 — GET /documents against a real LEFT JOIN/GROUP BY query (FR-002/003/005)
    // -----------------------------------------------------------------------------------------

    @Test
    void listingReturnsEveryIngestedDocumentIncludingAZeroChunkOneOrderedNewestFirst() throws Exception {
        stubEmbeddingSuccess();
        byte[] pdfContent = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("security-policy.pdf"));
        UUID withChunksId = upload("query-it-with-chunks.pdf", "application/pdf", pdfContent);
        UUID zeroChunkId = upload("query-it-zero-chunks.txt", "text/plain",
                "   \n\t  ".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(get("/documents")).andExpect(status().isOk()).andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());

        JsonNode withChunksEntry = findEntry(body, withChunksId);
        JsonNode zeroChunkEntry = findEntry(body, zeroChunkId);
        assertThat(withChunksEntry).as("the document with chunks appears in the list").isNotNull();
        assertThat(withChunksEntry.get("chunkCount").asInt()).isGreaterThan(0);
        assertThat(zeroChunkEntry).as("the zero-chunk document is not dropped by the LEFT JOIN (FR-003)")
                .isNotNull();
        assertThat(zeroChunkEntry.get("chunkCount").asInt()).isZero();

        // The document uploaded second (zero-chunk) must appear before the one uploaded first
        // (with chunks) — newest-first ordering (FR-005).
        int withChunksIndex = indexOf(body, withChunksId);
        int zeroChunkIndex = indexOf(body, zeroChunkId);
        assertThat(zeroChunkIndex).as("the more recently uploaded document is listed first")
                .isLessThan(withChunksIndex);
    }

    // -----------------------------------------------------------------------------------------
    // User Story 2 — GET /documents/{id}/content byte-for-byte round-trip (FR-008/011, SC-002)
    // -----------------------------------------------------------------------------------------

    @Test
    void downloadingAnIngestedPdfAndTextFileReturnsByteForByteIdenticalContent() throws Exception {
        stubEmbeddingSuccess();
        byte[] pdfContent = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("security-policy.pdf"));
        byte[] txtContent = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("expense-tool-faq.txt"));
        UUID pdfId = upload("roundtrip.pdf", "application/pdf", pdfContent);
        UUID txtId = upload("roundtrip.txt", "text/plain", txtContent);

        MvcResult pdfResult = mockMvc.perform(get("/documents/{id}/content", pdfId))
                .andExpect(status().isOk()).andReturn();
        MvcResult txtResult = mockMvc.perform(get("/documents/{id}/content", txtId))
                .andExpect(status().isOk()).andReturn();

        assertThat(pdfResult.getResponse().getContentAsByteArray()).isEqualTo(pdfContent);
        assertThat(txtResult.getResponse().getContentAsByteArray()).isEqualTo(txtContent);
    }

    @Test
    void aZeroChunkDocumentIsStillFullyDownloadable() throws Exception {
        stubEmbeddingSuccess();
        byte[] blankContent = "   \n\t  ".getBytes(StandardCharsets.UTF_8);
        UUID zeroChunkId = upload("roundtrip-blank.txt", "text/plain", blankContent);

        MvcResult result = mockMvc.perform(get("/documents/{id}/content", zeroChunkId))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(blankContent);
    }

    @Test
    void downloadingARandomNonexistentIdReturnsFourOhFourAgainstTheRealDatabase() throws Exception {
        mockMvc.perform(get("/documents/{id}/content", UUID.randomUUID()))
                .andExpect(status().isNotFound());
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

    private static JsonNode findEntry(JsonNode list, UUID documentId) {
        int index = indexOf(list, documentId);
        return index < 0 ? null : list.get(index);
    }

    private static int indexOf(JsonNode list, UUID documentId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).get("documentId").asText().equals(documentId.toString())) {
                return i;
            }
        }
        return -1;
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

package com.epam.aihelpdesk.ingestion;

import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code MockMvc} contract test for {@code POST /documents} (FR-001–FR-003, FR-005, FR-010, FR-011,
 * FR-017): request/response shape and status codes, against a real {@link TextExtractor} and
 * {@link Chunker} (so validation/parsing behavior is genuine) but a stubbed {@link EmbeddingClient}
 * and {@link DocumentRepository} — no live Azure call or database is ever touched, per constitution
 * Principle II. Runs in the default suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerContractTest {

    private static final Path SAMPLE_DOCUMENTS = Path.of("../sample-data/documents");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EmbeddingClient embeddingClient;

    @MockitoBean
    DocumentRepository documentRepository;

    // -----------------------------------------------------------------------------------------
    // User Story 1 — successful ingestion (FR-001, FR-004, FR-010)
    // -----------------------------------------------------------------------------------------

    @Test
    void uploadingAWellFormedTextFileReturnsCreatedWithDocumentIdAndPositiveChunkCount() throws Exception {
        stubSuccessfulEmbeddingAndWrite();
        byte[] content = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("expense-tool-faq.txt"));

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "expense-tool-faq.txt", "text/plain", content)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.documentId").exists())
                .andExpect(jsonPath("$.chunkCount", greaterThan(0)));
    }

    @Test
    void uploadingAWellFormedPdfFileReturnsCreatedWithDocumentIdAndPositiveChunkCount() throws Exception {
        stubSuccessfulEmbeddingAndWrite();
        byte[] content = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("security-policy.pdf"));

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "security-policy.pdf", "application/pdf", content)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").exists())
                .andExpect(jsonPath("$.chunkCount", greaterThan(0)));
    }

    // -----------------------------------------------------------------------------------------
    // FR-015 — zero-extractable-text success path (the spec's one resolved clarification)
    // -----------------------------------------------------------------------------------------

    @Test
    void aBlankTextFileIsStillAcceptedWithZeroChunksNotAnError() throws Exception {
        stubSuccessfulEmbeddingAndWrite();
        byte[] blankContent = "   \n\t  ".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "blank.txt", "text/plain", blankContent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").exists())
                .andExpect(jsonPath("$.chunkCount").value(0));

        verify(embeddingClient).embed(List.of());
    }

    // -----------------------------------------------------------------------------------------
    // User Story 2 — reject unsupported or invalid uploads cleanly (FR-002/003/005)
    // -----------------------------------------------------------------------------------------

    @Test
    void unsupportedFileTypeIsRejectedWithFourHundredAndNoDocumentStored() throws Exception {
        byte[] pngLikeContent = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "image.png", "image/png", pngLikeContent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_type"));

        verify(documentRepository, never()).save(any(), any(), any(), anyList());
    }

    @Test
    void emptyFileIsRejectedAsInvalidFile() throws Exception {
        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_file"));

        verify(documentRepository, never()).save(any(), any(), any(), anyList());
    }

    @Test
    void oversizedFileIsRejectedAsInvalidFile() throws Exception {
        byte[] oversized = new byte[(int) DocumentController.MAX_FILE_SIZE_BYTES + 1];

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "big.txt", "text/plain", oversized)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_file"));
    }

    @Test
    void aFileThatIsBothOversizedAndAnUnsupportedTypeIsReportedInvalidFileNeverUnsupportedType() throws Exception {
        // PNG magic bytes (an unsupported type) padded past the size limit — the size check MUST
        // win (FR-003, spec Edge Cases: validation order).
        byte[] oversizedPng = new byte[(int) DocumentController.MAX_FILE_SIZE_BYTES + 1];
        oversizedPng[0] = (byte) 0x89;
        oversizedPng[1] = 'P';
        oversizedPng[2] = 'N';
        oversizedPng[3] = 'G';

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "big.png", "image/png", oversizedPng)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_file"));
    }

    @Test
    void aRequestWithNoFilePartIsRejectedAsInvalidFile() throws Exception {
        mockMvc.perform(multipart("/documents"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_file"));
    }

    @Test
    void aRequestWithMoreThanOneFilePartIsRejectedAsInvalidFile() throws Exception {
        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("file", "b.txt", "text/plain", "b".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_file"));
    }

    @Test
    void aFilePartWithNoFilenameIsRejectedAsInvalidFile() throws Exception {
        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "", "text/plain", "content".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_file"));
    }

    @Test
    void aCorruptedPdfIsRejectedAsUnparseable() throws Exception {
        byte[] corruptedPdf = ("%PDF-1.4\nnot a real pdf body\n%%EOF").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "broken.pdf", "application/pdf", corruptedPdf)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unparseable"));
    }

    // -----------------------------------------------------------------------------------------
    // User Story 3 — provider-unconfigured / processing-failed both surface as 503 (FR-009/011)
    // -----------------------------------------------------------------------------------------

    @Test
    void anUnconfiguredEmbeddingProviderReturnsServiceUnavailableWithProviderUnconfigured() throws Exception {
        when(embeddingClient.embed(anyList()))
                .thenThrow(new IngestionProcessingException("provider_unconfigured",
                        "Azure OpenAI embedding configuration is incomplete; no request was attempted."));

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "doc.txt", "text/plain",
                                "some real content".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("provider_unconfigured"));

        verify(documentRepository, never()).save(any(), any(), any(), anyList());
    }

    @Test
    void anEmbeddingFailureReturnsServiceUnavailableWithProcessingFailed() throws Exception {
        when(embeddingClient.embed(anyList()))
                .thenThrow(new IngestionProcessingException("processing_failed", "Embedding request failed."));

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "doc.txt", "text/plain",
                                "some real content".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("processing_failed"));
    }

    @Test
    void aDatabaseWriteFailureReturnsServiceUnavailableWithProcessingFailed() throws Exception {
        stubSuccessfulEmbedding();
        when(documentRepository.save(any(), any(), any(), anyList()))
                .thenThrow(new IngestionProcessingException("processing_failed", "Failed to persist the document."));

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", "doc.txt", "text/plain",
                                "some real content".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("processing_failed"));
    }

    // -----------------------------------------------------------------------------------------
    // FR-017 — filename stored verbatim, never interpreted as a path
    // -----------------------------------------------------------------------------------------

    @Test
    void aPathLikeFilenameIsPassedThroughVerbatimToStorage() throws Exception {
        stubSuccessfulEmbeddingAndWrite();
        String pathLikeFilename = "../../etc/passwd.txt";

        mockMvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", pathLikeFilename, "text/plain",
                                "content".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> filenameCaptor = ArgumentCaptor.forClass(String.class);
        verify(documentRepository).save(filenameCaptor.capture(), any(), any(), anyList());
        org.assertj.core.api.Assertions.assertThat(filenameCaptor.getValue()).isEqualTo(pathLikeFilename);
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private void stubSuccessfulEmbeddingAndWrite() {
        stubSuccessfulEmbedding();
        when(documentRepository.save(any(), any(), any(), anyList())).thenReturn(UUID.randomUUID());
    }

    private void stubSuccessfulEmbedding() {
        when(embeddingClient.embed(anyList())).thenAnswer(invocation -> {
            List<ChunkDraft> drafts = invocation.getArgument(0);
            return drafts.stream().map(draft -> new EmbeddedChunk(draft, new float[1536])).toList();
        });
    }

}

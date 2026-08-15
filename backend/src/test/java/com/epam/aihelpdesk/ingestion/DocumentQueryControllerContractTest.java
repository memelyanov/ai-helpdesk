package com.epam.aihelpdesk.ingestion;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epam.aihelpdesk.ingestion.dto.DocumentSummaryResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code MockMvc} contract test for {@code GET /documents} (FR-001–FR-006, FR-012) and
 * {@code GET /documents/{id}/content} (FR-007–FR-011) — request/response shape and status codes,
 * against a stubbed {@link DocumentQueryRepository} — no live database is ever touched, per
 * constitution Principle II. Runs in the default suite.
 *
 * <p>Kept separate from {@link DocumentControllerContractTest} (feature 004's {@code POST
 * /documents} tests) per tasks.md's test-file split decision — this class is scoped to the two
 * read-only {@code GET} endpoints this feature adds.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentQueryControllerContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DocumentQueryRepository documentQueryRepository;

    // -----------------------------------------------------------------------------------------
    // User Story 1 — GET /documents (FR-001–FR-006, FR-012)
    // -----------------------------------------------------------------------------------------

    @Test
    void listingReturnsEveryDocumentWithAllSummaryFieldsNewestFirstIncludingZeroChunkEntries() throws Exception {
        OffsetDateTime newest = OffsetDateTime.parse("2026-08-15T14:32:07Z");
        OffsetDateTime oldest = OffsetDateTime.parse("2026-08-15T10:00:00Z");
        UUID newestId = UUID.randomUUID();
        UUID oldestId = UUID.randomUUID();
        // Repository is the source of ordering truth (FR-005); the stub returns entries already in
        // the newest-first order a real LEFT JOIN/GROUP BY query would produce.
        when(documentQueryRepository.findAll()).thenReturn(List.of(
                new DocumentSummaryResponse(newestId, "travel-expense-policy.pdf", "application/pdf", newest, 12),
                new DocumentSummaryResponse(oldestId, "blank-upload.txt", "text/plain", oldest, 0)));

        mockMvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].documentId").value(newestId.toString()))
                .andExpect(jsonPath("$[0].filename").value("travel-expense-policy.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[0].uploadedAt").exists())
                .andExpect(jsonPath("$[0].chunkCount").value(12))
                .andExpect(jsonPath("$[1].documentId").value(oldestId.toString()))
                .andExpect(jsonPath("$[1].chunkCount").value(0))
                // FR-012 — never a chunk's own text or embedding in the summary response.
                .andExpect(jsonPath("$[0].text").doesNotExist())
                .andExpect(jsonPath("$[0].embedding").doesNotExist())
                .andExpect(jsonPath("$[1].text").doesNotExist())
                .andExpect(jsonPath("$[1].embedding").doesNotExist());
    }

    @Test
    void listingAnEmptyCorpusReturnsTwoHundredWithAnEmptyArrayNotAnError() throws Exception {
        when(documentQueryRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // -----------------------------------------------------------------------------------------
    // User Story 2 — GET /documents/{id}/content (FR-007–FR-011)
    // -----------------------------------------------------------------------------------------

    @Test
    void downloadingAnExistingDocumentReturnsTheExactBytesWithContentTypeAndDisposition() throws Exception {
        UUID documentId = UUID.randomUUID();
        byte[] content = "the original file bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(documentQueryRepository.findContentById(documentId))
                .thenReturn(java.util.Optional.of(new DocumentContent("expense-tool-faq.txt", "text/plain", content)));

        mockMvc.perform(get("/documents/{id}/content", documentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", "attachment; filename=\"expense-tool-faq.txt\""))
                .andExpect(content().bytes(content));
    }

    @Test
    void downloadingAWellFormedButNonexistentIdReturnsFourOhFourDocumentNotFound() throws Exception {
        UUID nonexistentId = UUID.randomUUID();
        when(documentQueryRepository.findContentById(nonexistentId)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/documents/{id}/content", nonexistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("document_not_found"));
    }

    @Test
    void downloadingWithAMalformedIdReturnsTheIdenticalFourOhFourDocumentNotFound() throws Exception {
        mockMvc.perform(get("/documents/{id}/content", "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("document_not_found"));

        // No repository call for an id that never parses (research Decision 4) — the malformed
        // case is rejected before ever reaching the lookup.
        org.mockito.Mockito.verify(documentQueryRepository, org.mockito.Mockito.never())
                .findContentById(any());
    }

    @Test
    void aFilenameContainingAQuoteIsSafelyEncodedInTheDispositionHeaderNeverCorruptingIt() throws Exception {
        UUID documentId = UUID.randomUUID();
        String trickyFilename = "notes \"quoted\".txt";
        byte[] content = "content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(documentQueryRepository.findContentById(documentId))
                .thenReturn(java.util.Optional.of(new DocumentContent(trickyFilename, "text/plain", content)));

        mockMvc.perform(get("/documents/{id}/content", documentId))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String header = result.getResponse().getHeader("Content-Disposition");
                    org.assertj.core.api.Assertions.assertThat(header).isNotNull();
                    // RFC 6266-safe encoding: the raw quote must never appear unescaped, which
                    // would otherwise terminate the filename parameter early and corrupt the header.
                    org.assertj.core.api.Assertions.assertThat(header).doesNotContain("\"quoted\".txt\"");
                });
    }
}

package com.epam.aihelpdesk.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code MockMvc} contract test for {@code DELETE /documents/{id}} (spec.md User Stories 1 and 2;
 * FR-001–FR-002, FR-005–FR-006, FR-008, FR-010) against a stubbed {@link DocumentRepository} — no
 * live database is ever touched, per constitution Principle II. Runs in the default suite.
 *
 * <p>Kept as a separate sibling of {@link DocumentControllerContractTest} and
 * {@link DocumentQueryControllerContractTest} per tasks.md's test-file split decision — this class
 * is scoped to the one {@code DELETE} endpoint this feature adds.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentDeleteControllerContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DocumentRepository documentRepository;

    // -----------------------------------------------------------------------------------------
    // User Story 1 — successful deletion (FR-001, FR-002, FR-006)
    // -----------------------------------------------------------------------------------------

    @Test
    void deletingAnExistingDocumentReturnsTwoOhFourWithNoBody() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.deleteById(documentId)).thenReturn(true);

        mockMvc.perform(delete("/documents/{id}", documentId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    // -----------------------------------------------------------------------------------------
    // User Story 2 — clear feedback when a deletion can't succeed (FR-005, FR-008, FR-010)
    // -----------------------------------------------------------------------------------------

    @Test
    void deletingWithAMalformedIdReturnsFourOhFourDocumentNotFoundWithoutCallingTheRepository()
            throws Exception {
        mockMvc.perform(delete("/documents/{id}", "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("document_not_found"));

        // The malformed case is rejected before ever reaching the repository (research Decision 3).
        verify(documentRepository, never()).deleteById(any());
    }

    @Test
    void deletingAWellFormedButNonexistentIdReturnsTheIdenticalFourOhFourDocumentNotFound() throws Exception {
        UUID nonexistentId = UUID.randomUUID();
        when(documentRepository.deleteById(nonexistentId)).thenReturn(false);

        mockMvc.perform(delete("/documents/{id}", nonexistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("document_not_found"));
    }

    @Test
    void deletingAnAlreadyDeletedIdReturnsTheIdenticalFourOhFourDocumentNotFound() throws Exception {
        // Zero rows affected is indistinguishable from "already deleted" at the repository level
        // (research Decision 4/FR-008) — the same stubbed outcome as the nonexistent-id case above.
        UUID alreadyDeletedId = UUID.randomUUID();
        when(documentRepository.deleteById(alreadyDeletedId)).thenReturn(false);

        mockMvc.perform(delete("/documents/{id}", alreadyDeletedId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("document_not_found"));
    }

    @Test
    void anUnexpectedFailureWhileDeletingAnExistingDocumentReturnsFiveOhThreeDeletionFailed() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.deleteById(documentId))
                .thenThrow(new DocumentDeletionException("Failed to delete the document."));

        mockMvc.perform(delete("/documents/{id}", documentId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("deletion_failed"));
    }
}

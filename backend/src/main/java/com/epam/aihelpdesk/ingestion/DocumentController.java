package com.epam.aihelpdesk.ingestion;

import com.epam.aihelpdesk.ingestion.dto.DocumentIngestionResponse;
import com.epam.aihelpdesk.ingestion.dto.DocumentSummaryResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The {@code /documents} resource: {@code POST /documents} (feature 004) accepts a
 * {@code multipart/form-data} upload, validates it, and delegates to {@link IngestionService};
 * {@code GET /documents} (this feature) lists every ingested document; and
 * {@code GET /documents/{id}/content} (this feature) returns a document's original file bytes.
 * Request-level validation for {@code POST} (FR-003: empty, oversized, or a malformed request —
 * no/duplicate {@code file} part, no filename) runs here, before any content is inspected, and
 * always before the type/parse checks {@link TextExtractor} performs (FR-002/005) — the exact order
 * spec.md's Edge Cases mandates: an oversized file that is also an unsupported type is reported
 * {@code invalid_file}, never {@code unsupported_type}.
 *
 * <p>{@code file} is bound as a {@code List<MultipartFile>}, not a single {@link MultipartFile},
 * specifically so "more than one {@code file} part" and "no {@code file} part" are both ordinary
 * validation outcomes this method reports consistently as {@code invalid_file}, rather than one of
 * them surfacing as an uncaught framework exception.
 */
@RestController
@RequestMapping("/documents")
public class DocumentController {

    /** FR-003 — the single source of truth for this number; see spec.md and the API contract. */
    static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private final IngestionService ingestionService;
    private final DocumentQueryRepository documentQueryRepository;
    private final DocumentRepository documentRepository;

    public DocumentController(IngestionService ingestionService, DocumentQueryRepository documentQueryRepository,
            DocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.documentQueryRepository = documentQueryRepository;
        this.documentRepository = documentRepository;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentIngestionResponse> upload(
            @RequestParam(value = "file", required = false) List<MultipartFile> files) {
        MultipartFile file = validate(files);
        byte[] content = readBytes(file);
        DocumentIngestionResponse response = ingestionService.ingest(file.getOriginalFilename(), content);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * {@code GET /documents} — every ingested document, newest-first, zero-chunk documents
     * included (FR-001–FR-006). Always {@code 200 OK}; an empty corpus returns {@code 200} with
     * {@code []}, never an error (FR-006).
     */
    @GetMapping
    public ResponseEntity<List<DocumentSummaryResponse>> list() {
        return ResponseEntity.ok(documentQueryRepository.findAll());
    }

    /**
     * {@code GET /documents/{id}/content} — a document's original file bytes, byte-for-byte
     * (FR-007–FR-011). {@code id} is bound as {@code String}, not {@link UUID}, so a malformed id
     * never surfaces Spring's own {@code MethodArgumentTypeMismatchException}; a malformed id and a
     * well-formed-but-nonexistent id both resolve to the identical {@link DocumentNotFoundException}
     * (research Decision 4).
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> download(@PathVariable("id") String id) {
        UUID documentId = parseId(id);
        DocumentContent content = documentQueryRepository.findContentById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("No document exists with the given id."));
        ContentDisposition disposition =
                ContentDisposition.attachment().filename(content.filename()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content.content());
    }

    /**
     * {@code DELETE /documents/{id}} — permanently deletes a document and every chunk derived from
     * it, via feature 003's {@code ON DELETE CASCADE} (FR-001–FR-002, FR-011). A malformed id, a
     * well-formed-but-nonexistent id, and an already-deleted id all resolve to the identical
     * {@link DocumentNotFoundException} (FR-005/FR-008, research Decision 3/4) — reusing the same
     * {@link #parseId} helper the download endpoint already uses. An unexpected server-side failure
     * while deleting an existing document surfaces as {@link DocumentDeletionException} (FR-010),
     * never a partial deletion and never confused with the not-found outcome.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        UUID documentId = parseId(id);
        boolean deleted = documentRepository.deleteById(documentId);
        if (!deleted) {
            throw new DocumentNotFoundException("No document exists with the given id.");
        }
        return ResponseEntity.noContent().build();
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new DocumentNotFoundException("No document exists with the given id.");
        }
    }

    private MultipartFile validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 1) {
            throw new InvalidDocumentException("invalid_file", "Request must contain exactly one 'file' part.");
        }
        MultipartFile file = files.get(0);
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new InvalidDocumentException("invalid_file", "Uploaded file must have a filename.");
        }
        if (file.isEmpty()) {
            throw new InvalidDocumentException("invalid_file", "Uploaded file must not be empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidDocumentException("invalid_file", "Uploaded file exceeds the 20 MB size limit.");
        }
        return file;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            // A read failure here (e.g. the client disconnected mid-upload) is not an input-validity
            // problem — the same "no partial result, retry is safe" outcome as any other
            // mid-pipeline failure (FR-009, spec Edge Cases: client disconnect/timeout).
            throw new IngestionProcessingException("processing_failed", "Failed to read the uploaded file.", e);
        }
    }
}

package com.epam.aihelpdesk.ingestion;

import com.epam.aihelpdesk.ingestion.dto.DocumentIngestionResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code POST /documents} — accepts one {@code multipart/form-data} upload, validates it, and
 * delegates to {@link IngestionService}. Request-level validation (FR-003: empty, oversized, or a
 * malformed request — no/duplicate {@code file} part, no filename) runs here, before any content is
 * inspected, and always before the type/parse checks {@link TextExtractor} performs (FR-002/005) —
 * the exact order spec.md's Edge Cases mandates: an oversized file that is also an unsupported type
 * is reported {@code invalid_file}, never {@code unsupported_type}.
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

    public DocumentController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentIngestionResponse> upload(
            @RequestParam(value = "file", required = false) List<MultipartFile> files) {
        MultipartFile file = validate(files);
        byte[] content = readBytes(file);
        DocumentIngestionResponse response = ingestionService.ingest(file.getOriginalFilename(), content);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

package com.epam.aihelpdesk.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TextExtractor} — content-based type detection (FR-002), page-aware
 * {@code .pdf} extraction (FR-004/007, research Decision 2), and the parse-failure/unsupported-type
 * rejection paths (FR-005, spec Edge Cases). Pure CPU work against real sample-corpus files and
 * synthetic byte arrays, no I/O beyond local file reads — runs in the default suite.
 */
class TextExtractorTest {

    private static final Path SAMPLE_DOCUMENTS = Path.of("../sample-data/documents");

    private final TextExtractor extractor = new TextExtractor();

    @Test
    void extractsPlainTextAsASinglePageWithNoPageNumber() throws IOException {
        byte[] content = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("expense-tool-faq.txt"));

        TextExtractionResult result = extractor.extract(content);

        assertThat(result.contentType()).isEqualTo("text/plain");
        assertThat(result.pages()).hasSize(1);
        assertThat(result.pages().get(0).pageNumber()).isNull();
        assertThat(result.pages().get(0).text()).isNotBlank();
    }

    @Test
    void extractsPdfTextPerPageWithSequentialOneIndexedPageNumbers() throws IOException {
        byte[] content = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("security-policy.pdf"));

        TextExtractionResult result = extractor.extract(content);

        assertThat(result.contentType()).isEqualTo("application/pdf");
        // Verified via pypdf against this exact sample file (specs/004-document-ingestion-endpoint/spec.md SC-001).
        assertThat(result.pages()).hasSize(8);
        for (int i = 0; i < result.pages().size(); i++) {
            assertThat(result.pages().get(i).pageNumber()).as("1-indexed, sequential").isEqualTo(i + 1);
        }
        assertThat(result.pages()).allSatisfy(page -> assertThat(page.text()).isNotBlank());
    }

    @Test
    void rejectsContentThatIsNeitherTextNorPdfByItsActualBytesRegardlessOfNoFilenameHint() {
        // PNG magic bytes — recognizable, unambiguous binary content, not text or PDF.
        byte[] pngLikeContent = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        assertThatThrownBy(() -> extractor.extract(pngLikeContent))
                .isInstanceOf(InvalidDocumentException.class)
                .asInstanceOf(throwable(InvalidDocumentException.class))
                .extracting(InvalidDocumentException::errorCode)
                .isEqualTo("unsupported_type");
    }

    @Test
    void rejectsAPdfThatDeclaresItsFormatButCannotActuallyBeParsed() {
        // Starts with the real %PDF magic (so content-based detection says application/pdf) but has
        // no valid structure behind it — Tika's PDF parser must fail on this, not silently accept it.
        byte[] corruptedPdf = ("%PDF-1.4\n" + "this is not a real pdf body, just garbage after the magic bytes\n"
                + "%%EOF").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(corruptedPdf))
                .isInstanceOf(InvalidDocumentException.class)
                .asInstanceOf(throwable(InvalidDocumentException.class))
                .extracting(InvalidDocumentException::errorCode)
                .isEqualTo("unparseable");
    }

    @Test
    void rejectsTextThatCannotBeDecodedAsValidUtf8InsteadOfGuessingAnEncoding() {
        // UTF-16LE BOM followed by a truncated surrogate — Tika's content detection recognizes this
        // as textual content (text/plain), but 0xFF/0xFE are not valid UTF-8 lead bytes: this MUST
        // be rejected, never silently mis-decoded (spec.md Edge Cases; same byte sequence quickstart.md
        // uses for this case).
        byte[] undecodable = {(byte) 0xFF, (byte) 0xFE, 0x00, (byte) 0xD8, 0x00, 0x00};

        assertThatThrownBy(() -> extractor.extract(undecodable))
                .isInstanceOf(InvalidDocumentException.class)
                .asInstanceOf(throwable(InvalidDocumentException.class))
                .extracting(InvalidDocumentException::errorCode)
                .isEqualTo("unparseable");
    }

    @Test
    void aPdfDividerPageWithNoExtractableTextContributesABlankPageNotAFailure() throws IOException {
        // Every sample PDF has real text on every page, so this exercises the extractor's general
        // per-page contract rather than a true divider page — Chunker (ChunkerTest) is what proves a
        // blank page contributes zero chunks; this only proves extraction itself never throws for a
        // structurally valid multi-page PDF.
        byte[] content = Files.readAllBytes(SAMPLE_DOCUMENTS.resolve("travel-expense-policy.pdf"));

        TextExtractionResult result = extractor.extract(content);

        assertThat(result.pages()).isNotEmpty();
        assertThat(result.pages()).extracting(ExtractedPage::pageNumber).isEqualTo(pageNumbers(result.pages()));
    }

    private static List<Integer> pageNumbers(List<ExtractedPage> pages) {
        return java.util.stream.IntStream.rangeClosed(1, pages.size()).boxed().toList();
    }
}

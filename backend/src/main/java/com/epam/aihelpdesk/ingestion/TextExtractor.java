package com.epam.aihelpdesk.ingestion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Extracts readable text from an accepted upload, page-aware for {@code .pdf} (FR-004/007,
 * research Decision 2).
 *
 * <p>Content type is detected from the bytes alone — {@link Detector#detect} is called with no
 * filename/content-type hint in {@link Metadata}, so a caller cannot influence the outcome by
 * naming or mislabeling a file (FR-002). Anything other than {@code text/plain} or
 * {@code application/pdf} is rejected before any parsing is attempted.
 *
 * <p>{@code .pdf} content is parsed via Tika's {@link AutoDetectParser} with a page-boundary-aware
 * SAX handler (research Decision 2); a parse failure (corrupted PDF) is reported as
 * {@code unparseable} (FR-005). {@code .txt} content is decoded directly with a <strong>strict</strong>
 * UTF-8 {@link CharsetDecoder} rather than through Tika's own text parser: Tika's charset detection
 * is deliberately lenient (it guesses a best-fit encoding for whatever bytes it is given), which
 * conflicts with the spec's explicit "the system MUST NOT guess or transliterate an encoding" edge
 * case (spec.md Edge Cases) — undecodable bytes must fail outright, not be silently mis-decoded.
 * This is an implementation refinement of research Decision 1: Tika is still used for MIME
 * detection and for every {@code .pdf} parse; only the {@code .txt} text-decoding step bypasses
 * Tika's own parser.
 */
@Component
public class TextExtractor {

    private static final String TEXT_PLAIN = "text/plain";
    private static final String APPLICATION_PDF = "application/pdf";

    private final Detector detector = new DefaultDetector();
    private final Parser parser = new AutoDetectParser();

    public TextExtractionResult extract(byte[] content) {
        String contentType = detectType(content);
        if (TEXT_PLAIN.equals(contentType)) {
            String text = decodeStrictUtf8(content);
            return new TextExtractionResult(TEXT_PLAIN, List.of(new ExtractedPage(null, text)));
        }
        if (APPLICATION_PDF.equals(contentType)) {
            return new TextExtractionResult(APPLICATION_PDF, extractPdfPages(content));
        }
        throw new InvalidDocumentException("unsupported_type",
                "Unsupported file type: content is neither text/plain nor application/pdf.");
    }

    private String detectType(byte[] content) {
        try (TikaInputStream stream = TikaInputStream.get(content)) {
            // No RESOURCE_NAME_KEY / CONTENT_TYPE hint set on this Metadata — detection is driven
            // purely by the byte content, never by a filename extension or caller-declared type.
            MediaType mediaType = detector.detect(stream, new Metadata());
            return mediaType.getBaseType().toString();
        } catch (IOException e) {
            throw new InvalidDocumentException("unparseable", "Unable to read the uploaded file's content.", e);
        }
    }

    private String decodeStrictUtf8(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            throw new InvalidDocumentException("unparseable",
                    "Text file could not be decoded as valid UTF-8.", e);
        }
    }

    private List<ExtractedPage> extractPdfPages(byte[] content) {
        PageCollectingHandler handler = new PageCollectingHandler();
        try (InputStream stream = new ByteArrayInputStream(content)) {
            parser.parse(stream, handler, new Metadata(), new ParseContext());
        } catch (IOException | SAXException | TikaException e) {
            throw new InvalidDocumentException("unparseable", "Unable to parse PDF content.", e);
        }
        return handler.pages();
    }

    /**
     * Collects text per page by watching for Tika's PDF parser's {@code <div class="page">}
     * boundary markers in its XHTML SAX output (research Decision 2). Tracks a page-local element
     * depth so a {@code </div>} that closes a nested element inside the page (rare, but not
     * guaranteed absent) does not prematurely end the page.
     */
    private static final class PageCollectingHandler extends DefaultHandler {

        private final List<ExtractedPage> pages = new ArrayList<>();
        private StringBuilder currentPageText;
        private int openDivsInCurrentPage;
        private int pageNumber;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (!"div".equalsIgnoreCase(localName)) {
                return;
            }
            if (currentPageText == null) {
                if ("page".equals(attributes.getValue("class"))) {
                    pageNumber++;
                    currentPageText = new StringBuilder();
                    openDivsInCurrentPage = 1;
                }
            } else {
                openDivsInCurrentPage++;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (!"div".equalsIgnoreCase(localName) || currentPageText == null) {
                return;
            }
            openDivsInCurrentPage--;
            if (openDivsInCurrentPage == 0) {
                pages.add(new ExtractedPage(pageNumber, currentPageText.toString().trim()));
                currentPageText = null;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (currentPageText != null) {
                currentPageText.append(ch, start, length);
            }
        }

        List<ExtractedPage> pages() {
            return pages;
        }
    }
}

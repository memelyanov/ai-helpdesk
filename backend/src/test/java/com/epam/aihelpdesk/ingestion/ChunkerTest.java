package com.epam.aihelpdesk.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Chunker} — token-window sizing, 10–15% overlap, the final/sole-chunk
 * exception (FR-006), and the divider-page edge case, all pure (no I/O) so these run in the default
 * suite.
 */
class ChunkerTest {

    private static final Encoding ENCODING =
            Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private final Chunker chunker = new Chunker();

    @Test
    void longPageProducesFullSizeInteriorWindowsWithOverlapAndAShortFinalWindow() {
        String longText = repeatedWords(3000);
        int totalTokens = ENCODING.countTokens(longText);
        assertThat(totalTokens).as("sanity check: text is long enough to need multiple windows").isGreaterThan(1600);

        List<ChunkDraft> chunks = chunker.chunk(List.of(new ExtractedPage(3, longText)));

        assertThat(chunks).hasSizeGreaterThan(1);
        for (int i = 0; i < chunks.size(); i++) {
            ChunkDraft chunk = chunks.get(i);
            assertThat(chunk.chunkId()).as("chunk ids are sequential starting at 0").isEqualTo(i);
            assertThat(chunk.pageNumber()).as("page number carried from the source page").isEqualTo(3);
            int tokenCount = ENCODING.countTokens(chunk.text());
            if (i < chunks.size() - 1) {
                assertThat(tokenCount).as("every interior chunk is exactly the 500-token target").isEqualTo(500);
            } else {
                assertThat(tokenCount).as("the final chunk may be shorter, but never exceeds the target")
                        .isLessThanOrEqualTo(500);
            }
        }

        // 10-15% overlap (research Decision 3: 63 of 500 tokens = 12.6%): the last 63 token ids of
        // each chunk equal the first 63 token ids of the next chunk, by construction.
        for (int i = 0; i < chunks.size() - 1; i++) {
            IntArrayList currentTokens = ENCODING.encode(chunks.get(i).text());
            IntArrayList nextTokens = ENCODING.encode(chunks.get(i + 1).text());
            List<Integer> tailOfCurrent = lastN(currentTokens, 63);
            List<Integer> headOfNext = firstN(nextTokens, 63);
            assertThat(tailOfCurrent).as("chunk %d's trailing 63 tokens overlap chunk %d's leading 63 tokens", i,
                    i + 1).isEqualTo(headOfNext);
        }
    }

    @Test
    void shortPageProducesExactlyOneChunkEvenUnderTheTargetTokenCount() {
        String shortText = "This is a short test document with only a handful of words in it.";
        assertThat(ENCODING.countTokens(shortText)).as("sanity check: shorter than the 500-token target").isLessThan(500);

        List<ChunkDraft> chunks = chunker.chunk(List.of(new ExtractedPage(1, shortText)));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chunkId()).isZero();
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(0).text()).isEqualTo(shortText);
    }

    @Test
    void blankPageContributesNoChunksAndDoesNotRenumberSubsequentPages() {
        List<ExtractedPage> pages = List.of(
                new ExtractedPage(1, "first page text"),
                new ExtractedPage(2, "   "),
                new ExtractedPage(3, "third page text"));

        List<ChunkDraft> chunks = chunker.chunk(pages);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).pageNumber()).as("first page's chunk").isEqualTo(1);
        assertThat(chunks.get(0).chunkId()).isZero();
        assertThat(chunks.get(1).pageNumber())
                .as("third page's chunk keeps its true page number, never renumbered to 2").isEqualTo(3);
        assertThat(chunks.get(1).chunkId()).as("chunk ids remain sequential across the skipped page").isEqualTo(1);
    }

    @Test
    void textWithNoPageStructureCarriesNullPageNumber() {
        List<ChunkDraft> chunks = chunker.chunk(List.of(new ExtractedPage(null, "plain text file content")));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).pageNumber()).isNull();
    }

    @Test
    void emptyPageListProducesNoChunks() {
        assertThat(chunker.chunk(List.of())).isEmpty();
    }

    private static String repeatedWords(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append("word").append(i % 500).append(' ');
        }
        return builder.toString();
    }

    private static List<Integer> lastN(IntArrayList tokens, int n) {
        return java.util.stream.IntStream.range(tokens.size() - n, tokens.size())
                .mapToObj(tokens::get)
                .toList();
    }

    private static List<Integer> firstN(IntArrayList tokens, int n) {
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(tokens::get)
                .toList();
    }
}

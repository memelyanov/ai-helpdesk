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
 * exception (FR-006), and the divider-page edge case, plus (feature 012) cross-page excerpts in
 * both directions, the anchor-page rule for boundary and short-flanked-page chunks, and the
 * unpaged-text exclusion — all pure (no I/O) so these run in the default suite.
 */
class ChunkerTest {

    private static final Encoding ENCODING =
            Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    // Mirrors Chunker.OVERLAP_TOKENS (research Decision 3, feature 011) — cross-page excerpts
    // (feature 012) reuse this exact amount rather than a separately tuned value (spec.md FR-003).
    private static final int OVERLAP_TOKENS = 63;

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

    // --- Cross-page chunk overlap (feature 012) ---------------------------------------------

    @Test
    void firstChunkOfANewPageCarriesTrailingExcerptFromThePreviousPage() {
        String page1Text = repeatedWords(100);
        String page2Text = "This is page two's own short text, unrelated in length to page one.";
        assertThat(ENCODING.countTokens(page1Text)).as("sanity: long enough to have a real 63-token tail")
                .isGreaterThan(OVERLAP_TOKENS);
        assertThat(ENCODING.countTokens(page2Text)).as("sanity: short enough for page two to be a single chunk")
                .isLessThan(500);

        List<ChunkDraft> chunks =
                chunker.chunk(List.of(new ExtractedPage(1, page1Text), new ExtractedPage(2, page2Text)));

        assertThat(chunks).hasSize(2);
        IntArrayList page1Tokens = ENCODING.encode(page1Text);
        IntArrayList page2Tokens = ENCODING.encode(page2Text);

        // Page 1's only chunk is simultaneously its first (no preceding page: unaffected) and its
        // last (page 2 follows: gains page 2's leading OVERLAP_TOKENS tokens, FR-001) — this fixture
        // exercises both directions at once, exactly like T011's collision case, just without the
        // FR-010 short-native-text angle.
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(0).text())
                .as("page 1's own (and only) chunk also picks up page 2's leading excerpt (FR-001), since "
                        + "it is that page's last window too")
                .isEqualTo(ENCODING.decode(concatTokens(page1Tokens, headTokens(page2Tokens, OVERLAP_TOKENS))));

        String expectedPage2Text =
                ENCODING.decode(concatTokens(tailTokens(page1Tokens, OVERLAP_TOKENS), page2Tokens));
        assertThat(chunks.get(1).pageNumber()).as("still page 2's own anchor page, not page 1 (FR-004)")
                .isEqualTo(2);
        assertThat(chunks.get(1).text())
                .as("page 2's first chunk = page 1's trailing 63 tokens + page 2's own text (FR-002/FR-003)")
                .isEqualTo(expectedPage2Text);
    }

    @Test
    void firstPageOfDocumentGetsNoTrailingExcerptEvenWithLeadingBlankPages() {
        String pageText = "Page with no real predecessor in the document.";

        List<ChunkDraft> chunksNoBlank = chunker.chunk(List.of(new ExtractedPage(1, pageText)));
        assertThat(chunksNoBlank).hasSize(1);
        assertThat(chunksNoBlank.get(0).text()).isEqualTo(pageText);

        // One or more blank pages precede the document's actual first page-with-text.
        List<ExtractedPage> withLeadingBlanks = List.of(
                new ExtractedPage(1, "   "),
                new ExtractedPage(2, ""),
                new ExtractedPage(3, pageText));
        List<ChunkDraft> chunksWithLeadingBlanks = chunker.chunk(withLeadingBlanks);

        assertThat(chunksWithLeadingBlanks).hasSize(1);
        assertThat(chunksWithLeadingBlanks.get(0).pageNumber()).isEqualTo(3);
        assertThat(chunksWithLeadingBlanks.get(0).text())
                .as("leading blank pages are not mistaken for a phantom preceding page with real text (FR-006)")
                .isEqualTo(pageText);
    }

    @Test
    void trailingExcerptIsCappedByHowMuchThePrecedingPageActuallyHas() {
        String shortPrecedingText = "Only a few words here.";
        String pageText = "Second page own content that follows the very short first page.";
        int precedingTokenCount = ENCODING.countTokens(shortPrecedingText);
        assertThat(precedingTokenCount).as("sanity: shorter than the normal 63-token borrow amount")
                .isLessThan(OVERLAP_TOKENS);

        List<ChunkDraft> chunks =
                chunker.chunk(List.of(new ExtractedPage(1, shortPrecedingText), new ExtractedPage(2, pageText)));

        assertThat(chunks).hasSize(2);
        String expectedText = ENCODING.decode(concatTokens(
                tailTokens(ENCODING.encode(shortPrecedingText), OVERLAP_TOKENS), ENCODING.encode(pageText)));
        assertThat(chunks.get(1).text())
                .as("borrows only the %d tokens that actually exist, never padded or fabricated", precedingTokenCount)
                .isEqualTo(expectedText);
    }

    @Test
    void trailingExcerptSkipsPastAnInterveningBlankPageToTheNearestPageWithText() {
        String page1Text = repeatedWords(100);
        String page3Text = "Page three text that should still remember page one's tail, skipping page two.";
        List<ExtractedPage> pages = List.of(
                new ExtractedPage(1, page1Text),
                new ExtractedPage(2, "   "),
                new ExtractedPage(3, page3Text));

        List<ChunkDraft> chunks = chunker.chunk(pages);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1).pageNumber()).isEqualTo(3);
        String expectedText = ENCODING.decode(
                concatTokens(tailTokens(ENCODING.encode(page1Text), OVERLAP_TOKENS), ENCODING.encode(page3Text)));
        assertThat(chunks.get(1).text())
                .as("borrows from page 1 (nearest non-blank predecessor), skipping blank page 2 (FR-005)")
                .isEqualTo(expectedText);
    }

    @Test
    void lastChunkOfAPageCarriesLeadInExcerptFromTheFollowingPage() {
        String page1Text = "This is page one's own short text, unrelated in length to page two.";
        String page2Text = repeatedWords(100);
        assertThat(ENCODING.countTokens(page1Text)).as("sanity: short enough for page one to be a single chunk")
                .isLessThan(500);
        assertThat(ENCODING.countTokens(page2Text)).as("sanity: long enough to have a real 63-token head")
                .isGreaterThan(OVERLAP_TOKENS);

        List<ChunkDraft> chunks =
                chunker.chunk(List.of(new ExtractedPage(1, page1Text), new ExtractedPage(2, page2Text)));

        assertThat(chunks).hasSize(2);
        String expectedPage1Text = ENCODING.decode(
                concatTokens(ENCODING.encode(page1Text), headTokens(ENCODING.encode(page2Text), OVERLAP_TOKENS)));
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(0).text())
                .as("page 1's last (only) chunk = its own text + page 2's leading 63 tokens (FR-001/FR-003)")
                .isEqualTo(expectedPage1Text);
        assertThat(chunks.get(1).pageNumber()).as("still page 2's own anchor page, not page 1 (FR-004)")
                .isEqualTo(2);
    }

    @Test
    void lastPageOfDocumentGetsNoLeadInExcerptEvenWithTrailingBlankPages() {
        String pageText = "Page with no real successor in the document.";

        List<ChunkDraft> chunksNoBlank = chunker.chunk(List.of(new ExtractedPage(1, pageText)));
        assertThat(chunksNoBlank).hasSize(1);
        assertThat(chunksNoBlank.get(0).text()).isEqualTo(pageText);

        // One or more blank pages follow the document's actual last page-with-text.
        List<ExtractedPage> withTrailingBlanks = List.of(
                new ExtractedPage(1, pageText),
                new ExtractedPage(2, ""),
                new ExtractedPage(3, "   "));
        List<ChunkDraft> chunksWithTrailingBlanks = chunker.chunk(withTrailingBlanks);

        assertThat(chunksWithTrailingBlanks).hasSize(1);
        assertThat(chunksWithTrailingBlanks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunksWithTrailingBlanks.get(0).text())
                .as("trailing blank pages are not mistaken for a phantom following page with real text (FR-006)")
                .isEqualTo(pageText);
    }

    @Test
    void leadInExcerptIsCappedByHowMuchTheFollowingPageActuallyHas() {
        String pageText = "First page own content that precedes the very short second page.";
        String shortFollowingText = "Only a few words here too.";
        int followingTokenCount = ENCODING.countTokens(shortFollowingText);
        assertThat(followingTokenCount).as("sanity: shorter than the normal 63-token borrow amount")
                .isLessThan(OVERLAP_TOKENS);

        List<ChunkDraft> chunks =
                chunker.chunk(List.of(new ExtractedPage(1, pageText), new ExtractedPage(2, shortFollowingText)));

        assertThat(chunks).hasSize(2);
        String expectedText = ENCODING.decode(concatTokens(
                ENCODING.encode(pageText), headTokens(ENCODING.encode(shortFollowingText), OVERLAP_TOKENS)));
        assertThat(chunks.get(0).text())
                .as("borrows only the %d tokens that actually exist, never padded or fabricated",
                        followingTokenCount)
                .isEqualTo(expectedText);
    }

    @Test
    void leadInExcerptSkipsPastAnInterveningBlankPageToTheNearestPageWithText() {
        String page1Text = "Page one text that should still see page three's head, skipping page two.";
        String page3Text = repeatedWords(100);
        List<ExtractedPage> pages = List.of(
                new ExtractedPage(1, page1Text),
                new ExtractedPage(2, "   "),
                new ExtractedPage(3, page3Text));

        List<ChunkDraft> chunks = chunker.chunk(pages);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        String expectedText = ENCODING.decode(
                concatTokens(ENCODING.encode(page1Text), headTokens(ENCODING.encode(page3Text), OVERLAP_TOKENS)));
        assertThat(chunks.get(0).text())
                .as("borrows from page 3 (nearest non-blank successor), skipping blank page 2 (FR-005)")
                .isEqualTo(expectedText);
    }

    @Test
    void aShortFlankedPageReceivesBothExcerptsAtOnceAndKeepsItsOwnAnchorPage() {
        String beforeText = repeatedWords(100);
        String flankedText = "Tiny middle page.";
        String afterText = repeatedWords(100);
        List<ExtractedPage> pages = List.of(
                new ExtractedPage(1, beforeText),
                new ExtractedPage(2, flankedText),
                new ExtractedPage(3, afterText));

        List<ChunkDraft> chunks = chunker.chunk(pages);

        assertThat(chunks).hasSize(3);
        ChunkDraft flankedChunk = chunks.get(1);
        assertThat(flankedChunk.pageNumber())
                .as("anchor page stays page 2 regardless of borrowed-vs-native ratio (FR-010, SC-005)")
                .isEqualTo(2);

        String expectedText = ENCODING.decode(concatTokens(
                concatTokens(tailTokens(ENCODING.encode(beforeText), OVERLAP_TOKENS), ENCODING.encode(flankedText)),
                headTokens(ENCODING.encode(afterText), OVERLAP_TOKENS)));
        assertThat(flankedChunk.text())
                .as("carries both the trailing excerpt from page 1 and the lead-in excerpt from page 3 at once")
                .isEqualTo(expectedText);

        int nativeTokenCount = ENCODING.countTokens(flankedText);
        int borrowedTokenCount = 2 * OVERLAP_TOKENS;
        assertThat(borrowedTokenCount)
                .as("sanity: this fixture genuinely exercises the collision case — borrowed text outweighs "
                        + "native text")
                .isGreaterThan(nativeTokenCount);
    }

    @Test
    void everyFlankedShortPageInADocumentIndependentlyKeepsItsOwnAnchorPage() {
        List<ExtractedPage> pages = List.of(
                new ExtractedPage(1, "Page one short text."),
                new ExtractedPage(2, "Page two short text."),
                new ExtractedPage(3, "Page three short text."),
                new ExtractedPage(4, "Page four short text."));

        List<ChunkDraft> chunks = chunker.chunk(pages);

        assertThat(chunks).hasSize(4);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).pageNumber())
                    .as("page %d's chunk reports its own page number, independent of how many neighboring "
                            + "pages are also short (FR-010)", i + 1)
                    .isEqualTo(i + 1);
        }
    }

    // --- Anchor-page / stability verification (feature 012, User Story 2 — no new production
    // code; these confirm FR-004/FR-007/FR-008/FR-009 already hold across US1's and US3's boundary
    // chunks, per research.md Decision 4) ---------------------------------------------------------

    @Test
    void chunkIdStaysSequentialAndUniqueAcrossBoundaryChunksInBothDirections() {
        List<ExtractedPage> pages = List.of(
                new ExtractedPage(1, repeatedWords(100)),
                new ExtractedPage(2, "Short middle page text."),
                new ExtractedPage(3, repeatedWords(100)));

        List<ChunkDraft> chunks = chunker.chunk(pages);

        assertThat(chunks).hasSize(3);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkId()).as("chunk %d's id is sequential and 0-indexed (FR-008)", i)
                    .isEqualTo(i);
        }
        assertThat(chunks.stream().map(ChunkDraft::chunkId).distinct().count())
                .as("no duplicate chunk ids even though boundary chunk text grew")
                .isEqualTo(chunks.size());
    }

    @Test
    void everyBoundaryChunkStaysWithinTheEstablishedTokenSizeRange() {
        String longPage = repeatedWords(3000);
        String flankedText = "Tiny middle page.";
        List<ExtractedPage> pages =
                List.of(new ExtractedPage(1, longPage), new ExtractedPage(2, flankedText), new ExtractedPage(3, longPage));

        List<ChunkDraft> chunks = chunker.chunk(pages);

        for (ChunkDraft chunk : chunks) {
            assertThat(ENCODING.countTokens(chunk.text()))
                    .as("chunk for page %d stays within TARGET_TOKENS + 2*OVERLAP_TOKENS (626) even in the "
                            + "worst case (FR-009)", chunk.pageNumber())
                    .isLessThanOrEqualTo(500 + 2 * OVERLAP_TOKENS);
        }

        // The flanked page's own single chunk is the worst case this feature can produce: its own
        // text (well under 500 tokens) plus both a trailing and a leading excerpt at once.
        ChunkDraft flankedChunk =
                chunks.stream().filter(c -> c.pageNumber() == 2).findFirst().orElseThrow();
        int flankedTokenCount = ENCODING.countTokens(flankedChunk.text());
        int nativeOnlyTokenCount = ENCODING.countTokens(flankedText);
        // Re-encoding the decoded, concatenated text can merge a token or two across the seam (a
        // normal BPE property, e.g. whitespace merging with an adjacent word) — so this checks a
        // robust lower bound (native text plus at least one full side's excerpt), not an exact sum.
        assertThat(flankedTokenCount).as("sanity: this really is the both-excerpts collision case — "
                        + "substantially more than the page's own %d native tokens", nativeOnlyTokenCount)
                .isGreaterThan(nativeOnlyTokenCount + OVERLAP_TOKENS);
        assertThat(flankedTokenCount).as("well under the 1000-token ceiling (FR-009)").isLessThan(1000);
    }

    @Test
    void unpagedMultiWindowTextIsChunkedIdenticallyToBeforeThisFeature() {
        String longText = repeatedWords(3000);

        List<ChunkDraft> chunks = chunker.chunk(List.of(new ExtractedPage(null, longText)));

        assertThat(chunks).hasSizeGreaterThan(1);
        for (ChunkDraft chunk : chunks) {
            assertThat(chunk.pageNumber())
                    .as("no page structure means no anchor page, and no cross-page excerpt logic can "
                            + "apply (FR-007)")
                    .isNull();
        }
        // No neighbor exists in either direction for a single unpaged "page," so every window —
        // including its first and last — is exactly as before this feature.
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertThat(ENCODING.countTokens(chunks.get(i).text()))
                    .as("every interior window is still exactly the 500-token target (FR-007)")
                    .isEqualTo(500);
        }
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

    /** Last {@code n} tokens of {@code tokens}, or all of them if there are fewer than {@code n}. */
    private static IntArrayList tailTokens(IntArrayList tokens, int n) {
        int start = Math.max(0, tokens.size() - n);
        IntArrayList result = new IntArrayList(tokens.size() - start);
        for (int i = start; i < tokens.size(); i++) {
            result.add(tokens.get(i));
        }
        return result;
    }

    /** First {@code n} tokens of {@code tokens}, or all of them if there are fewer than {@code n}. */
    private static IntArrayList headTokens(IntArrayList tokens, int n) {
        int end = Math.min(n, tokens.size());
        IntArrayList result = new IntArrayList(end);
        for (int i = 0; i < end; i++) {
            result.add(tokens.get(i));
        }
        return result;
    }

    private static IntArrayList concatTokens(IntArrayList a, IntArrayList b) {
        IntArrayList result = new IntArrayList(a.size() + b.size());
        for (int i = 0; i < a.size(); i++) {
            result.add(a.get(i));
        }
        for (int i = 0; i < b.size(); i++) {
            result.add(b.get(i));
        }
        return result;
    }
}

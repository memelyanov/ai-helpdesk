package com.epam.aihelpdesk.ingestion;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Splits a document's extracted, per-page text into token-accurate chunks (FR-006, research
 * Decision 3): fixed 500-token target windows with a fixed 63-token (12.6%) overlap, counted via
 * {@code jtokkit}'s {@code cl100k_base} encoding — the same vocabulary
 * {@code text-embedding-3-small} (the constitution's mandated embedding model) is documented
 * against.
 *
 * <p>Windows are built independently per {@link ExtractedPage}, not across the whole document's
 * concatenated text: this keeps every chunk's page number exact (FR-007 — one real source page per
 * chunk, never an average or a guess) and avoids splitting a byte-pair-encoded token across a page
 * boundary that two independently-parsed pages have no reason to share. FR-006's "the last chunk of
 * a document... may fall under 500 tokens" exception is honored per page: a page's own trailing
 * remainder chunk may be short, exactly as a whole short document's sole chunk may be short — a
 * following page is, for chunking purposes, a fresh reading context, not a continuation of the same
 * token stream. A page with blank text (a divider page, spec Edge Cases) contributes zero chunks
 * and does not otherwise affect chunk numbering or neighboring pages' chunks.
 *
 * <p><b>Cross-page excerpts (feature 012):</b> per-page independence is otherwise complete, but a
 * page's <em>first</em> window may be prefixed with a short trailing excerpt borrowed from the
 * nearest preceding non-blank page (FR-002), and a page's <em>last</em> window may be suffixed with
 * a short lead-in excerpt borrowed from the nearest following non-blank page (FR-001) — both sized
 * at {@code OVERLAP_TOKENS} (FR-003), skipping any intervening blank page (FR-005), and absent at
 * either end of the document (FR-006). A page whose only window is simultaneously first and last
 * receives both at once. Every chunk still reports exactly one page number — its <b>anchor
 * page</b>: the page whose own reading-context loop built it, regardless of how much of its text
 * ended up borrowed from a neighbor (FR-004/FR-010) — so this per-page independence claim remains
 * true for page attribution even though a chunk's <em>text</em> may now include a little of a
 * neighbor's.
 */
@Component
public class Chunker {

    private static final int TARGET_TOKENS = 500;
    private static final int OVERLAP_TOKENS = 63;

    private static final Encoding ENCODING;

    static {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        ENCODING = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    public List<ChunkDraft> chunk(List<ExtractedPage> pages) {
        // Pass 1: tokenize every non-blank page once, keeping pages in order (feature 012, research
        // Decision 1). Filtering blank pages up front means "the nearest neighbor in this list" is
        // always "the nearest page that actually has text" — FR-005 falls out for free in both
        // directions, however many blank pages separated them in the original list.
        List<Integer> pageNumbers = new ArrayList<>();
        List<IntArrayList> pageTokens = new ArrayList<>();
        for (ExtractedPage page : pages) {
            if (page.text() == null || page.text().isBlank()) {
                continue;
            }
            pageNumbers.add(page.pageNumber());
            pageTokens.add(ENCODING.encode(page.text()));
        }

        // Pass 2: build each page's windows exactly as before, except the first window of a page
        // may be prefixed with a trailing excerpt borrowed from the previous non-blank page (FR-002)
        // and the last window of a page may be suffixed with a lead-in excerpt borrowed from the
        // next non-blank page (FR-001) — both sized at OVERLAP_TOKENS (FR-003). A page whose only
        // window is simultaneously first and last receives both at once (FR-010); a page at either
        // end of the document receives none on that side (FR-006). Every interior window, and every
        // window of a page with no neighbor on that side, is byte-for-byte identical to before this
        // feature.
        List<ChunkDraft> chunks = new ArrayList<>();
        int nextChunkId = 0;
        for (int i = 0; i < pageTokens.size(); i++) {
            IntArrayList tokens = pageTokens.get(i);
            IntArrayList precedingTail =
                    i > 0 ? tailTokens(pageTokens.get(i - 1), OVERLAP_TOKENS) : new IntArrayList(0);
            IntArrayList followingHead = i < pageTokens.size() - 1
                    ? headTokens(pageTokens.get(i + 1), OVERLAP_TOKENS)
                    : new IntArrayList(0);

            int start = 0;
            boolean isFirstWindow = true;
            while (start < tokens.size()) {
                int end = Math.min(start + TARGET_TOKENS, tokens.size());
                boolean isLastWindow = end == tokens.size();
                IntArrayList windowTokens = subRange(tokens, start, end);
                if (isFirstWindow && precedingTail.size() > 0) {
                    windowTokens = concat(precedingTail, windowTokens);
                }
                if (isLastWindow && followingHead.size() > 0) {
                    windowTokens = concat(windowTokens, followingHead);
                }
                String windowText = ENCODING.decode(windowTokens);
                chunks.add(new ChunkDraft(nextChunkId++, pageNumbers.get(i), windowText));
                isFirstWindow = false;
                if (isLastWindow) {
                    break;
                }
                start = end - OVERLAP_TOKENS;
            }
        }
        return chunks;
    }

    private static IntArrayList subRange(IntArrayList tokens, int start, int end) {
        IntArrayList range = new IntArrayList(end - start);
        for (int i = start; i < end; i++) {
            range.add(tokens.get(i));
        }
        return range;
    }

    /** Last {@code n} tokens of {@code tokens}, or all of them if there are fewer than {@code n}. */
    private static IntArrayList tailTokens(IntArrayList tokens, int n) {
        return subRange(tokens, Math.max(0, tokens.size() - n), tokens.size());
    }

    /** First {@code n} tokens of {@code tokens}, or all of them if there are fewer than {@code n}. */
    private static IntArrayList headTokens(IntArrayList tokens, int n) {
        return subRange(tokens, 0, Math.min(n, tokens.size()));
    }

    private static IntArrayList concat(IntArrayList first, IntArrayList second) {
        IntArrayList combined = new IntArrayList(first.size() + second.size());
        for (int i = 0; i < first.size(); i++) {
            combined.add(first.get(i));
        }
        for (int i = 0; i < second.size(); i++) {
            combined.add(second.get(i));
        }
        return combined;
    }
}

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
 * Decision 3): fixed 800-token target windows with a fixed 100-token (12.5%) overlap, counted via
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
 */
@Component
public class Chunker {

    private static final int TARGET_TOKENS = 800;
    private static final int OVERLAP_TOKENS = 100;

    private static final Encoding ENCODING;

    static {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        ENCODING = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    public List<ChunkDraft> chunk(List<ExtractedPage> pages) {
        List<ChunkDraft> chunks = new ArrayList<>();
        int nextChunkId = 0;
        for (ExtractedPage page : pages) {
            if (page.text() == null || page.text().isBlank()) {
                continue;
            }
            IntArrayList tokens = ENCODING.encode(page.text());
            int start = 0;
            while (start < tokens.size()) {
                int end = Math.min(start + TARGET_TOKENS, tokens.size());
                String windowText = ENCODING.decode(subRange(tokens, start, end));
                chunks.add(new ChunkDraft(nextChunkId++, page.pageNumber(), windowText));
                if (end == tokens.size()) {
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
}

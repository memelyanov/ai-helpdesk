package com.epam.aihelpdesk.ingestion;

/**
 * One candidate chunk before embedding — output of {@link Chunker} (data-model.md).
 *
 * @param chunkId    0-indexed, sequential and unique within its document (FR-007)
 * @param pageNumber carried from the source {@link ExtractedPage}; {@code null} for formats
 *                   without page structure
 * @param text       500–1000 tokens for every interior chunk; the final chunk of a page's token
 *                   stream may fall under 500 tokens (FR-006)
 */
public record ChunkDraft(int chunkId, Integer pageNumber, String text) {
}

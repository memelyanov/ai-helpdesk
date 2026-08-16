package com.epam.aihelpdesk.chat;

import java.util.UUID;

/**
 * One row returned by {@link ChatRetrievalRepository}'s similarity query, reusing feature 003's
 * {@code similarity-search-contract.md} column set exactly. At most {@code TOP_K} rows, ordered by
 * ascending {@code distance} (closest match first), for one question — never persisted, discarded
 * after the request completes.
 *
 * @param documentId     {@code chunks.document_id}.
 * @param chunkId        {@code chunks.chunk_id}.
 * @param sourceFilename {@code chunks.source_filename} (denormalized, feature 003).
 * @param pageNumber     {@code chunks.page_number}; {@code null} means no page structure.
 * @param text           {@code chunks.text} — the passage content included in the generation
 *                       prompt.
 * @param distance       pgvector cosine distance ({@code embedding <=> :query_vector}); similarity
 *                       is {@code 1 - distance}.
 */
public record RetrievedChunk(UUID documentId, int chunkId, String sourceFilename, Integer pageNumber, String text,
        double distance) {
}

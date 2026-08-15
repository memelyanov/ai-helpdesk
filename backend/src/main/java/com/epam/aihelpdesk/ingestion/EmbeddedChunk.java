package com.epam.aihelpdesk.ingestion;

/**
 * A {@link ChunkDraft} plus its embedding vector once the batched embedding call (or calls, if
 * sub-batched, research Decision 4) returns — the only shape {@link DocumentRepository} accepts
 * for the chunk-insert step (data-model.md). There is no representation of a chunk without its
 * vector, matching feature 003's "no chunk whose text exists but embedding does not" guarantee.
 *
 * @param chunk     the source chunk (text + position + page number)
 * @param embedding a 1536-dimensional vector, matching {@code chunks.embedding vector(1536)}
 */
public record EmbeddedChunk(ChunkDraft chunk, float[] embedding) {
}

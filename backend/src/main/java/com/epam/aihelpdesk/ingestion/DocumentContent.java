package com.epam.aihelpdesk.ingestion;

/**
 * A document's original stored content, carried from {@link DocumentQueryRepository} to
 * {@link DocumentController} for the {@code GET /documents/{id}/content} response. Not a JSON
 * DTO — a download's response shape is HTTP headers ({@code Content-Type},
 * {@code Content-Disposition}) plus a raw byte body (FR-008/FR-009), not a serialized object.
 */
public record DocumentContent(String filename, String contentType, byte[] content) {
}

package com.epam.aihelpdesk.ingestion;

/**
 * No stored document matches the requested identifier — either the identifier is not even a
 * validly formatted UUID, or it is well-formed but does not match any {@code documents} row.
 * {@link DocumentErrorHandler} maps this to {@code 404 Not Found} with the fixed
 * {@code document_not_found} error code (research Decision 4, spec.md FR-010): a caller sees one
 * consistent outcome either way and never needs to distinguish the two to know the document isn't
 * retrievable.
 *
 * <p>Deliberately a sibling of {@link IngestionException}, not a subtype — that hierarchy's own
 * Javadoc documents exactly two subclasses for the ingestion pipeline's own {@code 400}/{@code 503}
 * split (research Decision 6); a third, unrelated status code does not belong forced into it.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String message) {
        super(message);
    }
}

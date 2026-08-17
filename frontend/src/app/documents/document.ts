/**
 * Client-side mirror of `DocumentSummaryResponse` (data-model.md `DocumentSummary`, FR-008). No
 * field is dropped even though only `filename` (plus a type icon) is actually rendered per the
 * Clarifications session's sidebar-detail decision — `documentId` drives download/delete, and
 * `uploadedAt` drives sort order.
 */
export interface DocumentSummary {
  documentId: string;
  filename: string;
  contentType: string;
  uploadedAt: string;
  chunkCount: number;
}

export type DocumentIconKind = 'pdf' | 'text';

/** Maps a document's `contentType` to the mockup's `.pdf`/`.txt` icon distinction. */
export function iconKindForContentType(contentType: string): DocumentIconKind {
  return contentType === 'application/pdf' ? 'pdf' : 'text';
}

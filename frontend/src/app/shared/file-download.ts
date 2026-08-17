import { HttpClient, HttpErrorResponse } from '@angular/common/http';

/** Fixed local address (plan.md Technical Context — no deployment target yet). */
export const DOCUMENTS_CONTENT_BASE = 'http://localhost:8080/documents';

export type DownloadResult = { ok: true } | { ok: false; unavailable: boolean };

/**
 * research.md Decision 3: downloads `GET /documents/{id}/content` as a blob and triggers a browser
 * save under `suggestedFilename` — the filename the caller already has in hand from the same
 * response that gave it `documentId` (a sidebar row's `DocumentSummary.filename`, or a citation's
 * `Citation.filename`) — never one parsed from the response's `Content-Disposition` header, which
 * would require an extra CORS header exposure for no new information.
 *
 * `unavailable: true` distinguishes a `404 document_not_found` (FR-014's "no longer available"
 * messaging) from any other failure, so a citation badge and a sidebar row can both react correctly
 * without duplicating that distinction themselves (contracts/frontend-service-contract.md).
 */
export function downloadDocument(
  http: HttpClient,
  documentId: string,
  suggestedFilename: string,
): Promise<DownloadResult> {
  return new Promise((resolve) => {
    http
      .get(`${DOCUMENTS_CONTENT_BASE}/${documentId}/content`, { responseType: 'blob' })
      .subscribe({
        next: (blob) => {
          triggerSave(blob, suggestedFilename);
          resolve({ ok: true });
        },
        error: (error: unknown) => {
          const unavailable = error instanceof HttpErrorResponse && error.status === 404;
          resolve({ ok: false, unavailable });
        },
      });
  });
}

function triggerSave(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}

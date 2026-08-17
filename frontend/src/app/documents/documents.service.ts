import { Injectable, Signal, WritableSignal, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { mapDeleteError, mapUploadError } from '../shared/api-error';
import { DocumentSummary } from './document';

/** Fixed local address (plan.md Technical Context — no deployment target yet). */
export const DOCUMENTS_ENDPOINT = 'http://localhost:8080/documents';

/**
 * Holds the sidebar's live mirror of `GET /documents` (data-model.md `DocumentsService`,
 * contracts/frontend-service-contract.md). `loaded` starts `false` and flips permanently to `true`
 * once the first `refresh()` (issued at construction) settles — success or failure — so
 * `document-sidebar.component` can tell "not yet loaded" apart from "confirmed empty" (FR-008/
 * FR-009), which `documents().length === 0` alone cannot distinguish.
 *
 */
@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private readonly http = inject(HttpClient);

  private readonly _documents: WritableSignal<DocumentSummary[]> = signal([]);
  private readonly _loaded: WritableSignal<boolean> = signal(false);
  private readonly _uploading: WritableSignal<boolean> = signal(false);
  private readonly _uploadError: WritableSignal<string | null> = signal(null);

  readonly documents: Signal<DocumentSummary[]> = this._documents.asReadonly();
  readonly loaded: Signal<boolean> = this._loaded.asReadonly();
  /** `true` for exactly the duration of one in-flight `upload()` call (FR-012). */
  readonly uploading: Signal<boolean> = this._uploading.asReadonly();
  /** Decision 5's mapped message for the most recent failed upload, or `null` (FR-011). */
  readonly uploadError: Signal<string | null> = this._uploadError.asReadonly();

  constructor() {
    this.refresh();
  }

  /** Re-fetches `GET /documents` and replaces `documents` wholesale — never a merge/patch. */
  refresh(): void {
    this.http.get<DocumentSummary[]>(DOCUMENTS_ENDPOINT).subscribe({
      next: (documents) => {
        this._documents.set(documents);
        this._loaded.set(true);
      },
      error: () => {
        // No documented error vocabulary for GET /documents (out of this feature's FR scope) — the
        // list is simply left as its last known-good state, but `loaded` still flips so the sidebar
        // is never stuck showing a loading state forever (FR-008).
        this._loaded.set(true);
      },
    });
  }

  /**
   * Calls `POST /documents` with a single-file `multipart/form-data` body (FR-010). On success,
   * re-syncs `documents` wholesale via {@link refresh} rather than hand-appending the new entry —
   * the simplest way to guarantee the list's ordering stays server-authoritative (data-model.md).
   * On failure, `documents` is left untouched and `uploadError` is set to Decision 5's mapped
   * message. `uploadError` is reset to `null` at the very start of every attempt (FR-011).
   */
  upload(file: File): void {
    this._uploadError.set(null);
    this._uploading.set(true);

    const formData = new FormData();
    formData.append('file', file, file.name);

    this.http.post(DOCUMENTS_ENDPOINT, formData).subscribe({
      next: () => {
        this._uploading.set(false);
        this.refresh();
      },
      error: (error: unknown) => {
        const code =
          error instanceof HttpErrorResponse && error.status !== 0
            ? ((error.error as { error?: string } | null)?.error ?? null)
            : null;
        this._uploading.set(false);
        this._uploadError.set(mapUploadError(code));
      },
    });
  }

  /**
   * Calls `DELETE /documents/{id}` (FR-015/FR-016/FR-017). Unlike every other method here, this
   * one settles a result directly to its caller rather than only updating a signal — Decision 4's
   * confirmation UI (a single container-level `confirmingDocumentId`, data-model.md) needs to know
   * success/failure to decide whether to clear that state or show a row-local error, which is UI
   * state this service deliberately does not own. On success, the entry is removed from
   * `documents` (FR-016). On failure, `documents` is left untouched (FR-017) and the resolved
   * `message` is one of Decision 5's fixed strings — never raw backend text.
   */
  remove(documentId: string): Promise<{ ok: true } | { ok: false; message: string }> {
    return new Promise((resolve) => {
      this.http.delete(`${DOCUMENTS_ENDPOINT}/${documentId}`).subscribe({
        next: () => {
          this._documents.set(this._documents().filter((d) => d.documentId !== documentId));
          resolve({ ok: true });
        },
        error: (error: unknown) => {
          const code =
            error instanceof HttpErrorResponse && error.status !== 0
              ? ((error.error as { error?: string } | null)?.error ?? null)
              : null;
          resolve({ ok: false, message: mapDeleteError(code) });
        },
      });
    });
  }
}

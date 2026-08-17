import { Component, EventEmitter, Output, inject, input, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DocumentSummary, iconKindForContentType } from '../document';
import { downloadDocument } from '../../shared/file-download';
import { mapDownloadError } from '../../shared/api-error';

/**
 * Presentational — one sidebar row (plan.md: no direct `DocumentsService` dependency, though it
 * does inject `HttpClient` directly to call the stateless {@link downloadDocument} helper, per
 * contracts/frontend-service-contract.md's signature). Renders the filename plus a type icon
 * (Clarifications session), a download action (FR-013/FR-014), and a delete action gated behind an
 * inline two-step confirmation (FR-013/FR-015/FR-017, research Decision 4) — both actions reachable
 * by hover or keyboard focus, never only one.
 *
 * The delete flow's actual state (`confirmingDocumentId`, per-row error message) is owned by the
 * container (`document-sidebar.component`, T041) — this component only renders whatever
 * `confirming`/`deleteError` it's given and emits the three delete-related events, so it never
 * needs to know about any *other* row's state (a requirement of FR-021's "at most one open
 * confirmation at a time," which only makes sense at the container level).
 */
@Component({
  selector: 'app-document-item',
  imports: [],
  templateUrl: './document-item.component.html',
  styleUrl: './document-item.component.css',
})
export class DocumentItemComponent {
  private readonly http = inject(HttpClient);

  readonly document = input.required<DocumentSummary>();
  /** `true` when this row is the one currently showing its inline confirm/cancel UI (FR-015). */
  readonly confirming = input(false);
  /** The container's mapped delete-failure message for this row, or `null` (FR-017). */
  readonly deleteError = input<string | null>(null);

  @Output() readonly deleteRequested = new EventEmitter<void>();
  @Output() readonly deleteConfirmed = new EventEmitter<void>();
  @Output() readonly deleteCancelled = new EventEmitter<void>();

  private readonly _downloadError = signal<string | null>(null);
  readonly downloadError = this._downloadError.asReadonly();

  private readonly _downloadUnavailable = signal(false);
  readonly downloadUnavailable = this._downloadUnavailable.asReadonly();

  get iconKind() {
    return iconKindForContentType(this.document().contentType);
  }

  async onDownloadClick(): Promise<void> {
    this._downloadError.set(null);
    const doc = this.document();
    const result = await downloadDocument(this.http, doc.documentId, doc.filename);
    if (!result.ok) {
      this._downloadUnavailable.set(result.unavailable);
      this._downloadError.set(mapDownloadError(result.unavailable ? 'document_not_found' : null));
    }
  }
}

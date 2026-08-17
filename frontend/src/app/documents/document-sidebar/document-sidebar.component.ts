import { Component, WritableSignal, inject, signal } from '@angular/core';
import { DocumentsService } from '../documents.service';
import { DocumentItemComponent } from './document-item.component';

/**
 * User Story 2's container: injects {@link DocumentsService} and branches on `loaded()` to render
 * a loading state, an explicit empty state (FR-009), or the list of `document-item` rows (FR-008).
 * Also owns the upload control (User Story 3) and — per research.md Decision 4's revision — the
 * single container-level `confirmingDocumentId` signal that gates each row's inline delete
 * confirmation (User Story 5, data-model.md "Sidebar row UI state"). Tracking it once here, not
 * once per row, is precisely what makes FR-021's "at most one open confirmation at a time" true for
 * free: triggering delete on any row overwrites whatever id was there before.
 */
@Component({
  selector: 'app-document-sidebar',
  imports: [DocumentItemComponent],
  templateUrl: './document-sidebar.component.html',
  styleUrl: './document-sidebar.component.css',
})
export class DocumentSidebarComponent {
  private readonly documentsService = inject(DocumentsService);

  readonly documents = this.documentsService.documents;
  readonly loaded = this.documentsService.loaded;
  readonly uploading = this.documentsService.uploading;
  readonly uploadError = this.documentsService.uploadError;

  private readonly _confirmingDocumentId: WritableSignal<string | null> = signal(null);
  readonly confirmingDocumentId = this._confirmingDocumentId.asReadonly();

  private readonly _deleteErrors: WritableSignal<ReadonlyMap<string, string>> = signal(new Map());
  readonly deleteErrors = this._deleteErrors.asReadonly();

  /**
   * Starts the upload immediately on selection, with no separate confirm step (FR-010). Resets the
   * `<input type="file">`'s value afterward so selecting the *same* filename again still fires a
   * `change` event (browsers otherwise suppress it for an unchanged value).
   */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.documentsService.upload(file);
    }
    input.value = '';
  }

  /**
   * Opens `documentId`'s inline confirmation — overwriting whatever id was previously confirming,
   * which cancels that row's confirmation without deleting it (FR-021). Also clears any stale
   * delete-failure message for `documentId` from a prior attempt (FR-017's "until the next delete
   * attempt on that row").
   */
  onDeleteRequested(documentId: string): void {
    this._confirmingDocumentId.set(documentId);
    this.clearDeleteError(documentId);
  }

  onDeleteCancelled(): void {
    this._confirmingDocumentId.set(null);
  }

  /** Calls `DocumentsService.remove()` and settles the confirmation/error state either way (FR-017). */
  async onDeleteConfirmed(documentId: string): Promise<void> {
    const result = await this.documentsService.remove(documentId);
    this._confirmingDocumentId.set(null);
    if (!result.ok) {
      const next = new Map(this._deleteErrors());
      next.set(documentId, result.message);
      this._deleteErrors.set(next);
    }
  }

  private clearDeleteError(documentId: string): void {
    if (this._deleteErrors().has(documentId)) {
      const next = new Map(this._deleteErrors());
      next.delete(documentId);
      this._deleteErrors.set(next);
    }
  }
}

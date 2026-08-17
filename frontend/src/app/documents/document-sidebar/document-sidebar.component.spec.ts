import { TestBed } from '@angular/core/testing';
import { WritableSignal, signal } from '@angular/core';
import { DocumentSidebarComponent } from './document-sidebar.component';
import { DocumentsService } from '../documents.service';
import { DocumentSummary } from '../document';

const doc = (id: string, filename: string): DocumentSummary => ({
  documentId: id,
  filename,
  contentType: 'application/pdf',
  uploadedAt: '2026-08-16T10:00:00Z',
  chunkCount: 3,
});

describe('DocumentSidebarComponent (US2 container, FR-008/FR-009)', () => {
  let documents: WritableSignal<DocumentSummary[]>;
  let loaded: WritableSignal<boolean>;
  let uploading: WritableSignal<boolean>;
  let uploadError: WritableSignal<string | null>;
  let uploadSpy: (file: File) => void;
  let uploadedFiles: File[];
  let removeCalls: string[];
  let removeResolvers: Array<(result: { ok: true } | { ok: false; message: string }) => void>;

  beforeEach(() => {
    documents = signal<DocumentSummary[]>([]);
    loaded = signal(false);
    uploading = signal(false);
    uploadError = signal<string | null>(null);
    uploadedFiles = [];
    uploadSpy = (file: File) => uploadedFiles.push(file);
    removeCalls = [];
    removeResolvers = [];
    TestBed.configureTestingModule({
      imports: [DocumentSidebarComponent],
      providers: [
        {
          provide: DocumentsService,
          useValue: {
            documents,
            loaded,
            uploading,
            uploadError,
            upload: (file: File) => uploadSpy(file),
            remove: (documentId: string) => {
              removeCalls.push(documentId);
              return new Promise((resolve) => removeResolvers.push(resolve));
            },
          },
        },
      ],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(DocumentSidebarComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows a distinct loading state while not yet loaded — no rows, no empty-state message', () => {
    const { el } = render();
    expect(el.querySelectorAll('.file-item').length).toBe(0);
    expect(el.querySelector('.loading-state')).toBeTruthy();
    expect(el.querySelector('.empty-state')).toBeFalsy();
  });

  it('shows an explicit empty state once loaded with zero documents, naming the upload control (FR-009)', () => {
    loaded.set(true);
    const { el } = render();

    expect(el.querySelectorAll('.file-item').length).toBe(0);
    expect(el.querySelector('.loading-state')).toBeFalsy();
    const emptyState = el.querySelector('.empty-state');
    expect(emptyState).toBeTruthy();
    expect(emptyState?.textContent?.toLowerCase()).toContain('upload');
  });

  it('renders the loading state and the empty state with different content (FR-008)', () => {
    const loadingRender = render();
    const loadingText = loadingRender.el.textContent?.trim();

    loaded.set(true);
    const emptyRender = render();
    const emptyText = emptyRender.el.textContent?.trim();

    expect(loadingText).not.toBe(emptyText);
  });

  it('renders every document from DocumentsService.documents as a row', () => {
    loaded.set(true);
    documents.set([doc('d1', 'a.pdf'), doc('d2', 'b.pdf')]);
    const { el } = render();

    const rows = el.querySelectorAll('.file-item');
    expect(rows.length).toBe(2);
    expect(el.textContent).toContain('a.pdf');
    expect(el.textContent).toContain('b.pdf');
  });

  describe('upload control (User Story 3, FR-010/FR-011/FR-012)', () => {
    it('starts the upload immediately on file selection, with no separate confirm step (FR-010)', () => {
      const { el, fixture } = render();
      const fileInput = el.querySelector('input[type="file"]') as HTMLInputElement;
      const file = new File(['content'], 'new-doc.pdf', { type: 'application/pdf' });
      Object.defineProperty(fileInput, 'files', { value: [file] });

      fileInput.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(uploadedFiles).toEqual([file]);
    });

    it('shows a busy state while uploading and disables the upload control against a second selection (FR-012)', () => {
      uploading.set(true);
      const { el } = render();
      const uploadBtn = el.querySelector('.upload-btn') as HTMLButtonElement;
      const fileInput = el.querySelector('input[type="file"]') as HTMLInputElement;

      expect(uploadBtn.disabled).toBe(true);
      expect(fileInput.disabled).toBe(true);
    });

    it('shows the mapped upload error message at the upload control (FR-011)', () => {
      uploadError.set('That file could not be read.');
      const { el } = render();

      expect(el.querySelector('.upload-error')?.textContent).toContain(
        'That file could not be read.',
      );
    });

    it('does not show an upload error message when there is none', () => {
      const { el } = render();
      expect(el.querySelector('.upload-error')).toBeFalsy();
    });
  });

  describe('delete confirmation (User Story 5, FR-015/FR-017/FR-021)', () => {
    beforeEach(() => {
      loaded.set(true);
      documents.set([doc('d1', 'a.pdf'), doc('d2', 'b.pdf')]);
    });

    function rows(el: HTMLElement) {
      return Array.from(el.querySelectorAll('app-document-item'));
    }

    it('opens the inline confirmation on the row whose delete action was clicked, and only that row', () => {
      const { el, fixture } = render();
      const firstDeleteBtn = rows(el)[0].querySelector('.delete-action') as HTMLButtonElement;
      firstDeleteBtn.click();
      fixture.detectChanges();

      expect(rows(el)[0].querySelector('.delete-confirm')).toBeTruthy();
      expect(rows(el)[1].querySelector('.delete-confirm')).toBeFalsy();
    });

    it("cancels the first row's confirmation and opens the second when a second row's delete is triggered (FR-021)", () => {
      const { el, fixture } = render();
      (rows(el)[0].querySelector('.delete-action') as HTMLButtonElement).click();
      fixture.detectChanges();
      (rows(el)[1].querySelector('.delete-action') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(rows(el)[0].querySelector('.delete-confirm')).toBeFalsy();
      expect(rows(el)[1].querySelector('.delete-confirm')).toBeTruthy();
      expect(removeCalls).toEqual([]); // never deletes just by opening a second confirmation
    });

    it('Cancel reverts with no request sent', () => {
      const { el, fixture } = render();
      (rows(el)[0].querySelector('.delete-action') as HTMLButtonElement).click();
      fixture.detectChanges();
      (rows(el)[0].querySelector('.delete-cancel-btn') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(rows(el)[0].querySelector('.delete-confirm')).toBeFalsy();
      expect(removeCalls).toEqual([]);
    });

    it("Confirm calls DocumentsService.remove() with that row's documentId", () => {
      const { el, fixture } = render();
      (rows(el)[0].querySelector('.delete-action') as HTMLButtonElement).click();
      fixture.detectChanges();
      (rows(el)[0].querySelector('.delete-confirm-btn') as HTMLButtonElement).click();

      expect(removeCalls).toEqual(['d1']);
    });

    it('leaves the row listed with a persistent message on a failed delete (FR-017)', async () => {
      const { el, fixture } = render();
      (rows(el)[0].querySelector('.delete-action') as HTMLButtonElement).click();
      fixture.detectChanges();
      (rows(el)[0].querySelector('.delete-confirm-btn') as HTMLButtonElement).click();

      removeResolvers[0]({
        ok: false,
        message: 'The document could not be deleted. Please try again.',
      });
      await Promise.resolve();
      await Promise.resolve();
      fixture.detectChanges();

      expect(rows(el).length).toBe(2); // still listed
      expect(rows(el)[0].querySelector('.delete-error')?.textContent).toContain(
        'The document could not be deleted. Please try again.',
      );
      expect(rows(el)[0].querySelector('.delete-confirm')).toBeFalsy(); // confirmation itself closes
    });
  });
});

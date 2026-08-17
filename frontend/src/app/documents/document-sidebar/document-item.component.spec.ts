import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DocumentItemComponent } from './document-item.component';
import { DocumentSummary } from '../document';
import { DOCUMENTS_CONTENT_BASE } from '../../shared/file-download';

const pdfDoc: DocumentSummary = {
  documentId: 'd1',
  filename: 'company_handbook.pdf',
  contentType: 'application/pdf',
  uploadedAt: '2026-08-16T10:00:00Z',
  chunkCount: 8,
};

const textDoc: DocumentSummary = {
  documentId: 'd2',
  filename: 'api_reference.txt',
  contentType: 'text/plain',
  uploadedAt: '2026-08-15T10:00:00Z',
  chunkCount: 3,
};

describe('DocumentItemComponent (presentational, US2 Clarifications: filename only)', () => {
  let httpMock: HttpTestingController;

  function render(
    document: DocumentSummary,
    opts: { confirming?: boolean; deleteError?: string | null } = {},
  ) {
    const fixture = TestBed.createComponent(DocumentItemComponent);
    fixture.componentRef.setInput('document', document);
    fixture.componentRef.setInput('confirming', opts.confirming ?? false);
    fixture.componentRef.setInput('deleteError', opts.deleteError ?? null);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DocumentItemComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders the filename and a pdf icon for a .pdf document', () => {
    const { el } = render(pdfDoc);
    expect(el.textContent).toContain('company_handbook.pdf');
    expect(el.querySelector('.file-icon')?.className).toContain('pdf');
  });

  it('renders the filename and a text icon for a .txt document', () => {
    const { el } = render(textDoc);
    expect(el.textContent).toContain('api_reference.txt');
    expect(el.querySelector('.file-icon')?.className).toContain('text');
  });

  it('does not render upload date or chunk count as visible text (Clarifications session)', () => {
    const { el } = render(pdfDoc);
    expect(el.textContent).not.toContain('2026-08-16');
    expect(el.textContent).not.toContain('8');
  });

  describe('download action (User Story 4, FR-013/FR-014)', () => {
    it('is a real, always-present focusable control — reachable by keyboard, not only mouse hover', () => {
      const { el } = render(pdfDoc);
      const downloadBtn = el.querySelector('.download-action') as HTMLButtonElement;
      expect(downloadBtn).toBeTruthy();
      expect(downloadBtn.tagName).toBe('BUTTON');
      expect(downloadBtn.tabIndex).not.toBe(-1);
    });

    it("downloads that exact row's document on click", () => {
      const { el } = render(pdfDoc);
      const downloadBtn = el.querySelector('.download-action') as HTMLButtonElement;
      downloadBtn.click();

      const req = httpMock.expectOne(`${DOCUMENTS_CONTENT_BASE}/d1/content`);
      req.flush(new Blob(['bytes']));
    });

    it('shows the "no longer available" message on a 404 and disables further attempts (FR-014)', async () => {
      const { el, fixture } = render(pdfDoc);
      const downloadBtn = el.querySelector('.download-action') as HTMLButtonElement;
      downloadBtn.click();
      httpMock
        .expectOne(`${DOCUMENTS_CONTENT_BASE}/d1/content`)
        .flush(null, { status: 404, statusText: 'Not Found' });
      await Promise.resolve();
      await Promise.resolve();
      fixture.detectChanges();

      expect(el.querySelector('.download-error')?.textContent?.toLowerCase()).toContain(
        'no longer available',
      );
    });

    it('shows a generic retryable message on any other download failure (FR-014)', async () => {
      const { el, fixture } = render(pdfDoc);
      const downloadBtn = el.querySelector('.download-action') as HTMLButtonElement;
      downloadBtn.click();
      httpMock
        .expectOne(`${DOCUMENTS_CONTENT_BASE}/d1/content`)
        .flush(null, { status: 503, statusText: 'Service Unavailable' });
      await Promise.resolve();
      await Promise.resolve();
      fixture.detectChanges();

      expect(el.querySelector('.download-error')?.textContent).toBeTruthy();
    });
  });

  describe('delete action and inline confirmation (User Story 5, FR-013/FR-015/FR-017)', () => {
    it('is a real, always-present focusable control — reachable by keyboard, not only mouse hover', () => {
      const { el } = render(pdfDoc);
      const deleteBtn = el.querySelector('.delete-action') as HTMLButtonElement;
      expect(deleteBtn).toBeTruthy();
      expect(deleteBtn.tagName).toBe('BUTTON');
      expect(deleteBtn.tabIndex).not.toBe(-1);
    });

    it('emits deleteRequested when the delete action is clicked, without confirming anything itself', () => {
      const { el, fixture } = render(pdfDoc);
      const emitted: void[] = [];
      fixture.componentInstance.deleteRequested.subscribe(() => emitted.push(undefined));

      (el.querySelector('.delete-action') as HTMLButtonElement).click();

      expect(emitted.length).toBe(1);
    });

    it('shows no confirmation UI when confirming is false', () => {
      const { el } = render(pdfDoc, { confirming: false });
      expect(el.querySelector('.delete-confirm')).toBeFalsy();
    });

    it('shows an inline Confirm/Cancel prompt when confirming is true (FR-015)', () => {
      const { el } = render(pdfDoc, { confirming: true });
      const confirmUi = el.querySelector('.delete-confirm');
      expect(confirmUi).toBeTruthy();
      expect(confirmUi?.textContent?.toLowerCase()).toContain('delete');
      expect(el.querySelector('.delete-confirm-btn')).toBeTruthy();
      expect(el.querySelector('.delete-cancel-btn')).toBeTruthy();
    });

    it('emits deleteConfirmed when Confirm is clicked, and deleteCancelled when Cancel is clicked', () => {
      const { el, fixture } = render(pdfDoc, { confirming: true });
      const confirmed: void[] = [];
      const cancelled: void[] = [];
      fixture.componentInstance.deleteConfirmed.subscribe(() => confirmed.push(undefined));
      fixture.componentInstance.deleteCancelled.subscribe(() => cancelled.push(undefined));

      (el.querySelector('.delete-cancel-btn') as HTMLButtonElement).click();
      (el.querySelector('.delete-confirm-btn') as HTMLButtonElement).click();

      expect(cancelled.length).toBe(1);
      expect(confirmed.length).toBe(1);
    });

    it('shows a persistent row-level failure message when deleteError is set (FR-017)', () => {
      const { el } = render(pdfDoc, {
        deleteError: 'The document could not be deleted. Please try again.',
      });
      expect(el.querySelector('.delete-error')?.textContent).toContain(
        'The document could not be deleted. Please try again.',
      );
    });
  });
});

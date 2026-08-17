import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DocumentsService, DOCUMENTS_ENDPOINT } from './documents.service';
import { DocumentSummary } from './document';

const doc = (id: string, filename: string): DocumentSummary => ({
  documentId: id,
  filename,
  contentType: 'application/pdf',
  uploadedAt: '2026-08-16T10:00:00Z',
  chunkCount: 3,
});

describe('DocumentsService (contracts/frontend-service-contract.md)', () => {
  let httpMock: HttpTestingController;
  let service: DocumentsService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DocumentsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('is not loaded and empty immediately after construction, before the initial GET resolves (FR-008)', () => {
    expect(service.loaded()).toBe(false);
    expect(service.documents()).toEqual([]);
    httpMock.expectOne(DOCUMENTS_ENDPOINT).flush([]); // drain the auto-fired construction request
  });

  it('populates documents from GET /documents on construction, preserving the backend order', () => {
    const req = httpMock.expectOne(DOCUMENTS_ENDPOINT);
    req.flush([doc('d1', 'b.pdf'), doc('d2', 'a.pdf')]);

    expect(service.documents().map((d) => d.documentId)).toEqual(['d1', 'd2']);
    expect(service.loaded()).toBe(true);
  });

  it('becomes loaded even when the initial request fails (FR-008 — never left stuck)', () => {
    const req = httpMock.expectOne(DOCUMENTS_ENDPOINT);
    req.error(new ProgressEvent('error'));

    expect(service.loaded()).toBe(true);
  });

  it('refresh() replaces the list wholesale, never merges/patches', () => {
    httpMock.expectOne(DOCUMENTS_ENDPOINT).flush([doc('d1', 'a.pdf')]);
    expect(service.documents().length).toBe(1);

    service.refresh();
    httpMock.expectOne(DOCUMENTS_ENDPOINT).flush([doc('d2', 'b.pdf'), doc('d3', 'c.pdf')]);

    expect(service.documents().map((d) => d.documentId)).toEqual(['d2', 'd3']);
  });

  it('stays loaded through subsequent refresh() calls, even ones still in flight', () => {
    httpMock.expectOne(DOCUMENTS_ENDPOINT).flush([]);
    expect(service.loaded()).toBe(true);

    service.refresh();
    expect(service.loaded()).toBe(true); // never reverts to a "loading" state
    httpMock.expectOne(DOCUMENTS_ENDPOINT).flush([]);
  });

  describe('upload() (User Story 3, FR-010/FR-011/FR-012)', () => {
    beforeEach(() => {
      httpMock.expectOne(DOCUMENTS_ENDPOINT).flush([]); // drain the construction-time GET
    });

    it('is true only for the duration of the call, then false again on success', () => {
      expect(service.uploading()).toBe(false);
      service.upload(new File(['content'], 'a.pdf'));
      expect(service.uploading()).toBe(true);

      const postReq = httpMock.expectOne(DOCUMENTS_ENDPOINT);
      postReq.flush({ documentId: 'd1', chunkCount: 2 });
      httpMock.expectOne(DOCUMENTS_ENDPOINT).flush([doc('d1', 'a.pdf')]); // the re-sync refresh()

      expect(service.uploading()).toBe(false);
    });

    it('re-syncs documents from the server on success (FR-010) — never a hand-appended entry', () => {
      service.upload(new File(['content'], 'a.pdf'));
      httpMock.expectOne(DOCUMENTS_ENDPOINT).flush({ documentId: 'd1', chunkCount: 2 });

      const refreshReq = httpMock.expectOne(DOCUMENTS_ENDPOINT);
      refreshReq.flush([doc('d1', 'a.pdf')]);

      expect(service.documents().map((d) => d.documentId)).toEqual(['d1']);
      expect(service.uploadError()).toBeNull();
    });

    it.each(['unsupported_type', 'invalid_file', 'unparseable'])(
      'sets a mapped uploadError for %s and adds no entry (FR-011)',
      (code) => {
        service.upload(new File(['content'], 'a.pdf'));
        httpMock
          .expectOne(DOCUMENTS_ENDPOINT)
          .flush(
            { error: code, message: 'raw backend text' },
            { status: 400, statusText: 'Bad Request' },
          );

        expect(service.uploading()).toBe(false);
        expect(service.documents()).toEqual([]);
        expect(service.uploadError()).toBeTruthy();
        expect(service.uploadError()).not.toContain('raw backend text');
      },
    );

    it('treats provider_unconfigured, processing_failed, and a network failure identically (FR-011)', () => {
      service.upload(new File(['content'], 'a.pdf'));
      httpMock
        .expectOne(DOCUMENTS_ENDPOINT)
        .flush(
          { error: 'provider_unconfigured', message: 'x' },
          { status: 503, statusText: 'Service Unavailable' },
        );
      const firstMessage = service.uploadError();

      service.upload(new File(['content'], 'b.pdf'));
      httpMock
        .expectOne(DOCUMENTS_ENDPOINT)
        .flush(
          { error: 'processing_failed', message: 'x' },
          { status: 503, statusText: 'Service Unavailable' },
        );
      const secondMessage = service.uploadError();

      service.upload(new File(['content'], 'c.pdf'));
      httpMock.expectOne(DOCUMENTS_ENDPOINT).error(new ProgressEvent('error'));
      const thirdMessage = service.uploadError();

      expect(firstMessage).toBe(secondMessage);
      expect(secondMessage).toBe(thirdMessage);
    });

    it('resets uploadError to null at the start of each new upload attempt', () => {
      service.upload(new File(['content'], 'a.pdf'));
      httpMock
        .expectOne(DOCUMENTS_ENDPOINT)
        .flush({ error: 'unparseable', message: 'x' }, { status: 400, statusText: 'Bad Request' });
      expect(service.uploadError()).toBeTruthy();

      service.upload(new File(['content'], 'b.pdf'));
      expect(service.uploadError()).toBeNull();
      httpMock.expectOne(DOCUMENTS_ENDPOINT).flush({ documentId: 'd2', chunkCount: 1 });
      httpMock.expectOne(DOCUMENTS_ENDPOINT).flush([doc('d2', 'b.pdf')]);
    });
  });

  describe('remove() (User Story 5, FR-016/FR-017)', () => {
    beforeEach(() => {
      httpMock.expectOne(DOCUMENTS_ENDPOINT).flush([doc('d1', 'a.pdf'), doc('d2', 'b.pdf')]);
    });

    it('removes the entry from documents and resolves { ok: true } on success (FR-016)', async () => {
      const resultPromise = service.remove('d1');
      httpMock
        .expectOne(`${DOCUMENTS_ENDPOINT}/d1`)
        .flush(null, { status: 204, statusText: 'No Content' });

      const result = await resultPromise;
      expect(result).toEqual({ ok: true });
      expect(service.documents().map((d) => d.documentId)).toEqual(['d2']);
    });

    it('leaves documents unchanged and resolves { ok: false, message } on a 503 deletion_failed (FR-017)', async () => {
      const resultPromise = service.remove('d1');
      httpMock
        .expectOne(`${DOCUMENTS_ENDPOINT}/d1`)
        .flush(
          { error: 'deletion_failed', message: 'raw backend text' },
          { status: 503, statusText: 'Service Unavailable' },
        );

      const result = await resultPromise;
      expect(result.ok).toBe(false);
      expect((result as { ok: false; message: string }).message).toBeTruthy();
      expect((result as { ok: false; message: string }).message).not.toContain('raw backend text');
      expect(service.documents().map((d) => d.documentId)).toEqual(['d1', 'd2']);
    });
  });
});

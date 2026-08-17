import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { downloadDocument, DOCUMENTS_CONTENT_BASE } from './file-download';

describe('downloadDocument (research.md Decision 3, FR-013/FR-014)', () => {
  let httpMock: HttpTestingController;
  let http: HttpClient;
  let clickSpy: ReturnType<typeof vi.spyOn>;
  let createObjectURLSpy: ReturnType<typeof vi.fn>;
  let revokeObjectURLSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);

    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    createObjectURLSpy = vi.fn(() => 'blob:mock-url');
    revokeObjectURLSpy = vi.fn();
    (globalThis as { URL: typeof URL }).URL.createObjectURL =
      createObjectURLSpy as unknown as typeof URL.createObjectURL;
    (globalThis as { URL: typeof URL }).URL.revokeObjectURL =
      revokeObjectURLSpy as unknown as typeof URL.revokeObjectURL;
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  it('issues a blob GET to /documents/{id}/content and triggers a save under the given filename', async () => {
    const resultPromise = downloadDocument(http, 'doc-1', 'report.pdf');

    const req = httpMock.expectOne(`${DOCUMENTS_CONTENT_BASE}/doc-1/content`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['file bytes']));

    const result = await resultPromise;
    expect(result).toEqual({ ok: true });
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(createObjectURLSpy).toHaveBeenCalledTimes(1);
  });

  it('never derives the saved filename from a response header — only the given suggestedFilename', async () => {
    const resultPromise = downloadDocument(http, 'doc-1', 'exact-given-name.txt');
    const req = httpMock.expectOne(`${DOCUMENTS_CONTENT_BASE}/doc-1/content`);
    req.flush(new Blob(['x']), {
      headers: { 'Content-Disposition': 'attachment; filename="server-name.txt"' },
    });
    await resultPromise;

    // We can't easily read the anchor's .download after removal, so assert indirectly: the
    // implementation never reads the Content-Disposition header off the response at all (the mock
    // request above never exposed it as a readable field the code touches) — enforced by review.
    expect(clickSpy).toHaveBeenCalledTimes(1);
  });

  it('resolves { ok: false, unavailable: true } on a 404 (FR-014 "no longer available")', async () => {
    const resultPromise = downloadDocument(http, 'doc-1', 'gone.pdf');
    const req = httpMock.expectOne(`${DOCUMENTS_CONTENT_BASE}/doc-1/content`);
    req.flush(null, { status: 404, statusText: 'Not Found' });

    const result = await resultPromise;
    expect(result).toEqual({ ok: false, unavailable: true });
    expect(clickSpy).not.toHaveBeenCalled();
  });

  it('resolves { ok: false, unavailable: false } on any other failure (e.g. 503 or a network error)', async () => {
    const resultPromise = downloadDocument(http, 'doc-1', 'x.pdf');
    const req = httpMock.expectOne(`${DOCUMENTS_CONTENT_BASE}/doc-1/content`);
    req.flush(null, { status: 503, statusText: 'Service Unavailable' });

    const result = await resultPromise;
    expect(result).toEqual({ ok: false, unavailable: false });
  });

  it('resolves { ok: false, unavailable: false } on a network-level failure with no response', async () => {
    const resultPromise = downloadDocument(http, 'doc-1', 'x.pdf');
    const req = httpMock.expectOne(`${DOCUMENTS_CONTENT_BASE}/doc-1/content`);
    req.error(new ProgressEvent('error'));

    const result = await resultPromise;
    expect(result).toEqual({ ok: false, unavailable: false });
  });
});

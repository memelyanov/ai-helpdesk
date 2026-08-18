import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChatService, CHAT_ENDPOINT, MAX_QUESTION_LENGTH } from './chat.service';

describe('ChatService (contracts/frontend-service-contract.md)', () => {
  let httpMock: HttpTestingController;
  let service: ChatService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChatService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts with no messages and pending false', () => {
    expect(service.messages()).toEqual([]);
    expect(service.pending()).toBe(false);
  });

  it('ask() appends a user message then a pending assistant message, then settles it from the response (FR-018)', () => {
    service.ask('What is the refund policy?');

    expect(service.messages().length).toBe(2);
    expect(service.messages()[0].role).toBe('user');
    expect(service.messages()[0].text).toBe('What is the refund policy?');
    expect(service.messages()[0].status).toBe('complete');
    expect(service.messages()[1].role).toBe('assistant');
    expect(service.messages()[1].status).toBe('pending');
    expect(service.pending()).toBe(true);

    const req = httpMock.expectOne(CHAT_ENDPOINT);
    req.flush({
      answer: 'Refunds are available within 30 days.',
      sources: [{ documentId: 'doc-1', filename: 'policy.pdf', page: '2', score: 0.81 }],
    });

    expect(service.pending()).toBe(false);
    const assistantMessage = service.messages()[1];
    expect(assistantMessage.status).toBe('complete');
    expect(assistantMessage.text).toBe('Refunds are available within 30 days.');
    expect(assistantMessage.citations).toEqual([
      {
        documentId: 'doc-1',
        filename: 'policy.pdf',
        pageLabel: '2',
        scorePercent: 81,
        available: true,
      },
    ]);
  });

  it('is a no-op (no HTTP call, no new messages) for a blank/whitespace-only question (FR-004)', () => {
    service.ask('   ');
    expect(service.messages()).toEqual([]);
    httpMock.expectNone(CHAT_ENDPOINT);
  });

  it('is a no-op for a question over MAX_QUESTION_LENGTH characters after trimming (FR-005)', () => {
    service.ask('a'.repeat(MAX_QUESTION_LENGTH + 1));
    expect(service.messages()).toEqual([]);
    httpMock.expectNone(CHAT_ENDPOINT);
  });

  it('sends documentIds: null on every request, never populated (FR-020)', () => {
    service.ask('Any question');
    const req = httpMock.expectOne(CHAT_ENDPOINT);
    expect(req.request.body).toEqual({
      question: 'Any question',
      documentIds: null,
      includeTrace: true,
    });
    req.flush({ answer: 'ok', sources: [] });
  });

  it('settles to status "error" with a mapped message on a 400 response (FR-007)', () => {
    service.ask('A question');
    const req = httpMock.expectOne(CHAT_ENDPOINT);
    req.flush(
      { error: 'blank_question', message: 'raw backend text' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(service.pending()).toBe(false);
    const assistantMessage = service.messages()[1];
    expect(assistantMessage.status).toBe('error');
    expect(assistantMessage.errorMessage).toBeTruthy();
    expect(assistantMessage.errorMessage).not.toContain('raw backend text');
  });

  it('settles to status "error" with a mapped message on a 503 response (FR-007)', () => {
    service.ask('A question');
    const req = httpMock.expectOne(CHAT_ENDPOINT);
    req.flush(
      { error: 'processing_failed', message: 'raw backend text' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    expect(service.pending()).toBe(false);
    expect(service.messages()[1].status).toBe('error');
    expect(service.messages()[1].errorMessage).not.toContain('raw backend text');
  });

  it('settles to status "error" on a network-level failure with no response', () => {
    service.ask('A question');
    const req = httpMock.expectOne(CHAT_ENDPOINT);
    req.error(new ProgressEvent('error'));

    expect(service.pending()).toBe(false);
    expect(service.messages()[1].status).toBe('error');
    expect(service.messages()[1].errorMessage).toBeTruthy();
  });

  it('pending stays true indefinitely if the request never settles (no client-side timeout, FR-006)', () => {
    service.ask('A question');
    httpMock.expectOne(CHAT_ENDPOINT); // never flushed
    expect(service.pending()).toBe(true);
  });

  it('history stays in submission order across multiple questions (Story 1 Scenario 3)', () => {
    service.ask('First question');
    httpMock.expectOne(CHAT_ENDPOINT).flush({ answer: 'First answer', sources: [] });

    service.ask('Second question');
    httpMock.expectOne(CHAT_ENDPOINT).flush({ answer: 'Second answer', sources: [] });

    expect(service.messages().map((m) => m.text)).toEqual([
      'First question',
      'First answer',
      'Second question',
      'Second answer',
    ]);
  });
});

describe('ChatService trace collection (010-chat-trace-dialog data-model.md/contracts)', () => {
  let httpMock: HttpTestingController;
  let service: ChatService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChatService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('includeTrace() is true immediately after construction (FR-011)', () => {
    expect(service.includeTrace()).toBe(true);
  });

  it('setIncludeTrace() flips includeTrace() synchronously, in both directions', () => {
    service.setIncludeTrace(false);
    expect(service.includeTrace()).toBe(false);
    service.setIncludeTrace(true);
    expect(service.includeTrace()).toBe(true);
  });

  it('ask() sends includeTrace: true in the request body by default', () => {
    service.ask('A question');
    const req = httpMock.expectOne(CHAT_ENDPOINT);
    expect(req.request.body).toEqual({
      question: 'A question',
      documentIds: null,
      includeTrace: true,
    });
    req.flush({ answer: 'ok', sources: [] });
  });

  it('ask() sends includeTrace: false after setIncludeTrace(false)', () => {
    service.setIncludeTrace(false);
    service.ask('A question');
    const req = httpMock.expectOne(CHAT_ENDPOINT);
    expect(req.request.body).toEqual({
      question: 'A question',
      documentIds: null,
      includeTrace: false,
    });
    req.flush({ answer: 'ok', sources: [] });
  });

  it('settles a response with a trace array onto the assistant message unchanged, in order', () => {
    service.ask('A question');
    const trace = [
      { stage: 'request_received', durationMs: 1, detail: { question: 'A question' } },
      { stage: 'question_embedded', durationMs: 2, detail: { vectorDimensions: 1536 } },
    ];
    httpMock.expectOne(CHAT_ENDPOINT).flush({ answer: 'ok', sources: [], trace });

    expect(service.messages()[1].trace).toEqual(trace);
  });

  it('settles a response with no trace key with trace left undefined', () => {
    service.ask('A question');
    httpMock.expectOne(CHAT_ENDPOINT).flush({ answer: 'ok', sources: [] });

    expect(service.messages()[1].trace).toBeUndefined();
  });

  it('setIncludeTrace() after a message has settled does not alter that message\'s trace (FR-012)', () => {
    service.ask('A question');
    const trace = [{ stage: 'request_received', durationMs: 1, detail: {} }];
    httpMock.expectOne(CHAT_ENDPOINT).flush({ answer: 'ok', sources: [], trace });

    service.setIncludeTrace(false);
    service.setIncludeTrace(true);

    expect(service.messages()[1].trace).toEqual(trace);
  });
});

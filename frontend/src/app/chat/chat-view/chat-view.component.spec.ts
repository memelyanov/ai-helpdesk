import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChatViewComponent, LOADING_INDICATOR_DELAY_MS } from './chat-view.component';
import { ChatService, CHAT_ENDPOINT } from '../chat.service';

describe('ChatViewComponent (US1 container)', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [ChatViewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  function setup() {
    const fixture = TestBed.createComponent(ChatViewComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const input = () => el.querySelector('input') as HTMLInputElement;
    const sendBtn = () => el.querySelector('.send-btn') as HTMLButtonElement;
    function ask(question: string) {
      input().value = question;
      input().dispatchEvent(new Event('input'));
      fixture.detectChanges();
      sendBtn().click();
      fixture.detectChanges();
    }
    return { fixture, el, input, sendBtn, ask };
  }

  it('submitting a question while pending has no additional effect (FR-006)', () => {
    const { sendBtn, ask } = setup();
    ask('First question');
    httpMock.expectOne(CHAT_ENDPOINT); // left pending, not flushed

    // The send control is disabled while pending — a second click has no effect.
    expect(sendBtn().disabled).toBe(true);
    sendBtn().click();

    const chatService = TestBed.inject(ChatService);
    expect(chatService.messages().length).toBe(2); // one user + one pending assistant, never a third
    httpMock.expectNone(CHAT_ENDPOINT);
  });

  it('keeps history in submission order across multiple questions, with no citation bleed (Story 1 Scenario 3)', () => {
    const { fixture, el, ask } = setup();

    ask('First question');
    httpMock.expectOne(CHAT_ENDPOINT).flush({
      answer: 'First answer',
      sources: [{ documentId: 'd1', filename: 'a.pdf', page: '1', score: 0.9 }],
    });
    fixture.detectChanges();

    ask('Second question');
    httpMock.expectOne(CHAT_ENDPOINT).flush({ answer: 'Second answer', sources: [] });
    fixture.detectChanges();

    const bubbles = Array.from(el.querySelectorAll('.message'));
    expect(bubbles.map((b) => b.textContent?.trim())).toEqual([
      'First question',
      'First answer',
      'Second question',
      'Second answer',
    ]);
    // Only the first answer's message-group carries a citation badge.
    const badgeGroups = Array.from(el.querySelectorAll('.message-group')).filter(
      (g) => g.querySelectorAll('.source-badge').length > 0,
    );
    expect(badgeGroups.length).toBe(1);
  });

  it('keeps a failed question visible in history as its own entry (FR-007/FR-018)', () => {
    const { fixture, el, ask } = setup();
    ask('A question that fails');
    httpMock
      .expectOne(CHAT_ENDPOINT)
      .flush(
        { error: 'processing_failed', message: 'raw' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    fixture.detectChanges();

    expect(el.textContent).toContain('A question that fails');
    expect(el.querySelector('.message.error')).toBeTruthy();
  });

  it('does not show a loading indicator before LOADING_INDICATOR_DELAY_MS has elapsed (FR-006 anti-flash)', () => {
    const { el, ask } = setup();
    ask('A question');
    httpMock.expectOne(CHAT_ENDPOINT); // left pending

    expect(el.querySelector('.loading-indicator')).toBeFalsy();
  });

  it('shows the loading indicator once LOADING_INDICATOR_DELAY_MS has elapsed with the answer still pending', () => {
    const { fixture, el, ask } = setup();
    ask('A question');
    httpMock.expectOne(CHAT_ENDPOINT); // left pending

    vi.advanceTimersByTime(LOADING_INDICATOR_DELAY_MS);
    fixture.detectChanges();

    expect(el.querySelector('.loading-indicator')).toBeTruthy();
  });

  it('never shows the loading indicator if the response settles before the delay elapses', () => {
    const { fixture, el, ask } = setup();
    ask('A fast question');
    const req = httpMock.expectOne(CHAT_ENDPOINT);
    req.flush({ answer: 'Fast answer', sources: [] });
    fixture.detectChanges();

    vi.advanceTimersByTime(LOADING_INDICATOR_DELAY_MS);
    fixture.detectChanges();

    expect(el.querySelector('.loading-indicator')).toBeFalsy();
  });
});

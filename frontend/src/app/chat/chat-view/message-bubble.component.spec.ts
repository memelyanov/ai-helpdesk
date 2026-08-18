import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageBubbleComponent } from './message-bubble.component';
import { ChatMessage, ChatTraceStep } from '../chat-message';
import { DOCUMENTS_CONTENT_BASE } from '../../shared/file-download';

const TRACE: ChatTraceStep[] = [
  { stage: 'request_received', durationMs: 1, detail: { question: 'A question' } },
];

describe('MessageBubbleComponent (presentational, FR-002/FR-003/FR-007/FR-018)', () => {
  let httpMock: HttpTestingController;

  function render(message: ChatMessage) {
    const fixture = TestBed.createComponent(MessageBubbleComponent);
    fixture.componentRef.setInput('message', message);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MessageBubbleComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders a user message with distinct styling from an assistant message', () => {
    const { el: user } = render({
      id: '1',
      role: 'user',
      text: 'A question',
      citations: [],
      status: 'complete',
    });
    const { el: assistant } = render({
      id: '2',
      role: 'assistant',
      text: 'An answer',
      citations: [],
      status: 'complete',
    });

    const userBubble = user.querySelector('.message');
    const assistantBubble = assistant.querySelector('.message');
    expect(userBubble?.className).not.toBe(assistantBubble?.className);
  });

  it('renders citation badges with filename, page label, and rounded relevance percentage, in order', () => {
    const { el } = render({
      id: '1',
      role: 'assistant',
      text: 'Grounded answer',
      status: 'complete',
      citations: [
        { documentId: 'd1', filename: 'a.pdf', pageLabel: '3', scorePercent: 81, available: true },
        {
          documentId: 'd2',
          filename: 'b.txt',
          pageLabel: 'no page structure',
          scorePercent: 62,
          available: true,
        },
      ],
    });

    const badges = Array.from(el.querySelectorAll('.source-badge'));
    expect(badges.length).toBe(2);
    expect(badges[0].textContent).toContain('a.pdf');
    expect(badges[0].textContent).toContain('3');
    expect(badges[0].textContent).toContain('81');
    expect(badges[1].textContent).toContain('b.txt');
    expect(badges[1].textContent).toContain('no page structure');
    expect(badges[1].textContent).toContain('62');
  });

  it('renders the fixed "not covered" text with zero badges', () => {
    const { el } = render({
      id: '1',
      role: 'assistant',
      text: "I don't have this information in the documentation.",
      citations: [],
      status: 'complete',
    });

    expect(el.textContent).toContain("I don't have this information in the documentation.");
    expect(el.querySelectorAll('.source-badge').length).toBe(0);
  });

  it('renders errorMessage instead of text for an error-status message (FR-007)', () => {
    const { el } = render({
      id: '1',
      role: 'assistant',
      text: '',
      citations: [],
      status: 'error',
      errorMessage: 'Something went wrong. Please try again.',
    });

    expect(el.textContent).toContain('Something went wrong. Please try again.');
  });

  describe('citation badge download (User Story 4, FR-013/FR-014)', () => {
    const twoCitationMessage: ChatMessage = {
      id: '1',
      role: 'assistant',
      text: 'Grounded answer',
      status: 'complete',
      citations: [
        { documentId: 'd1', filename: 'a.pdf', pageLabel: '3', scorePercent: 81, available: true },
        { documentId: 'd2', filename: 'b.txt', pageLabel: '1', scorePercent: 70, available: true },
      ],
    };

    it("downloads the clicked citation's exact document on click", () => {
      const { el } = render(twoCitationMessage);
      const badges = el.querySelectorAll('.source-badge');
      (badges[0] as HTMLButtonElement).click();

      const req = httpMock.expectOne(`${DOCUMENTS_CONTENT_BASE}/d1/content`);
      req.flush(new Blob(['bytes']));
    });

    it('flips only the clicked badge to "unavailable" on a 404, leaving the other badge unaffected (FR-014)', async () => {
      const { el, fixture } = render(twoCitationMessage);
      const badges = el.querySelectorAll('.source-badge');
      (badges[0] as HTMLButtonElement).click();
      httpMock
        .expectOne(`${DOCUMENTS_CONTENT_BASE}/d1/content`)
        .flush(null, { status: 404, statusText: 'Not Found' });
      await Promise.resolve();
      await Promise.resolve();
      fixture.detectChanges();

      const updatedBadges = el.querySelectorAll('.source-badge');
      expect(updatedBadges[0].className).toContain('source-badge--unavailable');
      expect(updatedBadges[0].textContent?.toLowerCase()).toContain('no longer available');
      expect(updatedBadges[1].className).not.toContain('source-badge--unavailable');
    });
  });

  describe('trace control (010-chat-trace-dialog FR-001/FR-002/FR-013)', () => {
    it('renders a trace control when the message has a non-empty trace', () => {
      const { el } = render({
        id: '1',
        role: 'assistant',
        text: 'An answer',
        citations: [],
        status: 'complete',
        trace: TRACE,
      });

      const control = el.querySelector('.trace-control');
      expect(control).toBeTruthy();
      expect(control?.textContent?.toLowerCase()).toContain('trace');
    });

    it('renders no trace control when trace is undefined', () => {
      const { el } = render({
        id: '1',
        role: 'assistant',
        text: 'An answer',
        citations: [],
        status: 'complete',
      });
      expect(el.querySelector('.trace-control')).toBeFalsy();
    });

    it('renders no trace control when trace is an empty array', () => {
      const { el } = render({
        id: '1',
        role: 'assistant',
        text: 'An answer',
        citations: [],
        status: 'complete',
        trace: [],
      });
      expect(el.querySelector('.trace-control')).toBeFalsy();
    });

    it('renders no trace control for a pending message, even with a trace field', () => {
      const { el } = render({
        id: '1',
        role: 'assistant',
        text: '',
        citations: [],
        status: 'pending',
        trace: TRACE,
      });
      expect(el.querySelector('.trace-control')).toBeFalsy();
    });

    it('renders no trace control for an error message, even with a trace field', () => {
      const { el } = render({
        id: '1',
        role: 'assistant',
        text: '',
        citations: [],
        status: 'error',
        errorMessage: 'Something went wrong.',
        trace: TRACE,
      });
      expect(el.querySelector('.trace-control')).toBeFalsy();
    });

    it('opens the trace dialog on click, and closing it returns to closed', () => {
      const { el, fixture } = render({
        id: '1',
        role: 'assistant',
        text: 'An answer',
        citations: [],
        status: 'complete',
        trace: TRACE,
      });

      (el.querySelector('.trace-control') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(el.querySelector('dialog')?.hasAttribute('open')).toBe(true);

      (el.querySelector('.trace-dialog__close') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(el.querySelector('dialog')?.hasAttribute('open')).toBe(false);
    });

    it('keeps two different messages\' dialogs independent — opening one never closes the other (FR-013)', () => {
      const first = render({
        id: '1',
        role: 'assistant',
        text: 'First answer',
        citations: [],
        status: 'complete',
        trace: TRACE,
      });
      const second = render({
        id: '2',
        role: 'assistant',
        text: 'Second answer',
        citations: [],
        status: 'complete',
        trace: TRACE,
      });

      (first.el.querySelector('.trace-control') as HTMLButtonElement).click();
      first.fixture.detectChanges();
      (second.el.querySelector('.trace-control') as HTMLButtonElement).click();
      second.fixture.detectChanges();

      expect(first.el.querySelector('dialog')?.hasAttribute('open')).toBe(true);
      expect(second.el.querySelector('dialog')?.hasAttribute('open')).toBe(true);
    });
  });
});

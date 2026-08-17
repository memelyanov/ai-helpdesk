import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageBubbleComponent } from './message-bubble.component';
import { ChatMessage } from '../chat-message';
import { DOCUMENTS_CONTENT_BASE } from '../../shared/file-download';

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
});

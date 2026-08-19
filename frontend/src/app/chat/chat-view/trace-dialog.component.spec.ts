import { TestBed } from '@angular/core/testing';
import { TraceDialogComponent } from './trace-dialog.component';
import { ChatTraceStep } from '../chat-message';

const FULL_TRACE: ChatTraceStep[] = [
  {
    stage: 'request_received',
    durationMs: 1,
    detail: { question: 'What is the refund policy?', documentIds: [] },
  },
  {
    stage: 'question_embedded',
    durationMs: 12,
    detail: { vectorDimensions: 1536 },
  },
  {
    stage: 'vector_search_completed',
    durationMs: 34,
    detail: {
      candidateCount: 1,
      candidates: [
        {
          documentId: 'doc-1',
          chunkId: 'chunk-1',
          sourceFilename: 'policy.pdf',
          page: '2',
          text: 'Refunds are available within 30 days of purchase.',
          distance: 0.19,
          similarity: 0.81,
        },
      ],
    },
  },
  {
    stage: 'results_filtered',
    durationMs: 2,
    detail: {
      survivorCount: 1,
      discardedCount: 0,
      threshold: 0.5,
      survivors: [
        {
          documentId: 'doc-1',
          chunkId: 'chunk-1',
          sourceFilename: 'policy.pdf',
          page: '2',
          text: 'Refunds are available within 30 days of purchase.',
          distance: 0.19,
          similarity: 0.81,
        },
      ],
    },
  },
  {
    stage: 'prompt_assembled',
    durationMs: 3,
    detail: {
      systemPrompt: 'You are a helpful assistant that answers only from the provided context.',
      prompt: 'Context:\nRefunds are available within 30 days of purchase.\n\nQuestion: What is the refund policy?',
      passageCount: 1,
    },
  },
  {
    stage: 'model_response_received',
    durationMs: 890,
    detail: {
      rawResponse: 'Refunds are available within 30 days of purchase.',
      completionLength: 50,
      outcome: 'answered',
    },
  },
];

describe('TraceDialogComponent (010-chat-trace-dialog data-model.md/contracts)', () => {
  function render(steps: ChatTraceStep[], open = true) {
    const fixture = TestBed.createComponent(TraceDialogComponent);
    fixture.componentRef.setInput('steps', steps);
    fixture.componentRef.setInput('open', open);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TraceDialogComponent],
    });
  });

  it('renders all six stages, in KNOWN_STAGES order, each with every documented detail field', () => {
    const { el } = render(FULL_TRACE);
    const text = el.textContent ?? '';

    // request_received
    expect(text).toContain('What is the refund policy?');
    // question_embedded
    expect(text).toContain('1536');
    // vector_search_completed
    expect(text).toContain('policy.pdf');
    expect(text).toContain('Refunds are available within 30 days of purchase.');
    // results_filtered
    expect(text).toContain('0.5');
    // prompt_assembled
    expect(text).toContain('You are a helpful assistant that answers only from the provided context.');
    expect(text).toContain(
      'Context:\nRefunds are available within 30 days of purchase.\n\nQuestion: What is the refund policy?',
    );
    // model_response_received
    expect(text).toContain('answered');

    // Order: request_received's stage heading appears before model_response_received's.
    const sections = Array.from(el.querySelectorAll('[data-stage]'));
    expect(sections.map((s) => s.getAttribute('data-stage'))).toEqual([
      'request_received',
      'question_embedded',
      'vector_search_completed',
      'results_filtered',
      'prompt_assembled',
      'model_response_received',
    ]);
  });

  it('emits closed exactly once when the close button is clicked', () => {
    const { fixture, el } = render(FULL_TRACE);
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    (el.querySelector('.trace-dialog__close') as HTMLButtonElement).click();

    expect(closed).toHaveBeenCalledTimes(1);
  });

  it('emits closed exactly once when the dialog element itself is clicked (backdrop click fallback, research Decision 1)', () => {
    const { fixture, el } = render(FULL_TRACE);
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    (el.querySelector('dialog') as HTMLDialogElement).dispatchEvent(
      new MouseEvent('click', { bubbles: true }),
    );

    expect(closed).toHaveBeenCalledTimes(1);
  });

  it('does not emit closed when a click inside the content container is dispatched (bubbled, not the dialog itself)', () => {
    const { fixture, el } = render(FULL_TRACE);
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    const content = el.querySelector('.trace-dialog__content') as HTMLElement;
    content.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    expect(closed).not.toHaveBeenCalled();
  });

  it('never emits closed more than once per dismissal (no double emission)', () => {
    const { fixture, el } = render(FULL_TRACE);
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);

    const closeBtn = el.querySelector('.trace-dialog__close') as HTMLButtonElement;
    closeBtn.click();
    closeBtn.click();
    closeBtn.click();

    expect(closed).toHaveBeenCalledTimes(3);
  });

  describe('copy affordance (FR-009, research Decision 6)', () => {
    let writeText: ReturnType<typeof vi.fn>;

    beforeEach(() => {
      writeText = vi.fn().mockResolvedValue(undefined);
      Object.defineProperty(navigator, 'clipboard', {
        value: { writeText },
        configurable: true,
      });
    });

    it('copies the exact assembled prompt text and nothing else', async () => {
      const { el } = render(FULL_TRACE);
      const button = el.querySelector('[data-copy="prompt"]') as HTMLButtonElement;
      button.click();
      await Promise.resolve();

      expect(writeText).toHaveBeenCalledWith(FULL_TRACE[4].detail['prompt']);
      expect(writeText).toHaveBeenCalledTimes(1);
    });

    it('copies the exact raw response text and nothing else', async () => {
      const { el } = render(FULL_TRACE);
      const button = el.querySelector('[data-copy="response"]') as HTMLButtonElement;
      button.click();
      await Promise.resolve();

      expect(writeText).toHaveBeenCalledWith(FULL_TRACE[5].detail['rawResponse']);
      expect(writeText).toHaveBeenCalledTimes(1);
    });

    it('shows a "Copied" confirmation after a successful copy, and it is not permanently stuck (transient signal)', async () => {
      const { fixture, el } = render(FULL_TRACE);
      expect(fixture.componentInstance.copyFeedback()).toBeNull();

      const button = el.querySelector('[data-copy="prompt"]') as HTMLButtonElement;
      button.click();
      await Promise.resolve();

      expect(fixture.componentInstance.copyFeedback()).toBe('prompt');
    });

    it('throws no error and shows no confirmation when navigator.clipboard is absent', async () => {
      Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
      const { fixture, el } = render(FULL_TRACE);
      const button = el.querySelector('[data-copy="prompt"]') as HTMLButtonElement;

      expect(() => button.click()).not.toThrow();
      await Promise.resolve();

      expect(fixture.componentInstance.copyFeedback()).toBeNull();
    });
  });

  it('carries a non-empty accessible name on the dialog element', () => {
    const { el } = render(FULL_TRACE);
    const dialog = el.querySelector('dialog') as HTMLDialogElement;
    expect(dialog.getAttribute('aria-label')?.length).toBeGreaterThan(0);
  });

  it('is read-only: renders no control beyond close and copy (FR-007)', () => {
    const { el } = render(FULL_TRACE);
    const buttons = Array.from(el.querySelectorAll('button'));
    for (const button of buttons) {
      const isClose = button.classList.contains('trace-dialog__close');
      const isCopy = button.hasAttribute('data-copy');
      expect(isClose || isCopy).toBe(true);
    }
    expect(el.querySelectorAll('input, textarea, select').length).toBe(0);
  });

  describe('short-circuited trace (010-chat-trace-dialog User Story 2, FR-005/SC-003)', () => {
    const SHORT_TRACE: ChatTraceStep[] = [
      FULL_TRACE[0],
      FULL_TRACE[1],
      {
        stage: 'vector_search_completed',
        durationMs: 20,
        detail: { candidateCount: 0, candidates: [] },
      },
      {
        stage: 'results_filtered',
        durationMs: 1,
        detail: { survivorCount: 0, discardedCount: 0, threshold: 0.5, survivors: [] },
      },
    ];

    it('renders "Not reached" with a non-empty reason for prompt_assembled and model_response_received', () => {
      const { el } = render(SHORT_TRACE);

      const promptSection = el.querySelector('[data-stage="prompt_assembled"]') as HTMLElement;
      const responseSection = el.querySelector(
        '[data-stage="model_response_received"]',
      ) as HTMLElement;

      expect(promptSection.textContent).toContain('Not reached');
      expect(responseSection.textContent).toContain('Not reached');
      expect(
        promptSection.querySelector('.trace-not-reached-reason')?.textContent?.trim().length,
      ).toBeGreaterThan(0);
      expect(
        responseSection.querySelector('.trace-not-reached-reason')?.textContent?.trim().length,
      ).toBeGreaterThan(0);
    });

    it('gives the not-reached sections a distinct class the ran sections never carry, and vice versa', () => {
      const { el } = render(SHORT_TRACE);

      const notReached = el.querySelectorAll('.trace-stage--not-reached');
      expect(notReached.length).toBe(2);
      for (const section of Array.from(notReached)) {
        expect(section.getAttribute('data-stage')).toMatch(
          /^(prompt_assembled|model_response_received)$/,
        );
      }

      const ranSections = Array.from(el.querySelectorAll('.trace-stage')).filter(
        (s) => !s.classList.contains('trace-stage--not-reached'),
      );
      expect(ranSections.length).toBe(4);
    });

    it('renders zero "Not reached" labels for a full six-entry trace (regression guard)', () => {
      const { el } = render(FULL_TRACE);
      expect(el.querySelectorAll('.trace-stage--not-reached').length).toBe(0);
      expect(el.textContent).not.toContain('Not reached');
    });
  });
});

import {
  Component,
  ElementRef,
  EventEmitter,
  Output,
  WritableSignal,
  computed,
  effect,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { ChatTraceStep } from '../chat-message';

/** The six fixed stage values, in execution order (research.md Decision 5). */
const KNOWN_STAGES = [
  'request_received',
  'question_embedded',
  'vector_search_completed',
  'results_filtered',
  'prompt_assembled',
  'model_response_received',
] as const;

/** Human-readable heading per stage (data-model.md `TraceDialogComponent` view-model, C1 fix) —
 * the raw wire values above are never shown to the user directly. */
const STAGE_LABELS: Readonly<Record<string, string>> = {
  request_received: 'Question received',
  question_embedded: 'Question embedded',
  vector_search_completed: 'Passages retrieved',
  results_filtered: 'Passages filtered',
  prompt_assembled: 'Prompt assembled',
  model_response_received: 'Model response',
};

export type DisplayedStage =
  | { ran: true; stage: string; step: ChatTraceStep }
  | { ran: false; stage: string };

/**
 * Read-only diagnostic trace viewer (data-model.md/contracts/frontend-trace-contract.md). Renders
 * every one of the six known stages — ran, with full verbatim detail, or explicitly "Not reached" —
 * derived positionally from `steps` (research Decision 5). Every dismissal route (close button,
 * backdrop click, native Escape) funnels through {@link requestClose}, which prefers the native
 * `<dialog>` `.close()` and falls back to a direct `closed` emission when unavailable — this repo's
 * pinned jsdom has neither `showModal` nor `close` (research Decision 1), so these tests exercise the
 * fallback path; a real browser gets native focus management, Escape-to-close, and top-layer stacking
 * for free (FR-006/FR-016).
 */
@Component({
  selector: 'app-trace-dialog',
  imports: [],
  templateUrl: './trace-dialog.component.html',
  styleUrl: './trace-dialog.component.css',
})
export class TraceDialogComponent {
  readonly steps = input.required<ChatTraceStep[]>();
  readonly open = input(false);

  @Output() readonly closed = new EventEmitter<void>();

  readonly STAGE_LABELS = STAGE_LABELS;

  private readonly dialogRef = viewChild<ElementRef<HTMLDialogElement>>('dialogEl');

  /** Transient UI-only state — which copy button was just used, cleared after a short delay
   * (research Decision 6). Never set on failure or when the Clipboard API is absent. */
  readonly copyFeedback: WritableSignal<'prompt' | 'response' | null> = signal(null);

  readonly displayedStages = computed<DisplayedStage[]>(() => {
    const steps = this.steps();
    return KNOWN_STAGES.map((stage, i) =>
      i < steps.length ? { ran: true, stage, step: steps[i] } : { ran: false, stage },
    );
  });

  /** Non-empty accessible name identifying which response's trace this dialog shows (FR-016). */
  readonly dialogLabel = computed(() => {
    const question = this.steps().find((s) => s.stage === 'request_received')?.detail?.[
      'question'
    ];
    return typeof question === 'string' && question.length > 0
      ? `Diagnostic trace: ${question}`
      : 'Diagnostic trace';
  });

  constructor() {
    effect(() => {
      const isOpen = this.open();
      const dialog = this.dialogRef()?.nativeElement;
      if (!dialog) {
        return;
      }
      if (isOpen) {
        if (typeof dialog.showModal === 'function' && !dialog.open) {
          dialog.showModal();
        }
      } else if (typeof dialog.close === 'function' && dialog.open) {
        dialog.close();
      }
    });
  }

  /** The sole path every dismissal route (button/backdrop/Escape) funnels through (research
   * Decision 1). Native `.close()` triggers the `(close)` DOM listener, which is the only other
   * place `closed` is emitted — so there is exactly one emission per dismissal either way. */
  requestClose(): void {
    const dialog = this.dialogRef()?.nativeElement;
    if (dialog && typeof dialog.close === 'function') {
      dialog.close();
    } else {
      this.closed.emit();
    }
  }

  /** A click lands here whenever it bubbles up through the `<dialog>` element itself. Only a click
   * whose original target *is* the dialog element (the true backdrop, not its content) closes it —
   * a click starting inside `.trace-dialog__content` still bubbles here but keeps its own target,
   * so it is correctly ignored (FR-006's "padding/border count as the dialog" rule). */
  onBackdropClick(event: MouseEvent): void {
    if (event.target === this.dialogRef()?.nativeElement) {
      this.requestClose();
    }
  }

  copy(kind: 'prompt' | 'response', text: unknown): void {
    if (typeof text !== 'string') {
      return;
    }
    const clipboard = navigator.clipboard;
    if (!clipboard) {
      return;
    }
    clipboard.writeText(text).then(
      () => {
        this.copyFeedback.set(kind);
        setTimeout(() => {
          if (this.copyFeedback() === kind) {
            this.copyFeedback.set(null);
          }
        }, 2000);
      },
      () => {
        // Silent no-op on rejection (research Decision 6) — no error surfaced, no confirmation shown.
      },
    );
  }

  /** Reason shown next to a not-reached stage's "Not reached" label. Refined in 010's User Story 2
   * (T014) to derive a specific reason from the last ran stage where possible; a generic fallback
   * covers every other case so no not-reached entry is ever left unexplained (FR-005). */
  notReachedReason(displayedStages: DisplayedStage[]): string {
    const lastRan = [...displayedStages].reverse().find((s) => s.ran) as
      | { ran: true; stage: string; step: ChatTraceStep }
      | undefined;
    if (lastRan?.stage === 'results_filtered' && lastRan.step.detail['survivorCount'] === 0) {
      return 'No passage was similar enough, so the model was never called.';
    }
    return 'This stage did not run because the pipeline stopped earlier.';
  }
}

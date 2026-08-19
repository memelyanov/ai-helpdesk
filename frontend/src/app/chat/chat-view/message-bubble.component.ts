import { Component, WritableSignal, inject, input, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ChatMessage, Citation } from '../chat-message';
import { downloadDocument } from '../../shared/file-download';
import { TraceDialogComponent } from './trace-dialog.component';

function citationKey(citation: Citation): string {
  return `${citation.documentId}::${citation.pageLabel}`;
}

/**
 * Presentational — one {@link ChatMessage} (plan.md). Renders user vs. assistant with distinct
 * styling (FR-018), an assistant's citation badges in the given order with filename/page/relevance
 * (FR-002), the fixed "not covered" text with zero badges (FR-003), and `errorMessage` in place of
 * `text` for a failed question (FR-007). A citation badge click downloads that source directly
 * (User Story 4, FR-013) via {@link downloadDocument} — a 404 flips only that one badge to a
 * persistent "no longer available" state (FR-014, Story 4 Scenario 3) without affecting any other
 * badge in this or any other message, tracked locally since `documentId`+`pageLabel` uniquely
 * identifies a badge within one message. A message whose data includes a non-empty diagnostic
 * trace (010-chat-trace-dialog FR-001/FR-002) also gets a "View diagnostic trace" control beneath
 * its sources, opening its own {@link TraceDialogComponent} instance — each message's dialog-open
 * state is local to that message, so two messages' dialogs never interfere (FR-013).
 */
@Component({
  selector: 'app-message-bubble',
  imports: [TraceDialogComponent],
  templateUrl: './message-bubble.component.html',
  styleUrl: './message-bubble.component.css',
})
export class MessageBubbleComponent {
  private readonly http = inject(HttpClient);

  readonly message = input.required<ChatMessage>();

  private readonly _unavailable: WritableSignal<ReadonlySet<string>> = signal(new Set());
  private readonly _traceDialogOpen: WritableSignal<boolean> = signal(false);

  readonly traceDialogOpen = this._traceDialogOpen.asReadonly();

  isUnavailable(citation: Citation): boolean {
    return this._unavailable().has(citationKey(citation));
  }

  hasTrace(): boolean {
    return (this.message().trace?.length ?? 0) > 0;
  }

  openTraceDialog(): void {
    this._traceDialogOpen.set(true);
  }

  onTraceDialogClosed(): void {
    this._traceDialogOpen.set(false);
  }

  async onCitationClick(citation: Citation): Promise<void> {
    if (this.isUnavailable(citation)) {
      return;
    }
    const result = await downloadDocument(this.http, citation.documentId, citation.filename);
    if (!result.ok && result.unavailable) {
      const next = new Set(this._unavailable());
      next.add(citationKey(citation));
      this._unavailable.set(next);
    }
  }
}

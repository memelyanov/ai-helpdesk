import { Component, EventEmitter, Output, input, signal, computed } from '@angular/core';
import { MAX_QUESTION_LENGTH } from '../chat.service';

/**
 * Presentational input control (plan.md — no direct `ChatService` dependency, so this and
 * `message-bubble.component` can be built/tested in parallel with the service, per tasks.md). Owns
 * only its own trimmed-length validation (FR-004/FR-005, mirroring the backend's
 * `MAX_QUESTION_LENGTH`, research.md Decision 6) and emits `submitQuestion` — the container
 * (`chat-view.component`) is what actually calls `ChatService.ask()`.
 */
@Component({
  selector: 'app-chat-input',
  imports: [],
  templateUrl: './chat-input.component.html',
  styleUrl: './chat-input.component.css',
})
export class ChatInputComponent {
  /** FR-006: disables both the input and the send control immediately, with no added delay. */
  readonly pending = input(false);

  @Output() readonly submitQuestion = new EventEmitter<string>();

  readonly maxLength = MAX_QUESTION_LENGTH;
  readonly value = signal('');

  readonly trimmedLength = computed(() => this.value().trim().length);
  readonly overLimit = computed(() => this.trimmedLength() >= this.maxLength);
  readonly canSubmit = computed(
    () => this.trimmedLength() > 0 && !this.overLimit() && !this.pending(),
  );

  onInput(newValue: string): void {
    this.value.set(newValue);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.submit();
    }
  }

  submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.submitQuestion.emit(this.value().trim());
    this.value.set('');
  }
}

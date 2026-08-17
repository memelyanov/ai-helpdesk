import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { ChatService } from '../chat.service';
import { ChatInputComponent } from './chat-input.component';
import { MessageBubbleComponent } from './message-bubble.component';

/** research.md Decision 9 — fixed anti-flash delay before the loading indicator appears. */
export const LOADING_INDICATOR_DELAY_MS = 300;

/**
 * User Story 1's container: injects {@link ChatService} and composes `app-chat-input` with a list
 * of `app-message-bubble` (plan.md). Owns Decision 9's anti-flash timer — `pending` itself still
 * drives `chat-input`'s disabling immediately (FR-006), but the *visible* loading indicator here is
 * deliberately delayed by {@link LOADING_INDICATOR_DELAY_MS} so a fast-settling answer never
 * flashes it, and the timer is always cancelled (never left running) once `pending` goes back to
 * `false` or this component is destroyed.
 */
@Component({
  selector: 'app-chat-view',
  imports: [ChatInputComponent, MessageBubbleComponent],
  templateUrl: './chat-view.component.html',
  styleUrl: './chat-view.component.css',
})
export class ChatViewComponent {
  private readonly chatService = inject(ChatService);
  private readonly destroyRef = inject(DestroyRef);

  readonly messages = this.chatService.messages;
  readonly pending = this.chatService.pending;

  private readonly _showLoading = signal(false);
  readonly showLoading = this._showLoading.asReadonly();

  private timeoutId: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    effect(() => {
      const isPending = this.pending();
      this.clearTimer();
      if (isPending) {
        this.timeoutId = setTimeout(() => this._showLoading.set(true), LOADING_INDICATOR_DELAY_MS);
      } else {
        this._showLoading.set(false);
      }
    });
    this.destroyRef.onDestroy(() => this.clearTimer());
  }

  onSubmit(question: string): void {
    this.chatService.ask(question);
  }

  private clearTimer(): void {
    if (this.timeoutId !== undefined) {
      clearTimeout(this.timeoutId);
      this.timeoutId = undefined;
    }
  }
}

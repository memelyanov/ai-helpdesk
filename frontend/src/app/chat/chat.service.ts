import { Injectable, Signal, WritableSignal, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { mapChatError } from '../shared/api-error';
import { ChatMessage, ChatRequestBody, ChatResponse, mapSourcesToCitations } from './chat-message';

/** Fixed local address (plan.md Technical Context — no deployment target yet). */
export const CHAT_ENDPOINT = 'http://localhost:8080/chat';

/**
 * Mirrors 007-chat-endpoint's `ChatService.MAX_QUESTION_LENGTH` server-side constant so the
 * frontend can validate before sending (FR-005, research.md Decision 6).
 */
export const MAX_QUESTION_LENGTH = 1000;

function newId(): string {
  return crypto.randomUUID();
}

/**
 * Holds the current page load's conversation and drives `POST /chat` (data-model.md
 * `ChatService`, contracts/frontend-service-contract.md). Every failure path — network, 400, 503 —
 * resolves into the pending assistant message's `status: 'error'`/`errorMessage`, never an
 * unhandled rejection (FR-007). `documentIds` is always `null` (FR-020) — there is no UI state
 * anywhere in this feature that could populate it otherwise.
 */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  private readonly _messages: WritableSignal<ChatMessage[]> = signal([]);
  private readonly _pending: WritableSignal<boolean> = signal(false);
  /** On by default (FR-011, data-model.md `ChatService` state) — read fresh on every `ask()` call,
   * never "locked in" for the rest of a session (research.md Decision 2). */
  private readonly _includeTrace: WritableSignal<boolean> = signal(true);

  readonly messages: Signal<ChatMessage[]> = this._messages.asReadonly();
  readonly pending: Signal<boolean> = this._pending.asReadonly();
  readonly includeTrace: Signal<boolean> = this._includeTrace.asReadonly();

  /** Turns diagnostic trace collection off (or back on) for messages sent from this point forward.
   * Never touches `messages` — an already-settled `ChatMessage.trace` is unaffected (FR-012). */
  setIncludeTrace(value: boolean): void {
    this._includeTrace.set(value);
  }

  /**
   * No-ops (no state change, no HTTP call) for a blank/whitespace-only or over-length question
   * (FR-004/FR-005) — the input component is expected to have already blocked this, but the
   * service itself never sends an invalid request either way (contracts/frontend-service-contract.md).
   */
  ask(question: string): void {
    const trimmed = question.trim();
    if (trimmed.length === 0 || trimmed.length > MAX_QUESTION_LENGTH) {
      return;
    }

    const userMessage: ChatMessage = {
      id: newId(),
      role: 'user',
      text: trimmed,
      citations: [],
      status: 'complete',
    };
    const assistantMessage: ChatMessage = {
      id: newId(),
      role: 'assistant',
      text: '',
      citations: [],
      status: 'pending',
    };
    this._messages.set([...this._messages(), userMessage, assistantMessage]);
    this._pending.set(true);

    const body: ChatRequestBody = {
      question: trimmed,
      documentIds: null,
      includeTrace: this.includeTrace(),
    };
    this.http.post<ChatResponse>(CHAT_ENDPOINT, body).subscribe({
      next: (response) => this.settle(assistantMessage.id, response),
      error: (error: unknown) => this.settleError(assistantMessage.id, error),
    });
  }

  private settle(messageId: string, response: ChatResponse): void {
    this.updateMessage(messageId, {
      status: 'complete',
      text: response.answer,
      citations: mapSourcesToCitations(response.sources),
      trace: response.trace,
    });
    this._pending.set(false);
  }

  private settleError(messageId: string, error: unknown): void {
    const code =
      error instanceof HttpErrorResponse && error.status !== 0
        ? ((error.error as { error?: string } | null)?.error ?? null)
        : null;
    this.updateMessage(messageId, {
      status: 'error',
      errorMessage: mapChatError(code),
    });
    this._pending.set(false);
  }

  private updateMessage(messageId: string, patch: Partial<ChatMessage>): void {
    this._messages.set(this._messages().map((m) => (m.id === messageId ? { ...m, ...patch } : m)));
  }
}

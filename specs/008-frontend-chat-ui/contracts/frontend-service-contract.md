# Contract: Frontend service surface (`ChatService`, `DocumentsService`)

**Feature**: [Frontend Chat UI](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

This feature is purely a *consumer* of already-shipped backend contracts
([chat-api-contract.md](../../007-chat-endpoint/contracts/chat-api-contract.md),
[ingestion-api-contract.md](../../004-document-ingestion-endpoint/contracts/ingestion-api-contract.md),
[document-query-api-contract.md](../../005-document-listing-download/contracts/document-query-api-contract.md),
[document-delete-api-contract.md](../../006-document-delete/contracts/document-delete-api-contract.md))
— it defines no new HTTP surface. What it does introduce is a new internal contract: the two
Angular services every chat/sidebar component is built against. This document is what a component
author — today, or in a later feature — can rely on from `ChatService` and `DocumentsService`
without reading their implementations, mirroring how the REST contracts above let a caller avoid
reading controller source.

## `ChatService`

| Member | Type | Guarantee |
|---|---|---|
| `messages` | `Signal<ChatMessage[]>` | Reflects every `ask()` call made so far this page load, oldest first (FR-018). Never shrinks except on a full page reload (FR-019) — no method removes an entry. |
| `pending` | `Signal<boolean>` | `true` from the moment `ask()` accepts a question until that question's `ChatMessage` reaches `status: 'complete'` or `status: 'error'` (FR-006). A consumer MUST treat `pending` as the single source of truth for disabling its own send control, and MUST do so immediately, with no added delay — it MUST NOT derive a separate "is loading" flag by inspecting `messages` directly. A consumer's *visible loading indicator* is a distinct concern: it MAY (and per research Decision 9, `chat-view.component` does) delay its own appearance by a fixed interval after `pending` becomes `true`, so a fast-settling response never flashes it — that delay MUST NOT be applied to the disabling behavior above. |
| `ask(question: string): void` | method | Idempotently ignored (no state change, no HTTP call) if `question.trim()` is empty or exceeds 1000 characters (FR-004/FR-005). Otherwise: synchronously appends a `role: 'user'` message and a `role: 'assistant', status: 'pending'` message, then asynchronously settles the latter via `POST /chat`. **Never** throws — every failure path (network, `400`, `503`) resolves into the pending message's `status: 'error'` + `errorMessage`, never an unhandled promise/observable rejection a caller would need its own `try`/`catch` for. |

Consumer obligation: a component calling `ask()` is responsible for its own input-level validation
UI (FR-005's "indicate this before the request is sent") — `ChatService.ask()`'s silent no-op on
invalid input is a safety net, not a substitute for that UI-level check.

## `DocumentsService`

| Member | Type | Guarantee |
|---|---|---|
| `documents` | `Signal<DocumentSummary[]>` | Always the service's best-known full mirror of `GET /documents`'s current response — never a partial page, never optimistically patched ahead of a server-confirmed `upload()`/`remove()` outcome (FR-016/FR-017). Ordering matches `GET /documents`'s own (most-recently-uploaded first). |
| `loaded` | `Signal<boolean>` | `false` until the first `refresh()` call (issued at construction) settles, then permanently `true` for the rest of the page load (FR-008). A consumer MUST use this — not `documents().length === 0` — to distinguish "not yet loaded" from "confirmed empty" (FR-009); the two look different only via this signal. |
| `uploading` | `Signal<boolean>` | `true` for exactly the duration of one in-flight `upload()` call; a consumer MUST gate its upload control on this to satisfy FR-012 — `DocumentsService` itself does not reject a second concurrent `upload()` call, so the calling component is responsible for not issuing one. |
| `uploadError` | `Signal<string \| null>` | The most recent upload failure's mapped message (research Decision 5), or `null` if the most recent attempt succeeded or none has been made yet. Reset to `null` at the start of every new `upload()` call. |
| `upload(file: File): void` | method | On success, `documents` gains the new entry and `uploadError` becomes `null`. On failure, `documents` is unchanged and `uploadError` is set. Never throws. |
| `remove(documentId: string): Promise<{ ok: true } \| { ok: false; message: string }>` | method | The one method on either service that returns a settled result to its caller directly, rather than only updating a signal — because Decision 4's confirmation UI (a single container-level `confirmingDocumentId`, data-model.md) needs to know success/failure to decide whether to clear that state or show a row-local error, and that's UI state `DocumentsService` deliberately does not own. On success, `documents` no longer contains `documentId` (FR-016). On failure, `documents` is unchanged (FR-017) and the returned `message` is one of Decision 5's fixed strings. |
| `refresh(): void` | method | Re-fetches `GET /documents` and replaces `documents` wholesale; used once at service construction (FR-008) and available for any future caller that needs to force a resync. |

## Download (not a service method — a shared helper, research Decision 3)

| Function | Signature | Guarantee |
|---|---|---|
| `downloadDocument` | `(http: HttpClient, documentId: string, suggestedFilename: string) => Promise<{ ok: true } \| { ok: false; unavailable: boolean }>` | Issues `GET /documents/{id}/content` as a blob request and triggers a browser save using `suggestedFilename` (never a filename parsed from the response). `unavailable: true` distinguishes a `404 document_not_found` (FR-014 — "no longer available" messaging) from any other failure, so a citation badge (Story 4, Scenario 3) and a sidebar row (Story 4, Scenario 1) can both react correctly without duplicating that distinction themselves. |

## Non-guarantees (explicitly out of scope)

- **No shared "global error" signal** across `ChatService`/`DocumentsService` — each surfaces its own
  failures independently, since the spec's failure-handling requirements (FR-007, FR-011, FR-014,
  FR-017) are each scoped to their own flow, never to one another.
- **No retry/backoff built into either service** — every retry in this feature is a fresh user
  action (re-clicking send, re-attempting an upload, re-confirming a delete), never automatic
  (spec.md Edge Cases: the user "can immediately try again," not "the system retries for them").
- **No `documentIds` parameter on `ChatService.ask()`** — FR-020 fixes this feature's scope; a
  future feature reintroducing document-scoped filtering would extend this contract, not this one.

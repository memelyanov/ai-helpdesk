# Contract: Frontend trace surface (`ChatService` extensions, `TraceDialogComponent`, `TraceToggleComponent`)

**Feature**: [Chat Trace Dialog](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

This feature is purely a *consumer* of an already-shipped backend contract
([chat-diagnostic-trace-contract.md](../../009-chat-diagnostic-trace/contracts/chat-diagnostic-trace-contract.md))
— it defines no new HTTP surface. What it introduces is new internal contract: the `ChatService`
extensions and two new components every future trace-related change should be built against, the same
role [008's `frontend-service-contract.md`](../../008-frontend-chat-ui/contracts/frontend-service-contract.md)
already plays for `ChatService`'s original members.

## `ChatService` (extensions only — see 008's contract for `messages`/`pending`/`ask()`'s existing guarantees)

| Member | Type | Guarantee |
|---|---|---|
| `includeTrace` | `Signal<boolean>` | `true` immediately from service construction (FR-011) — no message is ever sent before this signal has a value. Changes only in response to `setIncludeTrace()`; never flips itself (e.g. never auto-disables after some number of messages). |
| `setIncludeTrace(value: boolean): void` | method | Synchronous, always succeeds, never throws. Has no effect on `messages` — a `ChatMessage` already in the list keeps whatever `trace` it was given at settle time, regardless of how many times this is called afterward (FR-012). |
| `ask(question: string): void` | method (behavior extended) | Same no-op/validation contract as 008. The `POST /chat` body it sends now always includes `includeTrace: <current includeTrace() value at call time>`, explicitly (`true` or `false`), never omitted. The settled `ChatMessage`'s `trace` field is exactly `response.trace` (`undefined` when the response didn't include the key) — never inferred from `includeTrace`, since a request can be sent with `includeTrace: true` and still, in principle, receive a response without a body-level `trace` key (e.g. an error response short-circuits before any trace exists) — the response is always the single source of truth for whether a settled message has trace data. |

## `TraceDialogComponent`

| Member | Type | Guarantee |
|---|---|---|
| `steps` | `input.required<ChatTraceStep[]>` | Rendered read-only — the component never mutates the array or any step within it, and provides no control that could edit, resend, or otherwise change the underlying conversation (FR-007). |
| `open` | `input<boolean>` (default `false`) | When `true`, the dialog is shown (via the native element's `showModal()` where available — research Decision 1); when `false`, it is not. The component never flips this input itself — it is driven entirely by the parent. Because `open` is scoped per `TraceDialogComponent` instance (one per `ChatMessage`, per `MessageBubbleComponent`'s own row below), more than one instance can independently hold `open: true` at the same time — opening one never forces another closed (FR-013). |
| `closed` | `EventEmitter<void>` (`@Output`) | Emits exactly once per user-initiated dismissal (explicit close control, backdrop click, or — in a real browser — the Escape key), regardless of which route triggered it (research Decision 1). A consumer MUST treat this as the sole signal to set its own `open` state back to `false`; the component does not do so itself, keeping "is the dialog open" a single piece of state the parent owns, not two that could disagree. A backdrop click is detected by comparing `event.target` against the dialog element itself — a click landing on the dialog's own padding/border still counts as "inside" and MUST NOT close it (FR-006). |
| Focus & accessibility | — | The component adds no custom focus-management code: moving focus into the dialog on open, containing it there while open, and restoring it to the trace control that opened the dialog on close are all provided by the native `<dialog>` element's `showModal()`/`close()` behavior (FR-006, research Decision 1). The rendered `<dialog>` carries an accessible name identifying which response's trace it shows (FR-016); its trigger — the trace control rendered by `MessageBubbleComponent` — is a real focusable, keyboard-operable element (FR-001), not a pointer-only affordance. |
| Rendering guarantee | — | For every one of the six known stages (research/data-model Decision 5), the dialog shows either that stage's full recorded detail (when present in `steps`) or an explicit "not reached" indication with a reason — never silently omits a stage between the first and last shown (FR-005/SC-002/SC-003). A not-reached stage is styled distinctly using more than color alone (a muted style plus an explicit "Not reached" label), meeting the same contrast standard as the rest of the interface in both light and dark presentations (FR-005). Passage text, the assembled prompt, and the raw model response are shown character-for-character as received — never truncated, summarized, or reformatted beyond scrolling/copy affordances (FR-004). The dialog's close control and scrollable content, and the trace toggle's visibility, remain usable at any viewport width the rest of the chat interface supports (FR-014); opening or closing a dialog never changes the underlying conversation's scroll position (FR-015). |
| Copy affordance | — | Copying the assembled prompt or the raw response places exactly that text (and no other) on the clipboard when the browser supports it, and shows a brief visible confirmation on success; when the API is unavailable (or the call fails), the action is a silent no-op with no thrown error, no confirmation shown, and no effect on the rest of the dialog (FR-009, research Decision 6). |

## `TraceToggleComponent`

| Member | Type | Guarantee |
|---|---|---|
| *(none — no `@Input`/`@Output`)* | — | Self-contained: reads `ChatService.includeTrace()` for its own displayed state and calls `ChatService.setIncludeTrace()` directly on user interaction (research Decision 2). A consumer places `<app-trace-toggle />` anywhere in the tree with no wiring required, the same way `<app-connection-status />` already works. Rendered in `app.html`'s `chat-header` so it stays visible for the whole session (FR-012); its markup visually distinguishes the on and off state clearly enough to read at a glance, and remains visible at any viewport width the chat interface otherwise supports (FR-012/FR-014). A change made mid-flight (while a message triggered before the change is still pending) has no effect on that already-sent request — `ask()` already read `includeTrace()` once, at send time (see `ChatService` row above). |

## `MessageBubbleComponent` (extension to 008's existing contract)

| Behavior | Guarantee |
|---|---|
| Trace control visibility | Rendered exactly when `message().trace !== undefined && message().trace.length > 0` (FR-001/FR-002) — never rendered for a `pending` or `error`-status message, and never rendered based on `ChatService.includeTrace()`'s *current* value, only on whether this specific already-settled message actually carries trace data (spec.md Edge Cases). |
| Dialog ownership | The component owns one local `open` signal for its own trace dialog (`false` initially); the control sets it `true`, `TraceDialogComponent`'s `(closed)` sets it back to `false`. No two message bubbles ever share dialog-open state — each `ChatMessage` gets its own dialog instance in the template, scoped by `@for`'s `track`. |
| Placement | The trace control renders below the sources panel (or directly below the message bubble when there are no sources — trace and sources are independent, per FR-002/data-model.md), and is styled as visually secondary to both the answer text and the sources list — smaller and lower-contrast than both (FR-010). It is a real `<button>` (or equivalent focusable, keyboard-operable element), labeled distinctly (e.g., "View diagnostic trace"), never a pointer-only `<div>`/`<span>` (FR-001). |

## Non-guarantees (explicitly out of scope)

- **No backend/API change of any kind** — this contract is additive purely on top of 009's already-shipped
  `chat-diagnostic-trace-contract.md`, which remains the complete and unmodified source of truth for
  what `POST /chat` accepts and returns.
- **No persistence of `includeTrace` or any `ChatMessage.trace` beyond the current page load** — a
  reload resets both to their defaults, consistent with 009's backend never persisting trace data
  either (spec.md Assumptions).
- **No retry, polling, or streaming behavior added to `ask()`** — this feature changes what one
  already-existing request/response cycle carries, not how many requests are made or when.
- **No new shared error-handling path** — a request that fails (400/503/network) settles exactly as
  008 already documents; `includeTrace`/`trace` play no role in error handling, since a trace only ever
  exists on a successful `200` response.

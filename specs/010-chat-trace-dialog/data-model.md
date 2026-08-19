# Phase 1 Data Model: Chat Trace Dialog

**Date**: 2026-08-17 | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

This feature persists nothing — every shape below is a client-side TypeScript type or Angular signal
living only in the browser tab's memory for the current page load, same lifecycle as every other piece
of `ChatService` state (spec.md Assumptions). Everything here is either a direct mirror of feature
009's already-shipped `POST /chat` response shape, or a small, purely presentational derivation of it
computed inside `TraceDialogComponent`. Feature 009's own shapes are documented in full in
[009's data-model.md](../009-chat-diagnostic-trace/data-model.md); this document states only what's
new or extended here.

## `ChatTraceStep` (new client-side type — direct mirror of 009's `ChatTraceStep`)

One recorded pipeline stage, exactly as `POST /chat` returns it (research Decision 3).

| Field | Type | Notes |
|---|---|---|
| `stage` | `string` | One of six fixed values (`request_received`, `question_embedded`, `vector_search_completed`, `results_filtered`, `prompt_assembled`, `model_response_received`) — 009's own closed vocabulary, carried through unchanged. |
| `durationMs` | `number` | Wall-clock time this stage took, as reported by the backend. |
| `detail` | `Record<string, unknown>` | Stage-specific fields, documented per stage in [009's data-model.md](../009-chat-diagnostic-trace/data-model.md#stage-values-and-their-detail-keys). Never re-shaped or renamed client-side — `TraceDialogComponent` reads keys directly by the names 009 documents. |

## `ChatMessage` (extended — spec.md Key Entities)

One entry in the conversation (unchanged fields omitted; see
[008's data-model.md](../008-frontend-chat-ui/data-model.md#chatmessage-client-side-only--specmd-key-entities)
for the full existing shape).

| Field | Type | Notes |
|---|---|---|
| `trace` | `ChatTraceStep[] \| undefined` | **New.** Present exactly when `ChatResponse.trace` was present on the response that settled this message (FR-002) — a direct pass-through, never re-derived (research Decision 4). `undefined` for every `role: 'user'` message, and for any `role: 'assistant'` message whose response didn't carry a trace (trace collection was off, or the request errored before a response arrived). `MessageBubbleComponent` renders its trace control exactly when `trace !== undefined && trace.length > 0` (FR-001/FR-002). |

## `ChatRequestBody` (extended — `POST /chat`'s request shape)

| Field | Type | Notes |
|---|---|---|
| `question` | `string` | Unchanged from 008. |
| `documentIds` | `null` | Unchanged from 008 (FR-020 of 008 — still always `null`, out of this feature's scope). |
| `includeTrace` | `boolean` | **New.** Set from `ChatService.includeTrace()` at the moment `ask()` builds the request body — `true` by default (FR-011), `false` after the user turns the toggle off. Always sent explicitly (never omitted), matching 009's own contract note that an explicit `false` and an absent field are equivalent on the wire. |

## `ChatResponse` (extended — `POST /chat`'s response shape, mirrors 009's own `data-model.md`)

| Field | Type | Notes |
|---|---|---|
| `answer` | `string` | Unchanged. |
| `sources` | `SourceCitation[]` | Unchanged. |
| `trace` | `ChatTraceStep[] \| undefined` | **New.** Present exactly when the request had `includeTrace: true` (009's FR-010/FR-011) — `undefined` (the JSON key is simply absent, not `null`) otherwise. |

## `ChatService` state (extended — research Decision 2)

Existing `messages`/`pending`/`ask()` unchanged in shape (see
[008's data-model.md](../008-frontend-chat-ui/data-model.md#chatservice-state-injectable-signal-based--research-decision-2));
`ask()`'s behavior and two new members are additive:

| Signal / method | Shape | Notes |
|---|---|---|
| `includeTrace` | `Signal<boolean>` | `true` from service construction (FR-011) until `setIncludeTrace(false)` is called; flips back on `setIncludeTrace(true)`. Read fresh on every `ask()` call — never "locked in" for the rest of a session. |
| `setIncludeTrace(value: boolean): void` | method | Updates `includeTrace` only. Never touches `messages` — changing this after a message was sent has no retroactive effect on that message's already-settled `trace` field (FR-012). |
| `ask(question: string): void` | method | Unchanged validation/no-op behavior (008's FR-004/FR-005). The request body it sends now includes `includeTrace: this.includeTrace()`; `settle()` now also assigns `trace: response.trace` onto the completed assistant `ChatMessage` (research Decision 4). |

## `TraceDialogComponent` — derived, render-only view model (not stored state)

Computed fresh from the `steps` input every time it changes; not persisted, not part of `ChatMessage`.

| Name | Shape | Notes |
|---|---|---|
| `KNOWN_STAGES` | `readonly string[]` (6 entries) | The fixed stage order, module-level constant — see research Decision 5. |
| `STAGE_LABELS` | `Readonly<Record<string, string>>` (6 entries) | Module-level constant mapping each `KNOWN_STAGES` value to the human-readable heading the template renders for it — the raw wire values (`request_received`, `question_embedded`, `vector_search_completed`, `results_filtered`, `prompt_assembled`, `model_response_received`) are never shown to the user directly. Exact strings: `request_received` → "Question received", `question_embedded` → "Question embedded", `vector_search_completed` → "Passages retrieved", `results_filtered` → "Passages filtered", `prompt_assembled` → "Prompt assembled", `model_response_received` → "Model response". Used for both a ran stage's heading and a not-reached stage's placeholder heading, so the two cases read as the same stage under two different outcomes rather than as differently-named things. |
| `displayedStages` | `Signal<DisplayedStage[]>` | One entry per `KNOWN_STAGES` element, always length 6, computed by zipping `steps()` against `KNOWN_STAGES` positionally: index `i` is `{ ran: true, step: steps()[i] }` when `i < steps().length`, otherwise `{ ran: false, stage: KNOWN_STAGES[i] }`. Drives FR-005/User Story 2's "visibly distinguish ran vs. not-reached" requirement directly from this one derived list — the template does not special-case "short trace" separately from "full trace." |
| `copyFeedback` | `WritableSignal<'prompt' \| 'response' \| null>` | Local, transient UI-only state — not derived from `steps`. Set to which copy button was just used on a successful `navigator.clipboard.writeText()` call, cleared back to `null` after a short timeout; never set on failure or when the Clipboard API is absent (research Decision 6). Drives FR-009's success-confirmation text ("Copied") with no effect on `displayedStages` or any other state. |

`DisplayedStage` (local type, not exported beyond the component):

```text
type DisplayedStage =
  | { ran: true; step: ChatTraceStep }
  | { ran: false; stage: string };
```

## `TraceDialogComponent` inputs/outputs

| Member | Shape | Notes |
|---|---|---|
| `steps` | `input.required<ChatTraceStep[]>()` | The `ChatMessage.trace` array for the response being inspected — never mutated by the component. |
| `open` | `input(false)` | Whether the dialog should be showing. An effect watches this to call the native element's `showModal()`/`close()` (guarded per research Decision 1). |
| `closed` | `EventEmitter<void>` (`@Output()`) | Fires exactly once per user-initiated dismissal, from whichever of the three routes (button, backdrop, Escape) actually fired — never fired by the `open` input itself changing. |

## `TraceToggleComponent`

No inputs, no outputs — self-contained (research Decision 2). Reads/writes `ChatService.includeTrace`/
`setIncludeTrace` directly via `inject(ChatService)`, the same shape `ConnectionStatusComponent` already
uses for `HealthService.status`.

## Relationship to existing backend contract

```text
POST /chat  { question, documentIds: null, includeTrace }  ──►  ChatResponse
  ChatResponse.trace[]  ──►  ChatMessage.trace[]  (research Decision 4 — direct pass-through)
    ChatMessage.trace[]  ──►  TraceDialogComponent.steps  ──►  displayedStages (research Decision 5)
```

## Out of scope for this feature's data shapes

- **No persisted trace or toggle state** — both live only in `ChatService`'s in-memory signals for the
  current page load; a reload resets `includeTrace` to its default (`true`) and discards every
  `ChatMessage.trace` already fetched (spec.md Assumptions), consistent with 009's backend not
  persisting trace data either.
- **No per-stage typed detail interfaces** — `ChatTraceStep.detail` stays `Record<string, unknown>`
  end-to-end; per-stage interpretation is template/component logic inside `TraceDialogComponent`, not a
  data shape (research Decision 3).
- **No explicit "not reached" entries in the wire format** — those are computed client-side from
  `KNOWN_STAGES`, never sent by the backend (research Decision 5).

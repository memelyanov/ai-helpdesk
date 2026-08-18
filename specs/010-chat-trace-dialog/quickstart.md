# Quickstart: Chat Trace Dialog

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Manual, runnable validation for each user story, plus the automated suite. Assumes
[008-frontend-chat-ui](../008-frontend-chat-ui/quickstart.md)'s prerequisites are already met — this
feature adds no new prerequisite of its own beyond feature 009 (already merged to `main`) being the
backend running.

## Prerequisites

- PostgreSQL/pgvector running, backend on `http://localhost:8080`, frontend on `http://localhost:4200`
  — same as [008's quickstart](../008-frontend-chat-ui/quickstart.md#prerequisites).
- At least one document ingested, so at least one question can produce a grounded answer with a
  non-empty trace (Story 1 needs this; Story 2 works even with zero documents ingested).
- Azure OpenAI credentials configured for the grounded-answer path (Story 1) — not required for
  Story 2's "documentation does not cover this" path or for Story 3 (toggling trace off doesn't need a
  model call to observe).

## Automated suite

```bash
cd frontend
npm test
```

Every new/changed service and component in this feature has its own `.spec.ts` — no live backend or
Azure credentials required (all HTTP calls are mocked via `HttpTestingController`). Note: the
`TraceDialogComponent` tests exercise its fallback close path, not real `HTMLDialogElement.showModal`/
`close` (research.md Decision 1) — the manual checks below cover the native browser behavior those
tests can't.

## Story 1 — Inspect a response's diagnostic trace

1. Open `http://localhost:4200`. Confirm the trace toggle in the chat header is on by default
   (FR-011) — no action needed to enable it.
2. Ask a question covered by an ingested document.
   **Expect**: the answer and its citation badges appear as before (008's behavior, unchanged), and a
   "View diagnostic trace" control appears beneath the sources panel.
3. Click it. **Expect**: a dialog opens showing, in order: the question received, the embedding step,
   the retrieved passages (with similarity scores and full passage text), which passages were kept vs.
   discarded, the exact assembled prompt (system prompt included), and the raw model response —
   nothing summarized or reworded.
4. Close the dialog via its close button. **Expect**: it closes; the conversation underneath — answer
   text, citation badges, message order — is unchanged from before you opened it.
5. Reopen it, and this time press Escape. **Expect**: same result (this exercises the native
   `<dialog>` behavior the automated suite can't — see Automated suite note above).
6. Reopen it, and this time click outside the dialog panel (on the dimmed backdrop). **Expect**: same
   result.

## Story 2 — Understand a short-circuited trace

1. Ask a question with no relation to any ingested document's content (or run this against an empty
   corpus).
   **Expect**: the fixed "documentation does not cover this" answer, no citation badges, but the trace
   control still appears (trace and sources are independent — data-model.md).
2. Open its trace. **Expect**: the dialog shows `request_received`, `question_embedded`,
   `vector_search_completed`, and `results_filtered` with their normal detail (including which
   candidates were discarded and why), and then visibly marks `prompt_assembled` and
   `model_response_received` as not reached, with a short explanation that no passage was similar
   enough for the model to be called.

## Story 3 — Turn diagnostic trace collection off

1. With the trace toggle on (default), send a message. **Expect**: its response has a trace control
   (Story 1).
2. Turn the toggle off. Send a second message. **Expect**: this response has no trace control at all —
   looks exactly like the pre-feature conversation.
3. Scroll up to the first response. **Expect**: its trace control and dialog still work exactly as
   before — turning the toggle off did not retroactively remove anything.
4. Turn the toggle back on. Send a third message. **Expect**: trace control appears again.

## Failure-path / edge-case spot checks

- Open a trace dialog, then (in a second tab or by resizing) confirm the underlying page's scrolling
  and the rest of the app are unaffected while it's open.
- Open the trace for a response with an unusually long answer/passage (or artificially lengthen one
  via a long ingested document). **Expect**: the dialog's content scrolls internally; the dialog itself
  never grows past the visible window (FR-008).
- Click "Copy" next to the assembled prompt. **Expect**: pasting elsewhere reproduces the exact prompt
  text shown (SC-006). If your browser/OS blocks clipboard access, confirm the click has no visible
  error and the dialog keeps working (spec.md Edge Cases).
- Reload the page mid-conversation. **Expect**: the trace toggle resets to on (its default) and the
  conversation itself resets too, consistent with existing (008) session-only behavior — no
  regression introduced by this feature.

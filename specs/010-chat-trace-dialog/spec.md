# Feature Specification: Chat Trace Dialog

**Feature Branch**: `010-chat-trace-dialog`

**Created**: 2026-08-17

**Status**: Draft

**Input**: User description: "turn this into an actual trace dialog component in the frontend"

## Clarifications

### Session 2026-08-17

- Q: Should diagnostic trace collection be off by default (opt-in per message) or on by default (opt-out)? → A: On by default — every chat message requests trace data unless the user turns it off.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Inspect a response's diagnostic trace (Priority: P1)

A support engineer or developer using the chat interface wants to understand why the assistant
answered a question the way it did — which passages it found, what prompt it built, what the model
actually returned. When a response carries diagnostic trace information, they open it directly from
that response, review every pipeline stage that ran, in order, and close it again without disrupting
the conversation.

**Why this priority**: This is the entire point of the feature — without it, feature 009's trace data
is reachable only by calling the API directly, which is not a realistic troubleshooting workflow for
anyone using the chat UI day to day.

**Independent Test**: Can be fully tested by sending a chat message that returns a response with trace
data, clicking its trace control, and confirming the dialog shows the recorded stages with their
detail, then closing it and confirming the conversation is unchanged.

**Acceptance Scenarios**:

1. **Given** an assistant response whose data includes a diagnostic trace, **When** the user views
   that response, **Then** a control to view its trace is visible beneath it.
2. **Given** that control, **When** the user selects it, **Then** a dialog opens showing every
   recorded pipeline stage in the order it occurred, including the question, the retrieved passages
   and their similarity scores, which passages were kept or discarded, the exact prompt sent to the
   model, and the model's raw response.
3. **Given** the trace dialog is open, **When** the user closes it (via a close control, the Escape
   key, or clicking outside the dialog), **Then** the dialog closes and the conversation — its
   messages, answer text, and sources — is exactly as it was before the dialog opened.

---

### User Story 2 - Understand a short-circuited trace (Priority: P2)

A user opens the trace for a response where the underlying pipeline stopped early — for example, no
retrieved passage was similar enough to use — and wants to see clearly which stages ran and which did
not, and why, instead of a trace that just looks incomplete or broken.

**Why this priority**: "Documentation does not cover this" is a common, expected outcome (feature 009
spec), and its trace always has fewer stages. Getting this state wrong makes the dialog look buggy on
one of the most frequent cases it will actually be used for.

**Independent Test**: Can be fully tested by sending a question with no matching documentation,
opening its trace, and confirming the dialog distinguishes the stages that ran from the stages that
were never reached, with a reason.

**Acceptance Scenarios**:

1. **Given** a response whose trace stopped after the retrieval stages because nothing met the
   similarity threshold, **When** the user opens its trace, **Then** the dialog shows the stages that
   ran normally and visibly marks the remaining stages as not reached, with a short explanation (no
   passage was similar enough, so the model was never called).

---

### User Story 3 - Turn diagnostic trace collection off when it isn't needed (Priority: P3)

Diagnostic trace collection is on by default, so every chat message a user sends returns a response
with a trace control they can inspect at will. When a user doesn't want the extra detail — or the
larger response payload it carries — they can turn trace collection off from a visible control; new
messages sent afterward return without trace data, while responses already shown keep whatever trace
they were fetched with.

**Why this priority**: Feature 009 built the trace as an opt-in API field so the caller decides whether
to request it; giving the frontend user visible control over that same decision — even though trace
defaults to on for this diagnostic-focused chat tool — keeps that choice available instead of baking a
single fixed behavior into the client. It's P3 because most day-to-day troubleshooting benefits from
having the trace present without any extra step.

**Independent Test**: Can be fully tested by sending a message with the default trace setting (trace
control appears), turning trace collection off, sending another message (no trace control on the new
response, while the first response's trace control and dialog still work), then turning it back on and
confirming the next message includes a trace control again.

**Acceptance Scenarios**:

1. **Given** a new chat session, **When** the user sends a message without changing the trace control,
   **Then** the resulting response includes a trace control that opens a populated trace dialog.
2. **Given** the user has turned trace collection off, **When** they send a message, **Then** that
   response has no trace control, matching the pre-feature conversation experience.
3. **Given** a response that already has a trace, **When** the user later changes the trace control,
   **Then** that earlier response's trace control and dialog continue to work unchanged.

---

### Edge Cases

- What happens if the user opens a trace dialog and then sends a new chat message while it's still
  open? The dialog keeps showing the trace it was opened for; new messages appear in the conversation
  behind it once closed.
- How does the dialog handle an unusually long question, passage, prompt, or response? The dialog
  content scrolls internally; the dialog itself never grows past the visible window.
- What happens if copying the prompt or raw response text to the clipboard is not permitted by the
  browser? The copy action fails quietly (e.g., reverts without a confusing error) and the rest of the
  dialog keeps working.
- What happens when a response has no sources because its trace shows zero retrieved passages survived
  filtering? The dialog still opens and shows the discarded candidates and the reason none survived,
  consistent with the "documentation does not cover this" answer shown in the conversation.
- What happens to an in-flight (pending) message, or one that returned an error? Neither ever has a
  trace control — only a completed assistant response with trace data does.
- What happens if the user changes the trace toggle while a message is still pending (in flight)? The
  in-flight request already carries whatever `includeTrace` value was in effect when it was sent; the
  toggle's new value applies only to messages sent after the change (FR-012).
- What happens if the user opens a second response's trace while another is already open? Both remain
  open independently — opening one never closes the other (FR-013).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The chat interface MUST display a control to view the diagnostic trace beneath any
  assistant response whose data includes trace information — labeled distinctly (e.g., "View
  diagnostic trace"), positioned beneath the sources panel (or beneath the message bubble when there
  are no sources), and implemented as a focusable, keyboard-operable interactive element (activatable
  via Enter or Space), not a pointer-only affordance.
- **FR-002**: The chat interface MUST NOT display any trace control on responses returned without
  trace information, so the default conversation view is unaffected by this feature.
- **FR-003**: Selecting a response's trace control MUST open a dialog that presents every pipeline
  stage recorded for that response, in the order the stages occurred.
- **FR-004**: For each recorded stage, the dialog MUST show the stage's name, how long it took, and
  its full detail — including, where applicable, the question and any document scope, embedding
  information, every retrieved passage with its source, page, similarity, and full text, which
  passages were kept versus discarded and why, the exact assembled prompt (including the fixed system
  instructions), and the model's full raw response text.
- **FR-005**: When a response's pipeline stopped before every stage ran, the dialog MUST visibly
  distinguish the stages that ran from the stages that did not — using more than color alone (a muted
  style plus an explicit "Not reached" label, meeting the same contrast standard as the rest of the
  interface in both light and dark presentations) — and state why the pipeline stopped.
- **FR-006**: Users MUST be able to close the trace dialog via an explicit close control, the Escape
  key, or clicking the backdrop outside the dialog's own bounding box (its padding and border count as
  part of the dialog, not the backdrop), and doing so MUST leave the conversation — messages, answer
  text, sources, and scroll position — exactly as it was before the dialog opened. The dialog relies on
  the browser's native modal-dialog behavior for moving focus into it on open, containing focus within
  it while open, and restoring focus to the trace control that opened it on close; no custom-built
  equivalent is required or expected (see Assumptions).
- **FR-007**: The trace dialog MUST be read-only: it MUST NOT provide any way to edit, resend, or
  otherwise change the conversation, the response, or its sources.
- **FR-008**: The trace dialog's content MUST remain fully readable and scrollable regardless of how
  long the question, passages, prompt, or response text are, without the dialog overflowing the
  viewport.
- **FR-009**: Users MUST be able to copy the full text of the assembled prompt and of the raw model
  response from within the dialog, with a brief visible confirmation shown when a copy succeeds.
- **FR-010**: The trace control MUST be visually secondary to the response and its sources — rendered
  smaller and lower-contrast than the answer text and the sources list — and MUST NOT obstruct, crowd,
  or compete with them.
- **FR-011**: Diagnostic trace collection MUST be on by default: a chat message sent without the user
  having turned it off MUST produce a response with a trace control, populated from the trace data
  returned for that message.
- **FR-012**: The chat interface MUST provide a visible control letting the user turn diagnostic trace
  collection off (or back on) for messages they send from that point forward, located in the chat
  header so it stays visible for the whole session, and MUST clearly indicate its current on/off state
  at a glance. Changing it MUST NOT alter trace data already attached to earlier responses, and MUST
  NOT affect a message that is already in flight when the change is made — only messages sent
  afterward are affected.
- **FR-013**: Multiple trace dialogs MAY be open at once, one per response — opening one response's
  trace dialog MUST NOT close another response's already-open trace dialog.
- **FR-014**: The trace dialog and the trace toggle MUST remain fully usable — including the dialog's
  close control, its scrollable content, and the toggle's visibility — at any viewport width the rest
  of the chat interface supports.
- **FR-015**: Opening and closing a trace dialog MUST NOT change the conversation's scroll position.
- **FR-016**: The trace dialog MUST be exposed to assistive technology with an accessible role and
  name identifying it as a dialog for the response it belongs to.

### Key Entities

- **Diagnostic Trace**: The ordered set of pipeline stages captured for one assistant response, present
  only when trace collection was on when that message was sent. Belongs to exactly one response.
- **Trace Stage**: One step of the pipeline recorded within a diagnostic trace (e.g., the question was
  received, the question was embedded, passages were retrieved, passages were filtered, the prompt was
  assembled, the model responded) — has a name, how long it took, and stage-specific detail. A trace
  holds between one and the full set of stages, in the order they ran; a pipeline that stops early
  simply has fewer of them.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From any assistant response that has one, a user reaches its full diagnostic trace in a
  single interaction — one click, tap, or Enter/Space key press on the trace control (FR-001).
- **SC-002**: 100% of the pipeline stages recorded for a response are visible in its trace dialog, in
  the order they occurred, with no stage silently omitted.
- **SC-003**: For a response whose pipeline stopped early, every not-reached stage is explicitly
  labeled as such with a stated reason directly in the dialog (FR-005), so identifying which stages ran
  and why never requires consulting anything outside the dialog itself.
- **SC-004**: Opening, reading, and closing a trace dialog never changes the answer or sources shown in
  the conversation, verified by comparing the conversation state before and after.
- **SC-005**: A user can turn trace collection off and every message sent afterward shows zero visible
  trace controls — no trace link, no dialog affordance — matching the pre-feature conversation view
  exactly on those messages.
- **SC-006**: A user can copy the exact assembled-prompt or raw-response text out of the dialog without
  any transcription step, and the copied text matches the displayed text exactly.

## Assumptions

- This feature is purely additive to the existing chat conversation UI (message bubbles, sources
  panel) introduced in feature 008; it does not change how responses without trace data are rendered.
- The trace data it displays is exactly what feature 009's API already returns (`ChatResponse.trace`,
  requested via `includeTrace`) — this feature adds no new backend capability and invents no data of
  its own.
- Trace collection state (on/off) and any already-fetched trace data live only for the current browser
  session; reloading the app resets the control to its default (on) and loses previously fetched
  traces, consistent with the backend not persisting trace data either.
- The trace control and dialog are available to any user of the chat interface — the application has
  no per-user authentication or role system today (feature 007's existing non-guarantee) to scope this
  more narrowly.
- Full retrieved-passage text, the exact prompt, and the raw model response are shown as returned by
  the API — this feature does not add any additional redaction beyond what feature 009 already
  guarantees (the Azure OpenAI credential is never present in this data).
- This feature assumes users access the chat interface with a modern, evergreen browser that supports
  standard native modal-dialog interaction patterns — including Escape-key dismissal and focus
  containment (FR-006); no custom-built fallback is provided for a browser that lacks them.
- The trace control and dialog being available to any user, with no additional access restriction (per
  the no-auth assumption above), was weighed and accepted as part of the `/speckit-clarify` Session
  2026-08-17 on-by-default decision — broader reachability of trace data was the explicit tradeoff that
  decision made, not an overlooked gap.
- FR-005's "both light and dark presentations" contrast requirement for the not-reached marking is
  forward-looking: the chat interface has no dark theme today (no `prefers-color-scheme` handling
  anywhere in the frontend), so that half of the requirement is currently a no-op with nothing to verify
  against. It becomes verifiable the moment a dark theme is added; until then only the light-theme
  contrast check applies.

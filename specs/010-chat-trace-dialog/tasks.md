# Tasks: Chat Trace Dialog

**Input**: Design documents from `specs/010-chat-trace-dialog/` — [spec.md](spec.md), [plan.md](plan.md),
[research.md](research.md), [data-model.md](data-model.md),
[contracts/frontend-trace-contract.md](contracts/frontend-trace-contract.md), [quickstart.md](quickstart.md)

**Tests**: Included per the project constitution's Test-Driven Development principle (Principle II,
`.specify/memory/constitution.md`) and plan.md's Constitution Check, which commits every new/changed
service and component to its own `.spec.ts`. Each implementation task is preceded by a task that writes
(or extends) that file's tests first, expected to fail until the following implementation task lands.

**Organization**: Tasks are grouped by user story (spec.md's User Story 1/2/3, priorities P1/P2/P3) so
each can be implemented, tested, and delivered independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different file, no dependency on another incomplete task in this list)
- **[Story]**: Maps the task to US1/US2/US3 from spec.md
- All file paths are relative to the repository root

## Path Conventions

Frontend-only feature, extending the existing `frontend/src/app/` Angular project (no backend change,
no new project) — paths follow plan.md's Project Structure exactly.

---

## Phase 1: Setup

**Purpose**: Confirm a clean baseline before touching any file. No new dependency or tooling is
introduced by this feature (research.md: native `<dialog>` and `Clipboard` are browser platform
features, not npm packages).

- [X] T001 Confirm `frontend/` currently builds and its existing test suite passes cleanly before any
  change in this feature (`cd frontend && npm test`) — establishes the baseline every later task's
  "still passing" checks are measured against.

**Checkpoint**: Clean baseline confirmed — proceed to Foundational.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared type and service surface every user story's UI reads from. **No user story
phase can start until this phase is complete** — US1's dialog needs `ChatTraceStep`/`ChatMessage.trace`
to exist to compile against; US2 needs the same types; US3's toggle needs `ChatService.includeTrace`/
`setIncludeTrace()` to exist to inject.

- [X] T002 Extend `frontend/src/app/chat/chat-message.ts` per data-model.md: add the `ChatTraceStep`
  interface (`stage: string`, `durationMs: number`, `detail: Record<string, unknown>`), add
  `trace?: ChatTraceStep[]` to `ChatMessage`, add `includeTrace: boolean` to `ChatRequestBody`, add
  `trace?: ChatTraceStep[]` to `ChatResponse`. No new mapping function (research Decision 4 — direct
  pass-through, unlike `mapSourcesToCitations`). Run `npm test` in `frontend/` afterward to confirm the
  existing `chat-message.spec.ts` still passes unmodified (these are additive/optional fields only).

- [X] T003 Write failing tests in `frontend/src/app/chat/chat.service.spec.ts` (new `describe` block,
  alongside the existing ones) covering: `includeTrace()` is `true` immediately after construction
  (FR-011); `setIncludeTrace(false)` then `setIncludeTrace(true)` flips it back, synchronously, with no
  thrown error; `ask()` sends `includeTrace: true` in the request body by default, and
  `includeTrace: false` after `setIncludeTrace(false)` — verified via `httpMock.expectOne(...).request.body`
  for two separate `ask()` calls; a response that includes a `trace` array settles onto the assistant
  `ChatMessage.trace` field unchanged (same array contents, in order); a response with no `trace` key
  settles with `trace` left `undefined`; calling `setIncludeTrace()` after a message has already settled
  does not alter that message's `trace` (FR-012). These tests will fail to compile/pass until T004 lands.

- [X] T004 Implement the changes in `frontend/src/app/chat/chat.service.ts` that make T003 pass: add a
  private `_includeTrace: WritableSignal<boolean>` initialized to `true`, expose it as
  `includeTrace: Signal<boolean> = this._includeTrace.asReadonly()`; add
  `setIncludeTrace(value: boolean): void` that calls `this._includeTrace.set(value)`; in `ask()`, build
  the request body as `{ question: trimmed, documentIds: null, includeTrace: this.includeTrace() }`; in
  `settle()`, add `trace: response.trace` to the patch passed to `updateMessage()`. Run `npm test` to
  confirm T003's tests now pass and no existing test in this file regressed.

**Checkpoint**: `ChatTraceStep`/`ChatMessage.trace` types exist; `ChatService` requests and carries
trace data end-to-end. No UI shows anything yet — all three user story phases can now start.

---

## Phase 3: User Story 1 - Inspect a response's diagnostic trace (Priority: P1) 🎯 MVP

**Goal**: A completed assistant response that has trace data shows a control beneath its sources; a
user opens it and sees every recorded stage's full detail, in order; closes it via button, Escape, or
backdrop click, with the conversation left exactly as it was (FR-001, FR-003, FR-004, FR-006, FR-007,
FR-008, FR-009, FR-010, FR-013, FR-014, FR-015, FR-016).

**Independent Test**: Send a chat message that returns a response with a full six-stage trace, click
its trace control, confirm the dialog shows all six stages with their documented detail, use the copy
button next to the assembled prompt, then close the dialog via each of its three dismissal routes and
confirm the conversation (messages, answer text, sources) is unchanged after each.

### Tests for User Story 1

- [X] T005 [US1] Create `frontend/src/app/chat/chat-view/trace-dialog.component.spec.ts` with failing
  tests for a `TraceDialogComponent` that does not exist yet: given a full six-entry `steps` array
  (one entry per `KNOWN_STAGES` value, using the exact `detail` keys documented in
  [009's data-model.md](../009-chat-diagnostic-trace/data-model.md#stage-values-and-their-detail-keys) —
  `question` for `request_received`; `vectorDimensions` for `question_embedded`; `candidateCount`/
  `candidates[]` for `vector_search_completed`; `survivorCount`/`discardedCount`/`threshold`/
  `survivors[]` for `results_filtered`; `systemPrompt`/`prompt`/`passageCount` for `prompt_assembled`;
  `rawResponse`/`completionLength`/`outcome` for `model_response_received`), setting `steps` and
  `open: true` renders each stage's name and every one of its `detail` fields as text content, in
  `KNOWN_STAGES` order; clicking the close button emits `closed` exactly once; clicking the dialog
  element itself (simulating a backdrop click, since jsdom lacks `showModal`) emits `closed`; calling
  the component's close path a second, third time still emits exactly once per call, never a double
  emission (research Decision 1's fallback path — this repo's jsdom has no `showModal`/`close`, so these
  tests exercise `requestClose()`'s fallback branch, not the native one); a "Copy" button next to the
  assembled prompt calls a mocked `navigator.clipboard.writeText` with the exact `prompt` text and
  nothing else, and a second "Copy" button does the same for `rawResponse`; after a successful copy the
  button's own text changes to include "Copied" and later (or immediately in the test, by asserting the
  signal directly) is not permanently stuck that way; when `navigator.clipboard` is undefined (deleted
  from the mock), clicking Copy throws no error and shows no "Copied" confirmation; the dialog carries an
  `aria-label` (or equivalent accessible name) that is non-empty; the dialog renders no control that
  edits, resends, or otherwise mutates anything passed into it (FR-007 — no input/button beyond close
  and copy).

### Implementation for User Story 1

- [X] T006 [US1] Create `frontend/src/app/chat/chat-view/trace-dialog.component.ts` implementing
  `TraceDialogComponent` per data-model.md/contracts/frontend-trace-contract.md: `steps =
  input.required<ChatTraceStep[]>()`; `open = input(false)`; `@Output() closed = new EventEmitter<void>()`;
  a private `readonly KNOWN_STAGES` constant (the six stage names in fixed order, research Decision 5);
  a private `readonly STAGE_LABELS` constant mapping each of those six values to its human-readable
  heading, using the exact strings in data-model.md's `TraceDialogComponent` view-model table (e.g.
  `request_received` → "Question received") — the template must never render a raw `stage` wire value
  directly; a `displayedStages = computed(...)` that zips `steps()` against `KNOWN_STAGES` positionally into
  `DisplayedStage[]` (`{ ran: true; step }` for `i < steps().length`, else `{ ran: false; stage }`); a
  `copyFeedback: WritableSignal<'prompt' | 'response' | null>` initialized to `null`; a `viewChild` (or
  `@ViewChild`) reference to the template's `<dialog>` element; a `requestClose()` method that calls the
  native element's `.close()` when it is a function, otherwise emits `closed` directly (research Decision
  1); an `effect()` that watches `open()` and calls the native element's `.showModal()` when it is a
  function and `open()` is `true`, and `.close()` when `open()` becomes `false` and the element reports
  itself open; a `copy(kind: 'prompt' | 'response', text: string)` method that calls
  `navigator.clipboard?.writeText(text)`, and on success sets `copyFeedback.set(kind)` then clears it
  back to `null` after a short `setTimeout` (research Decision 6), doing nothing further on rejection or
  when the API is absent. Depends on T005 (write tests first).

- [X] T007 [P] [US1] Create `frontend/src/app/chat/chat-view/trace-dialog.component.html`: a
  `<dialog #dialogEl [attr.open]="open() ? '' : null" aria-label="Diagnostic trace" (click)="onBackdropClick($event)" (close)="closed.emit()">`
  wrapping a content container (a click on that inner container must not reach the `<dialog>` element
  itself, so `onBackdropClick` — comparing `$event.target` to the `<dialog>` element via the ViewChild
  — only fires `requestClose()` for a true backdrop click, matching FR-006's "padding/border count as
  the dialog" rule); a close button calling `requestClose()`; one section per entry in
  `displayedStages()`, in order — a ran entry renders `STAGE_LABELS[stage]` as its heading (never the
  raw `stage` string), `durationMs`, and every `detail` field documented for that `stage` value (per the
  T005 list) with the full passage `text`, `prompt`, and `rawResponse` values rendered verbatim (no
  truncation, no `slice`/`substring`, FR-004); a not-reached entry (built out fully in US2, but the
  branch must exist now so `displayedStages()` never silently drops an index) renders a placeholder with
  `STAGE_LABELS[stage]` as its heading and a "Not reached" label; "Copy" buttons next to the `prompt` and
  `rawResponse` text calling
  `copy('prompt', step.detail['prompt'])` / `copy('response', step.detail['rawResponse'])`, each
  showing "Copied" text when `copyFeedback() === 'prompt'` / `'response'` respectively. Depends on T006.

- [X] T008 [P] [US1] Create `frontend/src/app/chat/chat-view/trace-dialog.component.css`: size the
  `<dialog>` to a fixed max-width/max-height well within typical viewport sizes with `overflow-y: auto`
  on its content container so long passage/prompt/response text scrolls internally without the dialog
  itself growing past the viewport (FR-008); style the backdrop (`::backdrop`) with a dimming overlay;
  lay out each stage section with clear visual separation and a duration/label header; give the
  not-reached placeholder a muted style scaffold (finished with an explicit label in US2, T015); ensure
  the dialog's width is fluid/percentage-based rather than a fixed pixel value too wide for narrow
  viewports (FR-014). Depends on T006 (can run in parallel with T007 — both only need the component
  class to exist, not each other's output).

- [X] T009 [US1] Extend `frontend/src/app/chat/chat-view/message-bubble.component.spec.ts` with new
  tests (failing until T010–T011 land): a message whose `trace` is a non-empty array renders a trace
  control button with visible text (e.g., containing "trace"); a message with `trace: undefined`, or
  `trace: []`, or `status: 'pending'`, or `status: 'error'` renders no trace control at all; clicking
  the trace control causes a `<dialog>` to become present/open (assert via the rendered
  `TraceDialogComponent`'s `open` input, or via `el.querySelector('dialog')?.hasAttribute('open')` given
  T007's fallback `[attr.open]` binding); triggering that dialog's `(closed)` output (e.g., by calling
  the child component's close button) returns the parent's open state to closed; rendering two separate
  `MessageBubbleComponent` instances, each with its own trace and each opened, keeps both open
  independently — opening one never closes the other (FR-013).

- [X] T010 [US1] Update `frontend/src/app/chat/chat-view/message-bubble.component.ts`: import and
  register `TraceDialogComponent` in the component's `imports` array; add a private
  `_traceDialogOpen: WritableSignal<boolean>` (initialized `false`) and expose it (e.g.,
  `traceDialogOpen = this._traceDialogOpen.asReadonly()`); add a computed or plain getter
  `hasTrace(): boolean` returning `(this.message().trace?.length ?? 0) > 0`; add `openTraceDialog(): void`
  setting `_traceDialogOpen` to `true` and `onTraceDialogClosed(): void` setting it back to `false`.
  Depends on T009.

- [X] T011 [US1] Update `frontend/src/app/chat/chat-view/message-bubble.component.html`: beneath the
  existing `sources` block (rendered whether or not `citations.length > 0`, so it also appears directly
  under the bubble when there are no sources — FR-001), add a
  `<button type="button" class="trace-control" (click)="openTraceDialog()">View diagnostic trace</button>`
  shown only when `hasTrace()` is true and the message is not `pending`/`error`; below it, render
  `<app-trace-dialog [steps]="message().trace ?? []" [open]="traceDialogOpen()" (closed)="onTraceDialogClosed()" />`
  unconditionally guarded by the same `hasTrace()` check (so no dialog instance — and no extra
  `<dialog>` in the DOM — exists for a message that never had trace data). Depends on T010.

- [X] T012 [P] [US1] Update `frontend/src/app/chat/chat-view/message-bubble.component.css`: style
  `.trace-control` smaller and lower-contrast than `.message` text and `.source-badge` (e.g., smaller
  `font-size`, a muted color such as `#888780` matching the existing `.sources-label` tone), with no
  background fill competing with the sources panel, positioned with a small top margin below `.sources`
  (FR-010). Depends on T010 (can run in parallel with T011 — both only need T010's class members to
  exist).

**Checkpoint**: User Story 1 is fully functional and independently testable — a response with a full
trace can be opened, read, copied from, and closed via all three routes without touching the
conversation. This is the MVP.

---

## Phase 4: User Story 2 - Understand a short-circuited trace (Priority: P2)

**Goal**: When a response's trace stopped early (fewer than six entries), the dialog visibly marks each
un-reached stage as "Not reached" with a stated reason, using more than color alone, meeting the
interface's contrast standard in both light and dark presentation (FR-005, SC-003).

**Independent Test**: Send a question with no matching documentation so its trace has only
`request_received`, `question_embedded`, `vector_search_completed`, and `results_filtered` (four
entries); open its trace dialog; confirm `prompt_assembled` and `model_response_received` are rendered
with an explicit "Not reached" label and a stated reason, styled distinctly from the four ran stages.

### Tests for User Story 2

- [X] T013 [US2] Extend `frontend/src/app/chat/chat-view/trace-dialog.component.spec.ts` with failing
  tests: given a `steps` array of length 4 (the four retrieval-stage entries, `results_filtered.detail`
  with `survivorCount: 0`), the rendered stage sections for `prompt_assembled` and
  `model_response_received` each contain the text "Not reached" and a non-empty reason string distinct
  from a ran stage's content; those two sections carry a CSS class (e.g., `trace-stage--not-reached`)
  that a ran stage's section never carries, and vice versa (so the distinction is never color-only —
  it's also a textual label and a structural class the DOM exposes to assistive tech); a `steps` array
  of length 6 renders zero "Not reached" labels (regression guard against always showing the label).

### Implementation for User Story 2

- [X] T014 [US2] Finish the not-reached branch in
  `frontend/src/app/chat/chat-view/trace-dialog.component.html` (the placeholder scaffolded in T007):
  render the stage's display name, the literal text "Not reached", and a reason string. Compute the
  reason from the last *ran* stage's detail where derivable — e.g., when the last ran stage is
  `results_filtered` with `detail['survivorCount'] === 0`, use "No passage was similar enough, so the
  model was never called" (matching spec.md's User Story 2 Acceptance Scenario wording exactly); fall
  back to a generic "This stage did not run because the pipeline stopped earlier" for any other
  not-reached case. This can live as a small pure function/method on `TraceDialogComponent` (e.g.
  `notReachedReason(displayedStages: DisplayedStage[]): string`) called from the template. Depends on
  T013.

- [X] T015 [US2] Finish `.trace-stage--not-reached` in
  `frontend/src/app/chat/chat-view/trace-dialog.component.css`: a muted style (reduced opacity or a
  grey background distinct from the ran-stage background) combined with the textual "Not reached" label
  already added in T014 — verify in both a light and a dark color scheme (if the app has dark-mode
  support; otherwise verify against the existing light palette only) that the label text itself meets
  standard body-text contrast against its background, not relying on a color difference alone to convey
  meaning (FR-005). Depends on T014.

**Checkpoint**: User Stories 1 AND 2 both work independently — a full trace and a short-circuited trace
both render correctly, with no shared regression.

---

## Phase 5: User Story 3 - Turn diagnostic trace collection off when it isn't needed (Priority: P3)

**Goal**: A visible control in the chat header lets the user turn trace collection off (or back on) for
messages sent from that point forward, without altering trace data already attached to earlier
responses or affecting a message already in flight (FR-012).

**Independent Test**: Send a message with the default (on) trace setting — its response has a trace
control. Turn the toggle off, send a second message — its response has no trace control, while the
first response's trace control and dialog still work. Turn the toggle back on, send a third message —
it has a trace control again.

### Tests for User Story 3

- [X] T016 [US3] Create `frontend/src/app/chat/trace-toggle.component.spec.ts` with failing tests for a
  `TraceToggleComponent` that does not exist yet: with `ChatService.includeTrace()` at its default
  (`true`), the rendered control's markup/text/attribute reflects an "on" state (e.g., a bound
  `[attr.aria-checked]` or `[class.on]`); clicking the control calls `ChatService.setIncludeTrace(false)`
  (verified by injecting the real `ChatService` from `TestBed` and asserting `includeTrace()` afterward,
  the same pattern `ConnectionStatusComponent`'s tests would use for `HealthService`); clicking it again
  flips back to `true`; the component takes no `@Input` and emits no `@Output` (constructed and rendered
  with zero bindings, per contracts/frontend-trace-contract.md).

### Implementation for User Story 3

- [X] T017 [US3] Create `frontend/src/app/chat/trace-toggle.component.ts`: `@Component` with selector
  `app-trace-toggle`, no inputs/outputs, `private readonly chatService = inject(ChatService)`, expose
  `includeTrace = this.chatService.includeTrace` for the template, and a `toggle(): void` method calling
  `this.chatService.setIncludeTrace(!this.chatService.includeTrace())`. Depends on T016.

- [X] T018 [P] [US3] Create `frontend/src/app/chat/trace-toggle.component.html`: a single
  `<button type="button" class="trace-toggle" [class.trace-toggle--on]="includeTrace()" [attr.aria-pressed]="includeTrace()" (click)="toggle()">`
  containing a short label whose text itself changes with state (e.g., "Trace: On" / "Trace: Off") so
  the on/off state is conveyed by text, not color alone, readable at a glance (FR-012). Depends on T017.

- [X] T019 [P] [US3] Create `frontend/src/app/chat/trace-toggle.component.css`: distinct visual
  treatment for `.trace-toggle--on` vs. the default (off) state (e.g., background/border color pairing
  consistent with `connection-status.component.css`'s `--healthy`/`--unreachable` treatment), sized to
  remain legible and clickable at any viewport width the header itself supports (FR-014). Depends on
  T017 (parallel with T018 — both only need T017's class to exist).

- [X] T020 [US3] Extend `frontend/src/app/app.spec.ts` with a failing test mirroring the existing
  "still hosts `<app-connection-status>` inside the sidebar header" test just above it: render `App`,
  query `.chat-header`, and assert it contains an `<app-trace-toggle>` element. This is the paired
  test-first task for T021 below (every other implementation task in this list has one; `app.ts`/
  `app.html` should not be the exception). Depends on T019 (nothing in this test needs T017–T019's
  content directly, but it's sequenced here so it fails for the right reason — the element is missing —
  rather than an unrelated compile error).

- [X] T021 [US3] Update `frontend/src/app/app.ts` and `frontend/src/app/app.html` to make T020 pass:
  import `TraceToggleComponent` into `App`'s `imports` array; add `<app-trace-toggle />` inside the
  existing `<header class="chat-header">` block in `app.html`, next to `chat-title`/`chat-subtitle`
  (mirroring how `<app-connection-status />` sits next to `sidebar-title` in the sidebar header), so it
  stays visible for the whole session regardless of which messages are on screen (FR-012). Depends on
  T020.

- [X] T022 [US3] Update `frontend/src/app/app.css` if the `.chat-header` flex layout needs adjusting
  (e.g., `display: flex; justify-content: space-between; align-items: center;`) so the new toggle sits
  alongside the title block without overlapping or wrapping awkwardly at the existing `768px` mobile
  breakpoint already defined in this file. Depends on T021.

**Checkpoint**: All three user stories work independently. US3 has no dependency on US1/US2 beyond the
Foundational phase — with more than one developer, it could be built fully in parallel with them.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Whole-feature verification that spans more than one user story.

- [X] T023 [P] Run the complete automated suite (`cd frontend && npm test`) and confirm every new/changed
  spec file from T002–T022 passes together, with no regression in any pre-existing spec file
  (quickstart.md's Automated suite section).

- [ ] T024 Manually execute quickstart.md's Story 1, Story 2, and Story 3 validation steps in a real
  browser against a running backend (`http://localhost:4200` / `http://localhost:8080`), specifically
  including the native Escape-key and backdrop-click dismissal checks (Story 1, steps 5–6) that this
  repository's pinned jsdom cannot exercise (research Decision 1) — these two paths are only ever
  verified here, not by any `.spec.ts` file.

- [ ] T025 [P] During the manual pass in T024, resize the browser to a narrow (mobile-width) viewport
  and confirm the trace dialog's close control and scrollable content, and the trace toggle's
  visibility, all remain usable (FR-014), and that opening/closing a trace dialog does not change the
  conversation's scroll position (FR-015). FR-015 has no automated test: jsdom has no real layout engine
  to assert a scroll offset against, so unlike every other User Story 1 FR, this one is verified here
  only, by design rather than oversight.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. **Blocks all three user story phases** — none of them
  compile or have anything to render without `ChatTraceStep`/`ChatMessage.trace`/`ChatService.includeTrace`.
- **User Stories (Phase 3–5)**: All depend only on Foundational, not on each other:
  - US1 (Phase 3) is fully self-contained once Foundational is done.
  - US2 (Phase 4) extends the same `trace-dialog.component.*` files US1 creates, so in this task list it
    is sequenced after US1 — but nothing about its own logic depends on US3.
  - US3 (Phase 5) touches an entirely disjoint set of files (`trace-toggle.component.*`, `app.*`) and
    could be built in parallel with US1/US2 by a second developer with no coordination needed beyond
    Foundational.
- **Polish (Phase 6)**: Depends on whichever user stories are in scope for a given release being done.

### Within Each Phase

- Tests are written first and are expected to fail (or fail to compile) until their matching
  implementation task lands (Test-Driven Development, constitution Principle II).
- `.ts` before `.html`/`.css` within a component (the template/styles reference members the class must
  already declare).

### Parallel Opportunities

- T007 and T008 (US1 dialog's `.html` and `.css`) — both depend only on T006.
- T011 and T012 (US1 bubble's `.html` and `.css`) — both depend only on T010.
- T018 and T019 (US3 toggle's `.html` and `.css`) — both depend only on T017.
- T023 and T025 (Polish) — independent checks, no shared file.
- With two or more developers: US3 (T016–T022) can proceed entirely in parallel with US1 (T005–T012)
  and US2 (T013–T015) once Foundational (T002–T004) is done.

---

## Parallel Example: User Story 1

```bash
# After T006 (trace-dialog.component.ts) lands, its template and styles can proceed together:
Task: "Create trace-dialog.component.html per T007"
Task: "Create trace-dialog.component.css per T008"

# After T010 (message-bubble.component.ts changes) lands, its template and styles can proceed together:
Task: "Update message-bubble.component.html per T011"
Task: "Update message-bubble.component.css per T012"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational (T002–T004) — CRITICAL, blocks everything else.
3. Complete Phase 3: User Story 1 (T005–T012).
4. **STOP and VALIDATE**: run the automated suite, then manually confirm a full trace opens, reads, and
   closes correctly (quickstart.md Story 1).
5. This is a deployable increment: any response that carries a trace is now inspectable end-to-end.

### Incremental Delivery

1. Setup + Foundational → foundation ready, nothing visible yet.
2. Add User Story 1 → validate independently → deployable (MVP: trace inspection for full traces).
3. Add User Story 2 → validate independently → deployable (short-circuited traces now read correctly
   instead of looking cut off).
4. Add User Story 3 → validate independently → deployable (users can opt out of trace collection).
5. Polish (T023–T025) once all three stories are in.

### Parallel Team Strategy

1. One person (or pair) completes Setup + Foundational.
2. Once Foundational is done: Developer A takes US1 then US2 (they share `trace-dialog.component.*`);
   Developer B takes US3 (a disjoint file set — `trace-toggle.component.*`, `app.*`).
3. Both integrate independently; Polish runs once both are merged.

---

## Notes

- [P] tasks touch different files and have no dependency on another incomplete task in this list.
- Every task names its exact file path(s) — no task requires guessing where code belongs.
- Commit after each task or logical group, per this repository's existing convention (one commit per
  completed spec+implementation pair in prior features' history).
- Stop at any Checkpoint above to validate that user story's Independent Test before continuing.
- Avoid: broadening a task's file scope beyond what's listed, and any cross-story dependency that would
  make US1/US2/US3 unable to be demoed independently of one another.

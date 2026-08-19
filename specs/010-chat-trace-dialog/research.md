# Phase 0 Research: Chat Trace Dialog

**Date**: 2026-08-17 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

Six decisions. No `[NEEDS CLARIFICATION]` markers remain — `/speckit-clarify` resolved the one
significant ambiguity (trace-collection default). The decisions below are about how this feature's
small amount of new frontend surface fits the patterns 008 already established, and one concrete
technical constraint discovered by testing against this repository's actual `jsdom` version before
committing to an approach.

## Decision 1: Native `<dialog>`, with every close path degrading to a fallback when `showModal`/`close` are unavailable

- **Decision**: `TraceDialogComponent` renders its content inside a real `<dialog>` element (matching
  the UX design already reviewed and approved in the earlier mockup session) shown via
  `HTMLDialogElement.showModal()` and dismissed via `.close()`. Every dismissal path — the explicit
  close button, a click on the backdrop (detected by comparing `event.target` to the dialog element
  itself, since `<dialog>` does not close on backdrop click for free), and the browser's native
  Escape-key handling once `showModal()`'d — funnels through one method,
  `TraceDialogComponent.requestClose()`. That method calls the native `.close()` when it exists; the
  dialog's own `(close)` DOM event listener is the *only* place the component's `closed` output is
  emitted, so there is exactly one notification path regardless of which of the three dismissal routes
  fired, with no risk of a double or missed emission. When `.close` (or `.showModal`, checked the same
  way before ever opening) is not a function on the element, `requestClose()` emits `closed` directly
  instead, and the `open` input's own effect skips calling the missing native method rather than
  throwing.
- **Rationale**: Before choosing this approach, `node_modules/jsdom`'s actual behavior was checked
  directly (`d.showModal()` / `d.close()` against a `<dialog>` node) rather than assumed from general
  browser-support knowledge. Confirmed: this repository's pinned `jsdom` (28.1.0, `frontend/package.json`
  devDependency) throws `TypeError: d.showModal is not a function` — `HTMLDialogElement`'s modal methods
  are not implemented. Since Angular 21's unit-test builder runs component tests through this same
  jsdom, a component that unconditionally calls `showModal()`/`close()` would throw inside every test
  that opens or closes the dialog. Feature-detecting the two methods and falling back to a direct
  `closed` emission keeps FR-006 (an explicit close control that works) testable without a real browser,
  while production code in an actual browser still gets the real `<dialog>` element's built-in focus
  trap, Escape-to-close, and top-layer stacking — capabilities not worth reimplementing in JavaScript
  for a feature this small. This same native behavior is what satisfies FR-006's and FR-016's focus-
  management and accessibility requirements at no extra implementation cost: the HTML Living Standard
  already specifies that `showModal()` moves focus into the dialog, traps it there, exposes the element
  with an implicit dialog role, and restores focus to whichever element triggered it once `close()`
  runs — the component only needs to supply an accessible name (e.g. `aria-label`) and ensure its own
  trigger (the trace control) is a real focusable element (FR-001), not reimplement any of the rest.
- **Alternatives considered**: a hand-rolled modal `<div>` with a manual focus trap and a
  `document`-level `keydown` listener for Escape (rejected — reimplements, with more code and more
  chances for an accessibility bug, exactly what `<dialog>` already provides natively; the only reason
  to do this would be jsdom's gap, and a feature-detected fallback closes that gap far more cheaply);
  polyfilling `HTMLDialogElement` in the test environment (rejected — adds a dependency and test-only
  configuration to work around a one-method gap that a five-line fallback in the component itself
  already handles, and the fallback is also genuinely useful in a real old-browser scenario, not just a
  test one).

## Decision 2: `ChatService` owns trace-collection state; a self-contained `TraceToggleComponent` reads and sets it directly, the same way `ConnectionStatusComponent` already does for `HealthService`

- **Decision**: `ChatService` gains a `includeTrace: Signal<boolean>` (backed by a private
  `WritableSignal`, default `true` per FR-011) and a `setIncludeTrace(value: boolean): void` method.
  `ask()` reads the current value into `ChatRequestBody.includeTrace` on every call — independently per
  message, so changing the toggle never touches a message already sent (FR-012). `TraceToggleComponent`
  is a small presentational control that `inject()`s `ChatService` directly and binds to
  `includeTrace()`/`setIncludeTrace()` with no `@Input`/`@Output` of its own, exactly the pattern
  `ConnectionStatusComponent` already uses for `HealthService.status`. It is placed in `app.html`'s
  existing `chat-header`, next to the chat title, since that is the one piece of chrome visible for the
  entire chat session regardless of which messages are on screen.
- **Rationale**: `ChatService` already owns every other piece of per-session chat state (`messages`,
  `pending`); trace-collection intent is exactly that kind of state, not something that belongs on a
  component. Self-injection avoids threading a new `@Input`/`@Output` pair through `App` →
  `ChatViewComponent` for a control that has no other reason to sit inside that component's own tree —
  the same reasoning that already justifies `ConnectionStatusComponent`'s self-contained shape.
- **Alternatives considered**: passing `includeTrace`/`(includeTraceChange)` as `@Input`/`@Output`
  through `App` and down into a control rendered by `ChatViewComponent` (rejected — more wiring for the
  same outcome, and `chat-header` — where the control visually belongs, next to the connection status
  indicator's own header — is not even inside `ChatViewComponent`'s template, so the plumbing would
  have to cross back out again); storing the toggle state in a new standalone service separate from
  `ChatService` (rejected — nothing else would ever read it, and `ask()` needs to read it on every call
  regardless, so co-locating it with the method that consumes it is simpler than coordinating two
  services).

## Decision 3: `ChatTraceStep`'s `detail` stays a loosely-typed object; all stage-specific interpretation lives inside `TraceDialogComponent`

- **Decision**: The frontend type is a direct structural mirror of 009's own wire shape:
  `interface ChatTraceStep { stage: string; durationMs: number; detail: Record<string, unknown>; }` —
  no per-stage TypeScript interface, no discriminated union keyed on `stage`. `TraceDialogComponent`'s
  template switches on `stage` to decide which of `detail`'s documented keys to read and how to render
  them (a passage list for `vector_search_completed`, a prompt/system-prompt pair for
  `prompt_assembled`, and so on, per 009's own `data-model.md` stage table).
- **Rationale**: This directly mirrors 009's own research Decision 2 (`Map<String, Object> detail`
  rather than six typed records) for the same reason on the frontend side: `detail`'s shape genuinely
  differs per stage, and six TypeScript interfaces behind a discriminated union would need to be kept
  in lockstep with a backend `Map` that documents its keys in prose, not in a shared schema — extra
  type machinery for a value that is read in exactly one component and never round-tripped or
  re-sent.
- **Alternatives considered**: six typed interfaces (`RequestReceivedDetail`, `QuestionEmbeddedDetail`,
  …) unioned on `stage` (rejected — see above; also would need updating in two places, frontend and
  backend, every time 009's own detail keys changed, for a benefit — compile-time key checking inside
  one template — that a handful of small per-stage accessor helpers inside the component already give
  without the duplication).

## Decision 4: `ChatMessage.trace` is a direct pass-through of `ChatResponse.trace` — no mapping function

- **Decision**: `ChatMessage` gains `trace?: ChatTraceStep[]`. `ChatService.settle()` assigns
  `response.trace` to it directly (`trace: response.trace`), with no transform — present exactly when
  the response had it, `undefined` exactly when it didn't (mirroring 009's own
  `@JsonInclude(Include.NON_NULL)` absence-not-null contract).
- **Rationale**: Unlike `sources` → `Citation[]` (008's `mapSourcesToCitations`, which derives
  `scorePercent` and adds `available`), there is nothing to derive here — every field the dialog needs
  is already present in `ChatTraceStep` exactly as the backend sends it (FR-004's "full detail," not a
  reduced view). Writing a one-line identity "mapping function" for symmetry with `mapSourcesToCitations`
  would be pure ceremony; a direct field assignment says the same thing with less code and one fewer
  thing to keep in sync.
- **Alternatives considered**: a `mapTraceSteps()` function mirroring `mapSourcesToCitations`'s shape for
  API consistency (rejected — with no derived fields, either it does nothing (an identity function) or
  it starts inventing structure the data model doesn't need, and 008's own `Citation` doc already
  frames deriving `scorePercent` as the *reason* that mapping function exists, not a pattern to apply
  unconditionally).

## Decision 5: "Not reached" stages are derived client-side from a fixed six-stage order, not sent by the backend

- **Decision**: `TraceDialogComponent` holds a constant, ordered list of the six known stage names
  (`request_received`, `question_embedded`, `vector_search_completed`, `results_filtered`,
  `prompt_assembled`, `model_response_received`, matching 009's `data-model.md` exactly) and derives a
  `displayedStages` view — one entry per known stage, positionally zipped against the `trace` array —
  where a stage beyond `trace.length` renders as "not reached" (User Story 2, FR-005) instead of being
  silently absent.
- **Rationale**: 009's own `data-model.md` guarantees the trace is "one entry per stage that actually
  ran, in execution order" and that a short-circuited pipeline only ever *truncates the end* of that
  list — it never produces a gap in the middle, and never reorders. That guarantee is exactly what
  makes positional zipping against a fixed known-stage list safe and sufficient; the frontend does not
  need the backend to send explicit placeholder entries for stages that never ran (FR-014 of 009's own
  contract explicitly rules that out for the API side, "no per-request tuning" / fixed six stages
  either).
- **Alternatives considered**: matching each `trace` entry to a known stage by its `stage` string value
  rather than by position (rejected — strictly more defensive against a hypothetical backend reordering
  that 009's own contract already rules out, at the cost of slightly more code for a case that cannot
  occur given the upstream guarantee); having the backend send explicit "not_reached" entries
  (rejected — that's a 009 contract change for a frontend-only presentational need, and 009's own
  Non-guarantees section already fixes its six stages as non-configurable).

## Decision 6: Copy-to-clipboard uses `navigator.clipboard.writeText`, feature-detected with a silent fallback

- **Decision**: The "Copy" affordance next to the assembled prompt and the raw response
  (FR-009/SC-006) calls `navigator.clipboard?.writeText(text)` when available; if the API is absent or
  the call rejects (e.g. permission denied), the action is a silent no-op — no thrown error, no
  blocking prompt — matching spec.md's Edge Cases wording exactly ("fails quietly... and the rest of
  the dialog keeps working"). On success, the component shows a brief transient confirmation (e.g. the
  "Copy" label swaps to "Copied" for a couple of seconds via a local signal) — FR-009's success case;
  on failure or when the API is absent, that confirmation never appears, which is itself the silent-
  failure signal — no separate error UI is added.
- **Rationale**: `Clipboard.writeText` is the standard, dependency-free browser API for this; no
  npm package is justified for one method call. Feature-detecting rather than assuming availability
  keeps the same defensive posture as Decision 1 (native browser capability, guarded, with a safe
  fallback) rather than introducing a second, inconsistent error-handling style for a very similar kind
  of "browser API might not be there" problem.
- **Alternatives considered**: a copy-to-clipboard npm package (rejected — no functional gain over the
  native API for plain text, and an unjustified new dependency per the constitution's "no dependency
  substitution without amendment" posture applied to *additions* as much as replacements); showing a
  visible error message on failure (rejected — spec.md's Edge Cases explicitly calls for a quiet
  failure, not a new error surface for what is a minor, non-blocking convenience action).

## Open questions

None.

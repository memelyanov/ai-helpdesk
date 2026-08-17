# Phase 0 Research: Frontend Chat UI

**Date**: 2026-08-16 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

No `[NEEDS CLARIFICATION]` markers remain in `spec.md` — every open question was resolved during
`/speckit-specify` and `/speckit-clarify` (see spec.md's Clarifications section and Assumptions).
This document instead records the technical decisions needed to turn that resolved spec into a
concrete Angular design, each one a genuine fork this feature had to pick between.

## Decision 1: The backend needs one small CORS change this feature depends on

**Decision**: Add a `WebMvcConfigurer` bean (or equivalent `CorsRegistry` configuration) that allows
`http://localhost:4200` to call `GET/POST /documents`, `GET /documents/{id}/content`,
`DELETE /documents/{id}`, and `POST /chat`, mirroring the CORS allowance
[002-frontend-health-wire](../002-frontend-health-wire/plan.md) already added for
`/actuator/health`.

**Rationale**: Grepping the backend (`WebMvcConfigurer`, `@CrossOrigin`, `CorsConfigurationSource`,
`allowedOrigins`) turns up exactly one CORS allowance in the whole codebase —
`management.endpoints.web.cors` in `application.yml`, scoped to the actuator's own dispatcher and
therefore to `/actuator/health` alone. `/documents` and `/chat` are ordinary `@RestController`
endpoints outside that dispatcher; a browser page served from `http://localhost:4200` calling either
one today gets a CORS-blocked response before this feature's own logic ever runs, regardless of how
correct that logic is. This is a small, additive, backend-side prerequisite — the same category of
change 002 already made and precedented — not a redesign of anything upstream.

**Alternatives considered**:
- *Serve the Angular build from the same origin as the backend (no CORS needed)* — rejected: not how
  local development runs today (`ng serve` on 4200, Spring Boot on 8080, per every prior frontend
  feature's Target Platform); changing that is out of this feature's scope and would affect every
  future feature too, not just this one.
- *A permissive `Access-Control-Allow-Origin: *`* — rejected: unnecessarily broad for a fixed,
  known local dev origin; the existing health CORS allowance already established the
  narrower-origin pattern this feature just extends.

## Decision 2: Client-side state lives in signal-based Angular services, no new dependency

**Decision**: `ChatService` and `DocumentsService` each hold their state as Angular `signal()`s
internally and expose it as read-only `Signal<T>` to components, with plain methods (`ask(...)`,
`upload(...)`, `remove(...)`, `refresh()`) that mutate that state after an `HttpClient` call
resolves — the same shape `HealthService` (002-frontend-health-wire) already established for a
read-only polled signal, extended here to services that also *write*.

**Rationale**: `@angular/core`'s signals are already in use, already tested in this codebase, and
need no new package. Angular 21's signal primitives (`signal`, `computed`) are sufficient for this
feature's state shape — an ordered message list, a document list, and a couple of busy/error flags —
none of which need cross-tab sync, time-travel debugging, or the ceremony a store library (NgRx,
Akita) brings. Introducing one would violate the constitution's "no dependency substitution without
an amendment" posture over a component of this size for no compensating benefit.

**Alternatives considered**:
- *NgRx / a signal-store library* — rejected: new dependency, disproportionate to two small pieces
  of mutable state; no requirement (FR or SC) calls for time-travel debugging, devtools, or
  cross-feature shared state.
- *RxJS `BehaviorSubject`s instead of signals* — rejected: works, but `HealthService` already
  proved the signals idiom fits this codebase's existing test style
  (`TestBed` + `HttpTestingController`, asserting on `.status()`/`.messages()` directly rather than
  subscribing); mixing both reactive primitives in one small frontend adds cognitive load for no
  behavioral gain.

## Decision 3: Downloads use the already-known filename, not a parsed response header

**Decision**: A download (sidebar hover action or citation-badge click) issues
`http.get(url, { responseType: 'blob' })` against `GET /documents/{id}/content`, then triggers a
save via a synthetic `<a>` element whose `download` attribute is set to the filename the UI already
has in hand — from the sidebar's `DocumentSummary.filename` (Story 4, Scenario 1) or the citation's
`SourceCitation.filename` (Story 4, Scenario 2) — never by reading the response's
`Content-Disposition` header.

**Rationale**: The backend contract (005's `document-query-api-contract.md`) does set
`Content-Disposition: attachment; filename="..."` on a successful download, but that header is not
in the default CORS-safelisted response header set — reading it from JavaScript would require the
backend to also add `Access-Control-Expose-Headers: Content-Disposition` to Decision 1's CORS
configuration. Since every caller of this download path already has the filename from the same
response that gave it the `documentId` (`GET /documents` for the sidebar, `POST /chat`'s `sources`
for a citation), reading the header back out would just be re-deriving a value already in hand, for
an added CORS surface with no behavioral benefit. `FR-014`'s "no longer available" message on a
`404` is handled by inspecting the blob request's error status, not by needing the header either.

**Alternatives considered**:
- *Parse `Content-Disposition` from the response* — rejected per above: needs an extra CORS
  exposure for zero new information.
- *Open the file in a new tab instead of downloading* — rejected: `Content-Disposition: attachment`
  already signals "save this," and a `.pdf` opened via `blob:` URL in a new tab loses the original
  filename entirely, which the mockup's file-type iconography (Story 4) depends on for `.txt` vs
  `.pdf` recognizability.

## Decision 4: Delete confirmation is an inline, in-component two-step control

**Decision**: Clicking a document row's delete icon does not call `DELETE` immediately. It sets a
single container-level `confirmingDocumentId` signal (data-model.md) to that row's id, which swaps
that one row's content for "Delete this document? [Confirm] [Cancel]" (Story 5, Scenarios 1/3);
clicking elsewhere or "Cancel" reverts it to `null` with no request sent; "Confirm" issues the
`DELETE` call. The signal is container-level, not per-row, specifically so triggering a second row's
delete action while a first is still unconfirmed overwrites `confirmingDocumentId` — cancelling the
first for free — satisfying FR-021's "at most one open confirmation at a time" without a separate
explicit-cancel step. Both the delete icon and the (research Decision 3-adjacent) download icon are
reachable via keyboard focus on the row, not only mouse hover, per FR-013.

**Rationale**: `FR-015` requires "an explicit confirmation step that can be cancelled without
effect" but leaves the exact mechanism to planning. The browser-native `window.confirm()` was
considered and rejected: it blocks the JS event loop (so `ChatService`'s in-flight signals can't
update while it's open), cannot be styled to match the mockup's visual language (Assumptions), and —
critically for the constitution's TDD principle (II) — `confirm()` is difficult to drive
deterministically from a Vitest component test without global mocking that couples every delete test
to `window`. An in-component signal is trivial to assert against directly (`fixture` +
`nativeElement` queries), consistent with how `ConnectionStatusComponent` already tests state via
its own signal rather than a browser API.

**Alternatives considered**:
- *`window.confirm()`* — rejected per above (untestable without global mocking, blocks the event
  loop, unstyleable).
- *A separate modal/dialog component* — viable, but heavier than the row-count and interaction
  model here calls for (a handful of documents in a sidebar list, not a destructive action needing
  a full-screen focus trap); revisit only if the sidebar's scale assumption changes.

## Decision 5: Backend error codes are mapped to fixed, human-readable strings — never surfaced raw

**Decision**: Each service (`ChatService`, `DocumentsService`) translates a failed HTTP response's
`error` code (the closed vocabularies already defined in
[chat-api-contract.md](../007-chat-endpoint/contracts/chat-api-contract.md),
[ingestion-api-contract.md](../004-document-ingestion-endpoint/contracts/ingestion-api-contract.md),
[document-query-api-contract.md](../005-document-listing-download/contracts/document-query-api-contract.md),
and [006-document-delete's contract](../006-document-delete/contracts/document-delete-api-contract.md))
into one fixed, pre-written UI string per code via a small lookup table; the backend's own
`message` field and the raw `error` code are never rendered (`FR-007`, `FR-011`, `FR-014`, `FR-017`).
An unrecognized `error` code (or a network-level failure/no response at all) falls back to one
generic "something went wrong, please try again" string rather than failing to render.

**Rationale**: `FR-007` explicitly forbids showing raw error codes or backend text; the contracts
this feature reads already treat `message` as advisory/non-parseable for *any* caller, and the
constitution's Error Handling & Logging section reinforces that a caller should be able to act on
the status/code alone. A closed lookup table is the simplest structure that satisfies both: it is
exhaustive against each contract's documented `error` enumeration (so every documented case gets
deliberate UI copy, not a generic fallback), and it has an explicit default arm so no backend
response — however malformed — can leave the UI silently stuck (the specific edge case `FR-007`'s
"never leave the conversation in a stuck waiting state" targets).

**Alternatives considered**:
- *Show `message` from the backend directly* — rejected: `FR-007`/`FR-011` explicitly disallow this;
  the backend contracts also reserve the right to change `message`'s exact wording without notice.
- *One generic error message for every failure* — rejected: loses the distinction users need
  (e.g., "that file type isn't supported" vs. "the file was too large" vs. "the service is
  temporarily unavailable") that the backend's own closed `error` vocabularies already carry for
  free.

**Clarification — two codes may share display text within one flow, but the table stays exhaustive**:
"Exhaustive per contract enumeration" means every documented `error` code gets its own deliberate
entry in the lookup table — it does not mean every entry must render distinct text. The two flows
this feature builds resolve that independently, per their own FR:
- **Upload** (`FR-011`): `provider_unconfigured`, `processing_failed`, and a network-level failure
  with no response at all are three separate lookup-table entries that intentionally resolve to the
  same string ("the service is temporarily unavailable, please try again") — none is something the
  uploading user can act on differently, so inventing three distinct messages would just be
  distinguishing backend internals the user can't use.
- **Chat** (`FR-007`): `provider_unconfigured` and `processing_failed` keep separate, distinct
  messages — the chat contract's own commentary treats them as meaningfully different operator-facing
  conditions, and FR-007's "a distinct message per distinct documented failure cause" applies without
  the upload flow's grouping exception.

This asymmetry is deliberate, not an oversight: the same two backend codes get one shared string in
one flow's lookup table and two distinct strings in the other's, because each flow's own FR settles
the question independently.

## Decision 6: Question-length validation mirrors the backend constant, client-side, before sending

**Decision**: The chat input component enforces the same `MAX_QUESTION_LENGTH = 1000` (characters,
after trimming) that [007-chat-endpoint's data-model.md](../007-chat-endpoint/data-model.md) fixes
server-side, disabling the send control and showing an inline character-count/limit indicator once
the trimmed input would exceed it — never sending a request the backend is guaranteed to reject as
`question_too_long` (`FR-005`).

**Rationale**: `FR-005` requires feedback "before the request is sent," which is only possible if
the frontend knows the same limit the backend enforces. Since the limit is a fixed, spec-documented
constant (not discoverable or configurable via any endpoint this feature calls), duplicating it as a
frontend constant is the only way to satisfy `FR-005` — there is no request this feature could make
first to learn the limit dynamically without defeating the point of client-side validation.

**Alternatives considered**:
- *Let the backend be the only enforcement point, show its `question_too_long` error after the
  round trip* — rejected: this is exactly `FR-005`'s "before the request is sent" requirement,
  which this alternative violates by construction.

## Decision 7: Relevance score is rendered as a rounded percentage

**Decision**: `SourceCitation.score` (a `double` in `[0.5, 1.0]`, already rounded to two decimals by
the backend per 007's data-model.md) is displayed as a rounded whole-number percentage (e.g. `0.81`
→ `"81% match"`) on each citation badge, per the Clarifications session's decision to surface the
score at all.

**Rationale**: The raw decimal (`0.81`) is accurate but not self-explanatory to a non-technical
user glancing at a badge; a percentage with a short "match" label is immediately legible without
requiring the user to know cosine-similarity is a 0–1 scale. Since the backend already guarantees
every citation's score is ≥ `SIMILARITY_THRESHOLD` (0.5), the displayed range is always 50–100%,
never needing a "low confidence" visual treatment this feature doesn't otherwise define.

**Alternatives considered**:
- *Show the raw decimal (`0.81`)* — rejected: technically correct but reads as an arbitrary number
  to a non-technical user without a stated unit.
- *A qualitative label ("strong match" / "weak match") instead of a number* — rejected: invents a
  bucketing scheme (where do the boundaries sit?) the spec and backend contract don't define;
  a direct percentage transform of the score needs no invented thresholds.

## Decision 8: Testing strategy — Vitest + `HttpTestingController`, no new tooling

**Decision**: Every new service gets a `.spec.ts` using `TestBed` + `provideHttpClientTesting()` /
`HttpTestingController` (exactly `health.service.spec.ts`'s pattern) to assert request shape and
resulting signal state from canned fixture responses/errors, with no live backend or AI provider
call. Every new component gets a `.spec.ts` using `TestBed` + `fixture.nativeElement` DOM
assertions (`connection-status.component.spec.ts`'s pattern), with service dependencies either
provided as light stand-ins or exercised through the same `HttpTestingController` fixtures end to
end, per what makes each specific test simplest to write and read.

**Rationale**: This is already the codebase's established, working pattern (002) — introducing a
different testing approach (e.g. Angular Testing Library, MSW) for this feature alone would add a
second testing idiom to learn and maintain for no capability this feature actually needs; the
constitution's TDD principle (II) is satisfied by depth of coverage, not by tooling choice, and
"Tests MUST NOT require live AI provider credentials to pass" is trivially met since every backend
call this feature's tests exercise is itself HTTP-mocked.

**Alternatives considered**:
- *Angular Testing Library* — rejected: a new dependency and a different query idiom than the
  codebase's existing `.spec.ts` files use, for the same underlying capability (`TestBed` already
  does what's needed here).
- *End-to-end browser tests (e.g. Playwright) against a real running backend* — rejected: out of
  scope for this feature's automated suite (no prior feature in this repo has one either); the
  spec's Independent Test descriptions are validated manually via `quickstart.md` instead,
  consistent with how 004–007 validate their own `curl`-based quickstarts.

## Decision 9: The loading indicator's anti-flash delay is a fixed 300ms, applied in the component

**Decision**: `ChatService.pending` still flips to `true` synchronously the instant `ask()` accepts a
question (research Decision 2/4's usual source-of-truth signal) — `chat-input.component` disables the
input/send control off that signal immediately, with no delay. Separately, `chat-view.component`
renders its visible loading indicator (e.g. an assistant "thinking" placeholder appended to the
message list) only after a fixed `LOADING_INDICATOR_DELAY_MS = 300` has elapsed since `pending` became
`true`, using a component-local `setTimeout`/`signal` pair that is cancelled if `pending` returns to
`false` before the timer fires. 300ms is fixed directly in the component, the same way Decision 6
fixed `MAX_QUESTION_LENGTH` directly in `chat-input.component` — no new shared constant module is
justified for a single fixed value used in one place.

**Rationale**: `FR-006` requires a loading indicator that "MUST NOT appear for a response fast enough
to feel instantaneous," which is unmeasurable and untestable without a concrete number — the widely
used UX threshold for "feels instant" is roughly 100–300ms (Nielsen's response-time limits); 300ms is
chosen as the more conservative (fewer false-flashes) end of that range. Splitting the *disabling*
behavior (immediate, no delay) from the *visible indicator* (delayed) matters because disabling the
input the instant a request starts is itself a correctness requirement (FR-006's overlap-prevention,
also relied on by the Edge Cases section), while only the indicator's appearance is what "flash"
refers to — delaying the disable itself would let a user fire a second overlapping request in the
first 300ms.

**Alternatives considered**:
- *No delay — show the indicator the instant `pending` becomes `true`* — rejected: this is exactly
  the flash `FR-006` prohibits; a sub-300ms answer would show a loading indicator for one render frame
  and then immediately replace it, a visible flicker.
- *A longer delay (e.g. 1000ms)* — rejected: past roughly 300–400ms a user starts perceiving the UI as
  unresponsive with no feedback at all, which is worse than an occasional brief indicator.
- *Debounce/delay `pending` itself instead of a separate indicator-only timer* — rejected: `pending`
  is the disabling signal too (FR-006), and delaying it would let a user submit a second overlapping
  question during the delay window — exactly what `pending`'s immediate flip is there to prevent.

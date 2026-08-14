# Feature Specification: Frontend Health Wire

**Feature Branch**: `002-frontend-health-wire`

**Created**: 2026-08-14

**Status**: Draft

**Input**: User description: "let's add a first simple wire between backend and ui
backend already exposes http://localhost:8080/actuator/health api that ui can consume to indicate connection help status"

## Definitions

- **Reachable**: the frontend received *any* HTTP response from the backend health endpoint,
  regardless of status code. Distinguished from **unreachable** below.
- **Unreachable**: the frontend's request to the backend health endpoint failed before a response
  was received — connection refused, DNS/network failure, blocked cross-origin request, or the
  request timing out. This is a different situation from the backend responding but reporting
  itself unhealthy, and the two MUST be distinguishable to the person looking at the page (FR-005,
  FR-006).
- **Healthy** / **degraded**: the two states a *reachable* backend can report, taken directly from
  the existing contract (`contracts/health-api.md`): overall `status: "UP"` is healthy, overall
  `status: "DOWN"` (HTTP 503) is degraded. The Azure/AI-provider component never changes this
  distinction — per that contract, an unconfigured AI provider still reports overall `UP`.

## User Scenarios & Testing *(mandatory)*

The user of this feature is anyone who opens the frontend placeholder page — in practice, today,
the developer working on the PoC — who currently has no way to tell, from the page itself, whether
the backend they just started is actually up. This feature adds the first real connection between
the two previously-independent parts scaffolded in
[001-project-scaffolding](../001-project-scaffolding/spec.md): the frontend calls the backend's
existing `GET /actuator/health` endpoint and shows what it learns. It delivers no other backend
integration — no chat, no upload, no data of any kind flows between the parts yet.

### User Story 1 - See whether the backend is up, at a glance (Priority: P1)

A developer opens the frontend page while the backend is running and reachable. Without checking a
terminal, running `curl`, or opening browser dev tools, they see a clear, visible indicator on the
page telling them the backend is up.

**Why this priority**: This is the entire point of the feature — a first, minimal, visible signal
that the two parts of the application can talk to each other. Nothing else in this feature has
value without this working first.

**Independent Test**: With the database, backend, and frontend all running per
`specs/001-project-scaffolding/quickstart.md`, open the frontend in a browser and observe a
"connected/healthy" indicator on the page, with no manual verification step needed.

**Acceptance Scenarios**:

1. **Given** the backend is running and its health endpoint reports overall status `UP`, **When**
   the developer opens or reloads the frontend page, **Then** the page displays an indicator
   stating the backend connection is healthy, distinguishable at a glance from every other state
   this feature defines.
2. **Given** the indicator is showing the healthy state, **When** the developer inspects the
   browser console, **Then** no errors are logged as a result of the health check.

---

### User Story 2 - See when the backend cannot be reached at all (Priority: P2)

A developer opens the frontend page while the backend is not running, or stops the backend while
the page is already open. The page tells them the backend is unreachable, distinctly from telling
them the backend is running but unhealthy.

**Why this priority**: The most common state a developer will actually encounter while working —
forgetting to start the backend, or restarting it — is exactly this one. Without it, the feature's
only visible behavior is a silent blank/default state that looks the same as "not built yet."

**Independent Test**: With the frontend running and the backend stopped, open the frontend and
observe an "unreachable" indicator; then start the backend and, without reloading the page,
observe the indicator change to healthy within the refresh behavior defined by FR-006.

**Acceptance Scenarios**:

1. **Given** the backend is not running, **When** the developer opens the frontend page, **Then**
   the page displays an indicator stating the backend cannot be reached, visually distinct from
   the healthy state.
2. **Given** the page is open and showing the healthy state, **When** the backend is stopped,
   **Then** the indicator changes to the unreachable state on its own, within the refresh interval
   defined by FR-006, without the developer reloading the page.
3. **Given** the page is open and showing the unreachable state, **When** the backend is started
   (or restarted), **Then** the indicator changes to the healthy state on its own, within the same
   refresh interval, without a page reload.

---

### User Story 3 - See when the backend is up but reporting a problem (Priority: P3)

A developer opens the frontend page while the backend is running and reachable, but its own health
check reports a problem — for example, the database is unreachable, mirroring the degraded case
already defined in `specs/001-project-scaffolding/contracts/health-api.md`. The page tells them
this is a *different* situation from the backend being unreachable altogether.

**Why this priority**: Valuable diagnostic detail, and cheap to add once User Stories 1 and 2 exist
— the backend already reports this distinction (HTTP 503, `status: "DOWN"`), so this story is
mostly about not collapsing it away. It is lower priority because "some indicator changed" already
delivers most of the value; getting the three-way distinction exactly right is refinement on top.

**Independent Test**: With the backend running and its database stopped (per
`specs/001-project-scaffolding` User Story 1/2), open the frontend and observe a "degraded"
indicator distinct from both the healthy state and the unreachable state.

**Acceptance Scenarios**:

1. **Given** the backend is running and reachable but its health endpoint reports overall status
   `DOWN` (HTTP 503), **When** the developer opens the frontend page, **Then** the page displays an
   indicator stating the backend reports a problem, visually distinct from both the healthy state
   (Story 1) and the unreachable state (Story 2).
2. **Given** the AI provider is unconfigured but the database is reachable, **When** the developer
   opens the frontend page, **Then** the indicator shows the healthy state — an unconfigured AI
   provider alone does not change the backend's overall status, per the existing health contract,
   and this feature introduces no separate AI-provider indicator.

---

### Edge Cases

- **The health request never completes** (backend hangs, network stalls). The frontend does not
  wait indefinitely: after a bounded wait, it treats the attempt as unreachable. **(FR-007)**
- **The browser blocks the request as cross-origin.** From the page's point of view this is
  indistinguishable from any other failure to receive a response, and is reported as unreachable
  like any other. Making the request permitted in the first place is a backend-side requirement of
  this feature. **(FR-008)**
- **The backend's response is reachable but malformed** (unexpected shape, not valid JSON). Treated
  the same as unreachable, rather than surfaced as a fourth state or left to crash the page — this
  feature does not depend on any field beyond `status`, per the existing contract's "fields beyond
  these MUST NOT be asserted" rule. **(FR-009)**
- **The developer never opens the frontend while the backend is unreachable.** No special handling
  needed — Story 1 (healthy) and Story 2 (unreachable) are independent, symmetric checks of the
  same mechanism, not sequential states.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The frontend MUST display a connection-status indicator on its existing placeholder
  page — no new page or route is introduced by this feature.
- **FR-002**: On loading the page, the frontend MUST request the backend's existing health endpoint
  and reflect what it learns in the indicator, without requiring any user action to trigger the
  first check.
- **FR-003**: The indicator MUST represent at least three distinguishable states — healthy,
  degraded, and unreachable, as defined above — and MUST NOT collapse degraded and unreachable into
  a single "not working" state, since they mean different things to a developer (Story 2 vs.
  Story 3).
- **FR-004**: The indicator MUST reflect the healthy state whenever the backend's overall health
  status is `UP`, regardless of the AI-provider component's status, consistent with
  `specs/001-project-scaffolding/contracts/health-api.md`'s existing severity rules.
- **FR-005**: The indicator MUST reflect the degraded state whenever the backend responds but its
  overall health status is `DOWN`.
- **FR-006**: The frontend MUST re-check the backend's health on a recurring basis while the page
  remains open, so that the indicator reflects a backend that starts, stops, or recovers after the
  page was loaded, without requiring a manual page reload. The specific interval is an
  implementation detail left to the plan; it MUST be frequent enough that a developer restarting
  the backend sees the page catch up without wondering whether the page is broken (see SC-002).
- **FR-007**: A health request that does not complete within a bounded time MUST be treated as
  unreachable rather than left pending indefinitely.
- **FR-008**: The backend MUST permit the frontend's origin to call the health endpoint from the
  browser; a request blocked as cross-origin is a defect in this feature, not an accepted
  limitation (contrast with `contracts/health-api.md`'s prior "no CORS configuration" note, which
  this feature supersedes for the health endpoint specifically).
- **FR-009**: A reachable response that cannot be understood (unexpected shape or content) MUST be
  treated as unreachable rather than crashing the page or displaying a raw error.
- **FR-010**: This feature MUST NOT introduce any other data exchange between frontend and
  backend — no chat, no document upload, no additional endpoints. Only the existing health endpoint
  is consumed.
- **FR-011**: The indicator MUST NOT display or log any secret value. It reflects only the health
  endpoint's existing non-secret fields (see `contracts/health-api.md`'s security posture, which
  this feature does not change).

### Key Entities

- **Connection Status**: a transient, unpersisted, client-side-only representation of the backend's
  most recently observed health state — healthy, degraded, or unreachable — plus the time it was
  last observed. Held only in the running page; nothing about it is stored, sent anywhere else, or
  survives a page reload.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With the backend running and reachable, a developer opening the frontend page sees
  the healthy indicator within **5 seconds** of the page finishing loading.
- **SC-002**: With the frontend page already open, stopping the backend results in the indicator
  changing to the unreachable state within **15 seconds**, with no page reload; starting the
  backend again results in the indicator returning to the healthy state within the same window.
- **SC-003**: All three defined backend conditions (healthy, degraded via unreachable database,
  unreachable) each produce a **visually distinct** indicator state, verified by exercising all
  three and confirming no two look the same.
- **SC-004**: Opening the frontend page under any of the three backend conditions produces **zero**
  browser console errors caused by the health check's own application code (an explicit
  `console.error` call, or an uncaught exception thrown from the health-check code path). A
  browser-native network-failure line the browser itself logs for a genuinely unreachable backend
  (connection refused, or a request blocked as cross-origin) is expected in that condition and does
  not count against this criterion — the frontend has no control over whether the browser logs its
  own network diagnostics, only over its own code's behavior.
- **SC-005**: The feature requires **no new manual step** beyond the existing documented start
  commands for the database, backend, and frontend — a developer who already knows how to run the
  three parts (per `specs/001-project-scaffolding/README.md`) needs no additional setup to see the
  indicator work.

## Assumptions

- **The audience is the developer running the stack locally**, the same audience as
  `001-project-scaffolding`. This feature adds no end-user-facing account, authentication, or
  multi-user consideration — the PoC still has none.
- **The existing health endpoint and its response contract are unchanged by this feature.** This
  feature is a consumer of `specs/001-project-scaffolding/contracts/health-api.md`, not a
  modification of it, with the single exception of FR-008 (permitting the frontend's origin), which
  that contract explicitly left open ("no CORS configuration and no client contract to agree" — a
  scope statement of the prior feature, not a constraint on this one).
- **A short, recurring re-check (polling) is an acceptable and sufficient mechanism** for FR-006;
  nothing in this feature requires push-based or real-time updates, consistent with the PoC's local,
  single-developer scope already established in `001-project-scaffolding`'s Assumptions.
- **No retry/backoff policy beyond the recurring re-check itself is required.** A failed check
  simply shows "unreachable" until the next scheduled check succeeds; there is no separate manual
  "retry now" control in this feature.
- **The three-state model (healthy / degraded / unreachable) is sufficient.** It does not attempt to
  surface the AI-provider configuration state (`UNKNOWN` in the health contract) as a distinct
  fourth indicator state — that stays folded into "healthy," per FR-004 and Story 3 Scenario 2,
  because this feature is about connectivity between the two parts, not about AI-provider readiness.
- **Local development only**, same as `001-project-scaffolding`: fixed local addresses
  (`http://localhost:8080`), no deployment target, no production CORS policy considerations.

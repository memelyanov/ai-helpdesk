# Phase 0 Research: Frontend Health Wire

**Feature**: [plan.md](plan.md) | **Date**: 2026-08-14

Six decisions, each resolving one open question from the Technical Context or the spec's
Assumptions. Verified against the toolchain already installed for
[001-project-scaffolding](../001-project-scaffolding/plan.md) — no new major dependency is
introduced by this feature, so there was nothing to re-verify against a live registry.

## Decision 1 — HTTP client and reactivity: `HttpClient` + RxJS, exposed as a signal

**Decision**: Add `provideHttpClient()` to `app.config.ts` and use `HttpClient.get<HealthResponse>`
inside a small `HealthService`. Compose the polling behavior with RxJS operators
(`timer`, `switchMap`, `timeout`, `catchError`, `map`) already available transitively through
Angular, and expose the result to the template as a `Signal<ConnectionStatus>` via `toSignal`.

**Rationale**: `frontend/src/app/app.config.ts` (read 2026-08-14) has no HTTP provider yet — this is
the first feature to need one. `HttpClient` is the framework-native choice; adding `fetch` directly
would work but throws away RxJS's `timeout`/`catchError` composition and Angular's testing support
(`provideHttpClientTesting`, `HttpTestingController`), which is exactly what FR-007 (bounded wait)
and FR-009 (malformed-response handling) need to test without a real backend. `rxjs` is already a
dependency (`frontend/package.json`, confirmed 2026-08-14) so no `package.json` change beyond
enabling the provider.

The service exposes a **signal**, not an `Observable` the component subscribes to, because
`frontend/src/main.ts` and `app.config.ts` (both read 2026-08-14) show no `zone.js` import and no
`provideZoneChangeDetection` call — this scaffold is Angular 21's zoneless default. Signal writes
notify the zoneless change-detection graph directly; an `Observable` the template awaited via the
`async` pipe would too, but a signal keeps the component template a plain `@if` on `status()`
rather than an `| async` chain with definedness checks. Either works under zoneless; the signal was
chosen for template simplicity, not because the alternative is broken.

**Alternatives considered**:
- **Raw `fetch` + `AbortController`** for the timeout — rejected: reinvents what `HttpClient` +
  `timeout()` already gives, and loses `HttpTestingController` for tests.
- **A third-party polling library** — rejected: RxJS's `timer`/`switchMap` is already sufficient
  and already present; adding a package for this would be unjustified weight for one polling loop.
- **`Observable` + `async` pipe** in the template instead of a signal — viable under zoneless, not
  chosen; see rationale above.

## Decision 2 — Polling interval and per-request timeout: 10s interval, 3s timeout

**Decision**: Poll every **10 seconds**, starting immediately at 0 (`timer(0, 10000)`); bound each
individual request to **3 seconds** (`timeout(3000)`) before treating it as unreachable.

**Rationale**: Both numbers are derived directly from the spec's own success criteria, not chosen
independently of them:
- **SC-001** (healthy indicator within 5s of page load): the first poll fires at `t=0`; on a local
  developer machine a healthy response returns in well under a second, and even the 3s timeout
  bound leaves headroom under the 5s budget.
- **SC-002** (indicator reflects a stop/start within 15s, no reload): the worst case is a backend
  that stops **just after** a poll succeeded — the next poll doesn't fire for up to 10s, and if it
  hangs rather than failing immediately (a hung request is the case FR-007 specifically calls out),
  it consumes up to another 3s before the timeout resolves it to unreachable. `10s + 3s = 13s`,
  inside the 15s budget with margin. A connection actually *refused* (the far more common case when
  a process is simply not running) fails near-instantly and does not consume the timeout budget at
  all.
- 10 seconds is also infrequent enough that a developer leaving the tab open all day does not
  generate a noticeable stream of requests against a health endpoint that itself does no I/O to
  Azure (per `contracts/health-api.md`) but does still touch the database connection pool.

**Alternatives considered**:
- **1–2s interval** for a snappier feel — rejected: well inside budget but adds ~10x the request
  volume for no acceptance-criteria benefit; SC-002's 15s budget does not need it.
- **30s+ interval** — rejected: fails SC-002's 15s budget on its own (a single interval tick alone
  would exceed it).
- **No timeout, rely on the browser's own connection timeout** — rejected: browser/OS-level TCP
  timeouts run well past 15s on some networks, directly violating SC-002 and the FR-007 edge case
  ("the health request never completes").

## Decision 3 — CORS: `management.endpoints.web.cors.*`, not a `WebMvcConfigurer` bean

**Decision**: Add to `backend/src/main/resources/application.yml`:

```yaml
management:
  endpoints:
    web:
      cors:
        allowed-origins: http://localhost:4200
        allowed-methods: GET
```

**Rationale**: Spring Boot Actuator endpoints are served through `WebMvcEndpointHandlerMapping`,
which is a separate request-mapping path from the application's own `@RestController`s and does
**not** inherit CORS configuration registered via a `WebMvcConfigurer` `addCorsMappings` bean — the
Spring Boot reference documentation states Actuator's CORS support is configured independently for
exactly this reason. This feature has no other `@RestController` and no other CORS need
(`contracts/health-api.md`'s "Not in this contract" list confirms `/documents` and `/chat` don't
exist yet), so a dedicated MVC CORS configuration class would add a class to configure nothing else
today. The origin is hardcoded to `http://localhost:4200` rather than sourced from an environment
variable, consistent with 001-project-scaffolding's Assumption that fixed local ports need no
negotiation strategy in this PoC phase — the same reasoning that let 001 hardcode ports 5432/8080/
4200 in the first place.

**Alternatives considered**:
- **A `WebMvcConfigurer` bean with `addCorsMappings`** — rejected: does not affect Actuator's
  separate handler mapping; would compile and pass review but silently fail to fix the problem.
- **`@CrossOrigin` on a custom controller wrapping health** — rejected: reimplements an endpoint
  Actuator already provides correctly, purely to attach an annotation; direct configuration is
  strictly less code.
- **Wildcard origin (`*`)** — rejected: broader than needed for a single known local frontend
  origin, and `allowCredentials` interactions with wildcard origins are exactly the kind of subtlety
  a fixed, explicit origin avoids entirely.

## Decision 4 — Classifying a response into a state: read only `status`

**Decision**: The frontend reads exactly one field from the response body — the top-level
`$.status` string — and maps `"UP"` → `healthy`, `"DOWN"` → `degraded`, anything else (including a
response that fails to parse as JSON, or is missing the field) → treated the same as a network
failure, i.e. `unreachable`.

**Rationale**: `contracts/health-api.md` (001-project-scaffolding) explicitly reserves the right for
"fields beyond these" — everything under `components` — to vary with the Actuator version and states
they "MUST NOT be asserted" by consumers. Reading only `$.status`, which *is* one of the fields the
contract guarantees, is the only choice that doesn't create a second, undocumented dependency on
`components.db` / `components.azureOpenAi` shape. It also directly satisfies spec FR-009 ("a
reachable response that cannot be understood... MUST be treated as unreachable") and Story 3
Scenario 2 (an unconfigured AI provider alone must not change the indicator away from healthy) —
since Azure being `UNKNOWN` never flips the *overall* `status` away from `UP` per that same
contract's severity-ordering guarantee, reading only `status` gets that behavior for free without
the frontend needing to know Azure exists at all.

**Alternatives considered**:
- **Also surface `components.db` / `components.azureOpenAi` individually** — rejected as *out of
  scope by the spec itself* (Assumptions: "does not attempt to surface the AI-provider
  configuration state... as a distinct fourth indicator state"); would also reintroduce the
  forbidden dependency on non-guaranteed fields.
- **Treat any non-2xx HTTP status as unreachable rather than reading the body** — rejected: `503` is
  a defined, reachable, degraded case per the contract (Case B), and collapsing it into
  "unreachable" would violate FR-003's required three-way distinction.

## Decision 5 — State model: four states in code, three required by the spec

**Decision**: The `ConnectionStatus` type has four members — `'checking' | 'healthy' | 'degraded' |
'unreachable'` — where `checking` is the transient value held only until the very first poll
resolves, and is never re-entered afterward (a later poll simply keeps showing the previous
resolved state, per Decision 1's `switchMap`-on-timer composition, until its own result arrives).

**Rationale**: FR-002 requires the first check to fire automatically on load with no user action,
which implies a brief window before any response has arrived — rendering nothing, or defaulting
silently to one of the three required states, would either look broken or misrepresent the backend
before it's actually been asked. FR-003's "at least three distinguishable states" permits a fourth;
it forbids collapsing degraded and unreachable into each other, which `checking` does not do. No
acceptance scenario or success criterion tests for `checking`'s absence — SC-001/SC-002's clocks
start at page load / backend stop respectively and both allow enough time (5s, 15s) for a resolved
state to be showing by the time anyone is asserting one, so `checking` is expected to have already
resolved.

**Alternatives considered**:
- **Default to `unreachable` until the first response** — rejected: indistinguishable from an actual
  failure, which could read as the feature being broken on a perfectly healthy first load.
- **Render nothing until the first response** — rejected: contradicts FR-001's "MUST display a
  connection-status indicator" being unconditional; an indicator that is sometimes simply absent for
  up to 3 seconds is a weaker reading of that requirement than a fourth transient value.

## Decision 6 — Testing without a real backend: `HttpTestingController` + fake timers

**Decision**: `health.service.spec.ts` uses `provideHttpClientTesting()` and
`HttpTestingController` to script the four provable-without-a-backend scenarios (200 `UP`, 503
`DOWN`, `req.error()` for a network failure, and a request left unflushed past the timeout window
under `vi.useFakeTimers()`) and asserts the resulting signal value after each. `connection-
status.spec.ts` tests the pure classification function directly with plain objects/strings, no HTTP
involved at all. The 200/`UP` case is seeded with the full real response body captured from a
running backend — see [contracts/frontend-health-consumption.md](contracts/frontend-health-consumption.md#reference-fixture-captured-2026-08-14)
— rather than a hand-trimmed `{ status: 'UP' }` stub, so the test proves indifference to
`diskSpace`/`ssl`/extra components instead of merely assuming it.

**Rationale**: Constitution Principle II requires tests to not need live credentials or a live
dependency; `HttpTestingController` is Angular's own tool for exactly this, already implicitly
available via `@angular/common/http/testing` once `@angular/common` is a dependency (confirmed in
`frontend/package.json`). Fake timers make Decision 2's 10s/3s real-world numbers testable in
milliseconds of wall-clock test time rather than requiring the suite to actually wait.

**Alternatives considered**:
- **Spin up the real backend in the frontend test suite** — rejected: reintroduces exactly the
  cross-process dependency Constitution Principle II and 001-project-scaffolding's own test design
  (stubbed AI provider, no Testcontainers) both deliberately avoid.
- **Mock at the `fetch`/global level instead of `HttpTestingController`** — rejected: more manual
  setup for the same guarantee `HttpTestingController` gives natively for `HttpClient`-based code.

# Contract: Frontend Consumption of `GET /actuator/health`

**Feature**: [Frontend Health Wire](../spec.md) | **Serves**: FR-002 through FR-009 | **Date**:
2026-08-14

This is not a new HTTP surface — it is the frontend's documented contract with the endpoint
`001-project-scaffolding` already defines in
[health-api.md](../../001-project-scaffolding/contracts/health-api.md). That document remains the
source of truth for the response shape; this document states what this feature promises to do with
it, and the one thing this feature requires *of the backend* that the prior contract did not
provide.

## What the frontend reads

Exactly one field of the response body: top-level `$.status`. See research.md Decision 4 for why —
in short, it is the only field `health-api.md` guarantees stable, and it already carries the
healthy/degraded distinction this feature needs without the frontend knowing anything about
`components.db` or `components.azureOpenAi`.

| `$.status` value | HTTP status seen | Frontend classification |
|---|---|---|
| `"UP"` | `200` | `healthy` |
| `"DOWN"` | `503` | `degraded` |
| (no response received) | — | `unreachable` |
| (response received, body unparseable or `status` field missing/other) | any | `unreachable` |

This table is a direct restatement of `health-api.md`'s Case A/B/C — Case C (Azure unconfigured) is
not a separate row here because it is still HTTP `200` / `$.status: "UP"`, i.e. `healthy`, by that
contract's own severity-ordering guarantee.

## What the frontend does *not* read

`$.components.*` in its entirety — including `db`, `azureOpenAi`, and `ping`. `health-api.md`
explicitly reserves these as allowed to vary with the Actuator version; depending on them here would
create an undocumented second contract. If a future feature needs to surface database or
AI-provider status specifically in the UI, it should extend *this* document deliberately, not rely
on this feature having quietly already read those fields.

## Request behavior the frontend guarantees

- One request is issued at page load (`t=0`), then one every 10 seconds for as long as the page
  remains open (research.md Decision 2). Nothing about this cadence is configurable by the response
  content — it does not speed up or slow down based on what the backend reports.
- Each request is abandoned and classified as `unreachable` if no response arrives within 3 seconds
  (FR-007).
- No request carries any payload, header, or query parameter beyond what `HttpClient.get` sends by
  default. No credentials, cookies, or authentication of any kind — matching `health-api.md`'s own
  "whole surface is unauthenticated" posture.
- The frontend never retries a single failed check out-of-band; the next scheduled poll is the only
  retry mechanism (spec Assumptions).

## What this feature requires of the backend that the prior contract did not

`health-api.md`'s Consumers section previously stated: *"None in this feature. The frontend makes
no backend calls, so there is no CORS configuration and no client contract to agree."* That is now
superseded for the health endpoint specifically. This feature requires:

- The backend permits cross-origin `GET` requests to `/actuator/health` from
  `http://localhost:4200` (FR-008), via `management.endpoints.web.cors.*` (research.md Decision 3)
  — a configuration addition, not a change to the response body contract itself.
- No other endpoint, method, or origin is opened. `POST /documents` and `POST /chat` remain
  undefined; this feature does not stub them (spec FR-010).

## Reference fixture (captured 2026-08-14)

A real response from a running backend, used as the `healthy` fixture in `health.service.spec.ts`
in preference to a hand-trimmed minimal stub — specifically *because* it carries extra top-level
components (`diskSpace`, `ssl`) beyond `db` and `azureOpenAi`. Testing against the full real shape,
not a convenient minimal one, is what proves Decision 4's "read only `$.status`" claim rather than
merely asserting it:

```json
{
  "status": "UP",
  "components": {
    "azureOpenAi": {
      "status": "UP",
      "details": { "configured": true, "endpointConfigured": true, "chatDeploymentConfigured": true }
    },
    "db": {
      "status": "UP",
      "details": { "database": "PostgreSQL", "validationQuery": "isValid()" }
    },
    "diskSpace": {
      "status": "UP",
      "details": { "total": 509218910208, "free": 163631063040, "threshold": 10485760, "path": "C:\\Epam\\ai-helpdesk\\backend\\.", "exists": true }
    },
    "ping": { "status": "UP" },
    "ssl": {
      "status": "UP",
      "details": { "validChains": [], "invalidChains": [] }
    }
  }
}
```

Also mirrored in [001-project-scaffolding/contracts/health-api.md](../../001-project-scaffolding/contracts/health-api.md)
Case A, which this fixture is copied from — kept in sync manually since the two documents serve
different audiences (that one documents the backend's contract; this one documents what a test
seeds `HttpTestingController` with). The `degraded` and `unreachable` test cases don't need a
similarly "real" fixture: `degraded` only needs `$.status: "DOWN"` with any `components` object
(untouched by the frontend regardless of shape), and `unreachable` involves no body at all
(`req.error()` / a request left unflushed past the timeout, per research.md Decision 6).

## Not in this contract

- Any change to the response body shape, status codes, or component names defined in
  `health-api.md` — unchanged by this feature.
- Any authentication, since none exists on either side.
- Any retry, backoff, or manual "retry now" affordance — explicitly out of scope (spec Assumptions).
- Surfacing `components.db` or `components.azureOpenAi` individually in the UI — explicitly out of
  scope (spec Assumptions; see "What the frontend does not read" above).

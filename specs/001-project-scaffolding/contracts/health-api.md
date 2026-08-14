# Contract: Backend Health API

**Feature**: [Project Scaffolding](../spec.md) | **Serves**: FR-006, FR-007, FR-020 | **Date**: 2026-08-13 (revised)

The only external interface the backend exposes in this feature. `POST /documents` and
`POST /chat` are mandated by the constitution but belong to the ingestion and chat features — they
do not exist yet, and this feature must not stub them.

## `GET /actuator/health`

Provided by Spring Boot Actuator with `management.endpoint.health.show-details: always`. Only the
`health` endpoint is exposed over HTTP.

Two components are specific to this application: `db` (built-in `DataSourceHealthIndicator`) and
`azureOpenAi` (custom, no network I/O — see [ai-provider.md](ai-provider.md)). Actuator also
auto-registers its own generic indicators whenever they're on the classpath — observed in practice:
`diskSpace`, `ping`, and `ssl`. These are stock Actuator behaviour, not something this feature adds
or configures, and — per the "fields beyond these MUST NOT be asserted" rule below — their presence,
absence, or shape is explicitly not guaranteed by this contract. The example below shows a captured
real response (2026-08-14) including them, so implementers aren't surprised by extra top-level
component keys that neither this document's earlier examples nor `db`/`azureOpenAi` account for.

### Case A — database reachable, Azure configured

**Status**: `200 OK`
**Content-Type**: `application/vnd.spring-boot.actuator.v3+json`

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

*(Captured from a running instance on 2026-08-14; component key order and the presence of
`diskSpace`/`ssl` reflect the actual Actuator/Spring Boot version installed at capture time, not a
guarantee — see below.)*

**Guaranteed by contract**:

- HTTP `200`; `$.status` is `"UP"`
- `$.components.db.status` is `"UP"`; `$.components.db.details.database` is `"PostgreSQL"`
- `$.components.azureOpenAi.status` is `"UP"`
- **No field anywhere in the response contains the API key**, in whole or in part

Fields beyond these — including whether `diskSpace` and `ssl` appear at all, their `details` shape,
and `$.components` key order — may vary with the Actuator version and the machine it runs on, and
MUST NOT be asserted.

### Case B — database unreachable

The contract that makes FR-007 testable: the service is **running and answering** while reporting
that its dependency is not.

**Status**: `503 Service Unavailable`

```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "DOWN",
      "details": { "error": "org.springframework.jdbc.CannotGetJdbcConnectionException: ..." }
    },
    "azureOpenAi": { "status": "UP", "details": { "configured": true } },
    "ping": { "status": "UP" }
  }
}
```

**Guaranteed by contract**:

- The application is running and the endpoint responds — it does not time out, refuse the
  connection, or crash the process
- HTTP `503`; `$.status` is `"DOWN"`
- `$.components.db.status` is `"DOWN"` — the `db` component key is what identifies the database as
  the failing dependency, satisfying FR-007's "names the failing dependency"
- `$.components.db.details.error` is present, non-empty, and **carries the underlying connection
  exception** — its type and message, not a generic substitute. FR-007 requires the cause to be
  diagnosable from the response alone, without reading logs or source.

The exception's **exact message text** is environment-dependent and MUST NOT be asserted verbatim.
Assert that the field is present and that it names the underlying exception type; do not assert the
full string. This is the boundary between FR-007's diagnosability requirement and a brittle test.

### Case C — Azure unconfigured, database reachable

The contract that makes FR-020 and SC-009 testable: a developer with **no Azure credentials at all**
still gets a healthy service.

**Status**: `200 OK`

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL" } },
    "azureOpenAi": {
      "status": "UNKNOWN",
      "details": { "configured": false, "missing": ["api-key", "endpoint", "chat-deployment-name"] }
    },
    "ping": { "status": "UP" }
  }
}
```

**Guaranteed by contract**:

- HTTP `200` — **the overall status stays `UP`**. Spring Boot's default severity ordering is
  `DOWN, OUT_OF_SERVICE, UP, UNKNOWN` and the aggregate takes the most severe present, so an
  `UNKNOWN` component cannot pull a healthy service down.
- `$.components.azureOpenAi.status` is `"UNKNOWN"`
- `$.details.missing` names each absent setting, so the fix is obvious without reading source
- Partial configuration (FR-021) produces the same `UNKNOWN` status with a shorter `missing` list —
  it is never reported as `UP`
- No request is made to Azure while producing this response

### Combined degradation — database unreachable and Azure unconfigured together

Not a fourth case: the `db` and `azureOpenAi` components are computed independently and simply
appear together, each exactly as documented in Case B and Case C respectively. Aggregation takes
the most severe status present (`DOWN` outranks `UNKNOWN`), so the overall result is Case B's `503`
with `azureOpenAi` still reporting `UNKNOWN` and its `missing` list in the same payload. No special
handling is required or specified beyond composing the two cases already defined.

### Field-name note (Case A vs Case B)

Case A's `azureOpenAi.details` shows three booleans (`configured`, `endpointConfigured`,
`chatDeploymentConfigured`); Case B's example shows only `configured` for brevity. This is not a
contract difference — the same `azureOpenAi` component shape applies regardless of `db`'s status,
per the "fields beyond these MUST NOT be asserted" rule above. Likewise, Cases B and C omit
`diskSpace` and `ssl` for brevity; they still appear in a real response in those cases exactly as
they do in Case A — omitting them here does not mean they're absent, and asserting either their
presence or absence is equally unsupported by this contract.

### Why 503 for the database but 200 for Azure

They mean different things. An unreachable database is a **fault**: the service is configured to
use one and cannot. An unconfigured AI provider is a **state**: this feature uses no AI capability,
so its absence prevents nothing. Returning 503 for a missing Azure key would tell every uptime
check that a perfectly functional scaffold is broken, and would make the scaffold unusable for
anyone without Azure access — which SC-009 explicitly forbids.

Note the limit this creates: `azureOpenAi: UP` means *configured*, not *working*. Wrong credentials
still report `UP` here, because the indicator makes no call. Proving credentials work is the job of
`backend/mvnw test -Pverify-ai`.

## Consumers

None in this feature — the frontend made no backend calls when this document was written. That is
superseded by [002-frontend-health-wire](../../002-frontend-health-wire/spec.md), which polls this
endpoint from the browser and requires CORS to be enabled for it. See that feature's
[frontend-health-consumption.md](../../002-frontend-health-wire/contracts/frontend-health-consumption.md)
for the client contract; the response shape documented above is unchanged by it. Beyond that
consumer, this endpoint is used by the backend's own contract tests and by a developer with a
browser or `curl`.

## Security posture

`show-details: always` returns connection error text and configuration status to unauthenticated
callers, and the whole surface is unauthenticated — consistent with the PoC scope, which excludes
authentication.

The API key is never exposed: the health payload reports only booleans and the names of missing
settings, never values. Constitution v1.3.0 requires this ("API keys MUST NOT appear in logs, error
messages, or responses under any circumstance") and a test asserts it.

**This is a local-development-only posture.** Before any deployment beyond a developer machine,
`show-details` must be restricted (`when-authorized` or `never`) and the actuator surface secured.

## Not in this contract

- `POST /documents` — ingestion feature
- `POST /chat` — chat feature
- Any authentication or rate limiting
- Any CORS *policy value* (which origins, methods) — that's owned by
  [002-frontend-health-wire](../../002-frontend-health-wire/contracts/frontend-health-consumption.md),
  per the Consumers section above; this document only guarantees the response body shape stays the
  same regardless of what CORS configuration exists
- Any actuator endpoint other than `health`
- Any endpoint that calls Azure — the verification is a tagged test, not an HTTP surface

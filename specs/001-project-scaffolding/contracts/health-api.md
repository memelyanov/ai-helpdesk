# Contract: Backend Health API

**Feature**: [Project Scaffolding](../spec.md) | **Serves**: FR-006, FR-007 | **Date**: 2026-08-13

The only external interface the backend exposes in this feature. `POST /documents` and
`POST /chat` are mandated by the constitution but belong to the ingestion and chat features —
they do not exist yet, and this feature must not stub them.

## `GET /actuator/health`

Provided by Spring Boot Actuator with `management.endpoint.health.show-details: always`.

### Case A — database reachable

**Status**: `200 OK`
**Content-Type**: `application/vnd.spring-boot.actuator.v3+json`

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

**Guaranteed by contract** (a test asserts each):

- HTTP status is `200`
- `$.status` is `"UP"`
- `$.components.db.status` is `"UP"`
- `$.components.db.details.database` is `"PostgreSQL"`

Fields beyond these may vary with the Actuator version and MUST NOT be asserted.

### Case B — database unreachable

This is the contract that makes FR-007 testable: the service is **running and answering** while
reporting that its dependency is not.

**Status**: `503 Service Unavailable`

```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "DOWN",
      "details": {
        "error": "org.springframework.jdbc.CannotGetJdbcConnectionException: ..."
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

**Guaranteed by contract**:

- The application is running and the endpoint responds — it does not time out, refuse the
  connection, or crash the process
- HTTP status is `503`
- `$.status` is `"DOWN"`
- `$.components.db.status` is `"DOWN"`
- `$.components.db.details.error` is present and non-empty

The exact exception text is environment-dependent and MUST NOT be asserted.

### Why 503 rather than 200

A caller asking "can this service do its job?" gets an honest no. Returning `200` with a `DOWN`
component would report success to any load balancer or uptime check that reads only the status
line, and would make the two cases indistinguishable without parsing the body. The spec's
acceptance scenario US2-3 requires the failure to be *visible*; a 503 is what makes it so.

## Consumers

None in this feature. The frontend makes no backend calls (spec assumption), so there is no
CORS configuration and no client contract to agree. The consumers here are the backend's own
contract test and a developer with a browser or `curl`.

## Security posture

`show-details: always` returns connection error text to unauthenticated callers, and the whole
surface is unauthenticated — consistent with the PoC scope, which excludes authentication.

**This is a local-development-only posture.** Before any deployment beyond a developer machine,
`show-details` must be restricted (`when-authorized` or `never`) and the actuator surface must be
secured. Recorded here so the decision is inherited deliberately rather than by omission.

## Not in this contract

- `POST /documents` — ingestion feature
- `POST /chat` — chat feature
- Any authentication, rate limiting, or CORS policy
- Any actuator endpoint other than `health` (`/actuator/**` exposure stays limited to `health`)

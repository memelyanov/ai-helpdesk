# Quickstart: Frontend Health Wire

**Feature**: [plan.md](plan.md) | **Date**: 2026-08-14

This validates the feature end-to-end, on top of an already-working scaffold. It assumes
`specs/001-project-scaffolding/quickstart.md` has already been followed at least once (Docker,
Java, Node installed; `.env` created from `.env.example`).

## Prerequisites

Same as [001-project-scaffolding](../001-project-scaffolding/quickstart.md): Docker Desktop, Java
17, Node.js (Angular 21 range). No new tooling is introduced by this feature.

## 1. Start the stack

```powershell
docker compose up -d
cd backend; .\mvnw.cmd spring-boot:run
```

```powershell
cd frontend; npm start
```

(Any order works, per FR-013/001-project-scaffolding — the frontend serves regardless of backend
state; it's the *indicator* that changes.)

## 2. See the healthy state (User Story 1)

Open `http://localhost:4200`. Within 5 seconds, the page shows a connection-status indicator in the
healthy state (see `data-model.md` for the exact state name used in the component).

**Check**: open the browser's network tab — a `GET http://localhost:8080/actuator/health` request
appears, `200`, and repeats roughly every 10 seconds. No console errors.

## 3. See the unreachable state (User Story 2)

With the frontend page still open, stop the backend (`Ctrl+C` in its terminal, or
`docker compose` is irrelevant here — only the backend process matters).

**Check**: within 15 seconds, without reloading the page, the indicator changes to the unreachable
state, visually distinct from the healthy state observed in step 2.

Restart the backend (`.\mvnw.cmd spring-boot:run`).

**Check**: within another 15 seconds, without reloading the page, the indicator returns to the
healthy state.

## 4. See the degraded state (User Story 3)

With the backend running, stop only the database:

```powershell
docker compose stop db
```

**Check**: within 15 seconds, the indicator shows the degraded state — distinct from both healthy
(step 2) and unreachable (step 3). Confirm directly against the backend, per
`specs/001-project-scaffolding/quickstart.md`'s own health check section: `GET
http://localhost:8080/actuator/health` returns `503` with `$.status: "DOWN"`.

Restart the database (`docker compose start db`) and confirm the indicator returns to healthy
within 15 seconds.

## 5. Confirm an unconfigured AI provider does not affect the indicator (Story 3, Scenario 2)

With no Azure environment variables set (the default per `.env.example`) and the database running,
confirm the indicator shows healthy — matching `health-api.md` Case C's `200`/`"UP"` overall status
despite `azureOpenAi` reporting `UNKNOWN`.

## 6. Run the automated suites

```powershell
cd backend; .\mvnw.cmd test
```

Expect the existing suite plus one new passing test asserting the CORS header on
`/actuator/health` (see `research.md` Decision 3).

```powershell
cd frontend; npm test
```

Expect the existing suite plus the new `connection-status` and `health.service` specs, all green,
none requiring the backend to be running (see `research.md` Decision 6).

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Indicator stuck on "checking" past 5s with backend confirmed running | CORS not applied — check browser console for a cross-origin error | Confirm `management.endpoints.web.cors.allowed-origins` is present in `application.yml` and the backend was restarted after adding it |
| Indicator shows unreachable while `curl http://localhost:8080/actuator/health` succeeds | Same as above — CORS is a browser-enforced restriction; `curl` isn't subject to it, so it succeeding doesn't rule out a CORS problem | Same fix |
| Indicator never updates after a backend restart | Page open from before the polling code existed, or browser tab was backgrounded and its timers throttled | Reload the page once; this is expected browser behavior for background tabs, not a defect in this feature |

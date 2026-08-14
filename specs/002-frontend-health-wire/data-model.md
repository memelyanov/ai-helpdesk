# Phase 1 Data Model: Frontend Health Wire

**Feature**: [plan.md](plan.md) | **Serves**: spec Key Entities ("Connection Status"), FR-003,
FR-006 | **Date**: 2026-08-14

There is no persisted data in this feature (see plan.md Technical Context: Storage — N/A). The one
entity worth modeling is the in-memory client state the spec names, because its shape and
transitions are exactly what the acceptance scenarios in User Stories 1–3 exercise.

## Entity: Connection Status

A transient value held only in the running frontend page. Never written to storage, never sent
anywhere, does not survive a page reload (spec Key Entities, spec Assumptions).

| Field | Type | Meaning |
|---|---|---|
| `state` | `'checking' \| 'healthy' \| 'degraded' \| 'unreachable'` | The classification of the most recent poll outcome (see research.md Decisions 4–5). |
| `lastCheckedAt` | `Date` | When the poll that produced the current `state` was issued. Drives no requirement directly; included so the indicator can optionally show recency, and so tests can assert a poll actually happened rather than the state being stale from initialization. |

### States

- **`checking`** — the initial value, before the first poll has resolved. Entered exactly once, at
  service construction; never re-entered. See research.md Decision 5.
- **`healthy`** — the backend responded and its overall `status` field was `"UP"`. Corresponds to
  spec Story 1 and Story 3 Scenario 2 (an unconfigured AI provider alone still yields `healthy`,
  because the backend's own `status` stays `"UP"` in that case per
  `001-project-scaffolding/contracts/health-api.md`).
- **`degraded`** — the backend responded and its overall `status` field was `"DOWN"`. Corresponds
  to spec Story 3 Scenario 1.
- **`unreachable`** — either no response was received (network failure, blocked cross-origin
  request, timeout — spec Definitions and Edge Cases) or a response was received but could not be
  understood (FR-009). `degraded` and `unreachable` are deliberately distinct terminal
  classifications; neither is a special case of the other (FR-003).

### Transitions

```text
        ┌───────────┐
        │ checking  │  (initial; entered once, at construction)
        └─────┬─────┘
              │ first poll resolves
              ▼
   ┌────────────────────────────────────────────┐
   │                                              │
   ▼                                              │
┌─────────┐   poll: status="UP"        ┌──────────────┐
│ healthy │◄───────────────────────────┤              │
└────┬────┘                            │  (any state) │
     │ poll: status="DOWN"             │              │
     ▼                                 │              │
┌──────────┐                           │              │
│ degraded │◄──────────────────────────┤              │
└────┬─────┘                           │              │
     │ poll: no/unparseable response   │              │
     ▼                                 │              │
┌─────────────┐                        │              │
│ unreachable │◄───────────────────────┴──────────────┘
└─────────────┘   every poll's outcome can move to any of
                   the three resolved states directly —
                   there is no required adjacency between
                   healthy / degraded / unreachable.
```

Every poll after the first is independent: whichever of `healthy`, `degraded`, or `unreachable` the
outcome classifies to becomes the new `state` immediately, regardless of what the previous `state`
was (spec Story 2 Scenarios 2–3: unreachable → healthy and back, without a reload, on the very next
tick — no intermediate state required).

### Invariants

- Exactly one of the four values holds at any time; there is no "unknown/none" value once
  `checking` has resolved.
- `state` only ever changes as the direct result of a poll's outcome (Decision 1's `switchMap`
  composition) — never by a timer alone, and never by user interaction, since this feature defines
  no retry-now control (spec Assumptions).

# Operational Resilience Requirements Checklist: Project Scaffolding

**Purpose**: Validate that the failure, degradation and recovery requirements — port conflicts,
stale volumes, mid-run restarts, partial startup — are complete, unambiguous and traceable to
functional requirements rather than living only as narrative edge cases.
**Created**: 2026-08-13
**Depth**: Formal gate — resolve before `/speckit-tasks`
**Scope audited**: [spec.md](../spec.md) + [contracts/runtime-surface.md](../contracts/runtime-surface.md),
[data-model.md](../data-model.md)
**Feature**: [spec.md](../spec.md)

> This checklist tests the **requirements**, not the implementation. Every item asks whether
> something is adequately *written*, not whether the stack recovers.

## Requirement Completeness — Edge Cases Lack Backing Requirements

- [x] CHK001 - Is the **port-conflict** behaviour backed by a functional requirement? It appears as an
      Edge Case bullet and as a contract "Failure behaviour" note, but no FR requires it — so nothing
      obliges an implementation to satisfy it. [Gap, Traceability, Spec §Edge Cases]
      → **Resolved 2026-08-13**: promoted to **FR-026**, which also forbids binding an alternative
      port and ties the prohibition to FR-014's documented addresses.
- [x] CHK002 - Is the **stale-volume** remedy backed by an FR? FR-014's documentation list does not
      include a reset command, and `docker compose down -v` appears only in the contract's
      "additional commands" table. [Gap, Traceability, Spec §Edge Cases]
      → **Resolved 2026-08-13**: promoted to **FR-024**, which requires the command to be documented
      *and* identified as destructive and distinct from FR-001's stop command.
- [x] CHK003 - Is the **mid-run database restart** recovery backed by an FR? It is an Edge Case bullet
      and a quickstart step with no requirement behind it. [Gap, Traceability, Spec §Edge Cases]
      → **Resolved 2026-08-13**: promoted to **FR-025**, with a 30-second detection window.
- [x] CHK004 - Is a requirement stated for behaviour when the database **initialisation script fails**?
      A partially initialised volume is a plausible state with no specified outcome. [Gap, Exception Flow]
      → **Resolved 2026-08-13**: recorded as an accepted gap under FR-024 in spec.md — a failed
      init script surfaces as the container's own healthcheck failing, diagnosable through the same
      stale-volume troubleshooting path FR-024 already documents; no separate requirement is needed.
- [x] CHK005 - Are requirements defined for the database being reachable but **without the `vector`
      extension** — the exact symptom the stale-volume trap produces? FR-002 requires the capability;
      nothing specifies what the system does when it is absent. [Gap, Coverage]
      → **Resolved 2026-08-13**: same spec.md bullet as CHK004/health.md CHK005 — accepted gap;
      FR-002's verification is the documented manual/quickstart check, not an automated one, in this
      feature.
- [x] CHK006 - Are requirements defined for **partial stack startup** (two parts up, one failed),
      including whether the running parts must remain usable? [Gap, Coverage]
      → **Resolved 2026-08-13**: FR-013 now states explicitly that a part already running is
      unaffected by another part being absent or failing, so running parts remain usable.
- [x] CHK007 - Are requirements defined for a **second instance** of any part being started while one
      is already running? [Gap, Coverage]
      → **Resolved 2026-08-13**: FR-026 now states a second instance fails through the same
      port-conflict path as any other collision; a new Edge Cases bullet makes this explicit.

## Acceptance Criteria Quality

- [x] CHK008 - Is US1-1's "within a short startup window" quantified? [quickstart.md](../quickstart.md)
      offers "roughly 30 seconds", which is neither a requirement nor a threshold. [Measurability, Spec §US1-1]
      → **Resolved 2026-08-13**: US1 Acceptance Scenario 1 now states "approximately 30 seconds,"
      matching the container's own healthcheck interval, and requires passing that healthcheck
      rather than merely accepting a TCP connection.
- [x] CHK009 - Is the recovery expectation ("the backend recovers ... without a manual restart") given
      a **detection deadline**, so "recovered" is falsifiable? [Measurability, Spec §Edge Cases]
      → **Resolved 2026-08-13**: FR-025 sets 30 seconds from the database accepting connections.
- [x] CHK010 - Is "shuts down cleanly and releases its port" (US1-4) measurable — is there a stated
      time bound or an observable release signal? [Measurability, Spec §US1-4]
      → **Resolved 2026-08-13**: US1 Acceptance Scenario 4 now defines the observable signal — the
      stop command exits successfully and a subsequent connection attempt fails within a few
      seconds.
- [x] CHK011 - Can SC-004's "**100%** of previously stored data" be verified by a stated method, or
      does it assume a probe the requirements never define? [Measurability, Spec §SC-004]
      → **Resolved 2026-08-13**: SC-004 now names the method — the write/restart/read probe already
      documented in `quickstart.md`'s persistence check.
- [x] CHK012 - Is "reports itself ready" (US1-1) distinguished from "accepts connections"? A container
      can accept TCP before the database accepts queries. [Ambiguity, Spec §US1-1]
      → **Resolved 2026-08-13**: US1 Acceptance Scenario 1 now requires passing the container's own
      healthcheck, explicitly distinguished from merely accepting a TCP connection.

## Requirement Clarity

- [x] CHK013 - Is FR-003's "survive stopping and restarting the environment" clear about scope — the
      documented stop command only, or also host reboot and container-runtime restart?
      [Ambiguity, Spec §FR-003]
      → **Resolved 2026-08-13**: recorded as an accepted scope decision — FR-003's guarantee covers
      the documented stop/start commands (FR-001); host reboot and container-runtime restart survive
      as an incidental property of the underlying Docker named volume, not a tested requirement of
      this feature. (No spec change was needed beyond this recorded decision; the mechanism already
      described in `data-model.md` makes the incidental durability accurate.)
- [x] CHK014 - Is "startup fails with a message naming the port **and the part**" clear about which
      component must produce that message, given a dev server may emit its own?
      [Clarity, Spec §Edge Cases]
      → **Resolved 2026-08-13**: FR-026 now states explicitly that this does not mandate a custom
      implementation — whichever tool detects the conflict first (Compose, the backend's embedded
      server, or the frontend's dev server) already reports the offending port by default, and that
      default output satisfies the requirement.
- [x] CHK015 - Is "the environment shuts down cleanly" defined in terms of data safety — that an
      in-flight write is not lost? [Clarity, Spec §US1-4]
      → **Resolved 2026-08-13**: recorded as an accepted scope decision — in-flight write durability
      during shutdown is PostgreSQL's own WAL/durability guarantee, not a feature-specific
      requirement; this feature's only data-safety requirement is FR-003's before/after check.
- [x] CHK016 - Is "only the backend's database health signal is affected by the absence of another
      part" accurate now that the `azureOpenAi` component also varies with the environment? The
      statement predates the Azure clarification. [Consistency, Spec §Edge Cases]
      → **Resolved 2026-08-13**: the Edge Cases bullet was rewritten to name both the database and
      AI-provider health components as independently varying, and to state they can degrade
      simultaneously without that being a distinct case.

## Requirement Consistency

- [x] CHK017 - Does the Edge Cases claim that a part must not silently pick another port sit
      consistently with FR-004, FR-005 and FR-010, which fix ports without stating conflict
      behaviour? [Consistency, Spec §FR-004, §FR-005, §FR-010]
      → **Resolved 2026-08-13**: resolved by FR-026's promotion (CHK001) — the conflict behaviour is
      now itself a requirement, consistent with the fixed-port requirements it complements.
- [x] CHK018 - Do [data-model.md](../data-model.md)'s state-transition diagram and FR-003 agree on
      which transitions preserve data, including the init-scripts-skipped path?
      [Consistency, Spec §FR-003]
      → **Resolved 2026-08-13**: confirmed consistent, no change needed — the diagram's
      `stopped → ready (init scripts skipped)` path is exactly the mechanism that makes FR-003 hold.
- [x] CHK019 - Is FR-013's independence guarantee consistent with the Edge Case admitting the backend's
      health signal changes — i.e. is "startable independently" defined as *starts* rather than
      *behaves identically*? [Consistency, Spec §FR-013]
      → **Resolved 2026-08-13**: FR-013 now defines independence explicitly as "able to start and
      serve", not "behaviour unaffected by which other parts are running" — resolving the ambiguity
      directly.

## Scenario Coverage

- [x] CHK020 - Are **recovery** flow requirements present as a class, or is recovery represented only
      by the single database-restart bullet? [Coverage, Recovery Flow]
      → **Resolved 2026-08-13**: new Assumption states only the database has meaningful
      restart/recovery semantics (FR-003, FR-025) because only it persists anything — recovery is
      deliberately a database-only class, not an oversight.
- [x] CHK021 - Are requirements defined for the frontend or backend being stopped and restarted, to
      the same standard as the database? Only the database has an explicit restart story.
      [Coverage, Completeness]
      → **Resolved 2026-08-13**: same Assumption as CHK020 — backend and frontend are stateless in
      this feature, so their "restart" is simply "start again" under FR-013; no dedicated recovery
      FR is needed for either.
- [x] CHK022 - Is the absence of **rollback** requirements deliberate and stated? The only state
      mutation in this feature is volume creation, so rollback may be genuinely inapplicable — but
      that should be recorded rather than merely absent. [Coverage, Gap]
      → **Resolved 2026-08-13**: new Assumption states rollback is not applicable beyond FR-024,
      which already documents how to reverse the one state mutation this feature performs.
- [x] CHK023 - Are requirements defined for resource exhaustion (disk full during volume write) or is
      it an explicitly accepted exclusion for a local PoC? [Coverage, Gap]
      → **Resolved 2026-08-13**: new Assumption explicitly excludes resource exhaustion, assuming
      sufficient disk space and typical network bandwidth.

## Dependencies & Assumptions

- [x] CHK024 - Is the assumption "a single developer on one machine at a time" load-bearing for the
      fixed-port decision, and is it stated as such where the port requirements live?
      [Assumption, Spec §Assumptions, §FR-004]
      → **Resolved 2026-08-13**: the Assumptions bullet now cross-references FR-004 (and FR-005,
      FR-010) directly, stating the fixed-port decisions rest on it.
- [x] CHK025 - Is the dependency on the container image's "init scripts run only on an empty data
      directory" behaviour recorded as an external behaviour that could change with an image upgrade?
      [Dependency, Assumption]
      → **Resolved 2026-08-13**: new Assumption records this dependency on
      `pgvector/pgvector:pg18` explicitly and notes it is worth re-verifying on an image major-version
      change, cross-referencing `data-model.md`.

## Notes

- Check items off as resolved: `[x]`
- ~~**The dominant finding is structural**: CHK001–CHK003 show that this feature's Edge Cases section
  carries real behavioural expectations that **no functional requirement backs**.~~ **Closed
  2026-08-13** — all three were promoted to FR-024, FR-025 and FR-026, and the Edge Cases section
  now opens with a statement that every behavioural bullet names its binding requirement, with
  inline `(FR-0xx)` references. An implementation can no longer satisfy every FR while failing the
  edge cases
- **All 25 items resolved 2026-08-13.**

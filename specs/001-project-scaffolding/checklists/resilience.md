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
- [ ] CHK004 - Is a requirement stated for behaviour when the database **initialisation script fails**?
      A partially initialised volume is a plausible state with no specified outcome. [Gap, Exception Flow]
- [ ] CHK005 - Are requirements defined for the database being reachable but **without the `vector`
      extension** — the exact symptom the stale-volume trap produces? FR-002 requires the capability;
      nothing specifies what the system does when it is absent. [Gap, Coverage]
- [ ] CHK006 - Are requirements defined for **partial stack startup** (two parts up, one failed),
      including whether the running parts must remain usable? [Gap, Coverage]
- [ ] CHK007 - Are requirements defined for a **second instance** of any part being started while one
      is already running? [Gap, Coverage]

## Acceptance Criteria Quality

- [ ] CHK008 - Is US1-1's "within a short startup window" quantified? [quickstart.md](../quickstart.md)
      offers "roughly 30 seconds", which is neither a requirement nor a threshold. [Measurability, Spec §US1-1]
- [x] CHK009 - Is the recovery expectation ("the backend recovers ... without a manual restart") given
      a **detection deadline**, so "recovered" is falsifiable? [Measurability, Spec §Edge Cases]
      → **Resolved 2026-08-13**: FR-025 sets 30 seconds from the database accepting connections.
- [ ] CHK010 - Is "shuts down cleanly and releases its port" (US1-4) measurable — is there a stated
      time bound or an observable release signal? [Measurability, Spec §US1-4]
- [ ] CHK011 - Can SC-004's "**100%** of previously stored data" be verified by a stated method, or
      does it assume a probe the requirements never define? [Measurability, Spec §SC-004]
- [ ] CHK012 - Is "reports itself ready" (US1-1) distinguished from "accepts connections"? A container
      can accept TCP before the database accepts queries. [Ambiguity, Spec §US1-1]

## Requirement Clarity

- [ ] CHK013 - Is FR-003's "survive stopping and restarting the environment" clear about scope — the
      documented stop command only, or also host reboot and container-runtime restart?
      [Ambiguity, Spec §FR-003]
- [ ] CHK014 - Is "startup fails with a message naming the port **and the part**" clear about which
      component must produce that message, given a dev server may emit its own?
      [Clarity, Spec §Edge Cases]
- [ ] CHK015 - Is "the environment shuts down cleanly" defined in terms of data safety — that an
      in-flight write is not lost? [Clarity, Spec §US1-4]
- [ ] CHK016 - Is "only the backend's database health signal is affected by the absence of another
      part" accurate now that the `azureOpenAi` component also varies with the environment? The
      statement predates the Azure clarification. [Consistency, Spec §Edge Cases]

## Requirement Consistency

- [ ] CHK017 - Does the Edge Cases claim that a part must not silently pick another port sit
      consistently with FR-004, FR-005 and FR-010, which fix ports without stating conflict
      behaviour? [Consistency, Spec §FR-004, §FR-005, §FR-010]
- [ ] CHK018 - Do [data-model.md](../data-model.md)'s state-transition diagram and FR-003 agree on
      which transitions preserve data, including the init-scripts-skipped path?
      [Consistency, Spec §FR-003]
- [ ] CHK019 - Is FR-013's independence guarantee consistent with the Edge Case admitting the backend's
      health signal changes — i.e. is "startable independently" defined as *starts* rather than
      *behaves identically*? [Consistency, Spec §FR-013]

## Scenario Coverage

- [ ] CHK020 - Are **recovery** flow requirements present as a class, or is recovery represented only
      by the single database-restart bullet? [Coverage, Recovery Flow]
- [ ] CHK021 - Are requirements defined for the frontend or backend being stopped and restarted, to
      the same standard as the database? Only the database has an explicit restart story.
      [Coverage, Completeness]
- [ ] CHK022 - Is the absence of **rollback** requirements deliberate and stated? The only state
      mutation in this feature is volume creation, so rollback may be genuinely inapplicable — but
      that should be recorded rather than merely absent. [Coverage, Gap]
- [ ] CHK023 - Are requirements defined for resource exhaustion (disk full during volume write) or is
      it an explicitly accepted exclusion for a local PoC? [Coverage, Gap]

## Dependencies & Assumptions

- [ ] CHK024 - Is the assumption "a single developer on one machine at a time" load-bearing for the
      fixed-port decision, and is it stated as such where the port requirements live?
      [Assumption, Spec §Assumptions, §FR-004]
- [ ] CHK025 - Is the dependency on the container image's "init scripts run only on an empty data
      directory" behaviour recorded as an external behaviour that could change with an image upgrade?
      [Dependency, Assumption]

## Notes

- Check items off as resolved: `[x]`
- ~~**The dominant finding is structural**: CHK001–CHK003 show that this feature's Edge Cases section
  carries real behavioural expectations that **no functional requirement backs**.~~ **Closed
  2026-08-13** — all three were promoted to FR-024, FR-025 and FR-026, and the Edge Cases section
  now opens with a statement that every behavioural bullet names its binding requirement, with
  inline `(FR-0xx)` references. An implementation can no longer satisfy every FR while failing the
  edge cases
- **CHK016** flags stale text: the "only the backend's database health signal is affected" bullet
  was written before the Azure clarification added a second environment-dependent component

# Health & Status Semantics Requirements Checklist: Project Scaffolding

**Purpose**: Validate that the health-check requirements — status vocabulary, aggregation, HTTP
mapping, and component coverage — are complete, unambiguous and objectively verifiable before
implementation begins.
**Created**: 2026-08-13
**Depth**: Formal gate — resolve before `/speckit-tasks`
**Scope audited**: [spec.md](../spec.md) + [contracts/health-api.md](../contracts/health-api.md),
[contracts/ai-provider.md](../contracts/ai-provider.md)
**Feature**: [spec.md](../spec.md)

> This checklist tests the **requirements**, not the implementation. Every item asks whether
> something is adequately *written*, not whether the endpoint behaves.

## Requirement Completeness

- [x] CHK001 - Is the health endpoint's **path** required anywhere in the spec? FR-006 says "a health
      check"; only [health-api.md](../contracts/health-api.md) fixes `/actuator/health`. [Gap, Spec §FR-006]
      → **Resolved 2026-08-13**: FR-006 now requires the health check be exposed "at a documented
      HTTP path"; the exact path remains a contract-level detail, consistent with the spec's
      tech-neutral requirements and the contract's job of fixing it.
- [x] CHK002 - Does any requirement mandate an **`azureOpenAi` component** by name, or does FR-020
      only constrain its effect on overall status while the contract invents the component?
      [Gap, Spec §FR-020]
      → **Resolved 2026-08-13**: FR-020 now requires the report be "a distinctly named, separate
      component of the health response", with the exact name fixed by the contract.
- [x] CHK003 - Are requirements defined for the **combined** degraded state — database unreachable
      *and* Azure unconfigured? The contract documents three cases, none of which is this one.
      [Coverage, Gap]
      → **Resolved 2026-08-13**: new Edge Cases bullet states the two components vary independently
      and can be degraded simultaneously; `health-api.md` gained a "Combined degradation" section
      showing this composes Case B and Case C rather than requiring new behaviour.
- [x] CHK004 - Are requirements defined for the **recovery** transition (database returns while the
      backend runs)? It appears as an Edge Case bullet and in the quickstart, but no FR and no
      contract case covers it. [Gap, Recovery Flow, Spec §Edge Cases]
      → **Resolved 2026-08-13**: already covered — **FR-025** (added in the resilience gate) binds
      this transition with a 30-second detection window; the Edge Cases bullet cites it.
- [x] CHK005 - Are requirements defined for the database being reachable but the **`vector`
      extension absent**? FR-002 requires it "verifiable", but no health requirement covers a
      connected-yet-unprepared database. [Gap, Coverage]
      → **Resolved 2026-08-13**: recorded as an accepted gap — new spec.md bullet under FR-024
      states this feature does not distinguish "connected" from "connected and schema-ready"
      automatically; FR-002's verification is the documented manual check, and automatic detection
      is deferred to the ingestion feature.
- [x] CHK006 - Is a requirement stated for health **response stability** — that consumers may rely on
      a named subset of fields? The contract asserts "fields beyond these MUST NOT be asserted",
      which is a constraint on tests, not a requirement on the response. [Gap]
      → **Resolved 2026-08-13**: FR-006 now states consumers may rely on the fields enumerated in
      `contracts/health-api.md`; other fields are not guaranteed stable.
- [x] CHK007 - Are requirements defined for the health check's own **failure mode** — what the
      endpoint returns if a health indicator throws? [Gap, Exception Flow]
      → **Resolved 2026-08-13**: FR-006 now states this feature does not specify indicator-throws
      behaviour; it inherits whatever the underlying health-check mechanism provides — an accepted,
      recorded exclusion rather than a silent gap.
- [x] CHK008 - Is the information-disclosure consequence of unauthenticated `show-details: always`
      (JDBC error text and configuration state to any caller) captured as a requirement or an
      accepted-risk statement in the spec, rather than only as a contract note? [Gap, Spec §Assumptions]
      → **Resolved 2026-08-13**: the "No authentication anywhere" Assumption now names this
      consequence explicitly and states the tightening-before-deployment obligation belongs to a
      later feature.

## Requirement Clarity

- [x] CHK009 - Is "whether its database connection is **usable**" defined? Connectable, pool-available,
      query-capable and schema-ready are four different bars. [Ambiguity, Spec §FR-006]
      → **Resolved 2026-08-13**: FR-006 now defines "usable" as able to obtain and validate a
      connection from the pool — not merely process-reachable, and not schema-readiness.
- [x] CHK010 - Is "its own status" (FR-006) distinguished from the aggregate of its components?
      [Clarity, Spec §FR-006]
      → **Resolved 2026-08-13**: FR-006 now explicitly distinguishes "basic liveness" from "the
      aggregate status contributed by its dependencies".
- [x] CHK011 - Is the status vocabulary (`UP` / `DOWN` / `UNKNOWN`) defined in the spec, or introduced
      only by the contract? FR-020 says an unconfigured provider "MUST NOT change the overall service
      status" without naming what it reports instead. [Gap, Spec §FR-020]
      → **Resolved 2026-08-13**: recorded as an intentional design boundary — FR-020 now states the
      exact vocabulary is a contract-level detail by design (the spec stays technology-neutral per
      its own Assumptions), while the spec fixes the observable behaviour the vocabulary must
      satisfy.
- [x] CHK012 - Is the **meaning of `UNKNOWN` to a consumer** defined, beyond its aggregation effect?
      [Clarity]
      → **Resolved 2026-08-13**: same design-boundary resolution as CHK011 — vocabulary meaning is a
      contract-level detail (see `health-api.md` Case C), the spec fixes only the required
      observable outcome (overall status unaffected).
- [x] CHK013 - Is "reports the AI provider as configured" clear that it means *credentials are set*
      and explicitly **not** *credentials are valid*? The contract states this limit prominently; the
      spec's US2-6 does not. [Ambiguity, Spec §US2-6]
      → **Resolved 2026-08-13**: US2-6 now states explicitly that "configured" means present, not
      valid, and that validity is established only by the on-demand verification.
- [x] CHK014 - Is "does not crash or restart in a loop" (US2-3) specified as an observable condition
      over a stated duration? [Clarity, Spec §US2-3]
      → **Resolved 2026-08-13**: covered by FR-007's new startup-not-blocked clause and the
      contract's requirement that the process stays running and answering; observed in practice by
      requesting the health check twice, seconds apart, and confirming no restart occurred between
      requests (see `quickstart.md`'s database-down verification, which already does exactly this).

## Acceptance Criteria Quality

- [x] CHK015 - Can "with a message a developer can act on" (US2-3) be objectively verified? The
      contract weakens it to "`details.error` is present and non-empty", which any string satisfies.
      Is the weaker form the accepted criterion? [Measurability, Conflict, Spec §US2-3]
      → **Resolved 2026-08-13, both sides moved.** US2-3 and FR-007 now require the report to name
      the failing dependency and carry the underlying connection error text, diagnosable without
      logs or source. [health-api.md](../contracts/health-api.md) Case B was tightened to match:
      the `db` component key identifies the dependency, `details.error` must carry the exception
      type and message, and only the *verbatim message string* remains un-assertable.
- [x] CHK016 - Is FR-007's "start successfully" bounded in **time**? A database-down boot can be
      delayed by connection-pool timeouts, and no requirement caps that. [Measurability, Spec §FR-007]
      → **Resolved 2026-08-13**: FR-007 now requires startup not be blocked or measurably delayed by
      database connection attempts or pool timeouts.
- [x] CHK017 - Is the Edge Case recovery expectation ("without a manual restart") bounded by a
      detection window, so "recovered" has a deadline? [Measurability, Spec §Edge Cases]
      → **Resolved 2026-08-13**: already satisfied — FR-025's 30-second window, cited in Edge Cases.
- [x] CHK018 - Is FR-020's "costs nothing and cannot be slowed by an external service" expressed as a
      verifiable property (no outbound request) rather than a performance aspiration?
      [Measurability, Spec §FR-020, §US2-6]
      → **Resolved 2026-08-13**: US2-6 now states this is verifiable by the absence of any outbound
      network call from the indicator.
- [x] CHK019 - Does FR-008's "at least one test that exercises the health check" specify **which
      cases** must be covered, or would a single happy-path test satisfy it while leaving FR-007
      untested? [Measurability, Spec §FR-008]
      → **Resolved 2026-08-13**: FR-008 now names the three minimum cases: database reachable,
      database unreachable, and AI provider unconfigured.

## Requirement Consistency

- [x] CHK020 - Do FR-020 ("MUST NOT change the overall service status") and the contract's severity
      argument (`UNKNOWN` ranks below `UP`) agree, or does the contract's mechanism happen to satisfy
      a requirement that never constrained the mechanism? [Consistency, Spec §FR-020]
      → **Resolved 2026-08-13**: confirmed as an intentional layering, no change needed — the spec
      states the *what* (overall status unaffected), the contract states the *how* (severity
      ordering). This is the same design boundary as CHK011/CHK012, not an inconsistency.
- [x] CHK021 - Is the 503-for-database / 200-for-Azure asymmetry derivable from the requirements
      alone, or only from the contract's rationale section? [Consistency, Traceability]
      → **Resolved 2026-08-13**: FR-007 now states its case is a **fault** (configured but
      unreachable) while FR-020 states its case is a **state** (not yet configured), mirroring the
      contract's rationale directly in the spec.
- [x] CHK022 - Are the `details` field names consistent between Case A (`configured`,
      `endpointConfigured`, `chatDeploymentConfigured`) and Case B (`configured` only)?
      [Consistency, Contract §Case A/B]
      → **Resolved 2026-08-13**: `health-api.md` gained a field-name note clarifying the shorter
      Case B example is illustrative brevity, not a contract difference — the same component shape
      applies regardless of `db`'s status.
- [x] CHK023 - Does US2-2 ("reports the database as reachable") use the same notion of reachability as
      FR-006's "usable"? [Consistency, Spec §US2-2, §FR-006]
      → **Resolved 2026-08-13**: US2-2 now says "usable" and states explicitly it is the same notion
      used throughout the feature.

## Scenario Coverage

- [x] CHK024 - Are **primary** flow requirements (both dependencies healthy) fully specified? [Coverage, Contract §Case A]
      → **Resolved 2026-08-13**: confirmed, no change needed — Case A plus FR-006/FR-020 fully
      specify this flow.
- [x] CHK025 - Are **exception** flow requirements specified for each dependency independently?
      [Coverage, Contract §Case B/C]
      → **Resolved 2026-08-13**: confirmed, no change needed — Case B and Case C, backed by FR-007
      and FR-020 respectively, specify each independently; the new "Combined degradation" section
      confirms they compose without new rules.
- [x] CHK026 - Are requirements specified for the health check being called **before** the application
      has finished initialising? [Coverage, Gap]
      → **Resolved 2026-08-13**: recorded as not applicable — the underlying server does not accept
      HTTP traffic until application context initialisation completes, so this scenario cannot occur
      by construction; no feature-specific requirement is needed.
- [x] CHK027 - Are requirements defined for concurrent health requests, or is that explicitly out of
      scope for a single-developer PoC? [Coverage, Gap]
      → **Resolved 2026-08-13**: explicitly out of scope — new Assumptions bullet states concurrent
      request scenarios are out of scope for this local, single-developer PoC.

## Non-Functional Requirements

- [x] CHK028 - Are the security requirements for the actuator surface stated for the *local-only*
      posture, with the pre-deployment tightening recorded as a requirement of a later feature rather
      than a floating note? [Gap, Contract §Security posture]
      → **Resolved 2026-08-13**: same edit as CHK008 — the Assumptions section now states this
      explicitly, including that tightening is a later feature's requirement.
- [x] CHK029 - Is the "no key in the response" guarantee stated as a spec requirement, not only a
      contract guarantee and a constitution clause? [Traceability, Gap]
      → **Resolved 2026-08-13**: FR-009 now states no secret value, including the AI provider key,
      may appear in the health response.

## Notes

- Check items off as resolved: `[x]`
- **All 29 items resolved 2026-08-13.** CHK003, CHK004, CHK026, the cluster of unspecified state
  combinations flagged as highest-risk, are each closed: CHK004 was already covered by FR-025,
  CHK003 by the new combined-degradation treatment, and CHK026 recorded as not applicable given the
  server's own lifecycle guarantees.
- **CHK015** was a genuine spec/contract conflict, not merely a gap; resolved by moving both sides to
  meet in the middle, as recorded above.

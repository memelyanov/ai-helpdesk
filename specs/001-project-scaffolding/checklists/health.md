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

- [ ] CHK001 - Is the health endpoint's **path** required anywhere in the spec? FR-006 says "a health
      check"; only [health-api.md](../contracts/health-api.md) fixes `/actuator/health`. [Gap, Spec §FR-006]
- [ ] CHK002 - Does any requirement mandate an **`azureOpenAi` component** by name, or does FR-020
      only constrain its effect on overall status while the contract invents the component?
      [Gap, Spec §FR-020]
- [ ] CHK003 - Are requirements defined for the **combined** degraded state — database unreachable
      *and* Azure unconfigured? The contract documents three cases, none of which is this one.
      [Coverage, Gap]
- [ ] CHK004 - Are requirements defined for the **recovery** transition (database returns while the
      backend runs)? It appears as an Edge Case bullet and in the quickstart, but no FR and no
      contract case covers it. [Gap, Recovery Flow, Spec §Edge Cases]
- [ ] CHK005 - Are requirements defined for the database being reachable but the **`vector`
      extension absent**? FR-002 requires it "verifiable", but no health requirement covers a
      connected-yet-unprepared database. [Gap, Coverage]
- [ ] CHK006 - Is a requirement stated for health **response stability** — that consumers may rely on
      a named subset of fields? The contract asserts "fields beyond these MUST NOT be asserted",
      which is a constraint on tests, not a requirement on the response. [Gap]
- [ ] CHK007 - Are requirements defined for the health check's own **failure mode** — what the
      endpoint returns if a health indicator throws? [Gap, Exception Flow]
- [ ] CHK008 - Is the information-disclosure consequence of unauthenticated `show-details: always`
      (JDBC error text and configuration state to any caller) captured as a requirement or an
      accepted-risk statement in the spec, rather than only as a contract note? [Gap, Spec §Assumptions]

## Requirement Clarity

- [ ] CHK009 - Is "whether its database connection is **usable**" defined? Connectable, pool-available,
      query-capable and schema-ready are four different bars. [Ambiguity, Spec §FR-006]
- [ ] CHK010 - Is "its own status" (FR-006) distinguished from the aggregate of its components?
      [Clarity, Spec §FR-006]
- [ ] CHK011 - Is the status vocabulary (`UP` / `DOWN` / `UNKNOWN`) defined in the spec, or introduced
      only by the contract? FR-020 says an unconfigured provider "MUST NOT change the overall service
      status" without naming what it reports instead. [Gap, Spec §FR-020]
- [ ] CHK012 - Is the **meaning of `UNKNOWN` to a consumer** defined, beyond its aggregation effect?
      [Clarity]
- [ ] CHK013 - Is "reports the AI provider as configured" clear that it means *credentials are set*
      and explicitly **not** *credentials are valid*? The contract states this limit prominently; the
      spec's US2-6 does not. [Ambiguity, Spec §US2-6]
- [ ] CHK014 - Is "does not crash or restart in a loop" (US2-3) specified as an observable condition
      over a stated duration? [Clarity, Spec §US2-3]

## Acceptance Criteria Quality

- [x] CHK015 - Can "with a message a developer can act on" (US2-3) be objectively verified? The
      contract weakens it to "`details.error` is present and non-empty", which any string satisfies.
      Is the weaker form the accepted criterion? [Measurability, Conflict, Spec §US2-3]
      → **Resolved 2026-08-13, both sides moved.** US2-3 and FR-007 now require the report to name
      the failing dependency and carry the underlying connection error text, diagnosable without
      logs or source. [health-api.md](../contracts/health-api.md) Case B was tightened to match:
      the `db` component key identifies the dependency, `details.error` must carry the exception
      type and message, and only the *verbatim message string* remains un-assertable.
- [ ] CHK016 - Is FR-007's "start successfully" bounded in **time**? A database-down boot can be
      delayed by connection-pool timeouts, and no requirement caps that. [Measurability, Spec §FR-007]
- [ ] CHK017 - Is the Edge Case recovery expectation ("without a manual restart") bounded by a
      detection window, so "recovered" has a deadline? [Measurability, Spec §Edge Cases]
- [ ] CHK018 - Is FR-020's "costs nothing and cannot be slowed by an external service" expressed as a
      verifiable property (no outbound request) rather than a performance aspiration?
      [Measurability, Spec §FR-020, §US2-6]
- [ ] CHK019 - Does FR-008's "at least one test that exercises the health check" specify **which
      cases** must be covered, or would a single happy-path test satisfy it while leaving FR-007
      untested? [Measurability, Spec §FR-008]

## Requirement Consistency

- [ ] CHK020 - Do FR-020 ("MUST NOT change the overall service status") and the contract's severity
      argument (`UNKNOWN` ranks below `UP`) agree, or does the contract's mechanism happen to satisfy
      a requirement that never constrained the mechanism? [Consistency, Spec §FR-020]
- [ ] CHK021 - Is the 503-for-database / 200-for-Azure asymmetry derivable from the requirements
      alone, or only from the contract's rationale section? [Consistency, Traceability]
- [ ] CHK022 - Are the `details` field names consistent between Case A (`configured`,
      `endpointConfigured`, `chatDeploymentConfigured`) and Case B (`configured` only)?
      [Consistency, Contract §Case A/B]
- [ ] CHK023 - Does US2-2 ("reports the database as reachable") use the same notion of reachability as
      FR-006's "usable"? [Consistency, Spec §US2-2, §FR-006]

## Scenario Coverage

- [ ] CHK024 - Are **primary** flow requirements (both dependencies healthy) fully specified? [Coverage, Contract §Case A]
- [ ] CHK025 - Are **exception** flow requirements specified for each dependency independently?
      [Coverage, Contract §Case B/C]
- [ ] CHK026 - Are requirements specified for the health check being called **before** the application
      has finished initialising? [Coverage, Gap]
- [ ] CHK027 - Are requirements defined for concurrent health requests, or is that explicitly out of
      scope for a single-developer PoC? [Coverage, Gap]

## Non-Functional Requirements

- [ ] CHK028 - Are the security requirements for the actuator surface stated for the *local-only*
      posture, with the pre-deployment tightening recorded as a requirement of a later feature rather
      than a floating note? [Gap, Contract §Security posture]
- [ ] CHK029 - Is the "no key in the response" guarantee stated as a spec requirement, not only a
      contract guarantee and a constitution clause? [Traceability, Gap]

## Notes

- Check items off as resolved: `[x]`
- Highest-risk cluster: **CHK003, CHK004, CHK026** — three unspecified state combinations, all of
  which a developer will actually hit on day one
- **CHK015** is a genuine spec/contract conflict, not merely a gap: the contract's testable form is
  materially weaker than the spec's stated intent. One of the two must move

# Developer Onboarding & Documentation Requirements Checklist: Project Scaffolding

**Purpose**: Validate that the onboarding, documentation and reproducibility requirements are
complete, unambiguous and objectively measurable — particularly the time, first-attempt and
single-command claims, which are the easiest in this spec to state and the hardest to assess.
**Created**: 2026-08-13
**Depth**: Formal gate — resolve before `/speckit-tasks`
**Scope audited**: [spec.md](../spec.md) + [contracts/runtime-surface.md](../contracts/runtime-surface.md),
[quickstart.md](../quickstart.md)
**Feature**: [spec.md](../spec.md)

> This checklist tests the **requirements**, not the implementation. Every item asks whether
> something is adequately *written*, not whether onboarding works.

## Acceptance Criteria Quality

- [ ] CHK001 - Is SC-001's 15-minute window given a defined **start point**? Clone, prerequisites
      installed, or first command? Cold-cache `npm install` plus Maven dependency resolution can
      consume most of the budget, and which of those counts is unstated. [Measurability, Spec §SC-001]
- [ ] CHK002 - Is SC-001's window given a defined **end point** — all three processes started, or all
      three *verified*? [Measurability, Spec §SC-001]
- [ ] CHK003 - Can "asking no questions" (SC-001) be objectively assessed, or is it an observation
      about a person rather than a property of the deliverable? [Measurability, Spec §SC-001]
- [ ] CHK004 - Is "first attempt" (SC-007) defined? Does re-running a failed command, or reading the
      troubleshooting table, end the first attempt? [Ambiguity, Spec §SC-007]
- [ ] CHK005 - Is "without editing any file the documentation did not tell them to edit" (SC-007)
      verifiable by a stated method, given `cp .env.example .env` produces an edited file the docs
      *did* sanction? [Measurability, Spec §SC-007]
- [x] CHK006 - Is SC-002's "**exactly one** documented command" reconcilable with the frontend
      requiring `npm install` before `npm start`? [quickstart.md](../quickstart.md) documents two.
      Either `npm install` is a prerequisite (and should be stated as one) or SC-002 is violated as
      written. [Conflict, Spec §SC-002]
      → **Resolved 2026-08-13**: SC-002 now excludes one-time dependency installation from the
      command count, on the stated grounds that it runs once per checkout rather than on every
      start. FR-014 correspondingly requires one-time setup steps to be documented as prerequisites,
      distinct from the start command they precede.
- [ ] CHK007 - Is FR-017's "state accurately" measurable, or does accuracy of prose remain a
      judgement call with no stated criterion? [Measurability, Spec §FR-017]
- [ ] CHK008 - Is SC-005's "three separate single-part startups" specified as starting from a
      **fully stopped state**, matching the contract's phrasing? [Measurability, Spec §SC-005]

## Requirement Completeness

- [ ] CHK009 - Does FR-014 name **which document** is the single place? "In one place" without a
      referent permits README and `quickstart.md` to both claim the role and drift.
      [Ambiguity, Spec §FR-014]
- [ ] CHK010 - Does FR-014's enumerated list include the **verification step** for each part?
      Prerequisites, commands, addresses and order are required; how to confirm a part actually
      works is not. [Gap, Spec §FR-014]
- [ ] CHK011 - Is a **troubleshooting** section required by any FR? `quickstart.md` provides a
      nine-row table that no requirement asks for — meaning nothing obliges it to stay current.
      [Gap, Spec §FR-014]
- [x] CHK012 - Are requirements defined for documenting the **destructive** reset command
      (`docker compose down -v`) as destructive? [Gap, Contract §Commands]
      → **Resolved 2026-08-13**: FR-024 requires it to be documented, identified as destructive, and
      distinguished from the ordinary stop command.
- [x] CHK013 - Is the **operating-system scope** of the documented commands stated? The spec is silent,
      the plan says "OS-neutral", and `quickstart.md` uses POSIX syntax (`cp`, `grep`, `curl … | grep`)
      on a project whose stated target machine runs Windows/PowerShell. [Gap, Conflict]
      → **Resolved 2026-08-13, both layers.** Requirement: **FR-027** requires the documentation to
      state which shell its commands assume, requires every command to run as written on the primary
      platform, and requires both forms where they differ; a new Assumption names Windows/PowerShell
      as that platform. Documentation: [quickstart.md](../quickstart.md) gained a **Shell
      conventions** section and dual PowerShell/bash forms for every command that differs — the
      Maven wrapper, all four health requests, the key-leak check and the tracked-`.env` check.
      Four PowerShell-specific troubleshooting rows were added.
- [ ] CHK014 - Are requirements defined for what documentation must say about **optional** setup —
      specifically that Azure credentials are not needed? SC-009 asserts the property; no FR requires
      documenting it. [Gap, Spec §SC-009]
- [ ] CHK015 - Is there a requirement that the documentation state the **stop** command for each part?
      FR-001 requires one for the database; FR-005 and FR-010 do not for backend and frontend, though
      FR-014 implies all three. [Completeness, Spec §FR-005, §FR-010, §FR-014]

## Requirement Clarity

- [ ] CHK016 - Is "required tooling and **versions**" clear that version *ranges with exclusions* are
      required, not just major versions? Angular 21 rejects Node 22.0–22.11, so "Node 22" is an
      actively misleading instruction. [Clarity, Spec §FR-014, §US4-1]
- [ ] CHK017 - Is "the documented prerequisites" a defined set, referenced consistently by US1-1,
      US3-1, SC-001 and SC-007? [Clarity, Traceability]
- [ ] CHK018 - Is "recommended start order" clear about being a recommendation rather than a
      constraint, given FR-013 guarantees any order works? [Clarity, Spec §FR-014, §FR-013]
- [ ] CHK019 - Is "a developer who has never seen the repository" (US4) specified with an assumed
      baseline skill set — for example, whether Docker familiarity is presumed? [Assumption, Spec §US4]

## Requirement Consistency

- [ ] CHK020 - Do FR-014's "one place" and the existence of `quickstart.md` as a parallel validation
      guide create a documented duplication with no stated precedence? [Consistency, Spec §FR-014]
- [ ] CHK021 - Do the prerequisites in [runtime-surface.md](../contracts/runtime-surface.md) and
      [quickstart.md](../quickstart.md) agree in both content and version ranges? Two tables state the
      same facts. [Consistency]
- [ ] CHK022 - Is FR-017's "status documentation" the same artifact as FR-014's "one place", or two
      different documents? [Consistency, Spec §FR-014, §FR-017]

## Scenario Coverage

- [ ] CHK023 - Are requirements defined for the onboarding path when a prerequisite is present but the
      **wrong version**, as distinct from missing entirely? The Edge Case bullet covers both with one
      sentence. [Coverage, Spec §Edge Cases]
- [ ] CHK024 - Are requirements defined for a **partially successful** setup — two parts running, one
      failing — including what the documentation should tell the developer to do next? [Gap, Coverage]

## Dependencies & Assumptions

- [ ] CHK025 - Is the assumption that a fresh developer is **available** to validate US4 recorded,
      given SC-001 and SC-007 cannot be assessed by the author? [Assumption, Spec §US4]
- [ ] CHK026 - Is the assumption of adequate network bandwidth for first-run dependency downloads
      stated, given it directly governs whether SC-001 is achievable? [Assumption, Gap]

## Notes

- Check items off as resolved: `[x]`
- ~~**CHK006 and CHK013 are genuine defects**, not stylistic gaps~~ — **both closed 2026-08-13**.
  CHK006: SC-002 amended, FR-014 extended. CHK013: FR-027 added *and* `quickstart.md` rewritten to
  comply, so the requirement and the artifact now agree rather than the requirement alone being fixed
- **CHK001–CHK005** all attack the same weakness — SC-001 and SC-007 read as measurable but specify
  neither boundaries nor method. They are the two success criteria most likely to be declared "met"
  without evidence

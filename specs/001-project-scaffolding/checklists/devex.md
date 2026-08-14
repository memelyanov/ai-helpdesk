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

- [x] CHK001 - Is SC-001's 15-minute window given a defined **start point**? Clone, prerequisites
      installed, or first command? Cold-cache `npm install` plus Maven dependency resolution can
      consume most of the budget, and which of those counts is unstated. [Measurability, Spec §SC-001]
      → **Resolved 2026-08-13**: SC-001 now states the clock starts at `git clone`, with
      prerequisites already installed.
- [x] CHK002 - Is SC-001's window given a defined **end point** — all three processes started, or all
      three *verified*? [Measurability, Spec §SC-001]
      → **Resolved 2026-08-13**: SC-001 now ends when all three parts are running, each verified by
      the check described in its own user story.
- [x] CHK003 - Can "asking no questions" (SC-001) be objectively assessed, or is it an observation
      about a person rather than a property of the deliverable? [Measurability, Spec §SC-001]
      → **Resolved 2026-08-13**: SC-001 now ties this to the same fresh-developer trial that
      validates US4 — a documentation gap forcing a question is the failure signal, independent of
      elapsed time.
- [x] CHK004 - Is "first attempt" (SC-007) defined? Does re-running a failed command, or reading the
      troubleshooting table, end the first attempt? [Ambiguity, Spec §SC-007]
      → **Resolved 2026-08-13**: SC-007 now defines the attempt as ending at the first completed run
      of all three start commands; re-running a failed command or reading the troubleshooting table
      does not end it.
- [x] CHK005 - Is "without editing any file the documentation did not tell them to edit" (SC-007)
      verifiable by a stated method, given `cp .env.example .env` produces an edited file the docs
      *did* sanction? [Measurability, Spec §SC-007]
      → **Resolved 2026-08-13**: SC-007 now explicitly excludes the documented `.env.example` copy
      step from counting as an undocumented edit.
- [x] CHK006 - Is SC-002's "**exactly one** documented command" reconcilable with the frontend
      requiring `npm install` before `npm start`? [quickstart.md](../quickstart.md) documents two.
      Either `npm install` is a prerequisite (and should be stated as one) or SC-002 is violated as
      written. [Conflict, Spec §SC-002]
      → **Resolved 2026-08-13**: SC-002 now excludes one-time dependency installation from the
      command count, on the stated grounds that it runs once per checkout rather than on every
      start. FR-014 correspondingly requires one-time setup steps to be documented as prerequisites,
      distinct from the start command they precede.
- [x] CHK007 - Is FR-017's "state accurately" measurable, or does accuracy of prose remain a
      judgement call with no stated criterion? [Measurability, Spec §FR-017]
      → **Resolved 2026-08-13**: FR-017 now defines "accurately" as consistent with FR-016's
      exclusion list and the feature's actual completion state, verified by direct comparison.
- [x] CHK008 - Is SC-005's "three separate single-part startups" specified as starting from a
      **fully stopped state**, matching the contract's phrasing? [Measurability, Spec §SC-005]
      → **Resolved 2026-08-13**: SC-005 now says "from a fully stopped state" explicitly.

## Requirement Completeness

- [x] CHK009 - Does FR-014 name **which document** is the single place? "In one place" without a
      referent permits README and `quickstart.md` to both claim the role and drift.
      [Ambiguity, Spec §FR-014]
      → **Resolved 2026-08-13**: FR-014 and US4 Scenario 1 now name `README.md` explicitly, with
      `quickstart.md` designated a supporting validation guide, not a competing source.
- [x] CHK010 - Does FR-014's enumerated list include the **verification step** for each part?
      Prerequisites, commands, addresses and order are required; how to confirm a part actually
      works is not. [Gap, Spec §FR-014]
      → **Resolved 2026-08-13**: FR-014's list now includes "how to verify each part is working".
- [x] CHK011 - Is a **troubleshooting** section required by any FR? `quickstart.md` provides a
      nine-row table that no requirement asks for — meaning nothing obliges it to stay current.
      [Gap, Spec §FR-014]
      → **Resolved 2026-08-13**: FR-014 now requires a troubleshooting section for known failure
      modes.
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
- [x] CHK014 - Are requirements defined for what documentation must say about **optional** setup —
      specifically that Azure credentials are not needed? SC-009 asserts the property; no FR requires
      documenting it. [Gap, Spec §SC-009]
      → **Resolved 2026-08-13**: FR-014 now requires the documentation to state that AI provider
      credentials are optional for running the scaffold.
- [x] CHK015 - Is there a requirement that the documentation state the **stop** command for each part?
      FR-001 requires one for the database; FR-005 and FR-010 do not for backend and frontend, though
      FR-014 implies all three. [Completeness, Spec §FR-005, §FR-010, §FR-014]
      → **Resolved 2026-08-13**: no spec change needed — FR-014 already reads "the start and stop
      command for each part", which was already unambiguous for all three; recorded here as
      confirmed rather than gapped.

## Requirement Clarity

- [x] CHK016 - Is "required tooling and **versions**" clear that version *ranges with exclusions* are
      required, not just major versions? Angular 21 rejects Node 22.0–22.11, so "Node 22" is an
      actively misleading instruction. [Clarity, Spec §FR-014, §US4-1]
      → **Resolved 2026-08-13**: FR-014 and US4 Scenario 1 now explicitly require version ranges
      with exclusions, not just major versions.
- [x] CHK017 - Is "the documented prerequisites" a defined set, referenced consistently by US1-1,
      US3-1, SC-001 and SC-007? [Clarity, Traceability]
      → **Resolved 2026-08-13**: Definitions now fixes "the documented prerequisites" as the table
      FR-014 requires, referenced by name from all four locations.
- [x] CHK018 - Is "recommended start order" clear about being a recommendation rather than a
      constraint, given FR-013 guarantees any order works? [Clarity, Spec §FR-014, §FR-013]
      → **Resolved 2026-08-13**: FR-014 and US4 Scenario 1 now say "recommended (not mandatory, per
      FR-013)" explicitly.
- [x] CHK019 - Is "a developer who has never seen the repository" (US4) specified with an assumed
      baseline skill set — for example, whether Docker familiarity is presumed? [Assumption, Spec §US4]
      → **Resolved 2026-08-13**: US4 Scenario 2 now states the assumed baseline — command-line, git
      and Docker familiarity — explicitly.

## Requirement Consistency

- [x] CHK020 - Do FR-014's "one place" and the existence of `quickstart.md` as a parallel validation
      guide create a documented duplication with no stated precedence? [Consistency, Spec §FR-014]
      → **Resolved 2026-08-13**: resolved by the same CHK009 edit — `README.md` is designated
      primary and `quickstart.md` explicitly subordinate, with `quickstart.md`'s own intro updated
      to state the same precedence.
- [x] CHK021 - Do the prerequisites in [runtime-surface.md](../contracts/runtime-surface.md) and
      [quickstart.md](../quickstart.md) agree in both content and version ranges? Two tables state the
      same facts. [Consistency]
      → **Resolved 2026-08-13**: confirmed consistent, no change needed — both tables list JDK 17,
      the same Node range, Docker Compose V2+, and "Azure OpenAI: not required" identically.
- [x] CHK022 - Is FR-017's "status documentation" the same artifact as FR-014's "one place", or two
      different documents? [Consistency, Spec §FR-014, §FR-017]
      → **Resolved 2026-08-13**: FR-017 now states explicitly that its status documentation is the
      status section of the same `README.md` FR-014 designates.

## Scenario Coverage

- [x] CHK023 - Are requirements defined for the onboarding path when a prerequisite is present but the
      **wrong version**, as distinct from missing entirely? The Edge Case bullet covers both with one
      sentence. [Coverage, Spec §Edge Cases]
      → **Resolved 2026-08-13**: accepted as intentional, recorded here rather than split — both
      cases produce the same observable behaviour (a clear failure naming the tool), so one bullet
      covering both is deliberate, not a gap.
- [x] CHK024 - Are requirements defined for a **partially successful** setup — two parts running, one
      failing — including what the documentation should tell the developer to do next? [Gap, Coverage]
      → **Resolved 2026-08-13**: FR-013 now states explicitly that each part's independence means a
      failing part does not affect parts already running, so a developer can keep using whichever
      parts are up while troubleshooting the failing one via FR-014's troubleshooting section.

## Dependencies & Assumptions

- [x] CHK025 - Is the assumption that a fresh developer is **available** to validate US4 recorded,
      given SC-001 and SC-007 cannot be assessed by the author? [Assumption, Spec §US4]
      → **Resolved 2026-08-13**: new Assumption records this as assumed, not guaranteed.
- [x] CHK026 - Is the assumption of adequate network bandwidth for first-run dependency downloads
      stated, given it directly governs whether SC-001 is achievable? [Assumption, Gap]
      → **Resolved 2026-08-13**: new Assumption states SC-001 assumes typical broadband and is not a
      guarantee under constrained or offline networks.

## Notes

- Check items off as resolved: `[x]`
- ~~**CHK006 and CHK013 are genuine defects**, not stylistic gaps~~ — **both closed 2026-08-13**.
  CHK006: SC-002 amended, FR-014 extended. CHK013: FR-027 added *and* `quickstart.md` rewritten to
  comply, so the requirement and the artifact now agree rather than the requirement alone being fixed
- **All 26 items resolved 2026-08-13.** CHK001–CHK005, the cluster attacking SC-001/SC-007's
  measurability, closed via explicit start/end points, a fresh-developer-trial tie-in for "no
  questions", a defined attempt boundary, and an explicit `.env.example` carve-out.

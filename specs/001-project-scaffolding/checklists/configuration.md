# Configuration & Secrets Requirements Checklist: Project Scaffolding

**Purpose**: Validate that the configuration, credential-binding and secret-handling requirements
are complete, unambiguous, internally consistent and objectively verifiable — before implementation
begins.
**Created**: 2026-08-13
**Depth**: Formal gate — resolve before `/speckit-tasks`
**Scope audited**: [spec.md](../spec.md) + [contracts/ai-provider.md](../contracts/ai-provider.md),
[contracts/runtime-surface.md](../contracts/runtime-surface.md), [data-model.md](../data-model.md)
**Feature**: [spec.md](../spec.md)

> This checklist tests the **requirements**, not the implementation. Every item asks whether
> something is adequately *written*, not whether the code works.

## Requirement Completeness

- [x] CHK001 - Are requirements defined for an environment variable that is **set but empty**, as
      distinct from unset? FR-019 says "absent" and SC-009 says "zero credentials present", neither
      of which covers `AZURE_OPEN_AI_KEY=""`. [Gap, Spec §FR-019, §SC-009]
      → **Resolved 2026-08-13**: new spec.md **Definitions** section defines "blank" as unset,
      empty, or whitespace-only, and states this single definition governs FR-019, FR-021 and the
      completeness rule — there is no separate "set but empty" state.
- [x] CHK002 - Are requirements defined for a variable set to **whitespace only**? The contracts use
      "present and non-blank"; the spec uses "present". [Gap, Spec §FR-021]
      → **Resolved 2026-08-13**: covered by the same Definitions "blank" entry as CHK001.
- [x] CHK003 - Is the prohibition on the API key appearing in **logs and error messages** stated as a
      requirement anywhere in the spec, or does it exist only in the constitution and
      [ai-provider.md](../contracts/ai-provider.md)? FR-009 covers version control only. [Gap, Spec §FR-009]
      → **Resolved 2026-08-13**: FR-009 now states no secret value may appear in logs, error
      messages, the health response, or test output.
- [x] CHK004 - Is the existence of a committed `.env.example` required by any FR, or only implied by
      FR-009's "working local defaults" and assumed by the contracts? [Gap, Spec §FR-009]
      → **Resolved 2026-08-13**: FR-009 now explicitly requires a committed `.env.example` template.
- [x] CHK005 - Are requirements defined for what the health `missing` list contains when the
      **embedding** deployment name is unset? FR-023 assigns that reporting to the on-demand
      verification; whether health also names it is unspecified. [Gap, Spec §FR-023]
      → **Resolved 2026-08-13**: FR-023 now states health does not name the embedding deployment;
      only the on-demand verification reports it missing. `data-model.md`'s new field-name mapping
      confirms `embeddingDeploymentName` never appears in the health `missing` list.
- [x] CHK006 - Is the prohibition on **placeholder default values** expressed as a requirement, or
      only as design rationale in [ai-provider.md](../contracts/ai-provider.md)? A future
      implementer is bound by requirements, not by a rejected-alternatives note. [Gap]
      → **Resolved 2026-08-13**: FR-018 now requires bound values to default to blank, never a
      non-blank placeholder, with the rationale summarised inline.
- [x] CHK007 - Are requirements defined for configuration values that change **while the backend is
      running** (key rotation, endpoint change)? Or is a restart-to-apply model an intentional,
      stated exclusion? [Gap, Coverage]
      → **Resolved 2026-08-13**: new Assumption states configuration is read once at startup; no
      live-reload requirement exists. FR-023 cross-references this for the embedding-deployment
      case specifically.
- [x] CHK008 - Are requirements defined for the non-Azure configuration surface — datasource URL,
      user, password — to the same standard as the Azure surface? FR-009 treats them in one clause
      while FR-018–FR-023 give Azure six. [Completeness, Spec §FR-009]
      → **Resolved 2026-08-13**: FR-009 now spells out that the database password is a secret on
      the same footing as the AI provider key, and that the same standard — environment-sourced,
      non-secret working defaults, nothing secret committed — applies to both surfaces; Azure simply
      has more variables to enumerate.
- [x] CHK009 - Does any requirement state which file or mechanism is the **authoritative** source of
      the four variable names, so "MUST NOT be renamed" has a referent? [Traceability, Spec §FR-018]
      → **Resolved 2026-08-13**: FR-018 now names `.specify/memory/constitution.md` v1.3.0 as the
      authoritative source.

## Requirement Clarity

- [x] CHK010 - Is "bind ... from the environment" defined with an observable outcome, or does it
      describe a mechanism without a testable result? [Clarity, Spec §FR-018]
      → **Resolved 2026-08-13**: FR-018 defines "bind" as the value becoming available to the
      running service and participating in the completeness rule and health reporting.
- [x] CHK011 - Is "present" (FR-021) defined identically to "present and non-blank"
      ([ai-provider.md](../contracts/ai-provider.md) completeness rule)? If not, which governs?
      [Ambiguity, Spec §FR-021]
      → **Resolved 2026-08-13**: Definitions states "present" and "present and non-blank" are the
      same test, defined once.
- [x] CHK012 - Is "reporting the provider as unconfigured" specified in terms of an observable
      output, or left to the implementer to choose a representation? [Clarity, Spec §FR-019]
      → **Resolved 2026-08-13**: FR-020 states this is an observable, distinctly named health
      response field, with exact vocabulary fixed by `contracts/health-api.md`.
- [x] CHK013 - Is "No secret value may be committed to version control" scoped — working tree only,
      or including **git history**? SC-006's "anywhere in version control" suggests history but does
      not say so. [Ambiguity, Spec §FR-009, §SC-006]
      → **Resolved 2026-08-13**: Definitions scopes this to the tracked working tree from this
      feature's first commit onward; remediating pre-existing history is out of scope.
- [x] CHK014 - Is "working local defaults for non-secret values" clear about whether the *database
      password* counts as secret? It is a local development credential with a default in
      `.env.example`. [Ambiguity, Spec §FR-009]
      → **Resolved 2026-08-13**: FR-009 states the database password is a secret, never committed;
      only the database name and user are the non-secret defaults in `.env.example`.
- [x] CHK015 - Is "an actionable failure" (FR-022) defined with criteria, or is it a subjective
      quality judgement? [Ambiguity, Spec §FR-022]
      → **Resolved 2026-08-13**: Definitions gives an explicit two-branch criterion (names the
      missing setting, or carries the provider's own status/error text).
- [x] CHK016 - Is it clear whether "the AI provider key" in FR-009 is one instance of a general rule
      or an exhaustive statement of the external credentials in scope? [Clarity, Spec §FR-009]
      → **Resolved 2026-08-13**: FR-009 now reads "including but not limited to the AI provider
      key", stating it is illustrative, not exhaustive.

## Requirement Consistency

- [x] CHK017 - Do the spec and [ai-provider.md](../contracts/ai-provider.md) define **completeness**
      with the same three fields and the same blankness rule? [Consistency, Spec §FR-021]
      → **Resolved 2026-08-13**: confirmed consistent — both use key/endpoint/chat-deployment-name
      and the shared "blank" definition; `ai-provider.md`'s completeness table now cross-references
      Definitions.
- [x] CHK018 - Is the completeness definition applied consistently by both consumers — the health
      contribution and the on-demand verification — or could they diverge? [Consistency]
      → **Resolved 2026-08-13**: confirmed — both read the same bound configuration object per
      `data-model.md`; no separate logic path exists for either consumer.
- [x] CHK019 - Do [data-model.md](../data-model.md)'s field names (`apiKey`, `endpoint`,
      `chatDeploymentName`, `embeddingDeploymentName`) map unambiguously to the health `missing`
      entries (`api-key`, `endpoint`, `chat-deployment-name`)? Two naming schemes describe one set.
      [Consistency]
      → **Resolved 2026-08-13**: `data-model.md` now states the one-to-one mapping explicitly.
- [x] CHK020 - Does FR-023 ("MAY be unset") sit consistently with
      [runtime-surface.md](../contracts/runtime-surface.md)'s configuration table marking the
      embedding variable "placeholder only; **may be unset**"? [Consistency, Spec §FR-023]
      → **Resolved 2026-08-13**: confirmed consistent, no change needed — both permit the same
      unset state under the same requirement.
- [x] CHK021 - Is FR-016's carve-out (config binding is not PoC behaviour) consistent with FR-022's
      single real request also being excluded from PoC behaviour? [Consistency, Spec §FR-016]
      → **Resolved 2026-08-13**: confirmed consistent, no change needed — FR-016 already names both
      exceptions with the same rationale (nothing processed, stored, or answered).
- [x] CHK022 - Do the spec's Assumptions and FR-018 agree on whether the fourth variable is
      *pre-existing* in the developer's environment? Assumptions list three as already defined;
      FR-018 requires four to be bound. [Consistency, Spec §FR-018]
      → **Resolved 2026-08-13**: Assumptions now state the fourth variable is newly introduced by
      this feature's Clarifications, not pre-existing like the other three, and unlike them MAY be
      unset.

## Acceptance Criteria Quality

- [x] CHK023 - Can SC-006 ("**Zero** secret values ... in version control") be objectively verified?
      Is a detection method, tool, or definition of "secret value" specified? [Measurability, Spec §SC-006]
      → **Resolved 2026-08-13**: SC-006 now names the verification method — `quickstart.md`'s Secret
      check section (`.env` absent from `git ls-files`; no literal in compose/application config).
- [x] CHK024 - Can SC-009 ("**zero** AI credentials present") be set up unambiguously, given CHK001's
      unset-vs-empty question? [Measurability, Spec §SC-009]
      → **Resolved 2026-08-13**: SC-009 now cross-references Definitions' "blank" rule.
- [x] CHK025 - Is SC-008's "**exactly one** request" measurable by a stated method, or does it assume
      an observation mechanism that no requirement provides? [Measurability, Spec §SC-008]
      → **Resolved 2026-08-13**: SC-008 and FR-022 now require the verification's own test to assert
      the single-call count, rather than relying on external observation.
- [x] CHK026 - Is FR-021's partial-configuration rule stated so that every partial combination has a
      determined outcome, rather than only the illustrated "endpoint without a key"? [Measurability, Spec §FR-021]
      → **Resolved 2026-08-13**: `ai-provider.md`'s completeness section now carries an explicit
      truth table covering all combinations, including embedding-set/chat-missing.

## Scenario & Edge Case Coverage

- [x] CHK027 - Are requirements defined for **all four** variables present but the endpoint
      syntactically invalid (not a URL)? Neither startup nor health validates format. [Coverage, Gap]
      → **Resolved 2026-08-13**: FR-019 now states format validation is explicitly out of scope; a
      non-blank but implausible value is treated as present, and the resulting failure surfaces only
      through the on-demand verification.
- [x] CHK028 - Are requirements defined for the embedding deployment name being set while the chat
      deployment name is missing — the inverse of the documented partial case? [Coverage, Edge Case]
      → **Resolved 2026-08-13**: FR-021 explicitly names this combination as one of the partial
      cases that reports unconfigured; the `ai-provider.md` truth table also covers it.
- [x] CHK029 - Are requirements defined for the key being present and syntactically plausible but
      belonging to a **different Azure resource** than the endpoint? [Coverage, Gap]
      → **Resolved 2026-08-13**: covered by the same FR-019 out-of-scope statement as CHK027 —
      accepted gap, surfaced only by the on-demand verification's provider error.
- [x] CHK030 - Is the exclusion of authentication/authorization for configuration status explicitly
      stated as a requirement, given health exposes `configured` booleans unauthenticated?
      [Coverage, Spec §Assumptions]
      → **Resolved 2026-08-13**: the "No authentication anywhere" Assumption now explicitly extends
      to the health check's configuration-status fields, naming this an accepted local-only posture.

## Dependencies & Assumptions

- [x] CHK031 - Is the assumption that the four variable names are **fixed by constitution v1.3.0**
      recorded with enough traceability that a constitution amendment would flag this spec as stale?
      [Assumption, Spec §Assumptions]
      → **Resolved 2026-08-13**: FR-018 now cites `.specify/memory/constitution.md` v1.3.0 directly
      as the authoritative source, alongside the existing Assumptions bullet.
- [x] CHK032 - Is the assumption "the embedding deployment may not exist yet" paired with a stated
      requirement about what the scaffold must do if someone provisions it mid-feature? [Assumption]
      → **Resolved 2026-08-13**: FR-023 now states that provisioning it mid-feature requires a
      backend restart to take effect, per the configuration-read-once-at-startup assumption.
- [x] CHK033 - Is the dependency on Spring AI's auto-configuration gating behaviour recorded as a
      **verified fact with a source**, so a version bump prompts re-verification? [Dependency, Assumption]
      → **Resolved 2026-08-13**: already satisfied, no change needed — `ai-provider.md`'s
      "Auto-configuration is off by default" section cites the disassembled class and jar version
      directly, which is exactly the re-verification trigger this item asks for.

## Notes

- Check items off as resolved: `[x]`
- An item may be resolved by amending the spec, amending a contract, or recording an explicit
  decision that the gap is intentional — all three are valid outcomes
- **All 33 items resolved 2026-08-13.** The former highest-risk cluster (CHK001, CHK002, CHK011,
  CHK024 — the "absent vs blank" question) closed as one edit: spec.md's new **Definitions**
  section.

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

- [ ] CHK001 - Are requirements defined for an environment variable that is **set but empty**, as
      distinct from unset? FR-019 says "absent" and SC-009 says "zero credentials present", neither
      of which covers `AZURE_OPEN_AI_KEY=""`. [Gap, Spec §FR-019, §SC-009]
- [ ] CHK002 - Are requirements defined for a variable set to **whitespace only**? The contracts use
      "present and non-blank"; the spec uses "present". [Gap, Spec §FR-021]
- [ ] CHK003 - Is the prohibition on the API key appearing in **logs and error messages** stated as a
      requirement anywhere in the spec, or does it exist only in the constitution and
      [ai-provider.md](../contracts/ai-provider.md)? FR-009 covers version control only. [Gap, Spec §FR-009]
- [ ] CHK004 - Is the existence of a committed `.env.example` required by any FR, or only implied by
      FR-009's "working local defaults" and assumed by the contracts? [Gap, Spec §FR-009]
- [ ] CHK005 - Are requirements defined for what the health `missing` list contains when the
      **embedding** deployment name is unset? FR-023 assigns that reporting to the on-demand
      verification; whether health also names it is unspecified. [Gap, Spec §FR-023]
- [ ] CHK006 - Is the prohibition on **placeholder default values** expressed as a requirement, or
      only as design rationale in [ai-provider.md](../contracts/ai-provider.md)? A future
      implementer is bound by requirements, not by a rejected-alternatives note. [Gap]
- [ ] CHK007 - Are requirements defined for configuration values that change **while the backend is
      running** (key rotation, endpoint change)? Or is a restart-to-apply model an intentional,
      stated exclusion? [Gap, Coverage]
- [ ] CHK008 - Are requirements defined for the non-Azure configuration surface — datasource URL,
      user, password — to the same standard as the Azure surface? FR-009 treats them in one clause
      while FR-018–FR-023 give Azure six. [Completeness, Spec §FR-009]
- [ ] CHK009 - Does any requirement state which file or mechanism is the **authoritative** source of
      the four variable names, so "MUST NOT be renamed" has a referent? [Traceability, Spec §FR-018]

## Requirement Clarity

- [ ] CHK010 - Is "bind ... from the environment" defined with an observable outcome, or does it
      describe a mechanism without a testable result? [Clarity, Spec §FR-018]
- [ ] CHK011 - Is "present" (FR-021) defined identically to "present and non-blank"
      ([ai-provider.md](../contracts/ai-provider.md) completeness rule)? If not, which governs?
      [Ambiguity, Spec §FR-021]
- [ ] CHK012 - Is "reporting the provider as unconfigured" specified in terms of an observable
      output, or left to the implementer to choose a representation? [Clarity, Spec §FR-019]
- [ ] CHK013 - Is "No secret value may be committed to version control" scoped — working tree only,
      or including **git history**? SC-006's "anywhere in version control" suggests history but does
      not say so. [Ambiguity, Spec §FR-009, §SC-006]
- [ ] CHK014 - Is "working local defaults for non-secret values" clear about whether the *database
      password* counts as secret? It is a local development credential with a default in
      `.env.example`. [Ambiguity, Spec §FR-009]
- [ ] CHK015 - Is "an actionable failure" (FR-022) defined with criteria, or is it a subjective
      quality judgement? [Ambiguity, Spec §FR-022]
- [ ] CHK016 - Is it clear whether "the AI provider key" in FR-009 is one instance of a general rule
      or an exhaustive statement of the external credentials in scope? [Clarity, Spec §FR-009]

## Requirement Consistency

- [ ] CHK017 - Do the spec and [ai-provider.md](../contracts/ai-provider.md) define **completeness**
      with the same three fields and the same blankness rule? [Consistency, Spec §FR-021]
- [ ] CHK018 - Is the completeness definition applied consistently by both consumers — the health
      contribution and the on-demand verification — or could they diverge? [Consistency]
- [ ] CHK019 - Do [data-model.md](../data-model.md)'s field names (`apiKey`, `endpoint`,
      `chatDeploymentName`, `embeddingDeploymentName`) map unambiguously to the health `missing`
      entries (`api-key`, `endpoint`, `chat-deployment-name`)? Two naming schemes describe one set.
      [Consistency]
- [ ] CHK020 - Does FR-023 ("MAY be unset") sit consistently with
      [runtime-surface.md](../contracts/runtime-surface.md)'s configuration table marking the
      embedding variable "placeholder only; **may be unset**"? [Consistency, Spec §FR-023]
- [ ] CHK021 - Is FR-016's carve-out (config binding is not PoC behaviour) consistent with FR-022's
      single real request also being excluded from PoC behaviour? [Consistency, Spec §FR-016]
- [ ] CHK022 - Do the spec's Assumptions and FR-018 agree on whether the fourth variable is
      *pre-existing* in the developer's environment? Assumptions list three as already defined;
      FR-018 requires four to be bound. [Consistency, Spec §FR-018]

## Acceptance Criteria Quality

- [ ] CHK023 - Can SC-006 ("**Zero** secret values ... in version control") be objectively verified?
      Is a detection method, tool, or definition of "secret value" specified? [Measurability, Spec §SC-006]
- [ ] CHK024 - Can SC-009 ("**zero** AI credentials present") be set up unambiguously, given CHK001's
      unset-vs-empty question? [Measurability, Spec §SC-009]
- [ ] CHK025 - Is SC-008's "**exactly one** request" measurable by a stated method, or does it assume
      an observation mechanism that no requirement provides? [Measurability, Spec §SC-008]
- [ ] CHK026 - Is FR-021's partial-configuration rule stated so that every partial combination has a
      determined outcome, rather than only the illustrated "endpoint without a key"? [Measurability, Spec §FR-021]

## Scenario & Edge Case Coverage

- [ ] CHK027 - Are requirements defined for **all four** variables present but the endpoint
      syntactically invalid (not a URL)? Neither startup nor health validates format. [Coverage, Gap]
- [ ] CHK028 - Are requirements defined for the embedding deployment name being set while the chat
      deployment name is missing — the inverse of the documented partial case? [Coverage, Edge Case]
- [ ] CHK029 - Are requirements defined for the key being present and syntactically plausible but
      belonging to a **different Azure resource** than the endpoint? [Coverage, Gap]
- [ ] CHK030 - Is the exclusion of authentication/authorization for configuration status explicitly
      stated as a requirement, given health exposes `configured` booleans unauthenticated?
      [Coverage, Spec §Assumptions]

## Dependencies & Assumptions

- [ ] CHK031 - Is the assumption that the four variable names are **fixed by constitution v1.3.0**
      recorded with enough traceability that a constitution amendment would flag this spec as stale?
      [Assumption, Spec §Assumptions]
- [ ] CHK032 - Is the assumption "the embedding deployment may not exist yet" paired with a stated
      requirement about what the scaffold must do if someone provisions it mid-feature? [Assumption]
- [ ] CHK033 - Is the dependency on Spring AI's auto-configuration gating behaviour recorded as a
      **verified fact with a source**, so a version bump prompts re-verification? [Dependency, Assumption]

## Notes

- Check items off as resolved: `[x]`
- An item may be resolved by amending the spec, amending a contract, or recording an explicit
  decision that the gap is intentional — all three are valid outcomes
- Highest-risk cluster: **CHK001, CHK002, CHK011, CHK024** all circle the same unresolved question
  — what exactly distinguishes "absent" from "blank". Resolving that one definition closes four items

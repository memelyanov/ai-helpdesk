<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/001-project-scaffolding/plan.md](specs/001-project-scaffolding/plan.md)

Supporting design artifacts for the active feature:
- [spec.md](specs/001-project-scaffolding/spec.md) — requirements, acceptance scenarios, clarifications
- [research.md](specs/001-project-scaffolding/research.md) — 11 decisions; versions and Spring property names verified against live registries and artifacts
- [data-model.md](specs/001-project-scaffolding/data-model.md) — database init state and bound configuration
- [contracts/health-api.md](specs/001-project-scaffolding/contracts/health-api.md) — health endpoint, three cases
- [contracts/ai-provider.md](specs/001-project-scaffolding/contracts/ai-provider.md) — Azure OpenAI binding and on-demand verification
- [contracts/runtime-surface.md](specs/001-project-scaffolding/contracts/runtime-surface.md) — ports, commands, independence
- [quickstart.md](specs/001-project-scaffolding/quickstart.md) — how to run and verify

Governance: [.specify/memory/constitution.md](.specify/memory/constitution.md) v1.3.0 — seven
principles, Spec-First and TDD first among them; Azure OpenAI is the mandated inference provider.

Two constraints that shape most of the backend design:
- The backend MUST start with the database down (FR-007) — hence no JPA, no startup migrations.
- The backend MUST start with Azure credentials absent (FR-019) — hence `spring.ai.model.chat`
  and `spring.ai.model.embedding` default to `none`, keeping the Spring AI starter inert.
<!-- SPECKIT END -->

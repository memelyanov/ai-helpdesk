<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/002-frontend-health-wire/plan.md](specs/002-frontend-health-wire/plan.md)

Supporting design artifacts for the active feature:
- [spec.md](specs/002-frontend-health-wire/spec.md) — requirements, acceptance scenarios, three-state connection model
- [research.md](specs/002-frontend-health-wire/research.md) — 6 decisions: HttpClient+signal, 10s poll/3s timeout, Actuator CORS, status-only classification, checking state, HttpTestingController tests
- [data-model.md](specs/002-frontend-health-wire/data-model.md) — the client-side Connection Status state machine
- [contracts/frontend-health-consumption.md](specs/002-frontend-health-wire/contracts/frontend-health-consumption.md) — what the frontend reads from `/actuator/health` and the CORS requirement
- [quickstart.md](specs/002-frontend-health-wire/quickstart.md) — how to see and verify the indicator

Prior feature, still the source of truth for the scaffold itself:
[specs/001-project-scaffolding/plan.md](specs/001-project-scaffolding/plan.md) — database, backend,
frontend skeleton; [contracts/health-api.md](specs/001-project-scaffolding/contracts/health-api.md)
— the health endpoint response shape this feature consumes unchanged.

Governance: [.specify/memory/constitution.md](.specify/memory/constitution.md) v1.3.0 — seven
principles, Spec-First and TDD first among them; Azure OpenAI is the mandated inference provider.

Two constraints that shape this feature's design:
- Degraded and unreachable MUST stay distinct indicator states, never collapsed together (FR-003).
- A hung health request MUST NOT block the indicator from eventually showing unreachable — hence a
  3-second per-request timeout separate from the 10-second poll interval (FR-007).
<!-- SPECKIT END -->

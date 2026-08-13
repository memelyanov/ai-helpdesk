# Feature Specification: Project Scaffolding

**Feature Branch**: `001-project-scaffolding`

**Created**: 2026-08-13

**Status**: Draft

**Input**: User description: "we need to scaffold all parts of the application: create docker-compose.yml to populate db image, create spring backend application and setup angular frontend. At the moment all these parts should be just created and able to run. All actual functionality will be added later"

## Clarifications

### Session 2026-08-13

- Q: How much Azure OpenAI setup belongs in this scaffolding feature? → A: Configuration binding plus an on-demand reachability check. No chat, no embeddings, no ingestion.
- Q: Azure needs a separate deployment for embeddings — how should that be handled? → A: Add a fourth variable `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME`, bound now, unused until ingestion.
- Q: What should the backend do when the Azure OpenAI variables are missing? → A: Start anyway and report the AI provider as unconfigured, mirroring the database treatment in FR-007.
- Q: How should the Azure reachability check be exposed and triggered? → A: The health endpoint reports configuration status without calling Azure; a separate on-demand verification makes one real call.
- Q: The constitution mandates direct OpenAI — how should the Azure switch be recorded? → A: Amend the constitution to Azure OpenAI before implementation begins.

## User Scenarios & Testing *(mandatory)*

The user of this feature is a **developer on the AI Helpdesk PoC** — the person who will
subsequently implement ingestion, retrieval and the chat interface. This feature delivers the
empty-but-running skeleton those later features are built into. It deliberately delivers **no
end-user functionality**: no upload, no search, no answers.

### User Story 1 - Start the database environment (Priority: P1)

A developer clones the repository, runs one documented command, and has a running database
environment capable of storing vector embeddings alongside document metadata. They can connect
to it, confirm the vector-storage capability is enabled, and stop and restart it without losing
what was stored.

**Why this priority**: Everything else in the PoC depends on a working store. It is also the
only part of the stack that is genuinely shared infrastructure — the backend cannot be
meaningfully verified without it, and it is the piece a developer is least able to improvise.

**Independent Test**: Run the documented start command on a clean checkout, connect to the
database, confirm the vector-storage capability is available, write a row, restart the
environment, and confirm the row survived. No backend or frontend involvement.

**Acceptance Scenarios**:

1. **Given** a clean checkout and the documented prerequisites installed, **When** the developer
   runs the documented database start command, **Then** the database becomes reachable on the
   documented local port within a short startup window and reports itself ready.
2. **Given** the database is running, **When** the developer inspects the installed capabilities,
   **Then** vector storage and vector similarity search are available for use.
3. **Given** data has been written to the database, **When** the environment is stopped and
   started again, **Then** the previously written data is still present.
4. **Given** the database is running, **When** the developer runs the documented stop command,
   **Then** the environment shuts down cleanly and releases its port.

---

### User Story 2 - Run the backend service (Priority: P2)

A developer starts the backend service with one documented command. The service boots, exposes a
health check that reports whether the service itself, its database connection, and its AI provider
configuration are usable, and its automated test suite runs green. The AI provider credentials are
bound from the environment and can be verified on demand, but no AI capability is used yet.

**Why this priority**: The backend is where all PoC logic will live (ingestion, retrieval, answer
generation). A booting service with a working test harness is the precondition for the
test-first workflow the constitution mandates — without it, no failing test can be written.

**Independent Test**: With the database running, start the backend and request the health check;
it reports a healthy service and a reachable database. Separately, run the test command and see
the suite pass. No frontend involvement.

**Acceptance Scenarios**:

1. **Given** the database environment is running, **When** the developer starts the backend with
   the documented command, **Then** the service starts and listens on the documented local port.
2. **Given** the backend is running, **When** a health check is requested, **Then** the response
   indicates the service is healthy and reports the database as reachable.
3. **Given** the database environment is **not** running, **When** the backend is started,
   **Then** the service still starts and its health check reports the database as unreachable,
   with a message a developer can act on — it does not crash or restart in a loop.
4. **Given** a clean checkout, **When** the developer runs the documented backend test command,
   **Then** the suite executes and every test passes, including at least one test exercising the
   health check.
5. **Given** the backend configuration, **When** the repository is inspected, **Then** database
   credentials and any API keys are supplied by environment/configuration with local development
   defaults, and no secret value is committed.
6. **Given** the AI provider credentials are present in the environment, **When** the health check
   is requested, **Then** it reports the AI provider as configured — without contacting the
   provider, so the check costs nothing and cannot be slowed by an external service.
7. **Given** the AI provider credentials are absent, **When** the backend is started, **Then** it
   starts normally and the health check reports the AI provider as unconfigured, leaving the
   overall service status unaffected.
8. **Given** the AI provider credentials are present, **When** the developer runs the documented
   on-demand connectivity verification, **Then** exactly one real request is made to the provider
   and the outcome is reported as success or as an actionable failure.

---

### User Story 3 - Run the frontend application (Priority: P3)

A developer starts the frontend with one documented command, opens it in a browser, and sees a
placeholder page that identifies the application. Its automated test suite runs green.

**Why this priority**: The frontend carries no logic at this stage and blocks nothing else, so it
is last. It still has to exist and run, because the later chat and upload views need a project to
be added to and a test harness to be written against.

**Independent Test**: On a clean checkout, run the documented frontend start command, open the
documented local address, and see the placeholder page render. Separately run the frontend test
command and see it pass. Requires neither backend nor database.

**Acceptance Scenarios**:

1. **Given** a clean checkout with the documented prerequisites, **When** the developer runs the
   documented frontend start command, **Then** the application builds and is served on the
   documented local port.
2. **Given** the frontend is being served, **When** the developer opens it in a browser, **Then**
   a placeholder page renders that names the application, with no console errors.
3. **Given** a clean checkout, **When** the developer runs the documented frontend test command,
   **Then** the suite executes and every test passes, including at least one test asserting the
   placeholder page renders.

---

### User Story 4 - Bring up the whole stack from the documentation alone (Priority: P3)

A developer who has never seen the repository follows the written setup instructions end to end
and gets all three parts running together, without asking anyone or reading source code.

**Why this priority**: The scaffolding is only useful if it is reproducible by someone other than
its author. This story is what turns three separate runnable parts into a usable project, but it
depends on all three existing first.

**Independent Test**: Hand the repository to a developer who has not worked on it. They follow
the setup documentation only and reach a state where database, backend and frontend are all
running.

**Acceptance Scenarios**:

1. **Given** the repository documentation, **When** a new developer reads the setup section,
   **Then** it states the required tooling and versions, the start command for each part, the
   local address each part serves on, and the order to start them in.
2. **Given** the documented steps are followed in order on a machine meeting the prerequisites,
   **When** the developer finishes, **Then** all three parts are running simultaneously and each
   is verifiable by the check described in its own story.
3. **Given** the scaffolding is complete, **When** the repository's status description is read,
   **Then** it accurately reflects that a runnable skeleton exists and that PoC functionality is
   not yet implemented.

---

### Edge Cases

- **A required local port is already occupied.** Startup fails with a message naming the port and
  the part that wanted it, rather than failing silently or hanging.
- **Prerequisite tooling is missing or the wrong major version.** The developer meets a clear
  failure that names the missing tool; the documentation states required versions so this is
  diagnosable before it happens.
- **The backend starts before the database is ready.** Covered explicitly: the service starts and
  reports the database as unreachable through its health check rather than crash-looping.
- **A stale database volume exists from an earlier attempt.** The documentation states how to
  discard the stored state and start clean.
- **The database container is restarted while the backend is running.** The backend recovers and
  its health check returns to reporting the database as reachable, without a manual restart.
- **A developer runs only one part.** Each part starts on its own; only the backend's database
  health signal is affected by the absence of another part.
- **AI provider credentials are absent or incomplete.** The backend starts and reports the
  provider as unconfigured. Partial configuration — for example an endpoint without a key — is
  reported as unconfigured rather than treated as configured, so a half-set environment cannot
  masquerade as a working one.
- **AI provider credentials are present but wrong.** Configuration status still reports
  *configured*, because it performs no network call; the on-demand verification is what surfaces
  the failure, and it reports the provider's own error rather than a generic message.
- **The embedding deployment name is unset.** Accepted without error in this feature — nothing
  uses it yet — but reported as missing by the on-demand verification so the gap is visible before
  the ingestion feature depends on it.

## Requirements *(mandatory)*

### Functional Requirements

**Database environment**

- **FR-001**: The repository MUST provide a single-command way to start a local database
  environment, and a matching command to stop it.
- **FR-002**: The database environment MUST have vector storage and vector similarity search
  capability enabled and verifiable, ready for later embedding work.
- **FR-003**: Data written to the database MUST survive stopping and restarting the environment.
- **FR-004**: The database MUST be reachable from the host machine on a documented, fixed local
  port, using documented local development credentials.

**Backend service**

- **FR-005**: The backend MUST start with a single documented command and serve on a documented,
  fixed local port.
- **FR-006**: The backend MUST expose a health check reporting its own status and whether its
  database connection is usable.
- **FR-007**: The backend MUST start successfully when the database is unavailable, reporting the
  unreachable database through the health check instead of failing to boot.
- **FR-008**: The backend MUST include an automated test suite, runnable with a single documented
  command, containing at least one passing test that exercises the health check.
- **FR-009**: Database connection settings and any external API credentials — including the AI
  provider key — MUST be supplied through configuration or environment variables, with working
  local defaults for non-secret values. No secret value may be committed to version control.

**AI provider configuration**

- **FR-018**: The backend MUST bind AI provider configuration from the environment: the API key,
  the service endpoint, the chat deployment name, and the embedding deployment name. Existing
  variable names in the developer's environment MUST be reused rather than renamed.
- **FR-019**: The backend MUST start successfully when some or all AI provider variables are
  absent, reporting the provider as unconfigured rather than failing to boot.
- **FR-020**: The health check MUST report AI provider configuration status **without contacting
  the provider**, and an unconfigured provider MUST NOT change the overall service status — a
  developer without AI credentials still gets a healthy service.
- **FR-021**: Configuration MUST be treated as complete only when the key, endpoint and chat
  deployment name are all present. A partially populated environment MUST report as unconfigured.
- **FR-022**: The repository MUST provide a documented, explicitly invoked verification that makes
  exactly one real request to the AI provider and reports success or an actionable failure. It
  MUST NOT run as part of normal startup, the health check, or the default test suite.
- **FR-023**: The embedding deployment name MUST be bound and documented but MAY be unset in this
  feature; nothing consumes it until the ingestion feature. The on-demand verification MUST report
  it as missing when unset.

**Frontend application**

- **FR-010**: The frontend MUST start with a single documented command and be served on a
  documented, fixed local port.
- **FR-011**: The frontend MUST render a placeholder page identifying the application, with no
  browser console errors.
- **FR-012**: The frontend MUST include an automated test suite, runnable with a single
  documented command, containing at least one passing test that asserts the placeholder page
  renders.

**Across the whole scaffold**

- **FR-013**: Each of the three parts MUST be startable independently of the others.
- **FR-014**: The repository MUST document, in one place, the prerequisites, the start and stop
  command for each part, the local address each part serves on, and the recommended start order.
- **FR-015**: Build outputs, downloaded dependencies, and local environment files MUST be
  excluded from version control.
- **FR-016**: This feature MUST NOT implement any PoC behaviour — no document upload, parsing,
  chunking, embedding, retrieval, or answer generation. Those arrive in later features. Binding AI
  provider configuration (FR-018) and verifying connectivity on demand (FR-022) are explicitly
  **not** PoC behaviour: no document is processed, no embedding is stored, and no answer is
  produced. The single request made by the verification exists to prove credentials, not to serve
  a user.
- **FR-017**: The repository's own status documentation MUST be updated to state accurately that
  a runnable skeleton exists without PoC functionality.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer starting from a clean checkout on a machine meeting the documented
  prerequisites has all three parts running in **under 15 minutes**, using the documentation
  alone and asking no questions.
- **SC-002**: Each of the three parts starts with **exactly one** documented command and produces
  a verifiable running signal (a reachable port and a health or page check).
- **SC-003**: Both automated test suites complete with **zero failures** and zero skipped tests on
  a clean checkout.
- **SC-004**: Stopping and restarting the database environment preserves **100%** of previously
  stored data.
- **SC-005**: Each part starts successfully when run **alone**, verified by three separate
  single-part startups.
- **SC-006**: **Zero** secret values are present anywhere in version control.
- **SC-007**: A developer who did not build the scaffold completes the full setup on the **first
  attempt** without editing any file that the documentation did not tell them to edit.
- **SC-008**: A developer holding valid AI provider credentials confirms connectivity with **one**
  documented command, and that command makes **exactly one** request to the provider.
- **SC-009**: With **zero** AI credentials present in the environment, all three parts still start
  and both test suites still pass — the scaffold is fully usable by someone with no AI access.

## Assumptions

- **The technology choices are already fixed** by `.specify/memory/constitution.md` (Technology
  Stack & Tooling Requirements) and `docs/poc-concept.md`. This specification therefore describes
  the three parts by role — database environment, backend service, frontend application — and
  leaves the named technologies, versions, and project layout to the implementation plan. Nothing
  here reopens a stack decision.
- **Only the database runs in a container** during the PoC phase; the backend and frontend run
  directly on the developer's machine, as the constitution's infrastructure row states.
- **Local development only.** No deployment target, CI pipeline, container image publishing, or
  production configuration is in scope for this feature.
- **A single developer on one machine at a time.** Fixed local ports are acceptable; no port
  allocation strategy is needed.
- **The health check reports database reachability** rather than the backend refusing to start
  without a database. This keeps the frontend and backend independently runnable, which SC-005
  requires.
- **No authentication anywhere**, consistent with the PoC scope boundaries — the scaffold exposes
  unauthenticated local endpoints only.
- **The placeholder frontend page makes no backend calls.** Wiring the two together is left to the
  feature that introduces the first real endpoint, so the frontend has no dependency to break.
- **AI credentials are not required to run the scaffold.** Every part starts, serves and tests
  green without them (SC-009). The only thing that contacts the AI provider is the on-demand
  verification, which a developer chooses to run.
- **The AI provider is Azure OpenAI, not the OpenAI API directly.** The developer's environment
  already defines `AZURE_OPEN_AI_KEY`, `AZURE_OPEN_AI_ENDPOINT` and
  `AZURE_OPEN_AI_DEPLOYMENT_NAME`; this feature reuses those names rather than introducing
  parallel ones. `OPENAI_API_KEY` is not set in that environment, so the direct-OpenAI path is not
  currently usable.
- **The constitution is amended to Azure OpenAI before implementation starts.** Its Technology
  Stack table currently mandates the OpenAI API directly and forbids substitution without an
  amendment, so this feature is blocked on that amendment landing first.
- **Azure OpenAI binds one model per deployment**, so chat and embeddings require two separate
  deployments and two separate names. The embedding deployment may not exist yet; creating it is a
  prerequisite of the ingestion feature, not of this one.
- **Test suites are part of the scaffold, not an addition to it.** The constitution mandates
  test-driven development, which is impossible without a working harness; each part therefore
  ships with a runnable suite and at least one real passing test.

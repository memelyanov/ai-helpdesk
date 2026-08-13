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

## Definitions

Terms used with a precise, load-bearing meaning across the requirements below:

- **Blank**: unset, set to the empty string, or set to a string containing only whitespace. FR-019
  ("absent"), FR-021 ("present") and the AI-provider completeness rule all use this one definition
  — an empty or whitespace-only environment variable is treated identically to an unset one
  everywhere in this feature. There is no separate "set but empty" state.
- **Present**: not blank, per the definition above. "Present" and "present and non-blank" are the
  same test, stated once here rather than twice per requirement.
- **Committed to version control** (FR-009, SC-006): scoped to the repository's tracked working
  tree from this feature's first commit onward — the files `git ls-files` returns. Remediating
  history that predates this feature is out of scope for it.
- **Actionable failure** (FR-022): a failure report that either names the specific setting that is
  missing (incomplete configuration) or carries the AI provider's own reported status and error
  text (complete configuration, request failed) — in both cases, diagnosable by reading the report
  alone, without consulting logs or source.
- **The documented prerequisites** (US1-1, US3-1, SC-001, SC-007): the tooling and version table
  FR-014 requires the documentation to state. Any requirement or scenario referring to "the
  documented prerequisites" means that table.

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
   documented local port within approximately 30 seconds and passes its own container healthcheck
   (not merely accepting a TCP connection).
2. **Given** the database is running, **When** the developer inspects the installed capabilities,
   **Then** vector storage and vector similarity search are available for use.
3. **Given** data has been written to the database, **When** the environment is stopped and
   started again, **Then** the previously written data is still present.
4. **Given** the database is running, **When** the developer runs the documented stop command,
   **Then** the stop command exits successfully and a subsequent connection attempt on the
   documented port fails within a few seconds, confirming the port was released.

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
   indicates the service is healthy and reports the database connection as usable — able to obtain
   and validate a connection from the pool, the same notion of "reachable" and "usable" used
   throughout this feature.
3. **Given** the database environment is **not** running, **When** the backend is started,
   **Then** the service still starts and its health check reports the database as unreachable,
   naming the database as the failing dependency and including the underlying connection error
   text — it does not crash or restart in a loop.
4. **Given** a clean checkout, **When** the developer runs the documented backend test command,
   **Then** the suite executes and every test passes, including at least one test exercising the
   health check.
5. **Given** the backend configuration, **When** the repository is inspected, **Then** database
   credentials and any API keys are supplied by environment/configuration with local development
   defaults, and no secret value is committed.
6. **Given** the AI provider credentials are present in the environment, **When** the health check
   is requested, **Then** it reports the AI provider as configured — meaning the credentials are
   present, not that they are valid — without contacting the provider (verifiable by the absence of
   any outbound network call from the indicator), so the check costs nothing and cannot be slowed
   by an external service. Whether the credentials actually work is established only by the
   on-demand verification (Scenario 8).
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
   **Then** it states the required tooling and versions (including any version ranges with
   exclusions, not just major versions), any one-time setup step, the start and stop command for
   each part, how to verify each part is working, the local address each part serves on, a
   troubleshooting reference for known failure modes, and the recommended (not mandatory — FR-013
   guarantees any order works) order to start them in. `README.md` is that single place;
   `quickstart.md` is a supporting validation guide and does not compete with it — where the two
   would ever disagree, `README.md` governs.
2. **Given** the documented steps are followed in order on a machine meeting the prerequisites,
   **When** the developer finishes, **Then** all three parts are running simultaneously and each
   is verifiable by the check described in its own story. A developer following this story is
   assumed to have baseline command-line, git and Docker familiarity; the documentation teaches the
   project, not those tools.
3. **Given** the scaffolding is complete, **When** the repository's status description is read,
   **Then** it accurately reflects that a runnable skeleton exists and that PoC functionality is
   not yet implemented.

---

### Edge Cases

> Each edge case below is backed by a functional requirement. Where a bullet carries a behavioural
> expectation, the requirement that binds it is named — narrative alone does not oblige an
> implementation.

- **A required local port is already occupied.** Startup fails with a message naming the port and
  the part that wanted it, rather than failing silently or hanging. **(FR-026)**
- **Prerequisite tooling is missing or the wrong major version.** The developer meets a clear
  failure that names the missing tool; the documentation states required versions so this is
  diagnosable before it happens.
- **The backend starts before the database is ready.** Covered explicitly: the service starts and
  reports the database as unreachable through its health check rather than crash-looping.
- **A stale database volume exists from an earlier attempt.** The documentation states how to
  discard the stored state and start clean. **(FR-024)**
- **The database container is restarted while the backend is running.** The backend recovers and
  its health check returns to reporting the database as reachable, without a manual restart and
  within the window FR-025 sets. **(FR-025)**
- **A developer runs only one part.** Each part starts independently (FR-013); a part already
  running is unaffected by another part being absent. The backend is the only part whose *reported
  health* varies with what else is running — its database and AI-provider components — and the two
  vary independently of each other, so both can be degraded at once (database unreachable and AI
  provider unconfigured simultaneously). That combination is not a distinct case: it is simply
  FR-007's database report and FR-020's AI-provider report each doing what they already do, at the
  same time.
- **A second instance of a part is started while one is already running.** No special handling
  exists or is needed: the second instance hits the same occupied port as any other conflict and
  fails per FR-026.
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
- **FR-024**: The repository MUST document a command that discards the stored database state and
  returns the environment to its initial condition, and MUST identify that command as destructive
  and distinguish it from the ordinary stop command of FR-001.
- A database container that is reachable but has not run its initialisation scripts (the stale-
  volume case FR-024 exists to remedy), or that is reachable without the `vector` extension present
  for any other reason, is a known, accepted gap in this feature: no health or startup requirement
  distinguishes "connected" from "connected and schema-ready". FR-002's verification query is a
  manual/documented check (see quickstart.md), not an automated one. Detecting this automatically
  is left to the ingestion feature, which is the first to depend on the extension being present.

**Backend service**

- **FR-005**: The backend MUST start with a single documented command and serve on a documented,
  fixed local port.
- **FR-006**: The backend MUST expose a health check, at a documented HTTP path, reporting its own
  basic liveness (distinct from the aggregate status contributed by its dependencies) and whether
  its database connection is usable — able to obtain and validate a connection from the pool, not
  merely that the process is reachable and not that the schema is fully prepared. Consumers MAY
  rely on the response fields enumerated in `contracts/health-api.md`; other fields are not
  guaranteed stable across changes. The health check's own behaviour if a dependency indicator
  throws is not specified by this feature; it inherits whatever failure handling the underlying
  health-check mechanism provides.
- **FR-007**: The backend MUST start successfully when the database is unavailable, reporting the
  unreachable database through the health check instead of failing to boot. The report MUST
  identify the database as the failing dependency and MUST carry the underlying connection error
  text, so the cause is diagnosable without reading application logs or source. Startup itself MUST
  NOT be blocked or measurably delayed by database connection attempts or pool timeouts — the
  service listens on its port and answers the health check as fast whether the database is up or
  down. This is a **fault**, not a state: the backend is configured to use a database and cannot
  reach it, which is why it changes the overall service status (unlike FR-020's AI-provider case,
  which is a state — a capability simply not configured yet).
- **FR-025**: When the database becomes reachable after having been unavailable, the backend MUST
  report it as reachable again **without being restarted**, within 30 seconds of the database
  beginning to accept connections.
- **FR-008**: The backend MUST include an automated test suite, runnable with a single documented
  command, containing at least one passing test exercising each of: the database reachable case,
  the database unreachable case, and the AI provider unconfigured case.
- **FR-009**: Database connection settings and any external API credentials — including but not
  limited to the AI provider key — MUST be supplied through configuration or environment
  variables, with working local defaults for non-secret values. No secret value may be committed to
  version control (see Definitions). The local database password is a secret under this rule on the
  same footing as the AI provider key — never committed, only ever supplied via `.env` — even
  though it grants access only to a local development container; the database name and application
  user are the non-secret values with working defaults in `.env.example`. No secret value,
  including the AI provider key, may appear in logs or error messages, in the health response, or
  in test output. The repository MUST provide a committed `.env.example` template covering every
  variable a developer must set locally and its non-secret default, if any.

**AI provider configuration**

- **FR-018**: The backend MUST bind AI provider configuration from the environment: the API key,
  the service endpoint, the chat deployment name, and the embedding deployment name. Existing
  variable names in the developer's environment MUST be reused rather than renamed. These four
  names are fixed by `.specify/memory/constitution.md` v1.3.0, which is their authoritative source;
  a future constitution amendment renaming them supersedes this requirement. "Bind" means each
  value becomes available to the running service and participates in the completeness rule
  (FR-021) and health reporting (FR-020) — an observable effect, not a mandated mechanism. Bound
  values MUST default to blank (see Definitions) when unset, never to a non-blank placeholder — a
  placeholder would let an unconfigured environment be misreported as configured, and (per Azure's
  own credential-resolution behaviour) a non-blank endpoint with a blank key can silently switch to
  a different authentication path instead of failing, which FR-019's "unconfigured" report exists
  to avoid.
- **FR-019**: The backend MUST start successfully when some or all AI provider variables are
  absent (blank, see Definitions), reporting the provider as unconfigured rather than failing to
  boot. Format-validating any variable — for example, confirming the endpoint is a syntactically
  valid URL, or that the key belongs to the same Azure resource as the endpoint — is explicitly out
  of scope for this feature; a syntactically implausible but non-blank value is treated as present,
  and a resulting connection failure surfaces only through the on-demand verification (FR-022),
  which reports the provider's own error.
- **FR-020**: The health check MUST report AI provider configuration status **without contacting
  the provider**, and an unconfigured provider MUST NOT change the overall service status — a
  developer without AI credentials still gets a healthy service. This report is contributed as a
  distinctly named, separate component of the health response (see `contracts/health-api.md` for
  the exact name and status vocabulary); this feature's own requirement is the observable behaviour
  above, not the vocabulary, which is a contract-level detail. This is a **state**, not a fault —
  see FR-007's contrasting case.
- **FR-021**: Configuration MUST be treated as complete only when the key, endpoint and chat
  deployment name are all present (see Definitions). A partially populated environment — any
  combination short of all three, including the embedding-name-set-but-chat-name-missing case —
  MUST report as unconfigured. There is no partially-usable state.
- **FR-022**: The repository MUST provide a documented, explicitly invoked verification that makes
  exactly one real request to the AI provider and reports success or an actionable failure (see
  Definitions). It MUST NOT run as part of normal startup, the health check, or the default test
  suite; the "exactly one request" guarantee MUST itself be verified by the verification's own test
  (for example, by asserting a single call was made), not left to inspection.
- **FR-023**: The embedding deployment name MUST be bound and documented but MAY be unset in this
  feature; nothing consumes it until the ingestion feature. The health check does not name the
  embedding deployment specifically — only the on-demand verification MUST report it as missing
  when unset. If the deployment is provisioned and the variable set while a running backend
  predates that change, no requirement in this feature applies the change automatically; a restart
  is required, consistent with configuration being read once at startup (see Assumptions).

**Frontend application**

- **FR-010**: The frontend MUST start with a single documented command and be served on a
  documented, fixed local port.
- **FR-011**: The frontend MUST render a placeholder page identifying the application, with no
  browser console errors.
- **FR-012**: The frontend MUST include an automated test suite, runnable with a single
  documented command, containing at least one passing test that asserts the placeholder page
  renders.

**Across the whole scaffold**

- **FR-013**: Each of the three parts MUST be startable independently of the others — meaning each
  is able to start and serve on its own, not that its reported behaviour is unaffected by which
  other parts are running (the backend's health signal is explicitly allowed to vary; see Edge
  Cases). A part already running is unaffected by another part being started or stopped, and the
  database and frontend hold no state that a backend or frontend restart could put at risk — only
  the database has meaningful restart/recovery semantics (FR-003, FR-025), because only it persists
  anything.
- **FR-014**: The repository MUST document, in one place (`README.md` — see US4 Scenario 1), the
  prerequisites and their versions including any version-range exclusions, any one-time setup step,
  the start and stop command for each part, how to verify each part is working, the local address
  each part serves on, a troubleshooting section for known failure modes, and the recommended (not
  mandatory, per FR-013) start order. A one-time dependency-installation step MUST be documented as
  a prerequisite, distinct from the start command it precedes. The documentation MUST state that AI
  provider credentials are optional for running the scaffold (SC-009).
- **FR-026**: When a required local port is already in use, the affected part MUST fail to start
  with a message naming the occupied port. It MUST NOT bind an alternative port instead, because
  the documented addresses of FR-014 would then be wrong. This requirement does not mandate a
  custom implementation: whichever tool detects the conflict first — Docker Compose, the backend's
  embedded server, or the frontend's dev server — already reports the offending port by default,
  and that default behaviour satisfies this requirement. A second instance of any part started
  while one is already running fails through this same port-conflict path; no separate handling is
  required.
- **FR-027**: The setup documentation MUST state which shell its commands are written for, and
  every documented command MUST be executable as written on the primary development platform.
  Where a command differs between the supported shells, each form MUST be given.
- **FR-015**: Build outputs, downloaded dependencies, and local environment files MUST be
  excluded from version control.
- **FR-016**: This feature MUST NOT implement any PoC behaviour — no document upload, parsing,
  chunking, embedding, retrieval, or answer generation. Those arrive in later features. Binding AI
  provider configuration (FR-018) and verifying connectivity on demand (FR-022) are explicitly
  **not** PoC behaviour: no document is processed, no embedding is stored, and no answer is
  produced. The single request made by the verification exists to prove credentials, not to serve
  a user.
- **FR-017**: The repository's own status documentation — the status section of `README.md`, the
  same document FR-014 designates — MUST be updated to state accurately that a runnable skeleton
  exists without PoC functionality. "Accurately" means: consistent with FR-016's exclusion list and
  with the feature's actual completion state at time of review; verified by a reviewer comparing
  the stated wording against both.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer starting from a clean checkout on a machine meeting the documented
  prerequisites (prerequisites already installed; the clock starts at `git clone`) has all three
  parts running — each verified by the check described in its own user story — in **under 15
  minutes**, using the documentation alone. "Asking no questions" is judged by the same fresh-
  developer trial that validates US4: if a documentation gap forces them to ask, the documentation
  failed regardless of the elapsed time.
- **SC-002**: Each of the three parts starts with **exactly one** documented command and produces
  a verifiable running signal (a reachable port and a health or page check). A one-time
  dependency-installation step counts as a prerequisite rather than a start command, and is
  excluded from this count — it is performed once per checkout, not on every start.
- **SC-003**: Both automated test suites complete with **zero failures** and zero skipped tests on
  a clean checkout.
- **SC-004**: Stopping and restarting the database environment preserves **100%** of previously
  stored data, verified by the write-restart-read probe in `quickstart.md`'s persistence check: a
  row written before restart is present, unchanged, after it.
- **SC-005**: Each part starts successfully when run **alone from a fully stopped state**, verified
  by three separate single-part startups.
- **SC-006**: **Zero** secret values are present anywhere in version control (see Definitions),
  verified by the method in `quickstart.md`'s Secret check section: `.env` is absent from
  `git ls-files`, and no password or key literal appears in `docker-compose.yml` or
  `application.yml`.
- **SC-007**: A developer who did not build the scaffold completes the full setup on the **first
  attempt** — ending at the first completed run of all three start commands; re-running a command
  that failed, or reading the troubleshooting table, does not itself end the attempt — without
  editing any file that the documentation did not tell them to edit. Copying `.env.example` to
  `.env` per the documented Setup step does not count as an undocumented edit.
- **SC-008**: A developer holding valid AI provider credentials confirms connectivity with **one**
  documented command, and that command makes **exactly one** request to the provider — asserted by
  the verification's own test counting the calls it makes, not by external observation.
- **SC-009**: With **zero** AI credentials present in the environment (see Definitions), all three
  parts still start and both test suites still pass — the scaffold is fully usable by someone with
  no AI access.

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
- **A single developer on one machine at a time.** Fixed local ports (FR-004, FR-005, FR-010) are
  acceptable; no port allocation strategy is needed. This is why FR-026's port-conflict behaviour,
  not a port-negotiation feature, is the correct response to a collision. Concurrent multi-developer
  or concurrent-request scenarios (including concurrent health requests) are out of scope for this
  local PoC.
- **No resource-exhaustion handling.** Disk space for the database volume and network bandwidth for
  first-run dependency downloads (Maven, npm) are assumed sufficient; SC-001's 15-minute budget
  assumes typical broadband and is not a guarantee under constrained or offline networks.
- **No rollback requirement beyond FR-024.** The only state mutation this feature performs is
  creating the database volume; FR-024 already documents how to reverse it. Nothing else in this
  feature needs a rollback path.
- **A failed database initialisation script, or a container upgrade that changes when init scripts
  run, is an accepted gap.** Both surface as the container's own healthcheck failing or the
  `vector` extension being absent — diagnosable through the same stale-volume troubleshooting path
  FR-024 documents, not a separate requirement. The dependency on
  `pgvector/pgvector:pg18` running init scripts only against an empty data directory is external and
  worth re-verifying if that image's major version changes (see `data-model.md`).
- **Configuration is read once, at startup.** Changing an environment variable while the backend is
  running takes effect only after a restart; this feature has no live-reload requirement.
- **Availability of a fresh-eyes developer is assumed, not guaranteed, for validating US4, SC-001
  and SC-007.** Those criteria describe a trial this feature's author cannot self-administer.
- **The primary development platform is Windows, with PowerShell as its shell.** That is the
  machine this feature is built and validated on, so FR-027 is satisfied against it first. The
  three parts themselves are OS-neutral; only the documented commands are shell-specific, and
  FR-027 governs how that is handled rather than restricting the project to one platform.
- **The health check reports database reachability** rather than the backend refusing to start
  without a database. This keeps the frontend and backend independently runnable, which SC-005
  requires.
- **No authentication anywhere**, consistent with the PoC scope boundaries — the scaffold exposes
  unauthenticated local endpoints only. This applies equally to the health check's configuration-
  status fields: `show-details: always` deliberately returns connection-error text and configuration
  booleans (never secret values) to any unauthenticated caller. That is an accepted local-only
  posture, not an oversight; tightening it before any deployment beyond a developer machine is a
  requirement of a later feature, not this one.
- **The placeholder frontend page makes no backend calls.** Wiring the two together is left to the
  feature that introduces the first real endpoint, so the frontend has no dependency to break.
- **AI credentials are not required to run the scaffold.** Every part starts, serves and tests
  green without them (SC-009). The only thing that contacts the AI provider is the on-demand
  verification, which a developer chooses to run.
- **The AI provider is Azure OpenAI, not the OpenAI API directly.** The developer's environment
  already defines `AZURE_OPEN_AI_KEY`, `AZURE_OPEN_AI_ENDPOINT` and
  `AZURE_OPEN_AI_DEPLOYMENT_NAME`; this feature reuses those names rather than introducing
  parallel ones. `OPENAI_API_KEY` is not set in that environment, so the direct-OpenAI path is not
  currently usable. The fourth variable, `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME`, is newly
  introduced by this feature's Clarifications rather than pre-existing like the other three, and
  unlike them it MAY be unset (FR-023).
- **The constitution mandates Azure OpenAI.** Ratified in v1.3.0 on 2026-08-13, which also fixes
  the four environment variable names and requires chat and embeddings to use separate
  deployments. This assumption was a blocking dependency when written; the amendment has landed
  and it no longer blocks implementation.
- **Azure OpenAI binds one model per deployment**, so chat and embeddings require two separate
  deployments and two separate names. The embedding deployment may not exist yet; creating it is a
  prerequisite of the ingestion feature, not of this one.
- **Test suites are part of the scaffold, not an addition to it.** The constitution mandates
  test-driven development, which is impossible without a working harness; each part therefore
  ships with a runnable suite and at least one real passing test.

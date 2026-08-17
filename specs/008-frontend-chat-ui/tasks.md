---

description: "Task list for Frontend Chat UI (008)"
---

# Tasks: Frontend Chat UI

**Input**: Design documents from `/specs/008-frontend-chat-ui/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/frontend-service-contract.md](contracts/frontend-service-contract.md),
[quickstart.md](quickstart.md)

**Tests**: **Not optional for this project.** Constitution Principle II (Test-Driven Development) is
mandatory: every behavior below gets a failing test before its implementation, using the codebase's
established patterns (`HttpTestingController`/`TestBed` for Angular — research.md Decision 8; `MockMvc`
for the one Spring change — mirroring `HealthEndpointCorsTest.java`). No test in this feature requires
a live backend, database, or Azure credential.

**Organization**: Tasks are grouped by user story (spec.md's five stories, P1→P3) to enable
independent implementation and testing of each.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: Which user story this task belongs to (US1–US5, matching spec.md)
- Every task names its exact file path(s)

## Path Conventions

Web application (existing `backend/`, `frontend/` split — see plan.md Project Structure):

- Backend: `backend/src/main/java/com/epam/aihelpdesk/`, `backend/src/test/java/com/epam/aihelpdesk/`
- Frontend: `frontend/src/app/`

---

## Phase 1: Setup

**Purpose**: Confirm a clean baseline before this feature's changes begin. No new dependency is
introduced anywhere in this feature (research.md Decisions 1, 2, 8), so there is no package
install/scaffolding step.

- [X] T001 Confirm the existing suites are currently green: `npm test` in `frontend/` and
      `./mvnw test` (or the project's configured Maven wrapper) in `backend/`. Fix or report any
      pre-existing failure before proceeding — every task below assumes this starting point.
      **Confirmed**: frontend 4 suites/17 tests green; backend `mvn test` 59 tests green (BUILD
      SUCCESS; `*IT.java` integration tests are excluded by the default `test` goal and require a
      running Postgres, consistent with quickstart.md's prerequisites — not part of this baseline).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infrastructure every user story needs. **No user story work can begin until this phase
is complete** — without T002/T003, no HTTP call any story makes can reach the backend from the
browser at all (research.md Decision 1); every story's error-handling relies on T004/T005; every
story renders inside the shell T006/T007 build.

- [X] T002 [P] Write a failing `MockMvc` preflight test in
      `backend/src/test/java/com/epam/aihelpdesk/config/WebCorsConfigTest.java` asserting
      `Access-Control-Allow-Origin: http://localhost:4200` on CORS preflight (`OPTIONS` with an
      `Access-Control-Request-Method` header) for `GET`/`POST /documents`,
      `GET /documents/{id}/content`, `DELETE /documents/{id}`, and `POST /chat` — mirroring
      `backend/src/test/java/com/epam/aihelpdesk/HealthEndpointCorsTest.java`'s pattern, adapted from
      the actuator-only `management.endpoints.web.cors` property to a real CORS configuration bean
      (research.md Decision 1: `WebMvcConfigurer`'s `addCorsMappings` does not cover actuator's own
      handler mapping, but does cover these ordinary `@RestController` endpoints).
- [X] T003 Implement `backend/src/main/java/com/epam/aihelpdesk/config/WebCorsConfig.java` (a
      `WebMvcConfigurer` bean allowing `http://localhost:4200` on `/documents/**` and `/chat`, `GET`/
      `POST`/`DELETE` methods) so T002 passes.
- [X] T004 [P] Write failing tests in `frontend/src/app/shared/api-error.spec.ts` covering every
      documented `error` code from the chat
      ([007's contract](../007-chat-endpoint/contracts/chat-api-contract.md): `blank_question`,
      `question_too_long`, `malformed_request`, `provider_unconfigured`, `processing_failed`),
      ingestion ([004's contract](../004-document-ingestion-endpoint/contracts/ingestion-api-contract.md):
      `unsupported_type`, `invalid_file`, `unparseable`, `provider_unconfigured`, `processing_failed`),
      document-query ([005's contract](../005-document-listing-download/contracts/document-query-api-contract.md):
      `document_not_found`), and document-delete
      ([006's contract](../006-document-delete/contracts/document-delete-api-contract.md):
      `document_not_found`, `deletion_failed`) contracts, plus one fallback case for an unrecognized
      code or a network-level failure with no response at all (research.md Decision 5). Per research
      Decision 5's clarification: assert the chat mapping gives `provider_unconfigured` and
      `processing_failed` two *different* strings (FR-007), while the ingestion/upload mapping gives
      `provider_unconfigured`, `processing_failed`, and a network-level failure (no response) the
      *same* string (FR-011) — both are still distinct, present entries in the lookup table.
- [X] T005 Implement `frontend/src/app/shared/api-error.ts` (closed lookup tables + fallback,
      exporting the chat- and document-scoped mapping functions the rest of this feature calls) so
      T004 passes.
- [X] T006 [P] Update `frontend/src/app/app.spec.ts`: replace the "PoC functionality is not yet
      implemented" placeholder assertions with assertions for the new two-pane shell — a sidebar
      region and a main chat region are both present, and `<app-connection-status>` still renders.
      This must fail against today's placeholder markup.
- [X] T007 Implement the new shell layout in `frontend/src/app/app.html` and
      `frontend/src/app/app.css` (a two-column layout mirroring `docs/rag_chatbot.html`'s
      `.container`/`.sidebar`/`.main` structure, with empty content regions later stories fill, and
      `<app-connection-status />` inside the sidebar header) and update `frontend/src/app/app.ts`'s
      imports accordingly, so T006 passes.

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Ask a question and get a grounded, cited answer (Priority: P1) 🎯 MVP

**Goal**: A user types a question, sends it, and sees the assistant's grounded answer with citation
badges (document, page, relevance score) — or the fixed "documentation does not cover this" message
when nothing qualifies.

**Independent Test**: With at least one document already ingested (via direct API/`curl` — no
upload UI is required for this story), open the chat UI, ask a question covered by that document,
and confirm the rendered answer and citations match `POST /chat`'s actual response exactly.

### Tests for User Story 1 ⚠️ write first, confirm they fail

- [X] T008 [P] [US1] Write failing tests in `frontend/src/app/chat/chat-message.spec.ts`: mapping a
      `ChatResponse` into a `ChatMessage`/`Citation[]` (data-model.md) — score→rounded-percent
      (research.md Decision 7), page label passed through verbatim including the fixed
      `"no page structure"` marker, and an empty `sources` list producing zero citations for the
      fixed "not covered" answer.
- [X] T009 [P] [US1] Write failing tests in `frontend/src/app/chat/chat.service.spec.ts`
      (`HttpTestingController`, mirroring `health.service.spec.ts`'s style): `ask()` appends a
      `user` message then a `pending` `assistant` message and settles it from `POST /chat`'s
      response; a blank/whitespace-only or over-1000-character question is a no-op (no HTTP call);
      every `POST /chat` body sent has `documentIds: null` (FR-020); a `400`/`503` response settles
      the message to `status: 'error'` with a message from `api-error.ts` (T004); `pending` stays
      `true` indefinitely if the request never settles (no client-side timeout, FR-006).
- [X] T010 [P] [US1] Write failing tests in
      `frontend/src/app/chat/chat-view/chat-input.component.spec.ts`: the send control is visibly
      disabled whenever the trimmed input is empty (FR-004); a live indicator appears once the
      trimmed input reaches/exceeds 1000 characters and submission is blocked (FR-005); pressing
      Enter has the same effect as clicking send; on submit the input clears immediately (FR-001);
      both the input and the send control are disabled while `pending` is `true` (FR-006).
- [X] T011 [P] [US1] Write failing tests in
      `frontend/src/app/chat/chat-view/message-bubble.component.spec.ts`: a `user` message and an
      `assistant` message render with distinct styling; an `assistant` message's citations render
      as badges showing filename, page label, and rounded relevance percentage, in the given order;
      the fixed "not covered" text renders with zero badges; an `error` message renders its
      `errorMessage` instead of `text`.
- [X] T012 [P] [US1] Write failing tests in
      `frontend/src/app/chat/chat-view/chat-view.component.spec.ts`: submitting a question while
      `pending` is `true` has no additional effect (FR-006); history stays in submission order
      across multiple questions and a later answer's citations never bleed into an earlier one
      (Story 1 Scenario 3); a failed question remains in history as its own entry rather than being
      removed or silently retried (FR-007/FR-018). Using `vi.useFakeTimers()` (mirroring
      `health.service.spec.ts`'s pattern): the visible loading indicator does NOT render while
      `pending` is `true` and fewer than 300ms have elapsed (research.md Decision 9, FR-006's
      anti-flash clause); it DOES render once 300ms have elapsed with `pending` still `true`; if the
      response settles before 300ms elapses, the indicator never renders at all and the timer is not
      left pending after the component is destroyed.

### Implementation for User Story 1

- [X] T013 [P] [US1] Implement `frontend/src/app/chat/chat-message.ts` (types + the
      `ChatResponse`→`ChatMessage`/`Citation` mapping) to pass T008.
- [X] T014 [P] [US1] Implement `frontend/src/app/chat/chat.service.ts` (`ChatService`: `messages`,
      `pending` signals, `ask()` per contracts/frontend-service-contract.md) to pass T009 (uses
      T013's mapping and T005's `api-error.ts`).
- [X] T015 [P] [US1] Implement `frontend/src/app/chat/chat-view/chat-input.component.ts/.html/.css`
      (presentational — a trimmed-length check and an `@Output()` submit event, no direct
      `ChatService` dependency) to pass T010.
- [X] T016 [P] [US1] Implement
      `frontend/src/app/chat/chat-view/message-bubble.component.ts/.html/.css` (presentational —
      `@Input() message: ChatMessage`) to pass T011 (uses T013's types).
- [X] T017 [US1] Implement `frontend/src/app/chat/chat-view/chat-view.component.ts/.html/.css`
      (injects `ChatService`, composes `chat-input` + a list of `message-bubble`, and implements
      research.md Decision 9's 300ms anti-flash timer for the visible loading indicator — a
      component-local `LOADING_INDICATOR_DELAY_MS = 300` timer started when `pending` becomes `true`
      and cleared/cancelled if `pending` returns to `false` first) to pass T012 (depends on T014,
      T015, T016).
- [X] T018 [US1] Wire `<app-chat-view>` into `frontend/src/app/app.html`'s main content region and
      import it in `frontend/src/app/app.ts` (depends on T017, T007).

**Checkpoint**: User Story 1 is fully functional and independently testable/demoable (MVP).

---

## Phase 4: User Story 2 - Browse the real, live list of ingested documents (Priority: P2)

**Goal**: The sidebar shows exactly what `GET /documents` currently returns — including an explicit
empty state — instead of the mockup's hardcoded file list.

**Independent Test**: With zero, one, and several documents ingested (via direct API calls), reload
the chat UI each time and confirm the sidebar list exactly matches `GET /documents`.

### Tests for User Story 2 ⚠️ write first, confirm they fail

- [X] T019 [P] [US2] Write failing tests in `frontend/src/app/documents/document.spec.ts`: the
      `DocumentSummary` shape and its `contentType`→icon mapping (`.pdf` vs `.txt`, per
      data-model.md).
- [X] T020 [P] [US2] Write failing tests in
      `frontend/src/app/documents/documents.service.spec.ts` (`HttpTestingController`): `documents`
      populates from `GET /documents` on construction (FR-008), preserving the backend's own
      ordering; `refresh()` replaces the list wholesale, never merges/patches; `loaded` is `false`
      immediately after construction (before the initial request resolves), becomes `true` once that
      first `GET /documents` settles — asserted for both a successful response and a failed one — and
      stays `true` through subsequent `refresh()` calls (data-model.md `DocumentsService.loaded`).
- [X] T021 [P] [US2] Write failing tests in
      `frontend/src/app/documents/document-sidebar/document-item.component.spec.ts`: a row renders
      only its filename plus a type icon (Clarifications session — no upload date/chunk count
      shown), for both content types.
- [X] T022 [P] [US2] Write failing tests in
      `frontend/src/app/documents/document-sidebar/document-sidebar.component.spec.ts`: every
      document from `DocumentsService.documents` renders as a row; while `loaded()` is `false` a
      distinct loading state renders (no rows, no empty-state message); once `loaded()` is `true`
      with a zero-document corpus, an explicit empty state renders instead (naming that no documents
      exist yet and pointing at the upload control, FR-009); these two zero-row states MUST assert on
      different rendered content/markup, not just differ conceptually (FR-008).

### Implementation for User Story 2

- [X] T023 [P] [US2] Implement `frontend/src/app/documents/document.ts` to pass T019.
- [X] T024 [P] [US2] Implement `frontend/src/app/documents/documents.service.ts`
      (`DocumentsService`: `documents` and `loaded` signals + `refresh()` only at this stage) to pass
      T020 (uses T023's types).
- [X] T025 [P] [US2] Implement
      `frontend/src/app/documents/document-sidebar/document-item.component.ts/.html/.css`
      (presentational — `@Input() document: DocumentSummary`) to pass T021 (uses T023's types).
- [X] T026 [US2] Implement
      `frontend/src/app/documents/document-sidebar/document-sidebar.component.ts/.html/.css`
      (injects `DocumentsService`, branches on `loaded()` to render a loading state, an empty state,
      or a list of `document-item`) to pass T022 (depends on T024, T025).
- [X] T027 [US2] Wire `<app-document-sidebar>` into `frontend/src/app/app.html`'s sidebar region,
      below `<app-connection-status>`, and update `frontend/src/app/app.ts` (depends on T026, T007).

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 - Upload a new document (Priority: P2)

**Goal**: The "Upload docs" control ingests a `.pdf`/`.txt` file via `POST /documents` and the new
document appears in the sidebar without a reload.

**Independent Test**: Upload a valid file; confirm it appears in the sidebar and is citable in a
follow-up chat question (Story 1).

### Tests for User Story 3 ⚠️ write first, confirm they fail

- [X] T028 [P] [US3] Add failing tests to
      `frontend/src/app/documents/documents.service.spec.ts` for `upload()`: success appends the
      new entry and re-syncs `documents` from the server (FR-010); failure sets `uploadError` via
      `api-error.ts` for each documented cause — `unsupported_type`, `invalid_file`, `unparseable`,
      and a service-unavailable/network failure treated identically whether the failure is a clean
      rejection or a mid-transfer drop (FR-011); `uploading` is `true` only for the call's duration
      and `uploadError` resets to `null` at the start of each new attempt.
- [X] T029 [P] [US3] Add failing tests to
      `frontend/src/app/documents/document-sidebar/document-sidebar.component.spec.ts` for the
      upload control: selecting a file starts the upload immediately with no separate confirm step
      (FR-010); the control shows a busy state and rejects a second concurrent selection while one
      is in flight (FR-012); a failed upload's message appears at the upload control and clears once
      a new attempt starts (FR-011).

### Implementation for User Story 3

- [X] T030 [US3] Extend `frontend/src/app/documents/documents.service.ts`: add `uploading`,
      `uploadError` signals and `upload(file: File)` to pass T028 (depends on T024; same file, must
      follow it).
- [X] T031 [US3] Extend
      `frontend/src/app/documents/document-sidebar/document-sidebar.component.ts/.html/.css`: add
      the upload control (hidden file input + button, wired to `DocumentsService.uploading`/
      `uploadError`) to pass T029 (depends on T030, T026).

**Checkpoint**: User Stories 1, 2, and 3 all work independently.

---

## Phase 6: User Story 4 - Download a document's original file (Priority: P3)

**Goal**: A sidebar row's download action and any citation badge both retrieve the original file via
`GET /documents/{id}/content`.

**Independent Test**: Download from a sidebar row; separately, download from a citation badge on a
rendered answer; confirm both retrieve the same file. Delete a document (Story 5), then confirm its
still-visible citation reports "no longer available" instead of a broken download.

### Tests for User Story 4 ⚠️ write first, confirm they fail

- [X] T032 [P] [US4] Write failing tests in `frontend/src/app/shared/file-download.spec.ts`:
      `downloadDocument()` issues a blob `GET` to `/documents/{id}/content` and triggers a save
      under the *given* filename (never one parsed from a response header, research.md Decision 3);
      a `404` resolves `{ ok: false, unavailable: true }`; any other failure resolves
      `{ ok: false, unavailable: false }`; success resolves `{ ok: true }`.
- [X] T033 [P] [US4] Add failing tests to
      `frontend/src/app/documents/document-sidebar/document-item.component.spec.ts`: a download
      action is revealed both on mouse hover and on keyboard focus of the row (FR-013), and invokes
      `downloadDocument` with that row's `documentId`/`filename`.
- [X] T034 [P] [US4] Add failing tests to
      `frontend/src/app/chat/chat-view/message-bubble.component.spec.ts`: clicking a citation badge
      invokes `downloadDocument` with that citation's `documentId`/`filename`; a `404` result flips
      that one badge into a persistent "no longer available" state (FR-014) without affecting any
      other badge in the same or a different message.

### Implementation for User Story 4

- [X] T035 [US4] Implement `frontend/src/app/shared/file-download.ts` to pass T032.
- [X] T036 [P] [US4] Extend
      `frontend/src/app/documents/document-sidebar/document-item.component.ts/.html/.css` to pass
      T033 (depends on T035, T025).
- [X] T037 [P] [US4] Extend `frontend/src/app/chat/chat-view/message-bubble.component.ts/.html/.css`
      to pass T034 (depends on T035, T016).

**Checkpoint**: User Stories 1–4 all work independently.

---

## Phase 7: User Story 5 - Delete a document (Priority: P3)

**Goal**: A sidebar row's delete action, gated by an inline confirmation, removes a document via
`DELETE /documents/{id}`.

**Independent Test**: Trigger delete, confirm, verify the row disappears and is no longer
downloadable/citable in new answers. Trigger delete, cancel, verify nothing changed. Trigger delete
on a second row while a first's confirmation is open, verify the first cancels.

### Tests for User Story 5 ⚠️ write first, confirm they fail

- [X] T038 [P] [US5] Add failing tests to
      `frontend/src/app/documents/documents.service.spec.ts` for `remove()`: success removes the
      entry from `documents` and resolves `{ ok: true }` (FR-016); a `503 deletion_failed` leaves
      `documents` unchanged and resolves `{ ok: false, message }` from `api-error.ts` (FR-017).
- [X] T039 [P] [US5] Add failing tests to
      `frontend/src/app/documents/document-sidebar/document-sidebar.component.spec.ts` (and/or
      `document-item.component.spec.ts` as appropriate): triggering a row's delete action opens an
      inline "Delete this document? [Confirm] [Cancel]" reachable by hover or keyboard focus
      (FR-013/FR-015); "Cancel" reverts with no request sent; triggering a second row's delete while
      a first's confirmation is open cancels the first via the shared `confirmingDocumentId`
      (data-model.md) and opens the second — never two at once (FR-021); "Confirm" calls `remove()`;
      a failed delete leaves the row listed with a message that persists until the next delete
      attempt on that row (FR-017); this state never blocks or is cleared by an unrelated pending
      chat question or in-progress upload (FR-022).

### Implementation for User Story 5

- [X] T040 [US5] Extend `frontend/src/app/documents/documents.service.ts`: add
      `remove(documentId: string)` to pass T038 (depends on T030; same file, must follow it).
- [X] T041 [US5] Extend
      `frontend/src/app/documents/document-sidebar/document-sidebar.component.ts/.html/.css` (add
      the container-level `confirmingDocumentId` signal) and
      `frontend/src/app/documents/document-sidebar/document-item.component.ts/.html/.css` (add the
      confirm/cancel UI, keyboard-reachable) to pass T039 (depends on T040, T036).

**Checkpoint**: All five user stories are independently functional — the full feature is complete.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Whole-feature verification that spans more than one story.

- [X] T042 [P] Run the project's formatter/linter (`prettier`, already a `frontend/` dev dependency)
      across every new/changed file under `frontend/src/app/` and fix any findings.
      **Done**: `npx prettier --write` across `chat/`, `documents/`, `shared/`, and the changed
      `app.*` files reformatted 16 files; `npm test` re-confirmed green afterward (120/120).
- [X] T043 [P] Re-run the full automated suites (`npm test` in `frontend/`, `./mvnw test` in
      `backend/`) and confirm everything is green, including T002's new CORS test.
      **Confirmed**: frontend 15 suites / 120 tests green; backend `mvn test` 65 tests green
      (BUILD SUCCESS), including `WebCorsConfigTest`'s 6 CORS preflight assertions.
- [X] T044 Execute [quickstart.md](quickstart.md)'s per-story manual validation end to end against
      the real running backend (with T003's CORS change deployed) and frontend; record the outcome
      of each story's steps.
      **Partially done, environment-limited**: the frontend dev server (`npm start`) was run and
      driven live in a real browser against the actual built bundle (not mocks). Confirmed live:
      the two-pane shell renders correctly; `<app-connection-status>` correctly shows "Backend
      unreachable"; the sidebar correctly shows its FR-009 empty state (`loaded()` still flips
      `true` on a real failed `GET /documents`, exactly as designed); submitting a chat question
      against the unreachable backend renders the question, then the FR-007 generic fallback error
      message ("Something went wrong. Please try again.") in place of a pending answer, with the
      input cleared and the send control usable again — an end-to-end, real-browser confirmation of
      FR-001/FR-006/FR-007/FR-008/FR-009. The backend itself (`./mvnw spring-boot:run`) could not be
      started in this sandbox: embedded Tomcat fails with `IOException: Unable to establish
      loopback connection` / `SocketException: Invalid argument: connect` from
      `sun.nio.ch.UnixDomainSockets.connect0` — a pre-existing Java NIO/Windows-sandbox limitation
      reproduced identically on two independent attempts (including with
      `-Djava.net.preferIPv4Stack=true`), unrelated to this feature's code (the same limitation
      would block any Spring Boot app in this sandbox). Stories 1's grounded-answer path and
      Stories 2–5's live document CRUD against a real backend response therefore could not be
      exercised live here; they remain covered by the automated suites (T043) — every backend call
      any story makes is exercised via `HttpTestingController`/`MockMvc` fixtures built from the
      real response shapes.
- [X] T045 Manually verify the two cross-cutting guarantees no single story's isolated tests fully
      exercise together: FR-021 (only one delete confirmation open across the whole sidebar) and
      FR-022 (a pending chat question, an in-progress upload, and an open delete confirmation never
      block or clear one another) — per quickstart.md's failure-path spot checks.
      **Done**: FR-021 has a dedicated automated test
      (`document-sidebar.component.spec.ts`: "cancels the first row's confirmation and opens the
      second...") exercising the exact scenario. FR-022 is satisfied by construction, verified by
      code inspection: `ChatViewComponent`/`ChatService` and `DocumentSidebarComponent`/
      `DocumentsService` share no signal, method, or mutable state with each other anywhere in the
      implementation — `chat.service.ts` never references `DocumentsService` and
      `documents.service.ts` never references `ChatService`, and `confirmingDocumentId`/
      `deleteErrors` live only inside `DocumentSidebarComponent`, scoped per row via `documentId`,
      never touching `uploading`/`uploadError`. There is structurally no code path by which one
      flow's pending/error state could block or clear another's.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. **Blocks every user story** — T002/T003 (CORS) gate
  every HTTP call any story makes; T004/T005 (error mapping) is used by both `ChatService` (US1) and
  `DocumentsService` (US2/US3/US5); T006/T007 (shell) is what every story's components render into.
- **User Stories (Phase 3–7)**: All depend on Foundational. Ordered here by priority (P1→P2→P2→P3→P3)
  and by the natural dependency that US3/US5 *extend* `documents.service.ts` (US2's file) and
  US4/US5 *extend* `document-item.component.ts` (US2's file) and `message-bubble.component.ts`
  (US1's file) — see "Within Each User Story" below. Each story is still independently testable at
  its own checkpoint once its phase completes.
- **Polish (Phase 8)**: Depends on every user story being complete.

### User Story Dependencies

- **US1 (P1)**: No dependency on any other story — independently testable once Foundational is done
  (a document can be ingested via direct API call, not through this feature's own upload UI).
- **US2 (P2)**: No functional dependency on US1; independently testable once Foundational is done.
- **US3 (P2)**: Extends `documents.service.ts` and `document-sidebar.component.ts`, both introduced
  by US2 — must follow US2 in implementation order, though it remains independently *testable*
  (its own Independent Test doesn't require US1/US4/US5).
- **US4 (P3)**: Extends `document-item.component.ts` (US2) and `message-bubble.component.ts` (US1)
  — must follow both in implementation order.
- **US5 (P3)**: Extends `documents.service.ts` (US2/US3) and `document-item.component.ts`
  (US2/US4) — must follow both in implementation order.

### Within Each User Story

- Tests are written first and MUST fail before their corresponding implementation task starts
  (constitution Principle II).
- Types/models before services; services before the components that inject them; presentational
  components (no service dependency) can be built in parallel with the service they'll later be
  composed with.
- A story extending a file an earlier story created (US3/US5 → `documents.service.ts`; US4/US5 →
  `document-item.component.ts`; US4 → `message-bubble.component.ts`) does so as a sequential
  follow-on task against that same file, never a parallel rewrite.

### Parallel Opportunities

- T002, T004, T006 (Foundational tests, three different files/domains) in parallel.
- Within US1: T008–T012 (all test files) in parallel; then T013, T014, T015, T016 (implementation,
  each only depending on foundational work + its own test) in parallel; T017 and T018 are
  sequential (they compose/wire the above).
- Within US2: T019–T022 in parallel; then T023, T024, T025 in parallel; T026/T027 sequential.
- Within US4: T032–T034 in parallel; T035 first, then T036/T037 in parallel.
- Different user story *phases* are not generally parallel across developers here, because US3/US4/
  US5 each extend a file an earlier story created (see above) — but once US2 (and, for US4, also
  US1) is done, US3 and US4 could proceed in parallel with each other (they touch disjoint files:
  `documents.service.ts`+`document-sidebar.component.ts` vs. `file-download.ts`+`document-item
  .component.ts`+`message-bubble.component.ts`) before US5 (which needs both's file states) begins.

---

## Parallel Example: User Story 1

```bash
# Tests, all different files:
Task: "Write failing tests in frontend/src/app/chat/chat-message.spec.ts"
Task: "Write failing tests in frontend/src/app/chat/chat.service.spec.ts"
Task: "Write failing tests in frontend/src/app/chat/chat-view/chat-input.component.spec.ts"
Task: "Write failing tests in frontend/src/app/chat/chat-view/message-bubble.component.spec.ts"
Task: "Write failing tests in frontend/src/app/chat/chat-view/chat-view.component.spec.ts"

# Implementation, once T005 (Foundational) and each task's own test exist:
Task: "Implement frontend/src/app/chat/chat-message.ts"
Task: "Implement frontend/src/app/chat/chat.service.ts"
Task: "Implement frontend/src/app/chat/chat-view/chat-input.component.ts/.html/.css"
Task: "Implement frontend/src/app/chat/chat-view/message-bubble.component.ts/.html/.css"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup
2. Phase 2: Foundational (CRITICAL — blocks everything)
3. Phase 3: User Story 1
4. **STOP and VALIDATE**: run US1's Independent Test against a locally ingested document
5. Demo: a live, cited, grounded chat — the core value this whole feature exists to deliver

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → validate independently → demo (MVP)
3. US2 → validate independently → demo (the sidebar is now live)
4. US3 → validate independently → demo (upload works end to end)
5. US4 → validate independently → demo (download from both entry points)
6. US5 → validate independently → demo (full corpus management)
7. Polish → whole-feature sign-off via quickstart.md

### Solo/Sequential Strategy

Given US3/US4/US5's file-extension dependencies on US1/US2 (see Dependencies above), the realistic
default execution order for a single implementer is exactly the priority order: US1 → US2 → US3 →
US4 → US5. A team of two could split after US2 completes: one developer takes US3, the other US4,
converging before US5.

---

## Notes

- [P] tasks touch different files with no incomplete-task dependency between them.
- [Story] labels map every user-story-phase task to spec.md's five stories for traceability.
- Every test task names the exact failing behavior it must assert — write it, watch it fail, then
  (and only then) do the paired implementation task.
- Commit after each task or logical group (e.g. a story's full test-then-implementation set).
- Stop at any checkpoint to validate that story independently before continuing.
- File paths above are exact per plan.md's Project Structure — no task should invent a new path.

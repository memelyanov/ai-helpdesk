# Feature Specification: Frontend Chat UI

**Feature Branch**: `main` (no feature branch created — no `before_specify` hook is registered,
consistent with [002-frontend-health-wire](../002-frontend-health-wire/spec.md) and every feature
since)

**Created**: 2026-08-16

**Status**: Draft

## Clarifications

### Session 2026-08-16

- Q: Citation badges currently show document + page (per the backend contract). Should they also
  display the retrieved passage's relevance score to the user? → A: Yes — show the score too, so the
  user can see how strong the match was.
- Q: While a question is awaiting its answer (no fixed backend time bound), how should the UI handle
  the wait? → A: Show an indefinite loading indicator and wait for the backend to respond or error —
  no client-side timeout, no cancel affordance.
- Q: The sidebar now lists real documents (id, filename, contentType, uploadedAt, chunkCount all
  available from `GET /documents`). Should each row show more than just the filename? → A: Filename
  only, matching the mockup exactly — upload date and chunk count are not displayed.

**Input**: User description: "let's work on frontend implementation. There is example of UX in
docs/rag_chatbot.html file. Let's start with this design implementation. All backend api is
implemented now, so we can implement real services and data models to make UI live"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ask a question and get a grounded, cited answer (Priority: P1)

A user opens the chat UI, types a question about the uploaded documentation, and sends it. The
assistant's answer appears in the conversation, grounded in the actual document corpus, together
with the document(s) and page(s) it was drawn from — or, when nothing in the corpus is relevant, a
clear "documentation does not cover this" message with no sources attached.

**Why this priority**: This is the entire reason the application exists (`poc-concept.md` §5's "RAG
search, grounded answers" in-scope statement). Every other story in this feature exists to support
or feed this one; without it there is no product.

**Independent Test**: With at least one document already ingested, open the chat UI, ask a question
whose answer is covered by that document, and confirm the rendered answer text and its source
citation(s) match what `POST /chat` actually returned — nothing invented, nothing dropped.

**Acceptance Scenarios**:

1. **Given** the corpus contains a document that answers the question, **When** the user submits the
   question, **Then** the answer appears in the conversation along with a citation badge for every
   distinct document-and-page that contributed to it, most relevant first.
2. **Given** the corpus has no document relevant to the question (including an empty corpus),
   **When** the user submits the question, **Then** the fixed "documentation does not cover this"
   message appears with no source citations.
3. **Given** an answer with citations is already displayed, **When** the user asks a second, unrelated
   question, **Then** both question/answer exchanges remain visible in order, and the second answer's
   citations do not bleed into the first.
4. **Given** the user has typed a question, **When** they press Enter or click the send control,
   **Then** the question is submitted (both trigger the same action).

---

### User Story 2 - Browse the real, live list of ingested documents (Priority: P2)

A user looks at the sidebar and sees the documents that are actually ingested right now — not a
fixed mock list — so they know what the assistant can currently answer questions about.

**Why this priority**: The mockup's sidebar is hardcoded; making it reflect real backend state is
the first piece of "real services and data models" the user asked for, and it is a prerequisite for
the upload and delete stories below to be visibly meaningful.

**Independent Test**: With zero, one, and several documents ingested (via direct API calls or prior
uploads), reload the chat UI each time and confirm the sidebar list exactly matches what
`GET /documents` currently returns, including the empty-corpus case.

**Acceptance Scenarios**:

1. **Given** the corpus has one or more ingested documents, **When** the chat UI loads, **Then** the
   sidebar lists every one of them by filename, most-recently-uploaded first.
2. **Given** the corpus has no ingested documents, **When** the chat UI loads, **Then** the sidebar
   shows a clear empty state instead of an empty list or the mockup's placeholder filenames.
3. **Given** two documents share the same filename (independent re-uploads), **When** both appear in
   the sidebar, **Then** both are shown as distinct entries and each can still be individually
   downloaded or deleted (Stories 3 and 4).

---

### User Story 3 - Upload a new document (Priority: P2)

A user clicks the "Upload docs" control, picks a `.pdf` or `.txt` file from their machine, and the
document is ingested into the corpus and immediately becomes visible in the sidebar and answerable
in chat.

**Why this priority**: Without a working upload path, the corpus can only grow through means outside
the UI, which defeats the point of a self-service chat application. Depends on Story 2's live list to
show the result.

**Independent Test**: Click upload, select a valid `.pdf` or `.txt` file, and confirm it appears in
the sidebar afterward and that a question about its content now returns a grounded answer citing it.

**Acceptance Scenarios**:

1. **Given** the user selects a valid `.pdf` or `.txt` file, **When** the upload completes
   successfully, **Then** the new document appears in the sidebar without a manual page reload.
2. **Given** the user selects a file that is not a supported type, is empty, or exceeds the size
   limit, **When** the upload is attempted, **Then** a specific, human-readable error is shown and no
   entry is added to the sidebar.
3. **Given** an upload is in progress, **When** the user looks at the upload control, **Then** it
   shows a busy/loading state and does not accept a second overlapping upload.
4. **Given** an upload fails because the backend could not process it (service error), **When** the
   error is shown, **Then** the user can immediately try uploading again without reloading the page.

---

### User Story 4 - Download a document's original file (Priority: P3)

A user wants to see the original file behind a sidebar entry or behind an answer's citation, and can
retrieve it with a single click from either place.

**Why this priority**: Useful verification/trust feature once the core chat and document list exist,
but the product delivers its primary value (Story 1) without it.

**Independent Test**: Hover a sidebar document entry, click its download action, and confirm the
original file is retrieved unchanged. Separately, click a citation badge on a rendered answer and
confirm it retrieves the same cited document.

**Acceptance Scenarios**:

1. **Given** a document is listed in the sidebar, **When** the user hovers over its row, **Then** a
   download action appears; clicking it retrieves that exact document's original file.
2. **Given** an answer is displayed with one or more source citation badges, **When** the user clicks
   a badge, **Then** the corresponding document's original file is retrieved.
3. **Given** a citation badge refers to a document that has since been deleted (Story 5), **When** the
   user clicks it, **Then** a clear message explains the source is no longer available, instead of a
   silent failure or a broken download.
4. **Given** a document is listed in the sidebar, **When** the user reaches its row by keyboard (e.g.
   Tab) rather than a mouse, **Then** the same download action is available without needing to hover
   (FR-013).

---

### User Story 5 - Delete a document (Priority: P3)

A user removes a document they no longer want in the corpus directly from the sidebar, with a
confirmation step so the irreversible action is not triggered by accident.

**Why this priority**: Completes basic corpus management (create/read/delete) but is not required for
the chat experience itself to work; ordered after download since deletion interacts with it (Story 4,
Scenario 3).

**Independent Test**: With a document in the sidebar, trigger its delete action, confirm the prompt,
and verify the document disappears from the sidebar and can no longer be downloaded or cited in new
answers.

**Acceptance Scenarios**:

1. **Given** a document is listed in the sidebar, **When** the user triggers its delete action,
   **Then** a confirmation prompt appears before anything is deleted.
2. **Given** the user confirms deletion, **When** the deletion succeeds, **Then** the document is
   immediately removed from the sidebar without a manual page reload.
3. **Given** the user cancels the confirmation prompt, **When** they dismiss it, **Then** the document
   remains untouched in the sidebar.
4. **Given** the deletion request fails (backend service error), **When** the failure is reported,
   **Then** the document remains listed in the sidebar exactly as before, and the user is told the
   deletion did not happen.
5. **Given** a document is listed in the sidebar, **When** the user reaches its row by keyboard rather
   than a mouse, **Then** the same delete action (and its confirmation step) is available without
   needing to hover (FR-013).

---

### Edge Cases

- What happens when the user submits a blank or whitespace-only question? The UI must not send it —
  the send action stays disabled/no-op until there is real input.
- What happens when the user types a question longer than 1000 characters? The UI must give feedback
  before/at the limit rather than letting the request round-trip only to be rejected as
  `question_too_long`.
- What happens when the backend is unreachable, or returns `provider_unconfigured` /
  `processing_failed` (503)? The UI must show a clear, non-technical failure message in place of an
  answer and let the user retry, without ever surfacing raw error codes or leaving the conversation in
  a stuck "waiting" state.
- What happens when a source citation has no page structure (a plain-text upload)? The UI must show
  the fixed "no page structure" indicator exactly as the backend sends it — never a page number, never
  blank.
- What happens if the user sends a second question while the first is still awaiting an answer? The
  UI must prevent overlapping submissions (Story 1, FR-006) rather than racing two requests.
- What happens if a document is deleted while its citation is still visible in earlier chat history?
  The chat history entry and its citation badge remain visible (history is not rewritten), but
  attempting to download that source reports it is no longer available (Story 4, Scenario 3).
- What happens when the sidebar is empty and the user asks a question anyway? The question is still
  sent; the backend's empty-corpus behavior naturally produces the "documentation does not cover this"
  response (Story 1, Scenario 2) — the UI does not need to special-case an empty corpus before
  sending.
- What happens if the user triggers a second document's delete confirmation while a first document's
  confirmation is still open? The first is cancelled (without deleting it) and the second opens —
  only one confirmation is ever open at a time (FR-021).
- What happens if the user starts an upload while a sidebar delete confirmation is open, or opens a
  delete confirmation while an upload is in progress, or asks a question while either is happening?
  All three areas — chat, upload, and per-row delete — are independent; none blocks or clears the
  others' state (FR-022).
- What happens if the user reloads or navigates away while an upload or delete request is still in
  flight? The request may complete on the backend without any client-side confirmation being shown;
  the next page load simply reflects whatever the corpus ended up being (FR-008) — there is no
  in-flight-request recovery to build, consistent with this feature having no persisted client-side
  state (FR-019).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The UI MUST let a user type a question and submit it either by clicking a send control
  or pressing Enter; on submission, the input field MUST clear immediately, ready for the user's
  next question once the current one settles.
- **FR-002**: The UI MUST render the assistant's answer together with a citation badge for every
  source the backend's response included, in the order the backend returned them, and each badge
  MUST display that source's relevance score alongside its document and page.
- **FR-003**: The UI MUST render the fixed "documentation does not cover this" wording with no
  citation badges when the backend's response has an empty source list.
- **FR-004**: The UI MUST prevent submitting a blank or whitespace-only question, keeping the send
  control visibly disabled — not merely silently ignoring a click or Enter press — whenever the
  trimmed input is empty.
- **FR-005**: The UI MUST prevent submitting a question over 1000 characters and MUST indicate this
  to the user continuously as they type, once the trimmed input reaches or exceeds the limit — not
  only at the moment of an attempted submission.
- **FR-006**: The UI MUST disable both the question input and the send control immediately while a
  question is awaiting its answer, and re-enable both immediately once the answer or an error is
  received. There is no client-side timeout and no way to cancel a pending question — the UI does not
  time out on its own, but per FR-007 it always eventually shows either an answer or an error once the
  backend responds. Separately, the UI MUST show a loading indicator (e.g. a "thinking" placeholder in
  the conversation) while the question is pending, but MUST delay that indicator's appearance by a
  fixed 300ms from submission (research.md Decision 9) — so a response that settles within 300ms
  produces no visible flash of a loading state, while the input/send disabling above is never delayed.
- **FR-007**: The UI MUST show a clear, human-readable, non-technical error message — a distinct
  message per distinct documented failure cause (per the chat API contract), never raw error codes or
  backend text — when `POST /chat` fails (`400` or `503`), displayed in place of that question's
  pending answer. The failed question remains visible in the conversation history as its own entry
  (FR-018); retrying means the user types and submits a new question, not an automatic replay of the
  failed one. This error state persists until the user submits their next question, and MUST NOT be
  affected by or clear any unrelated state elsewhere in the UI (FR-022).
- **FR-008**: The sidebar document list MUST be populated from `GET /documents` at load time,
  reflecting the corpus exactly as it exists at that moment — not the mockup's static entries. The
  brief moment before that first response arrives MUST show a distinct loading state, tracked by its
  own "not yet loaded" signal (data-model.md `DocumentsService.loaded`) rather than being inferred
  from an empty document list — so it MUST NOT be visually identical to the confirmed-empty state
  (FR-009), even though both render zero rows.
- **FR-009**: The sidebar MUST show an explicit empty state when the corpus has no documents,
  explaining that no documents have been uploaded yet and that the upload control (FR-010) is how to
  add one.
- **FR-010**: The UI MUST let a user pick a single `.pdf` or `.txt` file via an upload control, which
  MUST start the upload immediately upon selection with no separate confirmation step, submit it to
  `POST /documents`, and add it to the sidebar list on success without a manual page reload.
- **FR-011**: The UI MUST show a specific, human-readable error for a failed upload — a distinct
  message per distinct documented failure cause (unsupported type, invalid/oversized file, or
  unparseable content) — and MUST leave the upload control usable for another attempt afterward. The
  ingestion contract's two service-side codes (`provider_unconfigured`, `processing_failed`) and a
  network failure partway through the upload are intentionally *not* given three separate messages:
  none is something the user can act on differently, so all three share one "service unavailable, try
  again" string (research.md Decision 5) — this is a deliberate grouping, not a gap in the lookup
  table, which still keeps a distinct entry per code internally (see FR-007's contrasting treatment
  for chat, where `provider_unconfigured` and `processing_failed` keep separate messages). This error
  state persists until the next upload attempt starts.
- **FR-012**: The UI MUST disable/guard the upload control against starting a second upload while one
  is already in progress.
- **FR-013**: The UI MUST let a user download a document's original file both from a sidebar action
  and from any citation badge referencing that document, retrieving the file via
  `GET /documents/{id}/content`. The sidebar's download action (and its delete action, FR-015) MUST
  be reachable by keyboard focus on a row, not only by mouse hover — a keyboard-only user MUST have
  the same capability as a mouse user.
- **FR-014**: The UI MUST show a clear message, not a silent failure, when a download fails — both
  the specific "no longer available" case (`404 document_not_found`) and any other failure (e.g. the
  backend is unreachable), each with a message appropriate to its cause, shown at the point the
  download was triggered (the sidebar row or the citation badge). The "no longer available" message
  persists for that document for the rest of the page load (retrying cannot help — the document is
  permanently gone); any other download failure's message persists until the user attempts that same
  download again.
- **FR-015**: The UI MUST let a user delete a document from the sidebar via `DELETE /documents/{id}`,
  gated behind an explicit confirmation step that can be cancelled without effect. Only one document's
  delete confirmation MUST be open at a time — triggering delete on a second document while a first
  is still unconfirmed cancels the first (without deleting it) and opens the second (FR-021).
- **FR-016**: The UI MUST remove a successfully deleted document from the sidebar immediately, without
  a manual page reload.
- **FR-017**: The UI MUST leave the sidebar unchanged and report the failure, shown at that document's
  row, when a delete request fails (`503 deletion_failed`), never optimistically removing the entry
  before success is confirmed. This error state persists until the next delete attempt on that row.
- **FR-018**: The UI MUST preserve the full question/answer exchange history, in order, for the
  current page load, and MUST visually distinguish user messages from assistant messages — including
  a failed question/answer attempt (FR-007), which stays in place as its own history entry rather than
  being removed or silently retried.
- **FR-019**: The UI MUST NOT persist chat history beyond the current page load — reloading the page
  starts a new, empty conversation, consistent with the backend having no conversation-memory concept.
- **FR-020**: The UI MUST NOT restrict `POST /chat` to a subset of documents based on sidebar
  selection — every question searches the entire corpus (document-scoped filtering is explicitly out
  of scope for this feature; see Assumptions).
- **FR-021**: The UI MUST allow at most one open delete confirmation across the whole sidebar at any
  time (see FR-015).
- **FR-022**: The UI MUST treat the chat panel, the upload control, and each sidebar row's delete
  confirmation as independent of one another — a pending or failed state in one MUST NOT block the
  user from acting in, or clear the state of, either of the other two (e.g. a pending chat question
  does not prevent starting an upload or opening a delete confirmation, and vice versa).

### Key Entities

- **Chat Message** (client-side only, not persisted by the backend): role (user or assistant),
  message text, and — for assistant messages — the ordered list of source citations attached to that
  specific answer.
- **Source Citation**: a reference to one document-and-page pair that contributed to an answer —
  document identifier, filename, page indicator (a number, or the fixed "no page structure" marker),
  and relevance score, all displayed to the user; always computed from a `POST /chat` response, never
  invented client-side.
- **Document** (sidebar entry, sourced from `GET /documents`): identifier, filename, content type,
  upload timestamp, and chunk count are all retrieved, but only the filename (plus a type icon) is
  displayed per row, matching the mockup; the identifier is the same one used for both download and
  delete actions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can go from opening the chat UI to reading a cited, grounded answer to their
  first question in one continuous flow, with no manual refresh or external tool needed at any step.
- **SC-002**: 100% of citation badges shown alongside an answer correspond exactly to the sources the
  backend returned for that answer — no citation is ever added, dropped, or altered by the UI.
- **SC-003**: A document uploaded through the UI is visible in the sidebar and answerable in chat
  without the user needing to reload the page.
- **SC-004**: A document deleted through the UI disappears from the sidebar and stops being
  downloadable within the same interaction, with no page reload required.
- **SC-005**: Every failure case (invalid question, chat processing failure, upload rejected,
  download failed, delete failed) is shown with a specific, pre-defined message matching its cause
  (never a generic catch-all when the cause is known) and, for every one of those cases except a
  download against a permanently-deleted document, the same action can be attempted again
  immediately — never a dead-end or unexplained blank state.

## Assumptions

- **Document-scoped chat filtering is out of scope for this feature.** The sidebar does not restrict
  `POST /chat`'s `documentIds` to a selection; every question always searches the full corpus
  (decided during specification — deferred, not because it's technically infeasible).
- The visual design of `docs/rag_chatbot.html` (layout, colors, typography, iconography) is the
  starting point for this feature's UI; it is re-implemented as real Angular components/services
  rather than static HTML, preserving that visual language while replacing every hardcoded value
  (file list, conversation, "Backend ready" badge) with live data.
- Upload accepts exactly one file per action, matching `POST /documents`'s one-`file`-part contract —
  no batch/multi-file picker in this feature.
- Deletion requires an explicit user confirmation step before the `DELETE` request is sent, consistent
  with the backend's no-undelete guarantee (006-document-delete).
- No authentication or per-user scoping exists anywhere in this feature, consistent with every backend
  endpoint it calls (004–007 all have no auth) — any user of the UI can see, upload, download, and
  delete any document, and ask any question.
- The backend is reached at the same fixed local address already established by
  [002-frontend-health-wire](../002-frontend-health-wire/plan.md) (`http://localhost:8080`) — no new
  deployment target is introduced by this feature.
- Chat conversation state lives only in the browser tab's memory for the current page load; there is
  no draft-saving, multi-tab sync, or history persistence of any kind. Concurrent access from
  multiple tabs or clients (e.g. one tab deleting a document another currently has cited) is not
  specially reconciled beyond what FR-014's "no longer available" download message and FR-017's
  delete-failure handling already provide incidentally.
- This feature depends on the backend enabling CORS for `/documents` and `/chat` from the frontend's
  origin — the same kind of allowance already made for `/actuator/health`
  ([002-frontend-health-wire](../002-frontend-health-wire/plan.md)) — without which no request this
  feature makes can reach the backend from the browser at all.
- The Clarifications session's indefinite-wait decision (no client-side timeout on a pending chat
  question) is a deliberate acceptance of an unbounded — but always eventually resolving — wait:
  FR-007 guarantees the UI always eventually shows either an answer or an error once the backend
  responds, so this does not conflict with SC-001's "no dead-end" expectation. A backend that never
  responds at all is a backend-level failure mode outside this feature's UI-level scope.
- Reloading or navigating away while an upload or delete request is in flight is not specially
  handled — the request may complete on the backend without client-side confirmation; the next page
  load simply reflects whatever the corpus ended up being (FR-008), consistent with this feature
  having no persisted client-side state to reconcile (FR-019).

# Quickstart: Frontend Chat UI

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Manual, runnable validation for each user story, plus the automated suite. Assumes
[001-project-scaffolding](../001-project-scaffolding/plan.md)'s database and backend are already
set up per that feature's own quickstart.

## Prerequisites

- PostgreSQL/pgvector running (see 001's `docker-compose` setup).
- Backend running on `http://localhost:8080` (`./mvnw spring-boot:run` from `backend/`). Azure
  OpenAI credentials are **not required** to validate Stories 2–5 (list/upload/download/delete) or
  Story 1's "documentation does not cover this" path with an empty corpus; they are required for
  Story 1's grounded-answer path, per the constitution's AI Provider Configuration section.
- This feature's backend prerequisite (research.md Decision 1) is deployed: CORS allows
  `http://localhost:4200` to call `/documents/**` and `/chat`, not just `/actuator/health`.
- Frontend running on `http://localhost:4200` (`npm start` from `frontend/`).

## Automated suite

```bash
cd frontend
npm test
```

Every new service and component in this feature has its own `.spec.ts` (research Decision 8) run by
this command — no live backend or Azure credentials required (all HTTP calls are mocked via
`HttpTestingController`).

## Story 1 — Ask a question and get a grounded, cited answer

1. Ensure at least one document is ingested (see Story 3 below, or `curl` per
   [004's quickstart](../004-document-ingestion-endpoint/quickstart.md)).
2. Open `http://localhost:4200`.
3. Type a question covered by that document's content; press Enter.
4. **Expect**: the question appears immediately as a user message; a loading indicator appears;
   once the backend responds, the assistant's answer appears with a citation badge per source,
   each showing filename, page (or "no page structure"), and a relevance percentage (research
   Decision 7).
5. Ask a question unrelated to any ingested content (or run this step against an empty corpus).
   **Expect**: the fixed "documentation does not cover this" message, with no citation badges.
6. Type a question, submit it, and — while it's pending — try submitting another.
   **Expect**: the send control is disabled; the second attempt has no effect until the first
   settles.

## Story 2 — Browse the live document list

1. With zero documents ingested, reload `http://localhost:4200`.
   **Expect**: the sidebar shows an explicit empty state, not a blank space or mockup placeholders.
2. Ingest one or more documents (Story 3), reload.
   **Expect**: every ingested document appears, most-recently-uploaded first, by filename only.

## Story 3 — Upload a new document

1. Click "Upload docs"; select a valid `.pdf` or `.txt` file.
   **Expect**: a busy state while the upload is in flight; on success, the new entry appears in the
   sidebar without a page reload; asking a question about its content (Story 1) now cites it.
2. Attempt an upload with an unsupported file type (e.g. `.docx`).
   **Expect**: a specific error message; no sidebar entry is added; the upload control is usable
   again immediately.
3. Start an upload, and before it completes, attempt to start another.
   **Expect**: the second attempt is blocked while the first is in flight.

## Story 4 — Download a document's original file

1. Hover a sidebar row; click its download action.
   **Expect**: the browser downloads the original file, byte-for-byte identical to what was
   uploaded, saved under its original filename.
2. Click a citation badge on a rendered answer.
   **Expect**: the same download behavior for that citation's document.
3. Delete a document (Story 5), then click a citation badge in earlier chat history still
   referencing it.
   **Expect**: a clear "source no longer available" message — no broken download, no silent
   failure.

## Story 5 — Delete a document

1. Click a sidebar row's delete action.
   **Expect**: an inline confirmation appears in place ("Delete this document? [Confirm] [Cancel]")
   — nothing is deleted yet.
2. Click "Cancel".
   **Expect**: the row reverts unchanged; no request was sent.
3. Click delete again, then "Confirm".
   **Expect**: the document disappears from the sidebar immediately, with no page reload.
4. (Requires simulating a backend failure, e.g. stopping the database mid-request) Confirm a
   delete while the backend cannot complete it.
   **Expect**: the document remains listed; a failure message is shown.

## Failure-path spot checks (Edge Cases)

- Stop the backend entirely, then ask a question.
  **Expect**: a clear, non-technical error message; the send control re-enables so the user can
  retry once the backend is back.
- Type a question over 1000 characters.
  **Expect**: the send control is blocked (or a clear limit indicator shown) before any request is
  sent — check the Network tab to confirm no `POST /chat` fired.

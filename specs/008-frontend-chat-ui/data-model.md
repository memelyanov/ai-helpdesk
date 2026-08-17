# Phase 1 Data Model: Frontend Chat UI

**Date**: 2026-08-16 | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

This feature persists nothing — every shape below is a client-side TypeScript type living only in
the browser tab's memory for the current page load (spec.md Assumptions). Each one is either a
direct mirror of an existing backend response shape, or a small UI-only wrapper around one. Backend
shapes are documented in full in their own features' `data-model.md`; this document states only
what's new or reshaped for display.

## `ChatMessage` (client-side only — spec.md Key Entities)

One entry in the conversation, in submission order (FR-018).

| Field | Type | Notes |
|---|---|---|
| `id` | `string` | Client-generated (e.g. `crypto.randomUUID()`), for Angular `@for` tracking only — never sent to or received from the backend. |
| `role` | `'user' \| 'assistant'` | Which side of the conversation this message belongs to (FR-018). |
| `text` | `string` | The question (`role: 'user'`) or the answer (`role: 'assistant'`) verbatim — `answer` is rendered exactly as `ChatResponse.answer` arrives, including the fixed "not covered" string (FR-003); never edited or truncated client-side. |
| `citations` | `Citation[]` | Empty for `role: 'user'`. For `role: 'assistant'`, the ordered list built from `ChatResponse.sources` (FR-002) — empty exactly when `text` is the fixed "not covered" string, matching the backend's own invariant (007's data-model.md). |
| `status` | `'pending' \| 'complete' \| 'error'` | `'pending'` only while `role: 'assistant'`'s answer is in flight (Decision 6/FR-006); becomes `'complete'` once `text`/`citations` are populated, or `'error'` if the request failed (FR-007) — the associated `errorMessage` below is only set in this case. A `role: 'user'` message is always `'complete'` immediately. |
| `errorMessage` | `string \| undefined` | Set only when `status === 'error'` — one of Decision 5's fixed, pre-written strings, never raw backend text. |

## `Citation` (client-side shape derived from `SourceCitation`)

One badge under an assistant message (FR-002).

| Field | Type | Notes | Source |
|---|---|---|---|
| `documentId` | `string` (UUID) | Same identifier `GET /documents/{id}/content` accepts — used for the download action (FR-013). | `SourceCitation.documentId` (007's data-model.md) |
| `filename` | `string` | Rendered label; also the suggested filename for a citation-triggered download (Decision 3). | `SourceCitation.filename` |
| `pageLabel` | `string` | Rendered exactly as received — either a page number as a string, or the fixed `"no page structure"` marker (FR-002, spec Edge Cases). No parsing, no reformatting. | `SourceCitation.page` |
| `scorePercent` | `number` | `Math.round(score * 100)`, always in `[50, 100]` (Decision 7); the `%` suffix and "match" wording are template-level presentation, not stored separately. | `SourceCitation.score` |
| `available` | `boolean` | `true` until a download attempt against `documentId` returns `404 document_not_found` (FR-014, Story 4 Scenario 3), at which point it flips to `false` and the badge shows "source no longer available" instead of retrying. Starts `true` for every citation; never reverts to `true` once `false` within the same page load. |

## `DocumentSummary` (client-side mirror of `DocumentSummaryResponse`)

One sidebar row (FR-008), sourced verbatim from `GET /documents`
([005's data-model.md](../005-document-listing-download/data-model.md)) — no field is dropped, even
though only `filename` (plus a type icon derived from `contentType`) is actually rendered per the
Clarifications session's sidebar-detail decision. `documentId`, `uploadedAt`, and `chunkCount` are
retained on the object because `documentId` drives the download/delete actions and the list's sort
order, even though they aren't shown as separate text.

| Field | Type | Notes |
|---|---|---|
| `documentId` | `string` (UUID) | Identity; used for download (FR-013) and delete (FR-015) actions. |
| `filename` | `string` | The only field rendered as visible row text. |
| `contentType` | `string` | `text/plain` or `application/pdf` — maps to the file-type icon (mockup's `.pdf`/`.txt` icon distinction), not shown as text. |
| `uploadedAt` | `string` (ISO-8601) | Retained for sort order (most-recently-uploaded first, matching `GET /documents`'s own ordering — FR-008) but not rendered as visible text. |
| `chunkCount` | `number` | Retained for completeness of the mirrored shape; not rendered (Clarifications). |

## Sidebar row UI state (not part of `DocumentSummary` itself)

Transient, held in the sidebar container component rather than in `DocumentsService`'s list — this
is interaction state, not server data. It is tracked once at the container level, not once per row,
specifically so at most one row can be confirming at a time (FR-021 — see Decision 4's revision
below):

| Field | Type | Notes |
|---|---|---|
| `confirmingDocumentId` | `string \| null` | The `documentId` of the one row currently showing its inline "Delete this document? [Confirm] [Cancel]" (Decision 4), or `null` if none is. A row renders its confirming UI exactly when `confirmingDocumentId === row.documentId`. Triggering delete on any row sets this to that row's id — overwriting whatever id was there before, which is precisely how FR-021's "opening a second cancels the first" is satisfied for free, with no separate cancel step needed. Reverts to `null` on Cancel or once the delete request settles either way (FR-017). |

## `DocumentsService` state (Injectable, signal-based — research Decision 2)

| Signal / method | Shape | Notes |
|---|---|---|
| `documents` | `Signal<DocumentSummary[]>` | Populated from `GET /documents` on service init (FR-008) and after every successful upload/delete (FR-010, FR-016) — always the full current list, never a client-side patch/merge that could drift from the server. |
| `loaded` | `Signal<boolean>` | `false` from service construction until the first `refresh()` call settles (success or failure), then `true` for the rest of the page load — never reverts to `false` again (a later `refresh()`, e.g. after upload/delete, does not re-enter a "loading" state). This is the signal `document-sidebar.component` branches on to distinguish "not yet loaded" from "confirmed empty" (FR-008/FR-009): loading state when `!loaded()`, empty state when `loaded() && documents().length === 0`, list otherwise. Without this signal the two states would be indistinguishable, since both render an empty `documents` list. |
| `uploading` | `Signal<boolean>` | `true` for the duration of one `POST /documents` call (FR-012); guards the upload control against a second overlapping call. |
| `uploadError` | `Signal<string \| null>` | Decision 5's mapped message for the most recent failed upload, or `null`; cleared at the start of the next upload attempt (FR-011). |
| `upload(file: File): void` | method | Calls `POST /documents`; on success, appends the new entry and re-`refresh()`s (simplest way to guarantee list ordering stays server-authoritative — see FR-008); on failure, sets `uploadError`, adds no entry. |
| `remove(documentId: string): void` | method | Calls `DELETE /documents/{id}`; on success (`204`), removes the entry from `documents` (FR-016); on failure (`503 deletion_failed`), leaves `documents` untouched and reports the failure to the caller (FR-017) — the failure is surfaced through the calling row component's own local state, not a shared service-level error signal, since it's specific to one row's confirmation flow. |
| `refresh(): void` | method | Re-fetches `GET /documents` and replaces `documents` wholesale. |

## `ChatService` state (Injectable, signal-based — research Decision 2)

| Signal / method | Shape | Notes |
|---|---|---|
| `messages` | `Signal<ChatMessage[]>` | The full conversation for this page load (FR-018/FR-019) — grows by exactly two entries (`user`, then `assistant`) per `ask()` call, the second starting `status: 'pending'` and settling in place. |
| `pending` | `Signal<boolean>` | `true` between a user's submission and that same question's answer/error settling (FR-006); drives the send control's disabled state directly and immediately. The *visible* loading indicator is not driven by `pending` alone — `chat-view.component` gates its appearance behind Decision 9's fixed 300ms anti-flash timer, a component-local concern not modeled as a service signal (no timeout otherwise, so `pending` itself can only become `false` via a settled response). |
| `ask(question: string): void` | method | No-ops if `question` is blank/whitespace-only or exceeds `MAX_QUESTION_LENGTH` after trimming (FR-004/FR-005 — the input component is expected to have already blocked this, but the service itself never sends an invalid request either way); otherwise appends the `user` message, appends a `pending` `assistant` message, and calls `POST /chat` with `{ question, documentIds: null }` (FR-020 — `documentIds` is always omitted/`null`, never populated from any sidebar selection). |

## Relationship to existing backend contracts

```text
GET /documents          ──►  DocumentSummary[]        (DocumentsService.documents)
POST /documents          ──►  DocumentSummary (new)     (DocumentsService.upload)
DELETE /documents/{id}   ──►  (204, no body)             (DocumentsService.remove)
GET /documents/{id}/content ─► Blob                      (Decision 3's download helper — not stored on any service signal)

POST /chat                ──►  ChatResponse               (ChatService.ask → appends ChatMessage)
  ChatResponse.answer     ──►  ChatMessage.text
  ChatResponse.sources[]  ──►  ChatMessage.citations[] (Citation, via the mapping in Decision 7)
```

## Out of scope for this feature's data shapes

- **No persisted `ChatMessage` or `DocumentSummary` store** (e.g. `localStorage`, IndexedDB) — both
  live only in each service's in-memory signal for the current page load (FR-019, spec.md
  Assumptions).
- **No `documentIds` field anywhere in `ChatService`'s request construction** — FR-020 fixes this at
  `null`/omitted for every call this feature makes; there is no UI state this data model tracks that
  could populate it (document-scoped filtering is out of scope, spec.md Assumptions).
- **No pagination cursor or partial-list shape** for `documents` — `GET /documents` already returns
  the full corpus in one response (005's contract); `DocumentsService.documents` mirrors that
  wholesale, never a page at a time.

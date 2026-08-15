# Implementation Plan: Document Deletion Endpoint

**Branch**: `main` (no dedicated feature branch — no `before_specify`/`before_plan` hook is
registered in `.specify/extensions.yml`, same situation features 004 and 005's plans recorded) |
**Date**: 2026-08-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-document-delete/spec.md`

## Summary

Add `DELETE /documents/{id}` to the existing `/documents` resource
(`backend/.../ingestion/DocumentController.java`, feature 004, extended by feature 005): it
permanently deletes one document and, via feature 003's already-existing `ON DELETE CASCADE`, every
chunk derived from it, in a single atomic `DELETE FROM documents WHERE id = ?` statement. Success is
`204 No Content`; a malformed, nonexistent, or already-deleted id returns the same
`404 document_not_found` feature 005's download endpoint already established; an unexpected
server-side failure during an otherwise-valid deletion returns a new, distinct
`503 deletion_failed` — never a partial deletion (spec.md Clarifications, Session 2026-08-16;
FR-010). No schema change, no new dependency, no call to Azure OpenAI or any external provider —
this is the endpoint feature 003's FR-014 anticipated from the start but that no feature has
exposed until now.

Three decisions carry the design (full reasoning in [research.md](research.md)):

- **The single `DELETE` statement's own affected-row-count is the only signal needed** to decide
  between `204` (one row deleted) and `404` (zero rows matched) — no prior existence check, so there
  is no check-then-act race window for concurrent deletes of the same id to fall into.
- **No explicit transaction wrapping is added.** A single `DELETE` statement is already atomic in
  PostgreSQL, and the chunk cascade is a database-level guarantee (feature 003 FR-011), not
  something application code needs to orchestrate — unlike `DocumentRepository.save`'s multi-insert
  write, which genuinely needs its own `TransactionTemplate`.
- **A new `DocumentDeletionException` (→ `503 deletion_failed`)**, a sibling of
  `DocumentNotFoundException`, is added for the FR-010 failure case — not a reuse of the existing
  `IngestionProcessingException`, whose Javadoc is deliberately scoped to the ingestion/write
  pipeline's own two failure categories and would otherwise silently broaden past its documented
  meaning, the same kind of drift feature 005's `Ingestion*` → `Document*` rename already treated as
  a defect to avoid, not repeat.

## Technical Context

**Language/Version**: Java 17, Spring Boot 3.5.16 (unchanged from features 001/003/004/005).

**Primary Dependencies**: none new. Implemented entirely with what is already on
`backend/pom.xml`'s classpath — `spring-boot-starter-web` (REST), `spring-boot-starter-jdbc`
(`JdbcTemplate` for the one new `DELETE` statement), `org.postgresql:postgresql`.

**Storage**: Same PostgreSQL 18 + pgvector instance and `documents`/`chunks` tables feature 003
created — no schema change. This is the first feature to write a `DELETE` against them (features
004/005 only ever `INSERT` or `SELECT`); the cascade relationship that makes it safe
(`ON DELETE CASCADE`, feature 003 FR-011) already exists and needs no migration.

**Testing**: JUnit 5 (existing stack). Two layers, mirroring feature 005's exact split (research
Decision 7):
- **Contract test** (always run, `MockMvc`, no live DB): `DELETE /documents/{id}` against a stubbed
  `DocumentRepository`, asserting `204` on success, `404` for a malformed id, a nonexistent id, and
  an already-deleted id (FR-008), and `503` when the repository reports an unexpected failure.
- **`@Tag("db")` integration test** (opt-in, `verify-db` profile, Testcontainers
  `pgvector/pgvector:pg18`, reusing `DocumentIngestionIT`/`DocumentQueryIT`'s exact container/schema
  bring-up): ingest a real document, delete it, prove its `chunks` rows are actually gone
  (`SELECT count(*) FROM chunks WHERE document_id = ?` is `0`), and prove a second delete of the
  same id now returns `404`.

**Target Platform**: Same as features 001/003/004/005 — local developer machine, Docker Compose for
PostgreSQL only; backend runs locally (`mvnw spring-boot:run`).

**Project Type**: Web application (existing structure). This feature adds backend-only code
(`backend/src/main/java/.../ingestion/`) — no frontend change, consistent with features 004/005's
Assumptions that the Angular document-browse/upload view is a separate, later step
(`poc-concept.md` §10 item 7).

**Performance Goals**: SC-001/SC-005 — a deletion completes and is confirmed gone (via the next
listing or download call) in under 2 seconds. Trivial to hit: one indexed-by-primary-key `DELETE`
statement, no network call to an external provider anywhere in the endpoint's path.

**Constraints**:
- `DELETE /documents/{id}` MUST resolve a malformed or nonexistent `{id}` to the identical
  `404 document_not_found` feature 005's download endpoint already returns for the same two cases
  (research Decision 3) — reusing `DocumentController`'s existing `parseId` helper, not
  reimplementing UUID parsing.
- An id naming an already-deleted document MUST also resolve to `404 document_not_found` (FR-008) —
  the same code path as a never-issued id, since `deleteById`'s zero-rows-affected outcome cannot
  distinguish "never existed" from "already gone," and FR-008 says it must not need to.
- Deletion of an existing document MUST be all-or-nothing: on an unexpected server-side failure, the
  document and every one of its chunks MUST remain exactly as they were, and the caller MUST receive
  `503 deletion_failed`, never a partial deletion and never confused with `404` (FR-010,
  Clarifications Session 2026-08-16).
- Deleting one document MUST NOT affect any other document or its chunks (FR-007) — the `WHERE id =
  ?` clause is the only mechanism needed; no broader query or table scan is ever involved.
- No credential is in scope to leak here (deletion never touches Azure OpenAI configuration), but
  the existing discipline (no raw exception internals in a response body) still applies to the new
  `503` response, consistent with `DocumentErrorHandler`'s existing fixed, code-reviewed message
  pattern.

**Scale/Scope**: One new `DELETE` handler method on the existing controller, one new method
(`deleteById`) on the existing write-only `DocumentRepository`, one new exception
(`DocumentDeletionException`), one new `@ExceptionHandler` method on the existing
`DocumentErrorHandler`, and a one-line addition to `DocumentErrorResponse`'s Javadoc table (the new
`deletion_failed` error code). No new persisted entity, no new response DTO (a `204` has no body),
no rename of any existing class. Expected load: same 16-document sample corpus scale features
004/005 established; deletions happen far less often than uploads or list/browse calls at this
PoC's usage pattern.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.4.1** (unchanged since feature 005; no
amendment has landed between that feature and this one).

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` precedes this plan; `/speckit-clarify` resolved the one critical ambiguity found (failure-handling atomicity, Session 2026-08-16) before this plan was written. |
| II | Test-Driven Development (Mandatory) | ✅ PASS | Two test layers planned (contract / `db`) before any implementation task is written; the default suite (contract only) needs no live database. No `azure` tier exists to omit — deletion never touches the AI provider. |
| III | Grounded Answers (RAG-First) | ⏭️ N/A — deferred | No answer generation here; this endpoint only removes corpus content a future `/chat` feature would otherwise retrieve from. |
| IV | No Hallucination (Context Adherence) | ⏭️ N/A — deferred | No LLM chat call in this feature. |
| V | Semantic Understanding (Meaning-Based Retrieval) | ⏭️ N/A — deferred | No embedding or similarity search in this feature — deletion is an exact-match write by primary key, never a vector comparison. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ✅ PASS | The delete operates only against the existing self-hosted PostgreSQL/pgvector instance; no external call, no data leaves the organization's control. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ⏭️ N/A — deferred | No retrieval logic here to measure — same rationale features 004/005's plans recorded. |

**AI Provider Configuration compliance**: N/A — this endpoint reads no `AZURE_OPEN_AI_*` variable
and calls no Azure service.

**Chunking & Embedding Strategy compliance**: N/A — no chunking or embedding occurs; the cascade
delete simply removes existing `chunks` rows, it does not produce or re-derive any.

**Ingestion Pipeline compliance**: N/A — this feature adds no ingestion behavior; `POST /documents`
is unchanged.

**Query Pipeline compliance**: N/A — the constitution's "Query Pipeline" section describes the
future `/chat` retrieval endpoint, not this deletion endpoint.

**Error Handling & Logging compliance**: the new `503 deletion_failed` path is wrapped consistently
with the existing `400`/`404`/`503` pattern established across features 004/005 — a caller can tell
"not found" (`404`) from "processing failed" (`503`) from status code alone, satisfying the
constitution's explicit requirement that a `4xx`/`5xx` split be caller-distinguishable. The `DELETE`
statement is wrapped in a `try`/`catch` (mirroring `DocumentRepository.save`'s existing pattern) so
an unexpected JDBC failure never surfaces as an unmapped `500`; no credential is ever in scope to
leak from this endpoint.

**Testing & Validation compliance**: functional test coverage (contract + `db`-tagged) verifies the
success path, all three `404`-producing cases (malformed, nonexistent, already-deleted), the `503`
failure path, and the real cascade removing `chunks` rows — the negative-case discipline this
section requires, scoped to what this feature actually does.

**Code & Documentation Language Standard compliance**: this plan and all Phase 0/1 artifacts are in
English ✅; implementation-phase code, comments, and commit messages will follow the same standard.

**Technology Stack compliance**: Java 17 / Spring Boot 3 ✅; no new dependency introduced (research
Decision 7) ✅; PostgreSQL + pgvector ✅ (no schema change); no JPA/Hibernate introduced ✅ (plain
`JdbcTemplate`, consistent with 001/003/004/005).

**Post-Phase 1 re-check**: ✅ No change. Phase 1 design (data-model.md, contracts/, quickstart.md)
introduced no new persisted table or column, no new dependency, and no deviation from the mandated
tech stack.

**Gate result**: PASS — no violations, no justifications required. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/006-document-delete/
├── plan.md                                  # This file
├── research.md                              # Phase 0 — 7 decisions
├── data-model.md                            # Phase 1 — response/internal shapes (no new persisted entities)
├── quickstart.md                            # Phase 1 — bring-up and per-user-story validation
├── contracts/
│   └── document-delete-api-contract.md      # DELETE /documents/{id}
├── checklists/
│   └── requirements.md                      # Spec quality checklist — 16/16 items pass
└── tasks.md                                 # Phase 2 — created by /speckit-tasks, NOT by this command
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                                  # UNCHANGED — no new dependency (research Decision 7)
├── src/main/java/com/epam/aihelpdesk/
│   ├── health/                                              # UNCHANGED (feature 001)
│   └── ingestion/
│       ├── DocumentController.java                          # MODIFIED — + DELETE /documents/{id}
│       ├── DocumentRepository.java                          # MODIFIED — + deleteById(UUID)
│       ├── DocumentQueryRepository.java                     # UNCHANGED (feature 005) — read-only, untouched
│       ├── DocumentContent.java                              # UNCHANGED (feature 005)
│       ├── DocumentNotFoundException.java                    # UNCHANGED (feature 005) — reused for this feature's 404s too
│       ├── DocumentDeletionException.java                    # NEW — → 503 deletion_failed (research Decision 6)
│       ├── DocumentErrorHandler.java                         # MODIFIED — + 503 deletion_failed mapping
│       ├── IngestionException.java                            # UNCHANGED (feature 004)
│       ├── InvalidDocumentException.java                      # UNCHANGED (feature 004)
│       ├── IngestionProcessingException.java                  # UNCHANGED (feature 004) — NOT reused here (research Decision 6)
│       └── dto/
│           ├── DocumentIngestionResponse.java                 # UNCHANGED (feature 004)
│           ├── DocumentErrorResponse.java                     # MODIFIED — Javadoc + deletion_failed row only, shape unchanged
│           └── DocumentSummaryResponse.java                   # UNCHANGED (feature 005)
└── src/test/java/com/epam/aihelpdesk/ingestion/
    ├── DocumentControllerContractTest.java                    # UNCHANGED (feature 004) — POST tests only
    ├── DocumentQueryControllerContractTest.java                # UNCHANGED (feature 005) — GET tests only
    ├── DocumentDeleteControllerContractTest.java                # NEW — DELETE /documents/{id} contract tests
    ├── DocumentIngestionIT.java                                # UNCHANGED (no modification; DocumentDeleteIT.java is a new, separate file)
    ├── DocumentQueryIT.java                                     # UNCHANGED (feature 005)
    └── DocumentDeleteIT.java                                    # NEW — real DELETE + real cascade against Testcontainers

frontend/                                                       # UNCHANGED — no frontend work in this feature
```

**Structure Decision**: Web application structure from features 001/003/004/005 is unchanged. All
new production code lands in the existing `backend/.../ingestion/` package alongside
`DocumentController`/`DocumentRepository` — this is the third verb on the same `/documents` resource,
not a new bounded context, so it does not warrant a new top-level package. Test files follow the
existing one-file-per-endpoint-group split (`*ControllerContractTest`/`*IT` pairs) tasks.md will
finalize. No new top-level directories, no frontend changes, no schema changes.

## Complexity Tracking

*No entries — the Constitution Check gate passed with no violations requiring justification.*

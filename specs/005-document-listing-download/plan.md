# Implementation Plan: Document Listing and Download Endpoints

**Branch**: `main` (no dedicated feature branch — no `before_specify`/`before_plan` hook is
registered in `.specify/extensions.yml`, same situation feature 004's plan recorded) | **Date**:
2026-08-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-document-listing-download/spec.md`

## Summary

Add two read-only endpoints to the existing `/documents` resource
(`backend/.../ingestion/DocumentController.java`, feature 004): `GET /documents` lists every
ingested document (id, filename, content type, upload time, chunk count — newest first, zero-chunk
documents included), and `GET /documents/{id}/content` returns a document's original file bytes,
byte-for-byte, with the right `Content-Type` and `Content-Disposition` headers. Both are plain
`SELECT`s against the `documents`/`chunks` schema feature 003 created and feature 004 first wrote
into — no schema change, no new dependency, and no call to Azure OpenAI or any external provider.

Three decisions carry the design (full reasoning in [research.md](research.md)):

- **Both endpoints extend feature 004's existing `DocumentController`**, since all three verbs
  (`POST`/`GET`/`GET .../content`) operate on the same `/documents` resource — not a new controller.
- **A malformed document id and a well-formed-but-nonexistent one collapse into the same
  `404 document_not_found`** — a caller never needs to distinguish "not a UUID" from "no such
  document," since neither is fixable by retrying.
- **The shared `{error, message}` error surface is renamed from "ingestion" to "document" scope**
  (`IngestionErrorHandler`→`DocumentErrorHandler`, `IngestionErrorResponse`→`DocumentErrorResponse`)
  because it now serves all three endpoints' error responses, not `POST /documents` alone — keeping
  their Javadoc accurate rather than letting it silently go stale. The write-path exception
  hierarchy (`IngestionException`/`InvalidDocumentException`/`IngestionProcessingException`) is
  untouched; it still precisely describes only the ingestion pipeline's own 400/503 split.

## Technical Context

**Language/Version**: Java 17, Spring Boot 3.5.16 (unchanged from features 001/003/004).

**Primary Dependencies**: none new. Both endpoints are implemented entirely with what is already on
`backend/pom.xml`'s classpath — `spring-boot-starter-web` (REST), `spring-boot-starter-jdbc`
(`JdbcTemplate` for the two new read queries), `org.postgresql:postgresql`. Feature 004's one true
dependency gap (`pgvector`'s Java helper for the `vector` column) does not apply here — this feature
never reads the `embedding` column back (research Decision 7).

**Storage**: Same PostgreSQL 18 + pgvector instance and `documents`/`chunks` tables feature 003
created, feature 004 first populated — no schema change. Both new endpoints are read-only against
that schema; neither creates, modifies, nor deletes a row.

**Testing**: JUnit 5 (existing stack). Two layers, reusing feature 001/003/004's `db` tag convention
(no `azure` tag needed — research Decision 8):
- **Contract test** (always run, `MockMvc`, no live DB): `GET /documents` and
  `GET /documents/{id}/content` against a stubbed `DocumentQueryRepository`, asserting
  request/response shape, ordering, empty-list, and both 404 paths (malformed id, nonexistent id).
- **`@Tag("db")` integration test** (opt-in, `verify-db` profile, Testcontainers
  `pgvector/pgvector:pg18`, reusing `DocumentIngestionIT`'s exact container/schema bring-up): ingest
  real documents via `POST /documents` (stubbed embedding model, same pattern as
  `DocumentIngestionIT`), then prove the real `LEFT JOIN`/`GROUP BY` list query and a real
  byte-for-byte download against actual inserted rows.

**Target Platform**: Same as features 001/003/004 — local developer machine, Docker Compose for
PostgreSQL only; backend runs locally (`mvnw spring-boot:run`).

**Project Type**: Web application (existing structure). This feature adds backend-only code
(`backend/src/main/java/.../ingestion/`) — no frontend change. Consistent with feature 004's
Assumptions: the Angular document-browse/upload view is a separate, later step
(`poc-concept.md` §10 item 7); this feature is the REST endpoints only, exercised directly (HTTP
client or test harness) until that UI exists.

**Performance Goals**: SC-001 — listing the full 16-document sample corpus completes in under 2
seconds. Trivial to hit: one indexed-by-primary-key `LEFT JOIN`/`GROUP BY` query over a few hundred
`chunks` rows at most, no network call to an external provider anywhere in either endpoint's path.

**Constraints**:
- `GET /documents` MUST use a `LEFT JOIN` (never `INNER JOIN`) between `documents` and `chunks` —
  an `INNER JOIN` would silently drop every zero-chunk document, violating FR-003.
- `GET /documents/{id}/content`'s `Content-Disposition` header MUST safely encode the stored
  filename — that field is opaque, unsanitized text end to end (feature 004 FR-017: never
  interpreted as a path or executed), and this feature is the first place it flows into an HTTP
  response *header* rather than a JSON body value or a database column, a new context where an
  unescaped value (e.g. one containing a quote or a control character) could corrupt the response.
  Implementation MUST use Spring's `ContentDisposition` builder (RFC 6266-safe encoding), never
  hand-built header string concatenation.
- A malformed or nonexistent `{id}` MUST both resolve to `404 document_not_found` — no `400` path
  exists for this endpoint (research Decision 4).
- Neither endpoint may include chunk `text` or `embedding` content in any response (FR-012).
- No credentials are in scope to leak here (neither endpoint touches Azure OpenAI configuration),
  but the existing FR-014-style discipline (no raw exception internals in a response body) still
  applies to the new `404` response, consistent with `DocumentErrorHandler`'s existing pattern of
  fixed, code-reviewed response messages.

**Scale/Scope**: Two new `GET` handler methods on an existing controller, one new read-only
repository (`DocumentQueryRepository`), one new response DTO (`DocumentSummaryResponse`), one new
internal carrier record (`DocumentContent`), one new exception (`DocumentNotFoundException`), and a
rename of two existing classes (`DocumentErrorHandler`, `dto/DocumentErrorResponse`) to keep their
Javadoc accurate now that they serve three endpoints, not one. No new persisted entities
(data-model.md documents only request/response and internal shapes — the stored shapes are already
fully specified by feature 003, unchanged). Expected load: the 16-document sample corpus (same
scale feature 004 established), read far more often than written at this PoC's usage pattern (a
browse view re-lists on every visit; feature 004's uploads happen far less often).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.4.1** (unchanged since feature 004; no
amendment has landed between that feature and this one).

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` precedes this plan; `/speckit-clarify` found no critical ambiguities needing resolution (all Partial/Missing categories had reasonable, documented defaults in the spec's Assumptions section). |
| II | Test-Driven Development (Mandatory) | ✅ PASS | Two test layers planned (contract / `db`) before any implementation task is written; the default suite (contract only) needs no live database, honoring "clean checkout runs green." No `azure` tier exists to omit — neither endpoint touches the AI provider, so there is nothing there for Principle II's "Tests MUST NOT require live AI provider credentials" clause to even apply to. |
| III | Grounded Answers (RAG-First) | ⏭️ N/A — deferred | No answer generation here; these endpoints let a caller inspect the corpus a future `/chat` feature retrieves from, nothing more. |
| IV | No Hallucination (Context Adherence) | ⏭️ N/A — deferred | No LLM chat call in this feature. |
| V | Semantic Understanding (Meaning-Based Retrieval) | ⏭️ N/A — deferred | No embedding or similarity search in this feature — both endpoints are exact-match reads by primary key or a plain aggregate count, never a vector comparison. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ✅ PASS | Both endpoints read only from the existing self-hosted PostgreSQL/pgvector instance; no external call, no data leaves the organization's control at all in this feature. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ⏭️ N/A — deferred | No retrieval logic here to measure — same rationale feature 004's plan recorded: this PR's Constitution Compliance checklist should record this line as N/A, since `sample-data/evaluation-questions.csv` still has no query/chat pipeline to run against. |

**AI Provider Configuration compliance**: N/A — neither endpoint reads `AzureOpenAiProperties` or
any `AZURE_OPEN_AI_*` variable; nothing here is affected by whether the provider is configured.

**Chunking & Embedding Strategy compliance**: N/A — no chunking or embedding occurs in this
feature; `GET /documents`'s `chunkCount` only counts rows feature 004 already wrote correctly.

**Ingestion Pipeline compliance**: N/A — this feature adds no ingestion behavior; `POST /documents`
is unchanged.

**Query Pipeline compliance**: N/A — the constitution's "Query Pipeline" section describes the
future `/chat` retrieval endpoint, not these listing/download endpoints.

**Error Handling & Logging compliance**: the new `404` path is wrapped consistently with the
existing `400`/`503` pattern — a caller can tell "not found" from the other two categories by status
code alone, per the same status-code-carries-the-signal principle FR-011 (feature 004) established
and this feature's FR-010 continues; no credential is ever in scope to leak from either endpoint, so
that clause of Error Handling & Logging does not apply here — there is nothing to fail wrapping
around (no external API call in this feature) beyond the ordinary JDBC read, which already surfaces
as an unhandled `5xx` if it ever throws (no case in this feature's requirements calls for treating a
DB read failure as anything other than an ordinary server error, since there is no retry-guidance
distinction to make for a read the way FR-011 required one for a write). `404` itself is a `4xx`
status, so it already sits on the "input-problem" side of the constitution's `4xx`/`5xx` split — the
"input" in question is the requested `{id}`, which either does not name any stored document or is not
a validly formatted identifier at all; either way, the request as given cannot be satisfied and no
retry of the identical request will change that, the same caller-actionable distinction the `4xx`
category exists to signal.

**Testing & Validation compliance**: functional test coverage (contract + `db`-tagged) verifies list
ordering, empty-list, zero-chunk inclusion, byte-for-byte download, and both 404 paths — the
negative-case discipline this section requires, scoped to what this feature actually does (no
malformed-PDF or empty-query case applies here; those belong to `POST /documents` and the future
`/chat` endpoint respectively).

**Code & Documentation Language Standard compliance**: this plan and all Phase 0/1 artifacts are in
English ✅; implementation-phase code, comments, and commit messages will follow the same standard.

**Technology Stack compliance**: Java 17 / Spring Boot 3 ✅; no new dependency introduced (research
Decision 7) ✅; PostgreSQL + pgvector ✅ (no schema change); no JPA/Hibernate introduced ✅ (plain
`JdbcTemplate`, consistent with 001/003/004).

**Post-Phase 1 re-check**: ✅ No change. Phase 1 design (data-model.md, contracts/, quickstart.md)
introduced no new persisted table or column, no new dependency, and no deviation from the mandated
tech stack.

**Gate result**: PASS — no violations, no justifications required. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/005-document-listing-download/
├── plan.md                                  # This file
├── research.md                              # Phase 0 — 8 decisions
├── data-model.md                            # Phase 1 — response/internal shapes (no new stored entities)
├── quickstart.md                            # Phase 1 — bring-up and per-user-story validation
├── contracts/
│   └── document-query-api-contract.md       # GET /documents, GET /documents/{id}/content
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
│       ├── DocumentController.java                          # MODIFIED — + GET /documents, GET /documents/{id}/content
│       ├── DocumentRepository.java                          # UNCHANGED (feature 004) — write-only, untouched
│       ├── DocumentQueryRepository.java                     # NEW — findAll(), findContentById(UUID)
│       ├── DocumentContent.java                              # NEW — internal carrier (filename, contentType, content)
│       ├── DocumentNotFoundException.java                    # NEW — → 404 (research Decision 4)
│       ├── DocumentErrorHandler.java                         # RENAMED from IngestionErrorHandler.java — + 404 mapping
│       ├── IngestionException.java                            # UNCHANGED (feature 004)
│       ├── InvalidDocumentException.java                      # UNCHANGED (feature 004)
│       ├── IngestionProcessingException.java                  # UNCHANGED (feature 004)
│       └── dto/
│           ├── DocumentIngestionResponse.java                 # UNCHANGED (feature 004)
│           ├── DocumentErrorResponse.java                     # RENAMED from IngestionErrorResponse.java — Javadoc broadened
│           └── DocumentSummaryResponse.java                   # NEW — { documentId, filename, contentType, uploadedAt, chunkCount }
└── src/test/java/com/epam/aihelpdesk/ingestion/
    ├── DocumentControllerContractTest.java                    # MODIFIED — + list/download test cases (or split, see tasks.md)
    └── DocumentIngestionIT.java                                # UNCHANGED — or a sibling DocumentQueryIT.java, see tasks.md

frontend/                                                       # UNCHANGED — no frontend work in this feature
```

**Structure Decision**: Web application structure from features 001/003/004 is unchanged. All new
production code lands in the existing `backend/.../ingestion/` package alongside
`DocumentController`/`DocumentRepository` — this is an extension of the same `/documents` resource
feature 004 introduced, not a new bounded context, so it does not warrant a new top-level package.
The only structural churn is the `Ingestion*` → `Document*` rename of the two classes that are no
longer accurately scoped to "ingestion" once they serve read endpoints too (research Decision 6). No
new top-level directories, no frontend changes, no schema changes.

## Complexity Tracking

*No entries — the Constitution Check gate passed with no violations requiring justification.*

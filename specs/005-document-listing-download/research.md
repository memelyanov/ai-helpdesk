# Phase 0 Research: Document Listing and Download Endpoints

**Date**: 2026-08-15 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

Eight decisions. No `[NEEDS CLARIFICATION]` markers remain after this document — this feature adds
no new dependency and no schema change, so the research surface is entirely about how the two new
endpoints fit into feature 004's existing `ingestion` package, not about new technology choices.

## Decision 1: Both endpoints extend the existing `DocumentController` / `/documents` resource

- **Decision**: `GET /documents` (list) and `GET /documents/{id}/content` (download) are added as
  new handler methods on the existing `DocumentController` (`backend/.../ingestion/DocumentController.java`,
  feature 004), not a new controller class.
- **Rationale**: All three endpoints — `POST`, the new `GET`, and the new `GET .../content` — operate
  on the same REST resource, `/documents`. Spring MVC's convention (and this codebase's own
  `HealthEndpoint`-style single-controller-per-resource pattern) is to group every verb for one
  resource in one controller; splitting list/download into a second controller would duplicate the
  `@RequestMapping("/documents")` base path for no benefit.
- **Alternatives considered**: a separate `DocumentQueryController` (rejected — no distinct
  bounded-context reason to split from `DocumentController`; both files stay small — under 100
  lines each — even combined).

## Decision 2: List query is one SQL statement; response is a bare JSON array, newest first

- **Decision**: `DocumentQueryRepository.findAll()` issues one `LEFT JOIN` + `GROUP BY` +
  `COUNT(c.id)` query against `documents`/`chunks`, ordered `ORDER BY d.uploaded_at DESC, d.id DESC`
  (the `id` tiebreak makes ordering deterministic on the rare chance two documents share a
  timestamp — `uploaded_at`'s `now()` default is not guaranteed unique across near-simultaneous
  inserts). `GET /documents` returns `200 OK` with a bare JSON array of
  `DocumentSummaryResponse` objects — not an object wrapping a `documents` field — since there is no
  pagination metadata (total count, next-page token, etc.) to attach per this feature's Assumptions
  (no pagination at PoC scale), and a bare array is the simplest shape that is still trivially
  extensible later (wrapping it in `{ "documents": [...] }` remains a compatible future change if
  pagination is ever added; today it would only be an empty wrapper).
- **Rationale**: FR-002/FR-005/FR-006 (summary fields, newest-first default, empty-list-not-error)
  are all satisfied directly by one query — a `LEFT JOIN` (not `INNER JOIN`) is required specifically
  because FR-003 mandates zero-chunk documents appear in the list; an `INNER JOIN` would silently
  drop them. Doing the count in SQL (rather than N+1 application-level count queries, one per
  document) keeps SC-001's 2-second target trivial to hit even before any dedicated performance work.
- **Alternatives considered**: one query for documents plus a second query for chunk counts, joined
  in application code (rejected — two round trips for what one `GROUP BY` already does correctly,
  and reintroduces an N+1 risk if a future maintainer naively loops per document instead); wrapping
  the array in an envelope object (rejected for now — see Decision above, deferred until pagination
  is actually added, not spec'd preemptively).

## Decision 3: Download route is `GET /documents/{id}/content`, returning raw bytes with headers

- **Decision**: `GET /documents/{id}/content` returns `200 OK` with `Content-Type` set to the
  document's stored `content_type` (`text/plain` or `application/pdf` — never anything else, since
  that column is `CHECK`-constrained at the schema level, feature 003), body set to the exact stored
  `content` bytes, and a `Content-Disposition: attachment; filename="..."` header carrying the
  document's original filename.
- **Rationale**: The `/content` segment (rather than treating `GET /documents/{id}` itself as the
  download) keeps `GET /documents/{id}` free for a future feature that might want to return the
  same summary shape as the list endpoint for one document — a metadata fetch and a raw content
  fetch are different response shapes (`application/json` vs. the original file's own content type)
  and deserve different, explicit routes rather than content-negotiation on one route. `/content`
  also mirrors the underlying storage directly: it is exactly `documents.content` (feature 003),
  making the route name and the column it serves obviously the same thing.
- **Alternatives considered**: `GET /documents/{id}` returning the raw file directly (rejected — as
  above, forecloses a future plain-metadata-by-id route without a breaking change); `GET
  /documents/{id}/download` (a reasonable equally-valid alternative — not chosen only because
  `/content` names what is actually returned, matching the schema, rather than naming the client's
  intended action with it).

## Decision 4: A malformed or nonexistent document id both resolve to the same `404 document_not_found`

- **Decision**: `{id}` is bound as `@PathVariable("id") String id` (not `UUID`), then parsed inside
  the controller with `UUID.fromString`. A `IllegalArgumentException` from a malformed id (not a
  valid UUID string) and a well-formed-but-nonexistent id both result in the identical `404
  document_not_found` response — the controller never distinguishes "not a UUID" from "a UUID that
  doesn't exist" in its response body.
- **Rationale**: Both cases mean the same thing to a caller: "there is no document you can retrieve
  at this id." Distinguishing them would mean exposing an internal fact (that ids happen to be
  UUIDs) as part of the contract, for a distinction that gives the caller no actionable difference —
  neither case is fixable by retrying, per spec Edge Cases ("the system MUST report clearly that no
  such document exists"). Binding as `String` also sidesteps Spring's default behavior for a
  `UUID`-typed `@PathVariable` (a raw `MethodArgumentTypeMismatchException`, a 400 with a
  framework-generated message shape inconsistent with this feature's `{error, message}` contract).
- **Alternatives considered**: binding `{id}` as `UUID` directly and letting Spring's
  `MethodArgumentTypeMismatchException` surface as `400 invalid_id` (rejected — a different status
  code for what is functionally the same "nothing to retrieve" outcome as a valid-but-nonexistent
  id, forcing callers to handle two codes for one situation); returning `400` for a malformed id and
  `404` only for a well-formed-but-missing one (rejected for the same reason — no caller-actionable
  difference, extra complexity for no value at this PoC's scale).

## Decision 5: A new `DocumentQueryRepository`, separate from the write-only `DocumentRepository`

- **Decision**: List and download reads live in a new `DocumentQueryRepository` class
  (`backend/.../ingestion/DocumentQueryRepository.java`), not as new methods on the existing
  `DocumentRepository`.
- **Rationale**: `DocumentRepository`'s own Javadoc frames it narrowly: "Writes a document and its
  full chunk set in exactly one transaction." Every existing method, field, and the class-level
  contract described there is about the write/transaction path FR-009 governs. Adding read methods
  to it would both dilute that documented contract and mix two different failure/transaction
  models in one class (writes need a `TransactionTemplate`; reads here are simple, non-transactional
  `SELECT`s). A second single-purpose repository class keeps both honestly named and matches this
  codebase's existing pattern of small, single-purpose collaborators (`TextExtractor`, `Chunker`,
  `EmbeddingClient` are each one job).
- **Alternatives considered**: adding `findAll()`/`findContentById()` directly to
  `DocumentRepository` (rejected — see rationale); a generic `DocumentDao` merging both
  responsibilities under one class and rewriting `DocumentRepository`'s Javadoc to match (rejected —
  unnecessary churn to a class feature 004 already shipped and tested, for no functional gain).

## Decision 6: Shared error vocabulary renamed from "ingestion" to "document" scope

- **Decision**: `IngestionErrorHandler` → `DocumentErrorHandler`; the shared `{error, message}` DTO
  `dto/IngestionErrorResponse` → `dto/DocumentErrorResponse`, with its Javadoc broadened to document
  all three endpoints' error codes (not just `POST /documents`'s). The write-path exception
  hierarchy — `IngestionException` (abstract base), `InvalidDocumentException` (→400），
  `IngestionProcessingException` (→503) — is **left unchanged**: those names and that Javadoc
  describe the ingestion pipeline's own two-category split (FR-011 of feature 004) precisely, and
  still do after this feature. A new `DocumentNotFoundException` (→404) is added as a sibling
  `RuntimeException`, not a subtype of `IngestionException` — `IngestionException`'s own Javadoc
  states it exists so a caller can pick between exactly its two documented subclasses; a third,
  unrelated status code does not belong forced into that hierarchy.
- **Rationale**: `IngestionErrorHandler`/`IngestionErrorResponse`'s existing Javadoc explicitly scopes
  itself to `POST /documents`'s 400/503 outcomes. Once this feature adds a 404 outcome flowing
  through the same `@RestControllerAdvice` and the same response shape, that Javadoc becomes
  inaccurate the moment it is left unchanged — this repository's own constitution (Spec-First
  principle) treats stale documentation as a defect, not as acceptable drift. Renaming these two
  specific classes (both already scoped to "the shared HTTP error surface for `/documents`," not to
  ingestion logic itself) and rewriting their Javadoc is the direct fix; every other class in the
  package genuinely is ingestion-specific and keeps its name.
- **Alternatives considered**: leaving both classes named `Ingestion*` and just adding the 404
  handler method with an inline comment explaining the scope creep (rejected — the class-level
  Javadoc would still misdescribe the class, and a future reader has no reason to open every method
  before trusting a class's own doc comment); a third, separate `@RestControllerAdvice` just for the
  new 404 case (rejected — two advice classes mapping errors for the same `/documents` resource is
  more moving parts than one rename, and risks the two ever disagreeing on response shape).

## Decision 7: No new dependency, no schema change

- **Decision**: Both endpoints are implemented entirely with what feature 001/003/004 already put on
  the classpath — `JdbcTemplate` for the two new read queries (Decision 2/3), `MockMvc` and the
  existing Testcontainers `pgvector/pgvector:pg18` setup for tests. `backend/pom.xml` is unchanged.
- **Rationale**: Reading `bytea`/`text`/`timestamptz`/an aggregate `count(*)` are all things plain
  JDBC already does with no driver-level gap (unlike feature 004's one true gap, the `vector` column
  type, which stays write-only and is never read back by this feature). No new library earns its
  keep for two `SELECT`-shaped endpoints.
- **Alternatives considered**: none seriously — this is the default absence of a decision, recorded
  for completeness so a future reader does not have to re-derive "did this feature need anything
  new?" from a diff.

## Decision 8: Two-tier test strategy — no `azure` tag needed

- **Decision**: unit-level coverage is folded into the contract test (there is no pure-function
  algorithmic logic here worth isolating the way `Chunker`/`TextExtractor` warranted their own unit
  tests in feature 004). A `MockMvc` contract test (stubbed `DocumentQueryRepository`, default suite)
  covers request/response shape, ordering, empty-list, and both 404 paths; a `@Tag("db")`
  integration test (Testcontainers, `verify-db` profile, reusing `DocumentIngestionIT`'s exact
  container/schema bring-up pattern) proves the real `LEFT JOIN`/`GROUP BY` query and a real
  byte-for-byte download against actual inserted rows.
- **Rationale**: Neither endpoint calls Azure OpenAI or any external provider — there is nothing for
  an `@Tag("azure")` test to prove here, unlike feature 004 where the embedding call was the one
  genuinely external dependency. Requiring a test tier that would always trivially pass (because the
  code path it would exercise doesn't exist in this feature) adds process weight with no coverage
  gained.
- **Alternatives considered**: a single test tier only (rejected — the `db`-tagged test is what
  actually proves the `LEFT JOIN`/`COUNT` query and byte-for-byte content round-trip against
  PostgreSQL/pgvector; a fully-stubbed contract test alone cannot catch a SQL mistake in the real
  query, exactly the reasoning feature 004's Decision 9 already established for its own db tier).

## Open questions

None.

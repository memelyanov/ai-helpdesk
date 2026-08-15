# Phase 0 Research: Document Deletion Endpoint

**Date**: 2026-08-16 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

Seven decisions. No `[NEEDS CLARIFICATION]` markers remain — the one ambiguity the spec had
(failure handling for an in-flight deletion, spec.md Clarifications Session 2026-08-16) was already
resolved before this document was written, so the research surface here is entirely about how the
new endpoint fits into feature 004/005's existing `ingestion` package and reuses feature 003's
schema-level cascade, not about new technology choices.

## Decision 1: The endpoint extends the existing `DocumentController` / `/documents` resource

- **Decision**: `DELETE /documents/{id}` is added as a new handler method on the existing
  `DocumentController` (`backend/.../ingestion/DocumentController.java`, feature 004, extended by
  feature 005), not a new controller class.
- **Rationale**: Same reasoning feature 005's Decision 1 already established for `GET /documents`
  and `GET /documents/{id}/content` — all four verbs now operate on one REST resource, `/documents`,
  and this codebase groups every verb for one resource in one controller. Nothing about deletion
  introduces a distinct bounded context that would justify a second controller.
- **Alternatives considered**: a separate `DocumentAdminController` or `DocumentDeletionController`
  (rejected — no functional reason to split a single-method addition into its own file; the
  resource, not the HTTP verb, defines the controller boundary in this codebase).

## Decision 2: Route is `DELETE /documents/{id}`, not `/documents/{id}/content`

- **Decision**: The deletion route targets the document resource itself (`/documents/{id}`), not
  the `/content` sub-resource feature 005's download endpoint uses.
- **Rationale**: Deletion removes the whole document (its record and every chunk derived from it,
  FR-002) — the entire resource, not just its raw byte content. `/content` exists specifically to
  separate "the document's metadata/identity" from "the document's raw bytes" (feature 005 Decision
  3); deletion operates on the former, so it belongs on the bare `{id}` route, matching the ordinary
  REST convention of `DELETE` on the resource's own URI.
- **Alternatives considered**: `DELETE /documents/{id}/content` (rejected — would misleadingly imply
  only the content is removed while metadata survives, which is never true here); a dedicated
  `POST /documents/{id}/delete` action route (rejected — this codebase has no precedent for
  action-suffixed routes, and `DELETE` is the standard HTTP verb for exactly this operation).

## Decision 3: A malformed or nonexistent id both resolve to the same `404 document_not_found`

- **Decision**: `{id}` is bound as `@PathVariable("id") String id` (not `UUID`), reusing
  `DocumentController`'s existing `parseId` helper (feature 005 Decision 4) to translate a malformed
  id into the identical `DocumentNotFoundException` a well-formed-but-nonexistent id already
  produces.
- **Rationale**: FR-005 states this explicitly, and it is the same distinction-free outcome feature
  005 already established for the download endpoint — reusing the exact same helper method (rather
  than re-implementing UUID parsing) keeps the two endpoints' malformed-id behavior guaranteed
  identical by construction, not by convention.
- **Alternatives considered**: none seriously — this is a direct reuse of an already-settled
  decision, not a new one; recorded for completeness.
- **Precondition**: this decision requires `DocumentController`'s existing `parseId` helper and
  `DocumentNotFoundException` class (both feature 005) to remain present and unchanged. This feature
  does not duplicate or reimplement either — the tasks.md breakdown MUST treat both as pre-existing
  dependencies to reuse, not as artifacts this feature owns or modifies.

## Decision 4: One `DELETE` SQL statement decides both "not found" and "deleted" from its row count

- **Decision**: `DocumentRepository.deleteById(UUID id)` issues one statement —
  `DELETE FROM documents WHERE id = ?` — and returns whether exactly one row was affected. Zero rows
  affected (the id never existed, or was already deleted) maps to `DocumentNotFoundException` (→
  `404`, FR-005/FR-008); one row affected maps to a successful `204 No Content` response (FR-006).
  No prior `SELECT ... WHERE id = ?` existence check runs first.
- **Rationale**: A single statement whose own row count answers "did anything exist to delete" is
  simpler and race-free by construction — a separate "check, then delete" sequence would leave a
  window where a concurrent second delete (or the schema's own cascade) could change the answer
  between the check and the act (spec.md Edge Cases: two near-simultaneous deletes for the same id).
  Reading the row count off the one statement that actually performs the mutation means there is
  nothing to race against.
- **Alternatives considered**: `SELECT` for existence, then `DELETE` (rejected — introduces exactly
  the race window the one-statement approach avoids, for no benefit: the row count from the `DELETE`
  itself is already the authoritative answer); a soft-delete flag checked first (rejected — FR-009
  rules out soft-delete entirely).

## Decision 5: Cascade to chunks and the FR-010 atomicity guarantee both come from the database, not new application code

- **Decision**: No explicit `TransactionTemplate` or multi-statement transaction wraps the delete —
  feature 003's `chunks.document_id REFERENCES documents(id) ON DELETE CASCADE` (FR-011) already
  makes chunk removal an inseparable part of the same single `DELETE` statement, and PostgreSQL
  guarantees a single DML statement (cascading actions included) either fully applies or fully rolls
  back. `deleteById` wraps that one `jdbcTemplate.update(...)` call in a `try`/`catch`, mirroring
  `DocumentRepository.save`'s existing pattern, and rethrows any failure as a new
  `DocumentDeletionException` (→ `503`) rather than letting a raw exception surface as an
  unmapped `500` — this is the FR-010/Clarifications-session guarantee: on failure, nothing is
  deleted (the statement's own atomicity ensures this structurally) and the caller sees an explicit,
  distinct failure outcome.
- **Rationale**: `DocumentRepository.save` needs an explicit `TransactionTemplate` because it issues
  *multiple* statements (one document insert, N chunk inserts) that must succeed or fail together —
  a guarantee the database does not give for free across statements. Deletion has no such multi
  statement problem: one `DELETE` is already atomic on its own, and the cascade is a database-level
  guarantee (feature 003), not something application code orchestrates. Adding a
  `TransactionTemplate` here would wrap an already-atomic operation in a second, redundant
  atomicity mechanism for no behavioral gain.
- **Alternatives considered**: explicit `TransactionTemplate` wrapping the single `DELETE` (rejected
  — no multi-statement sequence exists here for it to protect; it would be inert ceremony,
  inconsistent with this codebase's preference for the simplest mechanism that actually does the
  work, feature 001/003/004's plain-`JdbcTemplate` stance); deleting chunks explicitly in application
  code before the document row (rejected — reintroduces exactly the two-statement atomicity problem
  the schema's cascade already solves, and duplicates a guarantee feature 003 already made
  structural).
- **Precondition, not assumption**: this decision only holds if feature 003's
  `ON DELETE CASCADE` relationship is actually present in the deployed schema (spec.md Key Entities
  now states this as an explicit implementation precondition, not merely a background assumption) —
  if it were ever missing, `deleteById` would remove the `documents` row but leave orphaned `chunks`
  rows behind, silently violating FR-002 with no error raised (a foreign-key violation would only
  occur if the cascade were absent *and* replaced by a plain `RESTRICT`/`NO ACTION` default, which
  would instead surface as a `DocumentDeletionException`, not a silent orphan — either way, verifying
  the cascade is genuinely in place before implementation begins is what makes this decision safe).
- **Same-document read/delete race (spec.md Edge Cases)**: this decision's single-statement atomicity
  is also what resolves the case of a download (feature 005) racing a delete for the *same* document
  id, with no extra coordination needed — each of `DocumentQueryRepository.findContentById` (one
  `SELECT`) and `DocumentRepository.deleteById` (one `DELETE`) is already atomic on its own, so
  whichever statement's transaction commits first fully determines the outcome for the other; there
  is no intermediate state either could observe.

## Decision 6: A new sibling exception, `DocumentDeletionException` (→ `503`), not a reuse of `IngestionProcessingException`

- **Decision**: The unexpected-server-failure case (Decision 5, FR-010) is reported through a new
  `DocumentDeletionException extends RuntimeException` — a sibling of `IngestionException`, exactly
  like `DocumentNotFoundException` (feature 005 Decision 6), not a subtype of it and not a reuse of
  the existing `IngestionProcessingException`. `DocumentErrorHandler` gains one new
  `@ExceptionHandler` mapping it to `503 Service Unavailable` with a fixed `deletion_failed` error
  code.
- **Rationale**: `IngestionProcessingException`'s own Javadoc scopes it explicitly to "the ingestion
  pipeline's own two-category split" with two documented, fixed `errorCode` values
  (`provider_unconfigured`, `processing_failed`) — neither describes "an existing document's
  deletion failed." Reusing it here would silently broaden a class whose whole documented purpose is
  to describe the *write/ingest* pipeline's failure modes, exactly the kind of Javadoc-vs-reality
  drift feature 005's Decision 6 already treated as a defect worth a rename, not something to repeat
  by cramming a fourth, unrelated meaning into the same errorCode/exception pair.
- **Alternatives considered**: reusing `IngestionProcessingException` with a new `errorCode` value
  (rejected — the class's Javadoc would need rewriting to stop being ingestion-scoped, which is a
  larger and less honest change than adding one small, precisely-scoped sibling class); folding this
  case into `DocumentNotFoundException` (rejected — conflating "nothing to delete" with "something to
  delete but the delete itself failed" is exactly the ambiguity the Clarifications session ruled out:
  the two MUST be distinguishable to the caller).
- **Retry semantics** (spec.md FR-010): `deletion_failed` belongs to the same "input was valid,
  processing failed, a retry MAY succeed once the underlying condition clears" family
  `IngestionProcessingException`'s own Javadoc already documents for `503` — the two classes share
  that retry semantic even though they deliberately do not share a type hierarchy (this decision's
  whole point). A caller does not need a fourth documented `errorCode` behavior to know retrying is
  safe; `503`'s existing, already-established meaning in this codebase covers it.

## Decision 7: No new dependency, no schema change, test strategy mirrors feature 005's two tiers

- **Decision**: Implemented entirely with what is already on `backend/pom.xml`'s classpath — plain
  `JdbcTemplate` (Decision 4/5), no new library. Test coverage follows feature 005's Decision 8
  pattern exactly: a `MockMvc` contract test (stubbed `DocumentRepository`, default suite) covering
  `204` success, both `404` paths (malformed id, nonexistent id, and an already-deleted id per
  FR-008), and the `503` failure path; a `@Tag("db")` integration test (Testcontainers, `verify-db`
  profile, reusing `DocumentIngestionIT`/`DocumentQueryIT`'s exact container/schema bring-up)
  ingesting a real document, deleting it, and proving both that its chunks are actually gone
  (`SELECT count(*) FROM chunks WHERE document_id = ?` is `0`) and that a second delete of the same
  id now returns `404`. No `azure` tag needed — deletion never calls Azure OpenAI or any external
  provider.
- **Rationale**: A single `DELETE ... WHERE id = ?` statement plus a database-enforced cascade is not
  new technology; it needs no new dependency to prove, only real database round-trips, which the
  `db`-tagged tier already exists to provide. Mirroring feature 005's exact two-tier split keeps the
  test suite's shape predictable for whoever reads it next.
- **Alternatives considered**: none seriously for the "no new dependency" half — recorded for
  completeness. For testing, a single tier only was considered and rejected for the same reason
  feature 005's Decision 8 rejected it: a fully-stubbed contract test alone cannot prove the real
  cascade actually removes chunk rows from PostgreSQL.

## Open questions

None.

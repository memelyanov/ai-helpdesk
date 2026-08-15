# Phase 0 Research: Document & Vector Storage Schema

**Date**: 2026-08-15 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

Nine decisions, each resolving either a spec ambiguity the clarification session didn't need to
reach (naming, types, indexing) or a piece of Technical Context the plan template requires. No
`[NEEDS CLARIFICATION]` markers remain after this document.

## Decision 1: Schema delivered as a new container init script, not a migration tool

- **Decision**: add `db/init/02-documents-and-chunks.sql` alongside the existing
  `db/init/01-init-vector.sql`, following the same convention: plain idempotent DDL, executed by
  the Postgres image's `/docker-entrypoint-initdb.d/` mechanism on first startup against an empty
  data directory. No Flyway, no Liquibase.
- **Rationale**: `specs/001-project-scaffolding/research.md` Decision 6/7 already rejected a
  migration tool for this project — Flyway runs during application startup and would reintroduce
  the exact coupling FR-007 (backend boots with the database down) was designed to avoid, and no
  JPA/Hibernate is in the dependency tree to drive schema generation either way. This feature adds
  tables, not application behaviour; nothing here changes that reasoning; extending the same
  mechanism keeps one way of doing schema, not two.
- **Alternatives considered**: Flyway/Liquibase (rejected — reintroduces the startup coupling
  Decision 6 avoided, and is a new dependency for a PoC with one schema change so far); JPA entity
  classes with `ddl-auto=validate` (rejected for the same reason as Decision 7 — Hibernate
  validation opens a connection at boot).
- **Known limitation carried forward**: init scripts only run once, against an empty data
  directory. A developer who already has a running `db-data` volume from feature 001 will not see
  the new tables until `docker compose down -v`. This is the same "stale volume" trap 001's
  quickstart documents (FR-003's flip side) — this feature's quickstart repeats the warning rather
  than assuming it carries over.

## Decision 2: Original document content stored as `bytea`, not a large object or filesystem path

- **Decision**: `documents.content` is `BYTEA NOT NULL`, holding the complete uploaded file.
- **Rationale**: The spec's Assumptions section already settled the storage *location* (inside the
  same PostgreSQL database, not a separate filesystem/object store) to keep the PoC on one
  storage layer. `bytea` is the direct way to do that in Postgres: it round-trips arbitrary bytes
  exactly (required by FR-003's byte-for-byte guarantee), needs no streaming API the way large
  objects (`lo`) do, and is trivial to read/write from plain JDBC (`PreparedStatement.setBytes` /
  `ResultSet.getBytes`) with no extra dependency — consistent with Decision 7 of feature 001 (no
  JPA, plain JDBC).
- **Alternatives considered**: Postgres large objects / `lo` (rejected — separate access API,
  separate vacuum/permission model, no benefit at PoC file sizes); filesystem path stored in the
  row (rejected — reopens the "one database for vectors and metadata" question the spec already
  closed, and adds a second thing to keep in sync and back up).

## Decision 3: Vector column fixed at 1536 dimensions now

- **Decision**: `chunks.embedding` is `vector(1536)`.
- **Rationale**: `specs/001-project-scaffolding/data-model.md` deliberately deferred this exact
  column, because committing a dimension before an embedding deployment existed would have been a
  guess. That deployment is now fixed by the constitution's Technology Stack table and Principle V:
  the mandated embedding model is `text-embedding-3-small`, which produces 1536-dimension vectors.
  This feature is the first to create a `chunks` table, so it is the right place to close that
  deferral. Principle V also requires that ingestion and query share one embedding deployment —
  a single fixed-width column structurally enforces that; a variable-width column would let
  vectors from two different models coexist in a way similarity search cannot compare correctly.
- **Alternatives considered**: unconstrained `vector` (no dimension) (rejected — pgvector allows it,
  but it removes the one structural guard against mixing incompatible embeddings, which is exactly
  the failure mode Principle V calls out); deferring the column again until an ingestion feature
  exists (rejected — this *is* that feature for storage purposes, per the scope note in
  001's `data-model.md`: "the first tables arrive with ingestion").

## Decision 4: Chunk metadata columns named per the constitution, not the spec's shorthand

- **Decision**: the chunk metadata columns are `source_filename`, `page_number`, and `chunk_id` —
  the exact names given in the constitution's "Chunking & Embedding Strategy" section — rather than
  the spec's `filename` / `page` shorthand (itself carried over verbatim from `poc-concept.md`
  §5.1 and the user's original request).
- **Rationale**: The spec and `poc-concept.md` use `filename`/`page` as plain-language shorthand for
  "which document, which page" — appropriate for a requirements document, which the Spec-First
  principle keeps implementation-detail-free. The constitution is the binding technical contract
  and spells out the actual field names (`source_filename`, `page_number`, `chunk_id`) alongside a
  concrete rule: "exact-match columns for metadata filtering." That phrase rules out storing
  metadata only as a join target (`chunks.document_id → documents.filename`) — a similarity query
  needs to filter or display `source_filename`/`page_number` without a join, so both are stored
  directly on the `chunks` row, denormalized from `documents` at the time each chunk is written.
  `chunk_id` was already the same name in both places, so it is unaffected.
- **Alternatives considered**: `filename`/`page` as literal column names (rejected — contradicts
  the constitution's explicit field names, and invites drift the next time an ingestion feature
  writes to the table using the constitution's own vocabulary); metadata as a `jsonb` column
  (rejected — the constitution's "exact-match columns" language specifically asks for queryable
  columns, not a nested blob).

## Decision 5: `page_number` is nullable; NULL is the documented "no page" value

- **Decision**: `chunks.page_number` is `INTEGER`, nullable. `NULL` means "this chunk's source
  document has no page structure" (e.g. a `.txt` file). It is never coerced to `0` or `-1`.
- **Rationale**: FR-008 requires an explicit, documented convention rather than an ambiguous value.
  `NULL` already means "absent" everywhere else in SQL and composes correctly with ordinary
  equality filtering (`page_number = 3` never accidentally matches a pageless chunk); a sentinel
  integer would require every future reader of this column to know the sentinel and remember to
  exclude it.
- **Alternatives considered**: sentinel `0` (rejected — collides with a real first page in
  1-indexed schemes and is easy to forget to special-case); sentinel `-1` (rejected — same problem,
  arbitrary rather than self-describing); separate boolean `has_page` column (rejected — one
  nullable column already carries this information without a second column that must stay in sync).

## Decision 6: `chunk_id` is a per-document integer; global uniqueness comes from the row's own key

- **Decision**: `chunks.chunk_id` is `INTEGER NOT NULL`, a sequence scoped to its document
  (0, 1, 2, … in chunking order). Uniqueness is enforced by `UNIQUE (document_id, chunk_id)`. The
  table's own primary key (`chunks.id`, `BIGINT GENERATED ALWAYS AS IDENTITY`) is the value that is
  globally unique across the corpus and is what any internal reference to "this exact chunk row"
  should use.
- **Rationale**: Directly implements the FR-012 clarification answer (per-document scope, citation
  use like "chunk 3 of travel-policy.pdf"). Using a plain, small integer keeps citation text
  readable, which a UUID would not. `documents.id` is a `UUID` (Decision 7) because it is a public,
  externally-addressed identifier used in a download URL; `chunks.id` has no equivalent external
  exposure requirement in this feature's scope, so the simpler, index-friendlier identity column is
  used instead — a distinction, not an inconsistency.
- **Alternatives considered**: globally unique `chunk_id` (e.g. UUID or a corpus-wide counter)
  (rejected by the clarification answer — it was the explicitly declined option); reusing `chunks.id`
  as `chunk_id` directly, i.e. one identity column instead of two (rejected — collapses a
  citation-facing per-document sequence and an internal surrogate key into one column, which
  forecloses the "chunk 3 of X" citation format the spec's Independent Test for User Story 2
  anticipates).

## Decision 7: `documents.id` is a `UUID`, generated by the database

- **Decision**: `documents.id UUID PRIMARY KEY DEFAULT gen_random_uuid()`. `gen_random_uuid()` is
  built into PostgreSQL core as of version 13 (this project runs PostgreSQL 18), so no extension
  beyond `vector` (already enabled by feature 001) is required.
- **Rationale**: FR-002 requires a stable identifier "distinct from its filename." A UUID is the
  conventional shape for a resource identifier that will appear in a download URL/path: it does not
  reveal upload order or corpus size the way a sequential integer would, and generating it in the
  database (rather than the application layer) keeps identifier assignment atomic with the insert,
  with no coordination needed once ingestion code exists.
- **Alternatives considered**: `BIGINT GENERATED ALWAYS AS IDENTITY` (rejected — simpler, but leaks
  ordering/volume information through a public identifier, a needless downgrade even though
  authentication is out of scope for the PoC); application-generated UUID (rejected — no
  application code writes to this table yet in this feature; database-generated keeps the schema
  independently testable by raw SQL/JDBC without needing a UUID library on the caller's side).

## Decision 8: No approximate-nearest-neighbor index yet; exact search via the `<=>` operator

- **Decision**: `chunks.embedding` gets no `ivfflat`/`hnsw` index in this feature. Similarity search
  queries use `ORDER BY embedding <=> :query_vector LIMIT :k` (cosine distance, per the
  constitution's Query Pipeline section), which pgvector executes as an exact sequential scan when
  no ANN index exists.
- **Rationale**: The PoC corpus is 16 documents (~107k characters); at the chunk sizes the
  constitution mandates (500–1000 tokens), that is on the order of a few hundred chunk rows —
  small enough that an exact scan is fast and, unlike an ANN index, gives exact top-K results with
  zero recall loss. Since Principle VII's ≥80% retrieval accuracy bar is measured against exactly
  this corpus, spending index-approximation error against it would be working against the PoC's own
  success criterion for no measured benefit.
- **Alternatives considered**: `hnsw (embedding vector_cosine_ops)` index (rejected for now — the
  right choice once the corpus grows past what a sequential scan can serve in the ~5s end-to-end
  budget (§7, poc-concept.md), but premature at PoC scale, and HNSW's recall/build-time tuning is a
  question this feature has no data to answer yet); `ivfflat` (rejected for the same reason, and it
  additionally needs a representative data sample to choose `lists` well, which does not exist
  before ingestion runs). Documented here so a follow-up feature can add the index without
  re-deriving the reasoning.

## Decision 9: Schema-verification tests use Testcontainers, gated behind a new opt-in Maven profile

- **Decision**: add integration tests (JUnit 5 + `testcontainers-postgresql`, image
  `pgvector/pgvector:pg18` to match `docker-compose.yml`) that apply
  `db/init/02-documents-and-chunks.sql` to a disposable container and assert the schema's guaranteed
  behaviour: FK enforcement, cascade delete, the `UNIQUE (document_id, chunk_id)` constraint, and
  round-tripping a `vector(1536)` value. These tests are tagged (`@Tag("db")`) and excluded from the
  default `mvn test` run via `maven-surefire-plugin`'s `excludedGroups`, the same mechanism already
  used for the `azure` tag (`specs/001-project-scaffolding/research.md` Decision 5/11); a new
  `verify-db` Maven profile re-includes them, mirroring the existing `verify-ai` profile.
- **Rationale**: `specs/001-project-scaffolding/research.md` Decision 11 deliberately kept the
  default suite free of any Docker dependency (clean-checkout guarantee, SC-003/SC-005/SC-009 of
  that feature) but named its own exception in advance: *"the ingestion feature, where real SQL
  against real pgvector is the thing under test, is the right moment to accept a Docker
  dependency."* This feature is that moment for the schema itself — there is no meaningful way to
  TDD a `CASCADE` constraint or a `vector(1536)` column against a mock. Gating behind an opt-in
  profile, rather than adding it to the default suite, preserves the specific guarantee 001 built
  (a clean checkout with no Docker daemon running still gets a green `mvn test`) while still
  satisfying constitution Principle II (TDD, with integration tests mandatory "at appropriate
  layers") for the layer that actually needs a real database.
- **Alternatives considered**: adding Testcontainers to the default suite (rejected — silently
  breaks 001's SC-003/SC-005/SC-009, forcing every future default `mvn test` run to require a
  live Docker daemon); testing purely against the already-running `docker-compose` database instead
  of Testcontainers (rejected — couples the test run to a developer having remembered `docker
  compose up`, and to that database's current state, where Testcontainers gives each run a fresh,
  disposable instance built from the exact init scripts under test).

## Open questions

None. All requirements in `spec.md` are resolved; the three clarification answers (cascade delete,
per-document `chunk_id`, independent documents on re-upload) are reflected directly in the FR text
and carried into Decisions 1–9 above.

One item is flagged for awareness, not blocking: Decision 8's "no ANN index yet" should be revisited
the first time the corpus grows meaningfully beyond the PoC's 16 documents, or the first time
end-to-end query latency is measured against a real corpus rather than assumed.

## Addendum: four requirements added during checklist review

`checklists/schema.md` surfaced three concrete inconsistencies and several gaps in `spec.md`
against the constitution and against itself; the spec was corrected directly rather than requiring
new design decisions here, since each was already settled by decisions above or by the
constitution's own binding text:

- **FR-014** (deletion is a supported capability) — FR-011 already specified cascade *behavior* for
  a deletion, but no requirement had granted deletion as an operation. No new decision: `DELETE FROM
  documents WHERE id = :id` is ordinary SQL: see [data-model.md](data-model.md).
- **FR-015** (reject non-`.txt`/`.pdf` uploads) — already implemented by the `content_type CHECK`
  constraint in [data-model.md](data-model.md); the FR was missing, the schema wasn't.
- **FR-016** (one embedding dimensionality for the whole corpus) — already implied by Decision 3's
  fixed `vector(1536)` column; promoted to an explicit FR because the constitution's Principle V
  consistency requirement had no corresponding FR to point to.
- **FR-017** (atomic per-document chunk batch write) — required by the constitution's Ingestion
  Pipeline section ("all chunks from one document succeed or all fail") but previously reflected
  only as a narrower per-chunk guarantee in FR-006. Unlike FR-014–016, this one is **not** enforced
  by any column or constraint — it is a transaction-boundary obligation on the future ingestion
  writer, documented explicitly in
  [contracts/chunk-schema.md](contracts/chunk-schema.md) so it isn't rediscovered later.

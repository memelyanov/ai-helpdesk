# Quickstart & Validation: Document & Vector Storage Schema

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-08-15

How to bring the schema up and prove it satisfies the spec. This is a validation guide, not an
implementation guide — the DDL and tests that make these steps pass are produced by
`/speckit-tasks` and the implementation phase. Commands are PowerShell (this project's primary
shell); the bash equivalent differs only in `mvnw.cmd` → `mvnw` where noted, per
`specs/001-project-scaffolding/quickstart.md`'s shell conventions.

## Prerequisites

Same as `specs/001-project-scaffolding/quickstart.md`, plus: this feature's schema-verification
tests need a **running Docker daemon** at test time (research Decision 9) — Docker being merely
*installed* is not enough, unlike the rest of the default backend suite.

## Stale-volume warning (read this before `docker compose up`)

If a database volume from feature 001 already exists on this machine, the new tables in
`db/init/02-documents-and-chunks.sql` will **not** appear on a plain `docker compose up` — init
scripts only run against an empty data directory (research Decision 1, same trap
001's quickstart documents). Force a fresh volume first:

```powershell
docker compose down -v
docker compose up -d
```

## Step 1 — Confirm both tables exist

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "\d documents"
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "\d chunks"
```

Expected: `documents` shows `id, filename, content_type, content, uploaded_at`; `chunks` shows
`id, document_id, chunk_id, source_filename, page_number, text, embedding` plus the
`chunks_document_id_chunk_id_key` unique constraint and a foreign key to `documents`.

## Step 2 — Run the schema-verification test suite

These tests are Docker-gated and excluded from the default suite (research Decision 9):

```powershell
backend\mvnw.cmd test -Pverify-db
```

```bash
backend/mvnw test -Pverify-db
```

Expected: green, and the default `backend\mvnw.cmd test` (no profile) stays green too, with no
Docker daemon required for that run — confirming this feature did not weaken feature 001's
clean-checkout guarantee.

## Step 3 — Validate each user story manually (optional, via `psql`)

### User Story 1 — upload and retrieve the original document

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "
INSERT INTO documents (filename, content_type, content)
VALUES ('sample.txt', 'text/plain', convert_to('hello world', 'UTF8'))
RETURNING id;"
```

Copy the returned `id`, then:

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "
SELECT convert_from(content, 'UTF8') AS content, filename FROM documents WHERE id = '<paste-id>';"
```

Expected: `content` reads back exactly `hello world` (SC-001).

### User Story 2 — store a chunk with vector, text, and metadata

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "
INSERT INTO chunks (document_id, chunk_id, source_filename, page_number, text, embedding)
VALUES ('<paste-id>', 0, 'sample.txt', NULL, 'hello world', array_fill(0.1, ARRAY[1536])::vector);
SELECT chunk_id, source_filename, page_number, text FROM chunks WHERE document_id = '<paste-id>';"
```

Expected: one row, `page_number` is `NULL` (the documented "no page" convention for a `.txt`
source, FR-008), `chunk_id = 0`.

### User Story 3 — trace a search hit back to a downloadable document

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "
SELECT document_id, chunk_id, source_filename, embedding <=> array_fill(0.1, ARRAY[1536])::vector AS distance
FROM chunks
ORDER BY distance
LIMIT 4;"
```

Expected: the result includes `document_id`, matching the id from User Story 1 — use it directly
against Step 3's User Story 1 query to fetch the whole document (per
`contracts/similarity-search-contract.md`).

### Edge case — cascade delete (FR-011/FR-014)

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "
DELETE FROM documents WHERE id = '<paste-id>';
SELECT count(*) FROM chunks WHERE document_id = '<paste-id>';"
```

Expected: `count = 0` — the chunk was removed along with its document, with no separate delete
statement against `chunks`.

### Edge case — reject a disallowed file type (FR-015)

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "
INSERT INTO documents (filename, content_type, content)
VALUES ('sample.docx', 'application/msword', convert_to('x', 'UTF8'));"
```

Expected: the statement fails with a `CHECK` constraint violation on `content_type` — no row is
created.

### Edge case — reject a wrong-dimension embedding (FR-016)

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "
INSERT INTO chunks (document_id, chunk_id, source_filename, page_number, text, embedding)
VALUES ('<any-existing-document-id>', 0, 'sample.txt', NULL, 'x', array_fill(0.1, ARRAY[3])::vector);"
```

Expected: the statement fails — `3` dimensions does not match the column's `vector(1536)` type.

## Step 4 — Clean up

```powershell
docker compose down -v
```

Removing the `-v` (volume) leaves the schema in place for the next session; including it (as
above) returns the database to a clean slate for the next full validation pass.

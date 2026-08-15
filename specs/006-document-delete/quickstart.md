# Quickstart & Validation: Document Deletion Endpoint

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-08-16

How to bring the endpoint up and prove it satisfies the spec. This is a validation guide, not an
implementation guide — the Java code and tests that make these steps pass are produced by
`/speckit-tasks` and the implementation phase. Commands are PowerShell (this project's primary
shell), per `specs/001-project-scaffolding/quickstart.md`'s shell conventions.

## Prerequisites

Same as `specs/005-document-listing-download/quickstart.md` (database up, backend running via
`backend\mvnw.cmd spring-boot:run`), plus:

- At least one document ingested (`POST /documents`, feature 004) to delete — Step 3 below ingests
  one if needed.
- No Azure OpenAI configuration is needed for anything in this guide — deletion never calls the
  embedding provider (research Decision 7).

## Step 1 — Run the default test suite (no Docker, no Azure credentials required)

```powershell
backend\mvnw.cmd test
```

Expected: green. This covers the `MockMvc` contract test for `DELETE /documents/{id}` (stubbed
`DocumentRepository`) — no live database is touched.

## Step 2 — Run the database-backed integration test

```powershell
backend\mvnw.cmd test -Pverify-db
```

Expected: green, using the same Testcontainers `pgvector/pgvector:pg18` setup
`DocumentIngestionIT`/`DocumentQueryIT` already established — proves a real `DELETE` against
PostgreSQL, that the database's own `ON DELETE CASCADE` actually removes the document's `chunks`
rows, and that a second delete of the same id returns `404`.

## Step 3 — Ingest a document to delete

```powershell
$response = curl.exe -s -X POST http://localhost:8080/documents `
  -F "file=@sample-data/documents/expense-tool-faq.txt;type=text/plain" | ConvertFrom-Json
$documentId = $response.documentId
$documentId
```

Note the `documentId` — used below.

## Step 4 — User Story 1: delete the document and confirm it's gone everywhere

```powershell
curl.exe -i -X DELETE http://localhost:8080/documents/$documentId
```

Expected: `204 No Content`, no body.

```powershell
curl.exe -s http://localhost:8080/documents | ConvertFrom-Json | Where-Object documentId -eq $documentId
```

Expected: no output — the document no longer appears in the list (FR-003, SC-001).

```powershell
curl.exe -i http://localhost:8080/documents/$documentId/content
```

Expected: `404 Not Found`, `{ "error": "document_not_found", ... }` — downloading the just-deleted
document behaves exactly as if the id had never been issued (FR-003).

### Edge case — a zero-chunk document is deletable the same way

```powershell
New-Item -ItemType File -Path blank.txt -Force | Out-Null
$blank = curl.exe -s -X POST http://localhost:8080/documents -F "file=@blank.txt;type=text/plain" | ConvertFrom-Json
curl.exe -i -X DELETE http://localhost:8080/documents/$($blank.documentId)
```

Expected: `204 No Content` — chunk count has no bearing on deletability (FR-004).

## Step 5 — User Story 2: deletion requests that can't succeed

### Edge case — a nonexistent id

```powershell
curl.exe -i -X DELETE http://localhost:8080/documents/00000000-0000-0000-0000-000000000000
```

Expected: `404 Not Found`, `{ "error": "document_not_found", ... }` (SC-003).

### Edge case — a malformed id

```powershell
curl.exe -i -X DELETE http://localhost:8080/documents/not-a-uuid
```

Expected: `404 Not Found`, `{ "error": "document_not_found", ... }` — the same outcome as a
well-formed-but-nonexistent id (research Decision 3), never a `400` or an unhandled server error.

### Edge case — deleting the same id twice

```powershell
curl.exe -i -X DELETE http://localhost:8080/documents/$documentId
curl.exe -i -X DELETE http://localhost:8080/documents/$documentId
```

Expected: the first call (if not already run in Step 4) returns `204`; the second returns `404
document_not_found` — the same outcome as any other nonexistent id, never a special
"already-deleted" error (FR-008).

## Step 6 — SC-002: chunks are actually gone (not just the document)

Only observable against the real database, since `GET /documents` only reports a count, not raw
chunk rows:

```powershell
docker compose exec postgres psql -U aihelpdesk -d aihelpdesk -c `
  "SELECT count(*) FROM chunks WHERE document_id = '$documentId';"
```

Expected: `0` — every chunk that referenced the deleted document is gone (SC-002). (Covered
automatically by the `db`-tagged integration test in Step 2; this step is for manual spot-checking.)

## Step 7 — SC-004: deleting one document leaves every other document untouched

```powershell
$before = curl.exe -s http://localhost:8080/documents | ConvertFrom-Json
$survivor = $before | Select-Object -First 1
curl.exe -X POST http://localhost:8080/documents -F "file=@sample-data/documents/security-policy.pdf;type=application/pdf" | Out-Null
$toDelete = (curl.exe -s http://localhost:8080/documents | ConvertFrom-Json | Where-Object filename -eq "security-policy.pdf")[0]
curl.exe -X DELETE http://localhost:8080/documents/$($toDelete.documentId)
curl.exe -s http://localhost:8080/documents/$($survivor.documentId)/content -o survivor-check.tmp
```

Expected: the survivor document (untouched by the delete) still downloads successfully with
`200 OK` — deleting one document has no effect on any other (SC-004).

## Step 8 — SC-005: deletion completes and is confirmed gone within 2 seconds

```powershell
$toTime = curl.exe -s -X POST http://localhost:8080/documents -F "file=@sample-data/documents/expense-tool-faq.txt;type=text/plain" | ConvertFrom-Json
Measure-Command {
  curl.exe -s -X DELETE http://localhost:8080/documents/$($toTime.documentId) | Out-Null
  curl.exe -s -o $null -w "%{http_code}" http://localhost:8080/documents/$($toTime.documentId)/content | Out-Null
}
```

Expected: total elapsed time under 2 seconds (SC-005).

## Step 9 — Clean up

```powershell
Remove-Item blank.txt, survivor-check.tmp -ErrorAction SilentlyContinue
docker compose down -v
```

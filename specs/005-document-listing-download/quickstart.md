# Quickstart & Validation: Document Listing and Download Endpoints

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-08-15

How to bring the endpoints up and prove they satisfy the spec. This is a validation guide, not an
implementation guide — the Java code and tests that make these steps pass are produced by
`/speckit-tasks` and the implementation phase. Commands are PowerShell (this project's primary
shell), per `specs/001-project-scaffolding/quickstart.md`'s shell conventions.

## Prerequisites

Same as `specs/004-document-ingestion-endpoint/quickstart.md` (database up, backend running via
`backend\mvnw.cmd spring-boot:run`), plus:

- At least one document already ingested (`POST /documents`, feature 004) — these two endpoints have
  nothing to list or download against an empty corpus otherwise. Step 3 below re-ingests the sample
  corpus if needed.
- No Azure OpenAI configuration is needed for anything in this guide — neither endpoint calls the
  embedding provider (research Decision 8).

## Step 1 — Run the default test suite (no Docker, no Azure credentials required)

```powershell
backend\mvnw.cmd test
```

Expected: green. This covers the `MockMvc` contract test for `GET /documents` and
`GET /documents/{id}/content` (stubbed `DocumentQueryRepository`) — no live database is touched.

## Step 2 — Run the database-backed integration test

```powershell
backend\mvnw.cmd test -Pverify-db
```

Expected: green, using the same Testcontainers `pgvector/pgvector:pg18` setup
`DocumentIngestionIT` (feature 004) already established — proves the real `LEFT JOIN`/`GROUP BY`
list query and a real byte-for-byte download against actual inserted rows.

## Step 3 — Ingest a couple of sample documents (if the corpus is empty)

```powershell
curl.exe -X POST http://localhost:8080/documents `
  -F "file=@sample-data/documents/expense-tool-faq.txt;type=text/plain"
curl.exe -X POST http://localhost:8080/documents `
  -F "file=@sample-data/documents/security-policy.pdf;type=application/pdf"
```

Note the two `documentId` values returned — used below.

## Step 4 — User Story 1: list every ingested document

```powershell
curl.exe -s http://localhost:8080/documents | ConvertFrom-Json | Format-Table
```

Expected: a JSON array with one entry per ingested document, each showing `documentId`, `filename`,
`contentType`, `uploadedAt`, and `chunkCount`; the two documents from Step 3 appear with
`chunkCount` greater than `0`, most-recently-uploaded first.

### Edge case — an empty corpus returns an empty list, not an error

Against a freshly initialized database (before Step 3, or `docker compose down -v` + restart):

```powershell
curl.exe -i http://localhost:8080/documents
```

Expected: `200 OK`, body `[]`.

### Edge case — a zero-chunk document still appears in the list

```powershell
New-Item -ItemType File -Path blank.txt -Force | Out-Null
curl.exe -X POST http://localhost:8080/documents -F "file=@blank.txt;type=text/plain"
curl.exe -s http://localhost:8080/documents | ConvertFrom-Json | Where-Object filename -eq "blank.txt"
```

Expected: the `blank.txt` entry appears with `chunkCount: 0` — present, not filtered out.

## Step 5 — User Story 2: download a document's original file

```powershell
curl.exe -o downloaded-security-policy.pdf http://localhost:8080/documents/<paste-pdf-documentId>/content
Compare-Object (Get-Content sample-data/documents/security-policy.pdf -Raw) (Get-Content downloaded-security-policy.pdf -Raw)
```

Expected: the download completes, and the comparison shows no differences — byte-for-byte identical
to the originally uploaded file (SC-002).

```powershell
curl.exe -i -X GET http://localhost:8080/documents/<paste-pdf-documentId>/content 2>&1 | Select-String "Content-Type|Content-Disposition"
```

Expected: `Content-Type: application/pdf` and a `Content-Disposition: attachment;
filename="security-policy.pdf"` header.

### Edge case — downloading a nonexistent document id

```powershell
curl.exe -i http://localhost:8080/documents/00000000-0000-0000-0000-000000000000/content
```

Expected: `404 Not Found`, `{ "error": "document_not_found", "message": "..." }` (SC-003).

### Edge case — a malformed document id

```powershell
curl.exe -i http://localhost:8080/documents/not-a-uuid/content
```

Expected: `404 Not Found`, `{ "error": "document_not_found", ... }` — the same outcome as a
well-formed-but-nonexistent id (research Decision 4), never a `400` or an unhandled server error.

### Edge case — downloading a zero-chunk document still works

```powershell
$blankId = (curl.exe -s http://localhost:8080/documents | ConvertFrom-Json | Where-Object filename -eq "blank.txt").documentId
curl.exe -i http://localhost:8080/documents/$blankId/content
```

Expected: `200 OK` with the (empty or near-empty) original file content — chunk count has no bearing
on download availability (FR-011).

## Step 6 — SC-001: listing performance against the full sample corpus

```powershell
Get-ChildItem sample-data/documents -File | ForEach-Object {
  $type = if ($_.Extension -eq ".pdf") { "application/pdf" } else { "text/plain" }
  curl.exe -s -X POST http://localhost:8080/documents -F "file=@$($_.FullName);type=$type" | Out-Null
}
Measure-Command { curl.exe -s http://localhost:8080/documents | Out-Null }
```

Expected: the full 16-document sample corpus is ingested, and the subsequent list call completes in
under 2 seconds (SC-001).

## Step 7 — Clean up

```powershell
docker compose down -v
```

# Quickstart & Validation: Document Ingestion Endpoint

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-08-15

How to bring the endpoint up and prove it satisfies the spec. This is a validation guide, not an
implementation guide — the Java code and tests that make these steps pass are produced by
`/speckit-tasks` and the implementation phase. Commands are PowerShell (this project's primary
shell); the bash equivalent differs only in `mvnw.cmd` → `mvnw`, per
`specs/001-project-scaffolding/quickstart.md`'s shell conventions.

## Prerequisites

Same as `specs/003-document-vector-schema/quickstart.md` (database up, including its stale-volume
warning if this is the first time `documents`/`chunks` exist on this machine), plus:

- **Precondition**: confirm the deployed schema still matches
  `specs/003-document-vector-schema/data-model.md` before starting (spec.md's Key Entities section
  requires this): `docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "\d documents" -c "\d chunks"`
  and compare against `specs/003-document-vector-schema/quickstart.md` Step 1's expected output. If
  it doesn't match, fix the schema drift before proceeding — this feature assumes it, it does not
  re-verify it at runtime.
- A running backend: `backend\mvnw.cmd spring-boot:run` (or the IDE equivalent).
- For the parts of this guide marked **"requires live Azure"**: `AZURE_OPEN_AI_KEY`,
  `AZURE_OPEN_AI_ENDPOINT`, and `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` set in the environment.
  Everything else in this guide (rejection cases, the default test suite) works with none of them
  set — that is the point of research Decision 6.

## Step 1 — Run the default test suite (no Docker, no Azure credentials required)

```powershell
backend\mvnw.cmd test
```

Expected: green. This covers the chunker/text-extractor unit tests and the `MockMvc` contract test
for `POST /documents` (stubbed embedding model and repository) — none of it touches a real database
or Azure (constitution Principle II).

## Step 2 — Run the database-backed pipeline test

```powershell
backend\mvnw.cmd test -Pverify-db
```

Expected: green, using a Testcontainers `pgvector/pgvector:pg18` instance and a **stubbed**
embedding model (research Decision 9) — proves the real atomicity/transaction behavior (User
Story 3) without needing Azure credentials.

## Step 3 — Upload a real sample document (requires the backend running; live Azure recommended but not required for the rejection cases below)

### User Story 1 — successful ingestion of a `.txt` file

```powershell
curl.exe -X POST http://localhost:8080/documents `
  -F "file=@sample-data/documents/expense-tool-faq.txt;type=text/plain"
```

Expected (with Azure configured): `201 Created`,
`{ "documentId": "<uuid>", "chunkCount": <n > 0> }`. Confirm the document exists and its content is
byte-identical to the source file:

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "
SELECT filename, content_type, octet_length(content) FROM documents WHERE id = '<paste-documentId>';"
```

### User Story 1 — successful ingestion of a `.pdf` file, with page numbers

```powershell
curl.exe -X POST http://localhost:8080/documents `
  -F "file=@sample-data/documents/security-policy.pdf;type=application/pdf"
```

Expected: `201 Created` as above. Confirm chunks carry page numbers:

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "
SELECT chunk_id, page_number, left(text, 40) FROM chunks WHERE document_id = '<paste-documentId>' ORDER BY chunk_id;"
```

Expected: `page_number` is populated (not `NULL`) for every row, ascending or repeating as chunks
span or stay within a page.

### User Story 2 — reject an unsupported file type

```powershell
curl.exe -i -X POST http://localhost:8080/documents `
  -F "file=@README.md;type=text/markdown"
```

Expected: `400 Bad Request`, `{ "error": "unsupported_type", "message": "..." }`. Confirm nothing
was stored: the response body's absence of a `documentId` is sufficient; optionally,
`SELECT count(*) FROM documents;` before and after should be unchanged.

### User Story 2 — reject an empty file

```powershell
New-Item -ItemType File -Path empty.txt -Force | Out-Null
curl.exe -i -X POST http://localhost:8080/documents -F "file=@empty.txt;type=text/plain"
```

Expected: `400 Bad Request`, `{ "error": "invalid_file", ... }`.

### Edge case — validation order (oversized AND unsupported type)

```powershell
$oversized = New-Object byte[] (21*1MB)
[IO.File]::WriteAllBytes("oversized.exe", $oversized)
curl.exe -i -X POST http://localhost:8080/documents -F "file=@oversized.exe;type=application/octet-stream"
```

Expected: `400 Bad Request`, `{ "error": "invalid_file", ... }` — **not** `unsupported_type`,
confirming the size check runs first (FR-003, spec Edge Cases).

### Edge case — malformed multipart request (no `file` part)

```powershell
curl.exe -i -X POST http://localhost:8080/documents -F "notes=hello"
```

Expected: `400 Bad Request`, `{ "error": "invalid_file", ... }`.

### Edge case — undecodable `.txt` encoding

```powershell
[IO.File]::WriteAllBytes("bad-encoding.txt", @(0xFF, 0xFE, 0x00, 0xD8, 0x00, 0x00))
curl.exe -i -X POST http://localhost:8080/documents -F "file=@bad-encoding.txt;type=text/plain"
```

Expected: `400 Bad Request`, `{ "error": "unparseable", ... }` — not silently stored as garbled text.

### SC-006 — a maximum-size file resolves within 60 seconds

Using the 20 MB (or a validly-sized large) `.txt`/`.pdf` file from a real corpus, time the request:

```powershell
Measure-Command { curl.exe -s -o $null -X POST http://localhost:8080/documents -F "file=@<large-file>;type=text/plain" }
```

Expected: the command completes (success or failure response) in under 60 seconds — never hangs
indefinitely.

### User Story 3 — no partial results when the embedding step fails

Requires simulating a downstream failure — the `DocumentIngestionIT` in the `db`-tagged suite
(Step 2) already exercises this deterministically via a stubbed failing embedding model; the manual
equivalent is to temporarily point `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` at a deployment name
that does not exist, restart the backend, and repeat the `.txt` upload from above.

Expected: `503 Service Unavailable`, `{ "error": "processing_failed", ... }`, and:

```powershell
docker compose exec db psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c "
SELECT count(*) FROM documents WHERE filename = 'expense-tool-faq.txt';"
```

returns the same count as before the attempt — no row was left behind.

### Edge case — provider not configured

With all three `AZURE_OPEN_AI_*` embedding-relevant variables unset, restart the backend and repeat
any successful-case upload above.

Expected: `503 Service Unavailable`, `{ "error": "provider_unconfigured", ... }`, returned
immediately (no delay from an attempted network call — research Decision 6).

## Step 4 — Full corpus ingestion (SC-004)

```powershell
Get-ChildItem sample-data/documents -File | ForEach-Object {
  $type = if ($_.Extension -eq ".pdf") { "application/pdf" } else { "text/plain" }
  curl.exe -s -X POST http://localhost:8080/documents -F "file=@$($_.FullName);type=$type"
}
```

Expected: all 16 sample documents return `201 Created`. This establishes the corpus the evaluation
set (`sample-data/evaluation-questions.csv`) depends on for the future `/chat` feature.

## Step 5 — Clean up

```powershell
docker compose down -v
```

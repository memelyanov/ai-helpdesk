# Quickstart & Validation: Chat Endpoint (Retrieve → Augment → Generate)

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-08-16

How to bring the endpoint up and prove it satisfies the spec. This is a validation guide, not an
implementation guide — the Java code and tests that make these steps pass are produced by
`/speckit-tasks` and the implementation phase. Commands are PowerShell (this project's primary
shell), per `specs/001-project-scaffolding/quickstart.md`'s shell conventions.

## Prerequisites

Same as `specs/006-document-delete/quickstart.md` (database up, backend running via
`backend\mvnw.cmd spring-boot:run`), plus:

- Steps 1–2 below (the default and `verify-db` test suites) need **no** Azure OpenAI configuration —
  both stub the AI provider (research Decision 9).
- Steps 4 onward (real `curl` calls against a running backend) need a **fully configured** Azure
  OpenAI environment (`AZURE_OPEN_AI_KEY`, `AZURE_OPEN_AI_ENDPOINT`, `AZURE_OPEN_AI_DEPLOYMENT_NAME`,
  `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME`) — chat calls both the embedding and chat deployments for
  every question, and ingestion (needed to have anything to ask about) already requires the embedding
  deployment (feature 004).
- At least one ingested document to ask about — Step 3 ingests the sample corpus if needed.

## Step 1 — Run the default test suite (no Docker, no Azure credentials required)

```powershell
backend\mvnw.cmd test
```

Expected: green. Covers `POST /chat` request validation and response-shape mapping via `MockMvc`
against stubbed collaborators — no live database, no live Azure call.

## Step 2 — Run the database-backed integration test

```powershell
backend\mvnw.cmd test -Pverify-db
```

Expected: green, using the same Testcontainers `pgvector/pgvector:pg18` setup
`DocumentIngestionIT`/`DocumentQueryIT`/`DocumentDeleteIT` already established — proves the real
pgvector similarity query ranks hand-seeded known vectors correctly, applies the 0.5 threshold, and
respects a `documentIds` filter, with the chat completion step stubbed.

## Step 3 — Ingest the sample corpus (needs a configured Azure OpenAI environment)

```powershell
Get-ChildItem sample-data/documents | ForEach-Object {
  $contentType = if ($_.Extension -eq ".pdf") { "application/pdf" } else { "text/plain" }
  curl.exe -s -X POST http://localhost:8080/documents -F "file=@$($_.FullName);type=$contentType" | Out-Null
}
curl.exe -s http://localhost:8080/documents | ConvertFrom-Json | Measure-Object | Select-Object -ExpandProperty Count
```

Expected: a count matching the number of files in `sample-data/documents` (16) — confirms the corpus
this feature will retrieve against is actually populated.

## Step 4 — User Story 1: ask a question with a known answer, get a grounded, cited answer

```powershell
$body = @{ question = "Can I expense a taxi from the airport when travelling for work?" } | ConvertTo-Json
curl.exe -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $body | ConvertFrom-Json
```

Expected: `200 OK`; `answer` is a non-blank string synthesized from the travel expense policy;
`sources` contains at least one entry whose `filename` is `travel-expense-policy.pdf` (FR-001, SC-001)
— the question deliberately doesn't use the document's exact wording ("expense a taxi" vs. whatever
phrasing the policy itself uses), exercising semantic retrieval rather than keyword luck (constitution
Principle V).

### Edge case — a page-less (plain `.txt`) source cites correctly

```powershell
$body = @{ question = "What are the corporate card spending limits?" } | ConvertTo-Json
curl.exe -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $body | ConvertFrom-Json | Select-Object -ExpandProperty sources
```

Expected: if `corporate-card-rules.txt` (a `.txt` source, no page structure) is among the sources, its
`page` field reads exactly `"no page structure"`, never a number or `null` (FR-009, Clarifications
Session 2026-08-16).

## Step 5 — User Story 2: ask an out-of-scope question, get the honest refusal

```powershell
$body = @{ question = "What's the CEO's personal cell phone number?" } | ConvertTo-Json
curl.exe -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $body | ConvertFrom-Json
```

Expected: `200 OK`; `answer` is exactly `"I don't have this information in the documentation."`;
`sources` is `[]` (FR-007, SC-002) — never a fabricated answer, never a citation.

### Edge case — a document filter matching nothing behaves the same way

```powershell
$body = @{ question = "Can I expense a taxi?"; documentIds = @("00000000-0000-0000-0000-000000000000") } | ConvertTo-Json
curl.exe -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $body | ConvertFrom-Json
```

Expected: the same fixed "not covered" response, even though the same question answered without the
filter (Step 4) succeeds — proves the filter genuinely narrows retrieval rather than being ignored.

## Step 6 — User Story 3: a distinct error when the system can't process the question

Temporarily point `AZURE_OPEN_AI_ENDPOINT` at an unreachable address (or stop the backend, edit the
environment, and restart it) to simulate a provider failure, then:

```powershell
$body = @{ question = "Can I expense a taxi?" } | ConvertTo-Json
curl.exe -i -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $body
```

Expected: `503 Service Unavailable`, `{ "error": "processing_failed", ... }` — never the `200`
"not covered" response, and never a raw stack trace (FR-013, SC-004). Restore the working
configuration and restart the backend before continuing.

## Step 7 — Validation edge cases

```powershell
# Blank question
curl.exe -i -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d '{"question":"   "}'

# Over-length question (1001 characters)
$long = @{ question = ('a' * 1001) } | ConvertTo-Json
curl.exe -i -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $long

# Malformed body
curl.exe -i -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d 'not json'
```

Expected, respectively: `400 blank_question`; `400 question_too_long`; `400 malformed_request` — each
distinct from the other two and from the `503` of Step 6 (FR-011/FR-012).

## Step 8 — SC-001: run the full evaluation set (separate from the automated test suite)

```powershell
Import-Csv sample-data/evaluation-questions.csv | ForEach-Object {
  $body = @{ question = $_.question } | ConvertTo-Json
  $response = curl.exe -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $body | ConvertFrom-Json
  [pscustomobject]@{
    id = $_.id
    expected = $_.expected_source_document
    hit = ($response.sources.filename -contains $_.expected_source_document)
  }
} | Tee-Object -Variable results | Format-Table
$accuracy = ($results | Where-Object hit | Measure-Object).Count / $results.Count
"Accuracy: {0:P0}" -f $accuracy
```

Expected: accuracy ≥ 80% (constitution Principle VII, spec SC-001). This is a manual/CI validation
activity run after implementation, not part of `mvnw test`'s automated suite (constitution Testing &
Validation section: "run after each major change").

## Step 9 — SC-003: response time

```powershell
$body = @{ question = "Can I expense a taxi from the airport when travelling for work?" } | ConvertTo-Json
Measure-Command { curl.exe -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $body | Out-Null }
```

Expected: total elapsed time under 10 seconds (SC-003).

## Step 10 — Live Azure connectivity test (opt-in)

```powershell
backend\mvnw.cmd test -Pverify-ai
```

Expected: green (requires the same Azure OpenAI environment as Steps 3–9) — proves the grounded-
answer and not-covered paths both work end-to-end against real Azure OpenAI deployments, in addition
to the manual `curl` checks above.

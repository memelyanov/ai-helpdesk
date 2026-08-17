# Quickstart & Validation: Chat Pipeline Diagnostic Logging & Trace

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-08-17

How to bring this feature up and prove it satisfies the spec. This is a validation guide, not an
implementation guide — the Java code and tests that make these steps pass are produced by
`/speckit-tasks` and the implementation phase. Commands are PowerShell (this project's primary shell),
per `specs/001-project-scaffolding/quickstart.md`'s shell conventions.

## Prerequisites

Same as `specs/007-chat-endpoint/quickstart.md`:

- Database up, backend running via `backend\mvnw.cmd spring-boot:run`.
- Steps 1–2 below (unit test + contract test) need **no** Azure OpenAI configuration and **no**
  Docker/database — both stub every collaborator.
- Steps 3 onward (real `curl` calls) need a fully configured Azure OpenAI environment and at least one
  ingested document, same as feature 007.

## Step 1 — Run the new unit test (no Docker, no Azure credentials, no database)

```powershell
backend\mvnw.cmd test -Dtest=ChatServiceTest
```

Expected: green. Proves, with `EmbeddingClient`/`ChatRetrievalRepository`/`ChatCompletionClient`
mocked, that:
- `includeTrace` absent/false → `ChatResponse.trace()` is `null`.
- `includeTrace=true`, normal answer → six ordered `ChatTraceStep`s, each with the full detail
  data-model.md documents (FR-001–FR-006, FR-011, FR-012).
- `includeTrace=true`, nothing survives filtering → exactly four steps, ending at
  `results_filtered` (FR-013).
- `includeTrace=true`, blank completion → all six steps appear, with
  `model_response_received.detail.outcome = "not_covered"`.

## Step 2 — Run the full default test suite

```powershell
backend\mvnw.cmd test
```

Expected: green, including the extended `ChatControllerContractTest` — `includeTrace` on the request
body reaches `ChatService.answer(...)` unchanged, and a stubbed response's `trace` field serializes
(or is entirely absent from the JSON body) exactly as data-model.md specifies.

## Step 3 — User Story 1: reconstruct a request's history from the backend log

```powershell
$body = @{ question = "Can I expense a taxi from the airport when travelling for work?" } | ConvertTo-Json
curl.exe -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $body | Out-Null
Get-Content logs\ai-helpdesk.log -Tail 30
```

Expected: one line per pipeline stage that ran (`request_received`, `question_embedded`,
`vector_search_completed`, `results_filtered`, `prompt_assembled`, `model_response_received`), all
sharing the same `chatRequestId` value, in that order, none containing the full retrieved-passage
text, the full prompt, or the full raw model response (SC-001, research Decision 4).

### Edge case — concurrent requests stay distinguishable

```powershell
1..3 | ForEach-Object -Parallel {
  $b = @{ question = "Can I expense a taxi?" } | ConvertTo-Json
  curl.exe -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $b | Out-Null
}
Select-String -Path logs\ai-helpdesk.log -Pattern "chatRequestId" -SimpleMatch:$false | Select-Object -Last 30
```

Expected: three distinct correlation-id values across the tail of the log, each with its own complete,
uninterleaved run of stage lines (FR-008).

## Step 4 — User Story 2: request the trace via the API

```powershell
$body = @{ question = "Can I expense a taxi from the airport when travelling for work?"; includeTrace = $true } | ConvertTo-Json
$response = curl.exe -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $body | ConvertFrom-Json
$response.trace | Format-Table stage, durationMs
$response.trace[-1].detail.rawResponse
```

Expected: `answer`/`sources` identical to what the same question returns without `includeTrace`
(Step 5); `trace` has six entries in order; the last entry's `detail.rawResponse` is the full raw model
response text (FR-011, FR-012, SC-002, SC-005).

### Edge case — an out-of-scope question truncates the trace

```powershell
$body = @{ question = "What's the CEO's personal cell phone number?"; includeTrace = $true } | ConvertTo-Json
curl.exe -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $body | ConvertFrom-Json | Select-Object -ExpandProperty trace | Select-Object -ExpandProperty stage
```

Expected: exactly `request_received`, `question_embedded`, `vector_search_completed`,
`results_filtered` — no `prompt_assembled`/`model_response_received` (FR-013).

## Step 5 — User Story 3: default behavior is unaffected

```powershell
$body = @{ question = "Can I expense a taxi from the airport when travelling for work?" } | ConvertTo-Json
$response = curl.exe -s -X POST http://localhost:8080/chat -H "Content-Type: application/json" -d $body
$response
$response | ConvertFrom-Json | Get-Member -Name trace
```

Expected: response body has no `trace` key at all (the `Get-Member` call returns nothing for `trace`);
`answer`/`sources` match feature 007's existing behavior exactly (FR-010, SC-003).

## Step 6 — Credential safety check

```powershell
Select-String -Path logs\ai-helpdesk.log -Pattern $env:AZURE_OPEN_AI_KEY -SimpleMatch
```

Expected: no matches, ever — across every request made in Steps 3–5 (FR-009, SC-004). Also inspect any
`trace` response captured in Step 4 by hand for the same reason: the key must not appear there either.

## Step 7 — Existing feature 007 test tiers remain green

```powershell
backend\mvnw.cmd test -Pverify-db
backend\mvnw.cmd test -Pverify-ai
```

Expected: both green, unaffected by this feature (research Decision 6) — proves the retrieval and live
Azure OpenAI paths this feature only observes are still working exactly as feature 007 established.

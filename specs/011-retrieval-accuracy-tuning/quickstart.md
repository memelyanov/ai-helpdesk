# Quickstart: Retrieval Accuracy Tuning

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Prerequisites

- Feature 001–007's backend stack already running: PostgreSQL + pgvector (`docker compose up` from
  `db/`), Azure OpenAI environment variables configured (`AZURE_OPEN_AI_*`), backend running
  (`backend/mvnw spring-boot:run`).
- At least one sample document already uploaded via `POST /documents`
  (`sample-data/documents/` or `sample-data/pdf-sources/`), so there is real content to re-chunk and
  re-query.
- This feature ships as a two-constant code change (`ChatService.SIMILARITY_THRESHOLD`,
  `Chunker.TARGET_TOKENS`/`OVERLAP_TOKENS`) — no schema migration, no new environment variable, no
  frontend change to bring up separately.

## User Story 1 — a near-verbatim question stops being falsely refused

1. Pick a sentence straight out of an already-ingested sample document (e.g. a line from
   `sample-data/pdf-sources/travel-expense-policy.md`).
2. Before pulling this feature's change, ask that question via `POST /chat` (or the chat UI) and
   confirm it currently returns the `"I don't have this information in the documentation."`
   fallback, or an answer built from a single, thin passage — the false-refusal / weak-context
   failure mode manual testing found.
3. Apply this feature's change (`SIMILARITY_THRESHOLD` → `0.35`).
4. Ask the identical question again. Expect a grounded answer whose `sources` cite the document the
   sentence came from, with a `score` in the `sources` entry that is `>= 0.35` (previously it would
   have needed to be `>= 0.5` to appear at all).
5. Ask a genuinely unrelated question (nothing in any uploaded document covers it). Expect the
   `"I don't have this information in the documentation."` fallback unchanged (spec.md Acceptance
   Scenario, User Story 1 #2) — confirms the lowered bar didn't turn into "accept anything."

## User Story 2 — smaller, more focused passages

1. Apply this feature's `Chunker` change (`TARGET_TOKENS` → `500`, `OVERLAP_TOKENS` → `63`).
2. Delete and re-upload one already-ingested sample document (`DELETE /documents/{id}` then
   `POST /documents` again) so it is re-chunked under the new window size — this feature does not
   reprocess documents automatically (research Decision 4).
3. Ask a question scoped to one narrow part of that document. In the trace dialog (feature 010) or
   the raw `POST /chat?includeTrace=true` response, compare the retrieved passage's `text` length
   against what the same document/question would have returned before re-ingestion — expect a
   visibly shorter, more topically focused passage.
4. Ask about a topic broader than one ~500-token window. Confirm multiple passages covering it are
   still retrieved — now up to `TOP_K = 5` (raised from 4, research Decision 6) specifically so
   shrinking passage size doesn't lose topic coverage.

## User Story 3 — no regression on the existing evaluation set

1. Run the automated suite: `backend/mvnw test` (default, no live Azure/DB needed) and
   `backend/mvnw test -Pverify-db` (real pgvector, `ChatRetrievalIT`'s re-pointed 0.35-boundary and
   5-passage-cap tests).
2. Run the existing curated evaluation set (`sample-data/evaluation-questions.csv`) once *before*
   applying this change and once *after*, the same way it was already run previously (manual pass, or
   `backend/mvnw test -Pverify-ai` if the live-Azure evaluation tier is set up), and compare the two
   pass counts directly (SC-003's verification method — no separately stored baseline is checked
   against). Expect equal or better, never worse.

## Automated tests

```bash
cd backend
mvnw test                 # default suite: unit + contract, no live DB or Azure — includes ChunkerTest at the new 500/63 constants (ChatServiceTest needs no change, see plan.md)
mvnw test -Pverify-db     # + ChatRetrievalIT against real Testcontainers pgvector, boundary re-pointed to 0.35, TOP_K cap re-pointed to 5
```

No `-Pverify-ai`-tier test is added by this feature (research Decision 5 scope) — the live-Azure
evaluation-set run stays the existing manual/CI activity the constitution's Testing & Validation
section already mandates, not a new automated test this feature introduces.

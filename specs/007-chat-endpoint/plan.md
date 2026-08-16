# Implementation Plan: Chat Endpoint (Retrieve → Augment → Generate)

**Branch**: `main` (no dedicated feature branch — no `before_specify`/`before_plan` hook is
registered in `.specify/extensions.yml`, same situation features 004–006's plans recorded) |
**Date**: 2026-08-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-chat-endpoint/spec.md`

## Summary

Add `POST /chat` as a new REST resource in a new `com.epam.aihelpdesk.chat` package: embed the
caller's question with the same Azure OpenAI embedding deployment used at ingestion time, run
feature 003's already-documented pgvector similarity query (`embedding <=> :query_vector`, top-K=4)
against the existing `chunks` table, keep only results at or above a 0.5 cosine-similarity threshold,
and — only when at least one passage survives — call the Azure OpenAI chat deployment with a fixed
grounding system prompt plus the surviving passages. The response is always the same shape: a
generated `answer` plus zero or more `sources` (document filename, page, similarity score). When
nothing relevant survives the threshold (including an empty corpus or a document filter matching
nothing), the endpoint returns the fixed "documentation does not cover this" answer with no sources
instead of calling the chat deployment at all — this is a plain `200` outcome, not an error. An
unexpected failure calling either Azure OpenAI deployment or the database is a distinct `503`,
engineered to be structurally impossible to confuse with the `200` "not covered" outcome (spec.md
FR-007/FR-013).

This is the first feature to read `chunks.embedding` for search rather than only write it, and the
first to construct an `AzureOpenAiChatModel` outside of the opt-in `AzureOpenAiConnectivityIT`
smoke test — everything else (schema, `EmbeddingClient`, `AzureOpenAiProperties`) already exists and
is reused, not rebuilt.

Nine decisions carry the design (full reasoning in [research.md](research.md)):

- **A new `chat` package/controller**, not another verb on `DocumentController` — `/chat` is a
  distinct resource from `/documents`, unlike features 005/006 which added verbs to an existing one.
- **The route is the bare `POST /chat`, JSON body in and out** — one endpoint, no query parameters,
  no multipart (unlike `POST /documents`) — a question is data submitted for processing, not an
  entity created at a discoverable URI.
- **`EmbeddingClient` (feature 004) gains one new method, `embedQuery(String)`**, reused as-is for
  turning a question into a vector — no second Azure client-construction code path. Its
  ingestion-scoped `IngestionProcessingException` is caught and translated into this feature's own
  `ChatProcessingException` at the package boundary, not thrown directly across packages.
- **A new `ChatCompletionClient`** builds an `AzureOpenAiChatModel` by hand, mirroring
  `AzureOpenAiConnectivityIT`'s existing construction pattern and gated by
  `AzureOpenAiProperties.isComplete()` (the chat-scoped completeness check feature 001 already
  defines).
- **Retrieval reuses feature 003's `similarity-search-contract.md` query verbatim** — top-K=4 is the
  SQL `LIMIT`; the 0.5 similarity threshold is applied afterward, in application code, against the
  already-limited top-4 result set (matching the constitution's literal "if top-K similarity scores
  are all below threshold" wording), not folded into the `WHERE` clause.
- **Citations are computed from retrieval, never parsed from the model's answer text** — every
  `sources` entry is a (document, page) pair that genuinely had a retrieved passage included in the
  prompt, so SC-005's "zero fabricated citations" holds by construction.
- **A page-less source (`page_number IS NULL`, plain `.txt`) renders a fixed `"no page structure"`
  indicator** in place of a page number (spec.md Clarifications, Session 2026-08-16).
- **A new, chat-scoped exception hierarchy and `ChatErrorHandler`** (`InvalidChatRequestException` →
  `400`, `ChatProcessingException` → `503`), not a reuse of `ingestion`'s `Document*`/`Ingestion*`
  classes — same Javadoc-scope-drift reasoning feature 006's Decision 6 already established.
- **Three-tier test strategy** — `contract` (default, stubbed), `db` (`verify-db`, real pgvector
  query against seeded known vectors, chat completion stubbed), `azure` (`verify-ai`, one live
  grounded-answer call and one live not-covered call) — reusing the existing tags/profiles, no
  `pom.xml` change.

## Technical Context

**Language/Version**: Java 17, Spring Boot 3.5.16 (unchanged from features 001/003/004/005/006).

**Primary Dependencies**: none new. `spring-ai-starter-model-azure-openai` (already on the
classpath, feature 001) is used for the first time to build a chat model in production code — every
prior feature only ever built an *embedding* model (`EmbeddingClient`) or exercised chat only inside
the opt-in `AzureOpenAiConnectivityIT` smoke test. `com.pgvector:pgvector` (feature 003/004) is
reused to bind the query vector parameter. No new Maven dependency, no `pom.xml` change.

**Storage**: Same PostgreSQL 18 + pgvector instance and `documents`/`chunks` tables feature 003
created — no schema change, no new column. This is the first feature to `SELECT ... ORDER BY
embedding <=> :query_vector` against `chunks` (features 004/005/006 only ever `INSERT`, plain
`SELECT`, or `DELETE`); the exact query shape was already specified by feature 003's
`similarity-search-contract.md` in anticipation of this feature.

**Testing**: JUnit 5 (existing stack), three tiers:

- **Contract test** (always run, `MockMvc`, no live DB, no Azure): `POST /chat` against stubbed
  retrieval/completion collaborators — validation (blank question, over-length question, malformed
  body), the `200` shape for both a grounded answer and the "not covered" outcome, and the `503`
  mapping for a simulated processing failure.
- **`@Tag("db")` integration test** (opt-in, `verify-db` profile, Testcontainers
  `pgvector/pgvector:pg18`, reusing `DocumentIngestionIT`/`DocumentQueryIT`'s exact container/schema
  bring-up): seed `chunks` rows with known, hand-picked vectors, prove the real similarity query
  ranks and thresholds them correctly, and prove a document-id filter actually narrows the candidate
  set — with `ChatCompletionClient` stubbed (`@MockitoBean`), so no live Azure call is needed to
  prove the retrieval half.
- **`@Tag("azure")` integration test** (opt-in, `verify-ai` profile, mirrors
  `AzureOpenAiConnectivityIT`'s existing pattern): ingest one real sample document, ask a question
  whose answer is known to be in it and confirm a non-blank, cited `200` response; ask an unrelated
  question and confirm the fixed not-covered `200` response. Requires real `AZURE_OPEN_AI_*`
  credentials, same as the existing `AzureOpenAiConnectivityIT`.

Tests MUST NOT require live Azure credentials to pass by default (constitution Principle II) — the
default suite (contract tier) stubs both `EmbeddingClient.embedQuery` and `ChatCompletionClient`.

**Target Platform**: Same as features 001/003/004/005/006 — local developer machine, Docker Compose
for PostgreSQL only; backend runs locally (`mvnw spring-boot:run`).

**Project Type**: Web application (existing structure). This feature adds backend-only code
(`backend/src/main/java/.../chat/`) — no frontend change; the Angular chat view is explicitly a
later step (`poc-concept.md` §10 item 7, after this backend endpoint per item 6).

**Performance Goals**: SC-003 — a complete answer (or the "not covered" outcome) in under 10 seconds
of wall-clock time for a typical question against the full sample corpus. Dominated by the two Azure
OpenAI network round-trips (embed the question, generate the answer); the pgvector similarity query
itself is a single indexed-adjacent `ORDER BY ... LIMIT 4` over a corpus of this PoC's scale.

**Constraints**:
- A blank/missing question and a question over 1000 characters MUST both be rejected before any
  retrieval or generation is attempted, with distinct `400` error codes (FR-011/FR-012, Clarifications
  Session 2026-08-16).
- Retrieval MUST use the same embedding deployment used at ingestion time (constitution Principle V)
  — `embedQuery` reuses `EmbeddingClient`'s existing deployment-name-driven construction, so there is
  no second place a mismatched deployment name could be introduced.
- The "not covered" outcome (FR-007) and the "could not process" failure (FR-013) MUST be
  structurally distinguishable — different HTTP status (`200` vs `503`) and different response shape
  (`ChatResponse` vs `ChatErrorResponse`), not just different text a caller would have to parse.
- No AI provider credential may appear in any response body or log line (FR-015) — mirrors
  `EmbeddingClient`'s existing discipline of logging only `e.toString()`/summary fields, never a raw
  exception that could carry a credential.
- Each request is handled independently — no conversation state is read or written anywhere (FR-014).

**Scale/Scope**: One new package (`chat`) with a controller, a service, a retrieval repository, a
completion client, three new exception classes, one error handler, and four new DTOs. One modified
existing file (`EmbeddingClient`, +1 method). No new persisted entity, no schema change, no new
dependency. Expected load: same 16-document sample corpus scale features 004–006 established.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.4.1** (unchanged since feature 006; no
amendment has landed between that feature and this one).

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` precedes this plan; `/speckit-clarify` resolved both critical ambiguities found (max question length, page-less citation display) before this plan was written. |
| II | Test-Driven Development (Mandatory) | ✅ PASS | Three test tiers planned (contract/`db`/`azure`) before any implementation task is written; the default suite (contract only) needs no live database or Azure credentials. |
| III | Grounded Answers (RAG-First) | ✅ PASS — first feature to fully implement this | Every generated answer is built exclusively from retrieved `chunks` passages (FR-003/FR-006); every citation names its contributing document and page (FR-008/FR-009). |
| IV | No Hallucination (Context Adherence) | ✅ PASS — first feature to fully implement this | A fixed system prompt enforces "answer only from context, say so if not present" (constitution Query Pipeline section); below-threshold or empty retrieval short-circuits to the fixed "not covered" response before the model is ever called (FR-005/FR-007). |
| V | Semantic Understanding (Meaning-Based Retrieval) | ✅ PASS — first feature to fully implement this | The question is embedded with the same Azure OpenAI embedding deployment used at ingestion (feature 004's `EmbeddingClient`, reused via the new `embedQuery` method); retrieval is pgvector cosine similarity (`<=>`), never keyword matching. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ✅ PASS | Retrieval reads only the self-hosted PostgreSQL/pgvector instance; inference runs through Azure OpenAI (tenant-scoped), not the public OpenAI API — consistent with every prior feature. |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ✅ PASS — first feature this metric can be measured against | SC-001 states the ≥80% bar directly; the `azure`-tagged test exercises the mechanism the full `evaluation-questions.csv` run (a separate, manual/CI-level activity per the constitution's Testing & Validation section, not this feature's own test suite) will use. |

**AI Provider Configuration compliance**: reads `AZURE_OPEN_AI_KEY`/`_ENDPOINT`/`_DEPLOYMENT_NAME`
(chat, via `AzureOpenAiProperties.isComplete()`) and the same four variables' embedding half (via
`EmbeddingClient.isEmbeddingComplete()`, reused) — no new variable name, no renamed variable. An
unconfigured provider is reported as `503 provider_unconfigured` before any network call, mirroring
feature 004's existing behavior for embeddings.

**Chunking & Embedding Strategy compliance**: N/A for new chunking (this feature ingests nothing);
the same embedding deployment and `VECTOR(1536)` dimensionality feature 003/004 already established
is reused for the query vector — no risk of a dimension mismatch, since `embedQuery` calls the same
`AzureOpenAiEmbeddingModel` construction path as chunk embedding.

**Ingestion Pipeline compliance**: N/A — `POST /documents` is unchanged.

**Query Pipeline compliance**: this feature *is* the constitution's "Query Pipeline" section made
concrete — top-K=4, the fixed system prompt text, the 0.5 cosine-similarity threshold, and the
required response fields (answer, source documents with filename+page, similarity scores) are all
implemented exactly as specified there.

**Error Handling & Logging compliance**: the new `503` failure paths (`provider_unconfigured`,
`processing_failed`) are wrapped consistently with the existing `4xx`/`5xx`, caller-distinguishable
pattern; the new `400` validation paths (`blank_question`, `question_too_long`, `malformed_request`)
never reach retrieval or generation. Structured logging follows `EmbeddingClient`'s existing
pattern — request/response summaries only, `e.toString()` never a raw exception, never a credential.

**Testing & Validation compliance**: functional test coverage (contract + `db` + `azure`-tagged)
verifies the grounded-answer path, the not-covered path (including an empty corpus and a
non-matching document filter), both validation failures, and the `503` failure path — the negative-
case discipline this section requires. The full `evaluation-questions.csv` run against ≥80% accuracy
(SC-001) is a separate, post-implementation validation activity (quickstart.md Step 8), not part of
the automated test suite, consistent with how the constitution scopes it ("run after each major
change") rather than as a per-commit unit test.

**Code & Documentation Language Standard compliance**: this plan and all Phase 0/1 artifacts are in
English ✅; implementation-phase code, comments, and commit messages will follow the same standard.

**Technology Stack compliance**: Java 17 / Spring Boot 3 ✅; Spring AI's Azure OpenAI chat model ✅
(already a declared dependency, feature 001, first production use here); PostgreSQL + pgvector ✅ (no
schema change); no JPA/Hibernate introduced ✅ (plain `JdbcTemplate`, consistent with 001/003/004–006).

**Post-Phase 1 re-check**: ✅ No change. Phase 1 design (data-model.md, contracts/, quickstart.md)
introduced no new persisted table or column, no new dependency, and no deviation from the mandated
tech stack.

**Gate result**: PASS — no violations, no justifications required. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/007-chat-endpoint/
├── plan.md                              # This file
├── research.md                          # Phase 0 — 8 decisions
├── data-model.md                        # Phase 1 — request/response/internal shapes (no new persisted entities)
├── quickstart.md                        # Phase 1 — bring-up and per-user-story validation
├── contracts/
│   └── chat-api-contract.md             # POST /chat
├── checklists/
│   ├── requirements.md                  # Spec quality checklist — 17/17 items pass
│   └── completeness.md                  # Requirements-completeness checklist — 30/30 items resolved
└── tasks.md                             # Phase 2 — created by /speckit-tasks, NOT by this command
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                                  # UNCHANGED — no new dependency
├── src/main/java/com/epam/aihelpdesk/
│   ├── health/                                              # UNCHANGED (feature 001)
│   ├── ingestion/
│   │   ├── EmbeddingClient.java                              # MODIFIED — + embedQuery(String) (research Decision 3)
│   │   └── ...                                                # UNCHANGED (all other files, features 004–006)
│   └── chat/                                                 # NEW package (research Decision 1)
│       ├── ChatController.java                                # NEW — POST /chat, request validation
│       ├── ChatService.java                                   # NEW — retrieve → threshold → augment → generate orchestration
│       ├── ChatRetrievalRepository.java                       # NEW — pgvector similarity query (research Decision 5)
│       ├── RetrievedChunk.java                                 # NEW — one similarity-search result row
│       ├── ChatCompletionClient.java                           # NEW — Azure OpenAI chat call (research Decision 4)
│       ├── ChatException.java                                  # NEW — abstract base, errorCode (mirrors IngestionException)
│       ├── InvalidChatRequestException.java                    # NEW — → 400 (blank_question, question_too_long, malformed_request)
│       ├── ChatProcessingException.java                        # NEW — → 503 (provider_unconfigured, processing_failed)
│       ├── ChatErrorHandler.java                                # NEW — @RestControllerAdvice
│       └── dto/
│           ├── ChatRequest.java                                 # NEW — { question, documentIds }
│           ├── ChatResponse.java                                # NEW — { answer, sources }
│           ├── SourceCitation.java                              # NEW — { documentId, filename, page, score }
│           └── ChatErrorResponse.java                           # NEW — { error, message }
└── src/test/java/com/epam/aihelpdesk/
    ├── ingestion/                                             # UNCHANGED (features 004–006)
    └── chat/                                                  # NEW package
        ├── ChatControllerContractTest.java                     # NEW — default suite
        ├── ChatRetrievalIT.java                                 # NEW — @Tag("db")
        └── ChatCompletionConnectivityIT.java                    # NEW — @Tag("azure")

frontend/                                                       # UNCHANGED — no frontend work in this feature
```

**Structure Decision**: A new `chat` package, sibling to `ingestion` and `health`, is introduced —
`/chat` is a new REST resource distinct from `/documents`, so (unlike features 005/006, which added
verbs to the existing `DocumentController`) this is not an extension of an existing controller.
`EmbeddingClient` is the one file this feature modifies outside the new package, gaining a single
additive method reused across the package boundary. No new top-level directories, no frontend
changes, no schema changes.

## Complexity Tracking

*No entries — the Constitution Check gate passed with no violations requiring justification.*

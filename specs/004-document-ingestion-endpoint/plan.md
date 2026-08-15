# Implementation Plan: Document Ingestion Endpoint

**Branch**: `003-document-vector-schema` (no dedicated feature branch — no `before_specify`/
`before_plan` hook is registered in `.specify/extensions.yml`) | **Date**: 2026-08-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-document-ingestion-endpoint/spec.md`

## Summary

Implement `POST /documents`: the first production Java code in this repository that writes to the
`documents`/`chunks` schema `specs/003-document-vector-schema` created. One multipart file in
(`.txt` or `.pdf`, ≤20 MB) → Apache Tika extracts text → the text is split into 500–1000-token
chunks with 10–15% overlap (token-accurate, via `jtokkit`'s `cl100k_base` encoding) → every chunk's
text is embedded in a single batched Azure OpenAI embedding call → the document row and its full
chunk set (each with its vector) are written in one JDBC transaction → the caller gets back the new
document's id and chunk count.

Three decisions carry the design:

- **Embeddings are computed before any row is written, and the whole document commits in one
  transaction.** FR-008/FR-009's atomicity requirement becomes a natural consequence of ordering,
  not a rollback-and-cleanup mechanism: nothing is inserted until every chunk already has its
  vector in hand, so there is no partial state to roll back *from*.
- **The Azure OpenAI embedding model is built by hand from `AzureOpenAiProperties`**, the same
  pattern `AzureOpenAiConnectivityIT` already uses for chat — not Spring AI's auto-configured
  `EmbeddingModel` bean, which `application.yml` deliberately pins to `spring.ai.model.embedding:
  none` (feature 001, Decision 4) so the app still boots with no Azure credentials. Ingestion needs
  its own completeness check (key + endpoint + **embedding** deployment name — chat's deployment
  name is irrelevant here) to fail fast with a clear, distinguishable error before touching the
  network.
- **No JPA.** Feature 001/003 already committed to plain JDBC over Hibernate; this feature is a
  writer, not a schema owner, so it follows the same line: `JdbcTemplate` + a `TransactionTemplate`
  (or `@Transactional`, both backed by Spring Boot's auto-configured `DataSourceTransactionManager`
  once `spring-boot-starter-jdbc` is on the classpath, which it already is) and the `pgvector` Java
  helper for the one type JDBC has no built-in mapping for (`vector`).

## Technical Context

**Language/Version**: Java 17, Spring Boot 3.5.16 (unchanged from feature 001/003).

**Primary Dependencies** (all new, added to `backend/pom.xml`):
- `org.apache.tika:tika-core` + `org.apache.tika:tika-parser-pdf-module` +
  `org.apache.tika:tika-parser-text-module` — text extraction for `.pdf` and `.txt` respectively.
  Lean per-format modules rather than the `tika-parsers-standard-package` aggregator, which pulls in
  OCR, image, and office-format parsers this feature has no use for (constitution scope is `.txt`/
  `.pdf` only).
- `com.knuddels:jtokkit` — pure-Java, no native dependencies, OpenAI-compatible BPE tokenizer.
  Used for accurate `cl100k_base` token counts so FR-006's "500–1000 tokens" is a real token count,
  not a word-count approximation.
- `com.pgvector:pgvector` — the official pgvector JDBC helper (`PGvector` value type), used to bind
  `float[]` to the `chunks.embedding vector(1536)` column and back through plain `JdbcTemplate`
  calls, exactly as `documents.content bytea` already binds through `byte[]` with no helper needed.
- `org.springframework.ai:spring-ai-starter-model-azure-openai` — already present (feature 001);
  reused for its `AzureOpenAiEmbeddingModel` class and Azure SDK transitive dependencies, built
  programmatically rather than via its auto-configuration (see Summary).

**Storage**: Same PostgreSQL 18 + pgvector instance and `documents`/`chunks` tables feature 003
created — no schema change. This feature is exactly the "future ingestion feature" both
[contracts/document-schema.md](../003-document-vector-schema/contracts/document-schema.md) and
[contracts/chunk-schema.md](../003-document-vector-schema/contracts/chunk-schema.md) were written
for; its writes must satisfy the guarantees those contracts already state, not renegotiate them.

**Testing**: JUnit 5 (existing stack). Four layers, mirroring the `db`/`azure` tag convention
feature 001/003 established:
- **Unit tests** (always run): the chunker (token windows, overlap, short-document edge case) and
  the PDF per-page text splitter, both pure functions with no I/O.
- **Contract test** (always run, `MockMvc`, no live DB or Azure call): `POST /documents` against a
  stubbed `EmbeddingModel` and a stubbed chunk-writer, asserting request/response shape and status
  codes for FR-002/003/005/010/011.
- **`@Tag("db")` integration test** (opt-in, `verify-db` profile, Testcontainers
  `pgvector/pgvector:pg18`): the full pipeline through a real database with a **stubbed** embedding
  model (fixed-length fake vectors) — proves atomicity, cascade behavior, and actual row shape
  without needing Azure credentials (constitution Principle II: "Tests MUST NOT require live AI
  provider credentials").
- **`@Tag("azure")` integration test** (opt-in, `verify-ai` profile, extends the existing
  `AzureOpenAiConnectivityIT` pattern): one real embedding call against the configured deployment,
  proving the hand-built `AzureOpenAiEmbeddingModel` actually reaches Azure.

**Target Platform**: Same as feature 001/003 — local developer machine, Docker Compose for
PostgreSQL only; backend runs locally (`mvnw spring-boot:run`). This feature makes no target
platform demands beyond what 003 already introduced.

**Project Type**: Web application (existing structure). This feature adds backend-only code
(`backend/src/main/java/.../ingestion/`) — no frontend change, consistent with the spec's
Assumptions (upload UI is a later step, `poc-concept.md` §10 item 7).

**Performance Goals**: SC-001 — a typical multi-page document (~7–8 pages, ~1,800 words) fully
ingested and confirmed within 15 seconds; SC-006 — even a file at the 20 MB maximum either completes
or is reported as failed within 60 seconds, never left neither confirmed nor failed. The dominant
cost is the embedding call; batching every chunk of one document into a single Azure OpenAI
embeddings request (sub-batched only if the provider's 2048-input ceiling is exceeded, research
Decision 4) keeps this to one network round trip in the common case, well inside budget at the
sample corpus's few-page document sizes.

**Constraints**:
- FR-003's size/type check MUST run before FR-002's type detection and before any Tika parsing is
  attempted — file size is known from the request without inspecting content, so an oversized,
  wrong-type file is reported as too large, not as an unsupported type (spec Edge Cases).
- All of a document's chunk rows, plus the document row itself, MUST commit in exactly one
  transaction (FR-009) — enforced by not opening the transaction until every embedding is already
  in hand (see Summary), including every sub-batch when a document needs more than one embedding
  call (research Decision 4).
- `embedding` values MUST be exactly 1536-dimensional, matching the constitution's mandated
  `text-embedding-3-small` deployment — the column type itself rejects anything else (003,
  Decision 3); this feature does not add its own dimension check, it relies on that guarantee.
- Uploads MUST be capped at 20 MB (FR-003) — rejected before Tika ever sees the bytes.
- No API key, endpoint, or deployment name may appear in a log line or an HTTP response body
  (FR-014); every upload attempt, embedding request, and database write MUST produce a structured
  log record (FR-016), carried from the constitution's Error Handling & Logging section.
- The original filename MUST be stored verbatim and MUST NOT be interpreted as a filesystem path or
  executed (FR-017) — it is opaque text data end to end.

**Scale/Scope**: One REST endpoint, one request/response DTO pair, a parsing component, a chunking
component, an embedding component, a JDBC writer, and a global error handler — no new persisted
entities (data-model.md documents the request/response shapes only; the stored shapes are already
fully specified by feature 003). Expected load: the 16-document sample corpus (SC-004), a few
hundred chunk rows total, one upload at a time in practice though the spec requires concurrent
uploads not to interfere (Edge Cases).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` **v1.4.1** (the v1.4.1 patch generalized
Error Handling & Logging's status-code wording from a literal "500" to "an explicit error status
code... stated by the feature's own contract" specifically to reconcile it with this feature's
`400`/`503` design below — see the constitution's Sync Impact Report for that amendment).

| # | Principle | Status | Assessment |
|---|---|---|---|
| I | Spec-First (Documentation-First) | ✅ PASS | `spec.md` precedes this plan; its one clarification (empty-text documents → store with zero chunks) was resolved before planning started. |
| II | Test-Driven Development (Mandatory) | ✅ PASS | Four test layers planned (unit / contract / `db` / `azure`) before any implementation task is written; the default suite (unit + contract) needs neither a live database nor Azure credentials, honoring "clean checkout runs green." |
| III | Grounded Answers (RAG-First) | ⏭️ N/A — deferred | No answer generation here; this feature populates the corpus a future `/chat` feature will retrieve from and cite. |
| IV | No Hallucination (Context Adherence) | ⏭️ N/A — deferred | No LLM chat call in this feature — only embeddings, which are not generative. |
| V | Semantic Understanding (Meaning-Based Retrieval) | ✅ PASS | Every chunk is embedded via the same Azure OpenAI embedding deployment (`AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME`), matching what a future query pipeline must use for comparable vectors — enforced by there being exactly one embedding component in this feature's design, not a choice left to each call site. |
| VI | Data Sovereignty (Self-Hosted Vectors) | ✅ PASS | Vectors and original content both land in the same self-hosted PostgreSQL/pgvector instance; no fine-tuning path exists in this design. Azure OpenAI is used only for stateless embedding calls (Principle VI's explicit exception). |
| VII | Quality Validation (≥80% Retrieval Accuracy) | ⏭️ N/A — deferred | No retrieval logic exists yet to measure against; SC-004 (all 16 sample documents ingest successfully) is what makes that future measurement possible, not the measurement itself. This is also why Governance → Compliance Review's "the evaluation set MUST be run and reported in the PR before merging" line is **N/A for this PR, not silently skipped**: there is no query/chat pipeline for `sample-data/evaluation-questions.csv` to run against until a future feature adds one. This PR's Constitution Compliance checklist should record that line as N/A with this rationale. |

**AI Provider Configuration compliance**: reads the four mandated env vars via the existing
`AzureOpenAiProperties` (extended with an embedding-specific completeness check, see research
Decision 6) ✅; the application already starts with AI variables absent (feature 001, unaffected by
this feature) ✅; no key/endpoint/deployment name is logged or returned in a response (FR-014) ✅.

**Chunking & Embedding Strategy compliance**: 500–1000 tokens, 10–15% overlap ✅ (research
Decision 3, token-accurate via `jtokkit`); every chunk retains `source_filename`, `page_number`,
`chunk_id` ✅ (already the schema's shape, this feature just populates it correctly); embeddings
generated at ingestion time, not query time ✅.

**Ingestion Pipeline compliance**: `/documents` accepts `.txt`/`.pdf` ✅ (FR-001/002); Apache Tika
parses, failures are explicit ✅ (FR-005, research Decision 1); embedding failures are not silently
swallowed — a failed batch (or sub-batch) call fails the whole upload, logged, and reported to the
caller ✅ (FR-009/011, research Decision 4); vectors and metadata written atomically ✅ (FR-009,
Summary); response includes document id and chunk count ✅ (FR-010).

**Error Handling & Logging compliance**: external calls (Azure, DB) wrapped and mapped to explicit
error responses, not swallowed ✅; structured logging of each upload outcome, embedding request, and
DB write is now a spec-level requirement, not merely an implementation-phase intent ✅ (FR-016); no
credentials in logs/responses, with an explicit verification method stated (FR-014) ✅; failed
uploads leave no partial index — this is the same guarantee as FR-009, not a separate mechanism ✅.

**Code & Documentation Language Standard compliance**: this plan and all Phase 0/1 artifacts are in
English ✅; implementation-phase code, comments, and commit messages will follow the same standard
(carried forward, not re-decided here).

**Technology Stack compliance**: Java 17 / Spring Boot 3 ✅; Apache Tika ✅ (this is the feature
`specs/001-project-scaffolding/plan.md` explicitly deferred it to); Azure OpenAI via Spring AI's
Azure starter ✅ (already a dependency, no new provider integration); PostgreSQL + pgvector ✅ (no
schema change); no JPA/Hibernate introduced ✅ (plain JDBC, consistent with 001/003).

**Post-Phase 1 re-check**: ✅ No change. Phase 1 design (data-model.md, contracts/, quickstart.md)
introduced no new persisted tables or columns, no new mandated dependency beyond what Phase 0
research already justified, and no deviation from the mandated tech stack.

**Gate result**: PASS — no violations, no justifications required. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/004-document-ingestion-endpoint/
├── plan.md                              # This file
├── research.md                          # Phase 0 — 9 decisions
├── data-model.md                        # Phase 1 — request/response shapes (no new stored entities)
├── quickstart.md                        # Phase 1 — bring-up and per-user-story validation
├── contracts/
│   └── ingestion-api-contract.md        # POST /documents: request/response/error shape
├── checklists/
│   └── requirements.md                  # Spec quality checklist — all items pass
└── tasks.md                             # Phase 2 — created by /speckit-tasks, NOT by this command
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                          # MODIFIED — tika-core, tika-parser-pdf-module,
│                                                     #   tika-parser-text-module, jtokkit, pgvector
├── src/main/java/com/epam/aihelpdesk/
│   ├── health/                                      # UNCHANGED (feature 001)
│   │   ├── AzureOpenAiConfigHealthIndicator.java
│   │   └── AzureOpenAiProperties.java               # MODIFIED — embedding-completeness check added
│   └── ingestion/                                   # NEW — this feature
│       ├── DocumentController.java                  # POST /documents
│       ├── IngestionService.java                    # Orchestrates parse → chunk → embed → write
│       ├── TextExtractor.java                       # Tika-backed; page-aware for .pdf
│       ├── Chunker.java                              # Token-window chunking (jtokkit)
│       ├── EmbeddingClient.java                      # Hand-built AzureOpenAiEmbeddingModel wrapper
│       ├── DocumentRepository.java                   # JdbcTemplate writer (documents + chunks, one tx)
│       ├── IngestionException.java                   # Base for the invalid-input vs processing-failure split
│       ├── InvalidDocumentException.java             # → 400 (FR-002/003/005)
│       ├── IngestionProcessingException.java         # → 503 (FR-009's failure case, provider unconfigured)
│       ├── IngestionErrorHandler.java                # @ControllerAdvice mapping exceptions → responses
│       └── dto/
│           ├── DocumentIngestionResponse.java         # { documentId, chunkCount }
│           └── IngestionErrorResponse.java            # { error, message }
└── src/test/java/com/epam/aihelpdesk/ingestion/
    ├── ChunkerTest.java                              # NEW — unit, always run
    ├── TextExtractorTest.java                        # NEW — unit, always run
    ├── DocumentControllerContractTest.java            # NEW — MockMvc, stubbed collaborators, always run
    └── DocumentIngestionIT.java                       # NEW — @Tag("db"), Testcontainers, verify-db profile

frontend/                                              # UNCHANGED — no frontend work in this feature
```

**Structure Decision**: Web application structure from feature 001/003 is unchanged. All new
production code lands in one new package, `backend/.../ingestion/`, alongside the existing
`health/` package; `AzureOpenAiProperties` gains one method rather than being duplicated. No new
top-level directories, no frontend changes, no schema changes — this feature is purely an
application-layer writer against the storage feature 003 already delivered.

## Complexity Tracking

*No entries — the Constitution Check gate passed with no violations requiring justification.*

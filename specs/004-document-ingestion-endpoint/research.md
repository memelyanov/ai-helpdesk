# Phase 0 Research: Document Ingestion Endpoint

**Date**: 2026-08-15 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

Nine decisions, each resolving a piece of Technical Context the plan template requires or an
algorithmic detail the spec deliberately left to planning (see spec.md Assumptions: "chunk token
measurement, embedding-call retry/backoff policy, and specific error-response formatting are
implementation decisions"). No `[NEEDS CLARIFICATION]` markers remain after this document.

## Decision 1: Text extraction via lean Tika parser modules, not the standard-package aggregator

- **Decision**: `tika-core` + `tika-parser-pdf-module` + `tika-parser-text-module` on the backend
  classpath. `TextExtractor` uses Tika's `Detector` (magic-byte content sniffing, no filename/
  content-type hint — FR-002) to classify every upload, and Tika's `AutoDetectParser` to parse
  every `.pdf`. **Implementation refinement, discovered during T016/T023–T026 (unit-tested empirically,
  `TextExtractorTest`)**: `.txt` content is decoded directly via a strict UTF-8 `CharsetDecoder`
  rather than through Tika's own text parser. Tika is still used for MIME detection and for every
  `.pdf` parse, unchanged from the original decision below.
- **Rationale**: The constitution mandates Apache Tika and restricts scope to `.txt`/`.pdf`
  (`documents.content_type CHECK`, feature 003). The per-format modules provide exactly those two
  parsers; `tika-parsers-standard-package` would additionally pull in OCR, image, and
  office-document parser dependencies this project has no CHECK constraint that would ever accept,
  for a PoC that already declared "OCR of scanned PDFs" out of scope (`poc-concept.md` §9).
  The `.txt`-decoding refinement exists because Tika's own charset detection is deliberately
  *lenient* — it guesses a best-fit encoding for whatever bytes it receives rather than failing —
  which directly conflicts with spec.md's Edge Case: "the system MUST NOT guess or transliterate an
  encoding" for undecodable `.txt` content (FR-005). A strict UTF-8 decoder that reports on any
  malformed input or unmappable character is the direct way to satisfy that requirement; routing
  `.txt` through Tika's parser first and hoping it fails on bad input was not something the original
  decision (below) could verify without writing the code and testing it against real malformed
  bytes, which is what happened.
- **Alternatives considered**: `tika-parsers-standard-package` (rejected — footprint disproportionate
  to a two-format scope); a hand-rolled PDF reader via PDFBox directly with no Tika involvement
  (rejected — contradicts the constitution's explicit "Apache Tika MUST parse the document"
  requirement, and reimplements format-detection Tika already provides for free); routing `.txt`
  content through Tika's own `TXTParser` end-to-end, relying on its charset detection to fail loudly
  on bad input (the original plan for this decision — rejected upon implementation: Tika's charset
  detection guesses rather than fails, so it does not reliably surface FR-005's undecodable-text edge
  case; a direct strict-UTF-8 decode does, deterministically, and is simpler to unit test than
  Tika's internal heuristics).

## Decision 2: Per-page text extraction for `.pdf` via a page-boundary-aware content handler

- **Decision**: `TextExtractor` parses `.pdf` files with a `ContentHandlerDecorator` that watches
  for Tika's PDF parser's per-page `<div class="page">` SAX markers in its XHTML output, splitting
  the accumulated text into one string per page as it goes. `.txt` files have no such structure;
  their extracted text is a single unpaged string.
- **Rationale**: FR-007 requires each chunk to retain the source page number for formats with page
  structure. Tika's `PDFParser`, driven through `AutoDetectParser` with the default SAX content
  handler, already emits a page boundary marker per page in its structured output — capturing that
  during parsing is the direct way to know which page a given span of text came from, and keeps
  page-awareness inside the one Tika-based extraction step rather than a second, format-specific
  pass.
- **Alternatives considered**: `PDFTextStripper.setStartPage`/`setEndPage` in a loop, calling
  PDFBox (Tika's PDF module dependency) directly per page (rejected — works, but steps outside
  Tika's own parser abstraction that Decision 1 committed to, and re-detects "this is a PDF"
  information `AutoDetectParser` already established); concatenating all pages into one string and
  leaving `page_number` `NULL` for every PDF chunk (rejected — contradicts FR-007 and defeats the
  citation quality `poc-concept.md`'s walkthrough example depends on, e.g. "travel-expense-policy.pdf
  (p. 5)").

## Decision 3: Token-accurate chunking via `jtokkit`'s `cl100k_base` encoding

- **Decision**: `Chunker` tokenizes extracted text with `jtokkit` (pure Java, no native
  dependencies) using the `cl100k_base` encoding, then builds sequential windows of a fixed target
  size (800 tokens) with a fixed overlap (100 tokens, 12.5% — inside the constitution's 10–15%
  band), decoding each window's token span back to text via the same encoding. The 500–1000 target
  applies to interior windows; the final chunk of a document (and the sole chunk of a short
  document) may fall under 500 tokens, because there is nothing left to merge it with without
  violating the same range on its neighbor — the range constrains chunk size, not document length.
  **Implementation refinement**: windows are built independently per `ExtractedPage`, not across the
  whole document's concatenated text. This keeps every chunk's page number exact — one real source
  page per chunk, never an average or a guess (FR-007) — and treats a following page as a fresh
  reading context for chunking purposes, exactly as a whole short document's sole chunk may be short.
  A page's own trailing remainder chunk may therefore be short even when it is not literally the
  document's last chunk overall; this is a deliberate, narrow reading of "the last chunk of a
  document" that resolves the tension between this decision (document-wide token windows) and
  Decision 2 (page-first extraction) in favor of exact page attribution, which FR-007 states as a
  MUST while the shortness exception is phrased as a MAY.
- **Rationale**: FR-006 and the constitution both state the bound in tokens, not words or
  characters; a word-count approximation would only be approximately right, and "500–1000 tokens"
  is exactly the kind of number a test can assert precisely when the count is real.
  `text-embedding-3-small` (the constitution's mandated embedding model) is documented against the
  `cl100k_base` vocabulary, so token counts computed with it match what the embedding call will
  actually consume — relevant for staying under the model's input-token ceiling on large batched
  requests (Decision 4).
- **Alternatives considered**: naive whitespace/word-count approximation (≈0.75 tokens/word)
  (rejected — imprecise, and the constitution's bound is explicit enough to warrant exactness);
  sentence- or paragraph-boundary-aware chunking (rejected for this PoC — meaningfully more complex
  to implement and test correctly, and the constitution's stated goal, "semantically self-contained"
  chunks via overlap, is already served by the fixed-window-plus-overlap approach at this corpus's
  scale); a heavier tokenizer library with native bindings (e.g. a JNI wrapper around OpenAI's own
  `tiktoken`) (rejected — `jtokkit` gives the same encoding with no native dependency to build or
  ship, simpler for a PoC running on a developer machine).

## Decision 4: One batched embedding call per document, split only if the provider's batch limit requires it

- **Decision**: `EmbeddingClient` sends every chunk's text for a document as a single Azure OpenAI
  embeddings request (`EmbeddingRequest` with a list of inputs), receiving back one vector per
  input in the same order, rather than issuing one HTTP call per chunk. If a document's chunk count
  would exceed the embedding deployment's per-request batch limit — Azure OpenAI's embeddings API
  documents a 2048-input ceiling per request for `text-embedding-3-small` — `EmbeddingClient` splits
  that document's chunks into consecutive sub-batches of up to that many inputs each, issuing one
  request per sub-batch **before** any database write is attempted (Decision 5 still applies
  unchanged: nothing is persisted until every sub-batch has returned successfully). At this PoC's
  corpus scale (a handful to a few dozen chunks per sample document, FR-006's 500–1000-token chunks
  against `sample-data/documents/`'s largest file at under 12 KB) a document never needs more than
  one sub-batch in practice — the split path exists so FR-008/FR-009 stay correct if a future,
  larger document ever needs it, not because the sample corpus exercises it.
- **Rationale**: SC-001 targets ingestion "confirmed searchable within 15 seconds"; the embedding
  call is the dominant network cost in the pipeline, and batching turns "N chunks" into "one round
  trip" (or a small, bounded number of round trips) regardless of N. It also simplifies the
  atomicity story (Summary, plan.md): each sub-batch call either returns a vector for every one of
  its inputs or throws — and a throw from any sub-batch means the whole document embeds nothing, per
  Decision 5 — so "every chunk embedded, or none persisted" (FR-008/009) falls directly out of the
  calls' own success/failure, with no partial-batch bookkeeping to write, even in the split case.
- **Alternatives considered**: one call per chunk (rejected — N round trips per document, working
  directly against SC-001, and reintroduces a partial-success case — "chunk 3 of 7 failed to
  embed" — that batching avoids entirely); parallel per-chunk calls (rejected — same partial-failure
  problem as sequential per-chunk calls, plus added concurrency complexity for a PoC-scale
  corpus that does not need it); ignoring the batch-limit ceiling entirely and assuming it is never
  reached (rejected — cheap to handle correctly now via a sub-batch loop, and leaving it unhandled
  would silently break FR-008/009 the first time a large enough document arrived, with no test able
  to catch it before then).

## Decision 5: Transaction opened only after every embedding is already in hand

- **Decision**: `IngestionService`'s write step is: parse (Tika) → chunk (jtokkit) → embed (one
  batched call) — all in memory, no database access — then open exactly one JDBC transaction that
  inserts the `documents` row and every `chunks` row (with its vector already populated), and
  commits. If parsing, chunking, or embedding fails, no transaction is ever opened.
- **Rationale**: FR-009 (all-or-nothing per document) and FR-008 ("no chunk row without a
  populated vector") are automatically satisfied by this ordering, not by a rollback-on-failure
  handler that has to remember every way a partial write could occur. There is exactly one
  transaction boundary in the whole pipeline, which is also the simplest thing to write a
  Testcontainers assertion against (research context for `DocumentIngestionIT`, plan.md).
- **Alternatives considered**: insert the `documents` row first, then insert chunks as their
  embeddings complete, rolling back the whole transaction on any failure (rejected — functionally
  equivalent once a transaction wraps everything, but invites a design where a chunk insert happens
  before its embedding exists, which is exactly the state FR-008 says must never be representable);
  per-chunk transactions with manual cleanup on failure (rejected — reintroduces the partial-state
  cleanup problem FR-009 exists specifically to rule out).

## Decision 6: The Azure OpenAI embedding model is built by hand, gated by its own completeness check

- **Decision**: add an `isEmbeddingComplete()` method to the existing `AzureOpenAiProperties`
  (`backend/.../health/AzureOpenAiProperties.java`, feature 001) requiring `apiKey`, `endpoint`,
  and `embeddingDeploymentName` all non-blank — distinct from the existing `isComplete()`, which
  checks the **chat** deployment name and is used by the health indicator. `EmbeddingClient`
  checks `isEmbeddingComplete()` before constructing an `AzureOpenAiEmbeddingModel` (via
  `OpenAIClientBuilder` + `AzureKeyCredential`, the same construction pattern
  `AzureOpenAiConnectivityIT` already uses for `AzureOpenAiChatModel`); if incomplete, the request
  fails immediately with a distinguishable "provider not configured" error and no network call.
- **Rationale**: `application.yml` pins `spring.ai.model.embedding: none` (feature 001, Decision 4)
  specifically so the application boots without Azure credentials — Spring AI's
  auto-configured `EmbeddingModel` bean is therefore never available to `@Autowire`. Building the
  model by hand, on demand, inside the one component that needs it, is the same choice
  `AzureOpenAiConnectivityIT` already made for chat, for the same reason: it keeps the "boots
  without credentials" guarantee intact while still allowing a real call when configuration is
  present. Embedding completeness is a **different** check than the existing `isComplete()` (which
  intentionally excludes the embedding deployment name, per feature 001's FR-023, because it is
  purely a chat/health signal) — reusing it here would either wrongly permit ingestion with a chat
  deployment but no embedding deployment, or wrongly require a chat deployment ingestion does not
  need.
- **Alternatives considered**: re-enabling `spring.ai.model.embedding: azure-openai` and
  `@Autowire`-ing Spring AI's bean (rejected — reopens the exact startup failure feature 001,
  Decision 4 closed: `AzureOpenAiClientBuilderConfiguration` fails fast with "Endpoint must not be
  empty" when credentials are absent, breaking the "app starts unconfigured" requirement for every
  developer without Azure access); a third, separate properties class duplicating
  `AzureOpenAiProperties` (rejected — the four values and their wiring already exist; adding one
  method is less duplication than a parallel class reading the same four `@Value` paths).

## Decision 7: `pgvector`'s Java helper for the `vector` column, plain `JdbcTemplate` for everything else

- **Decision**: `DocumentRepository` uses `org.springframework.jdbc.core.JdbcTemplate` for every
  insert. The one column plain JDBC has no built-in mapping for — `chunks.embedding vector(1536)`
  — is bound via `com.pgvector:pgvector`'s `PGvector` type (a `PGobject` implementation the
  PostgreSQL JDBC driver already knows how to send as a parameter). `documents.content bytea` binds
  through a plain `byte[]` parameter, exactly as `PreparedStatement.setBytes` already handles it —
  no helper needed there, consistent with feature 003's Decision 2.
- **Rationale**: Feature 001/003 already committed to no JPA/Hibernate; this feature has no reason
  to reopen that. `pgvector`'s helper is the smallest possible addition that lets a `float[]`
  become the SQL `vector` type through ordinary `JdbcTemplate.update(...)` calls — the alternative
  is hand-formatting the pgvector text literal (`"[0.1,0.2,...]"`) and casting it in SQL, which
  works but is exactly the kind of string-building code a maintained helper exists to avoid.
- **Alternatives considered**: hand-formatted vector literal string + `::vector` cast in the SQL
  (rejected — works, matches feature 003's `quickstart.md` manual `psql` examples, but is
  needless string-formatting code for something a five-year-old, narrowly-scoped library already
  does correctly); introducing Spring Data JDBC or JPA at this point (rejected — no new
  justification has appeared since 001/003 rejected it; this feature is a writer against an
  existing schema, not a reason to change data-access strategy).

## Decision 8: HTTP status codes distinguish invalid input from processing failure

- **Decision**: `POST /documents` responds `201 Created` with `{ documentId, chunkCount }` on
  success; `400 Bad Request` for FR-002/003/005 (unsupported type, empty/oversized file, unparseable
  content — all input-level problems, nothing was attempted downstream); `503 Service Unavailable`
  for FR-009's failure case (embedding call failed, provider unconfigured, or a database write
  failed) — the input was valid, but the system could not currently process it. Both error
  responses share one JSON shape: `{ "error": "<machine-readable-code>", "message":
  "<human-readable>" }`.
- **Rationale**: FR-011 requires the response itself to let a caller distinguish "retry pointless
  without changing the file" from "retry might succeed once the transient condition clears" — the
  4xx/5xx split is exactly that distinction in HTTP's own vocabulary, so no additional response
  field is needed to carry it; the status code *is* the signal, and `error`/`message` carry the
  specifics for logging/display.
- **Alternatives considered**: `200 OK` with a `success: false` body for all failures (rejected —
  discards the standard HTTP signal FR-011 needs, and every HTTP client/test tool already
  understands status-code semantics for free); `422 Unprocessable Entity` for parse failures
  instead of `400` (rejected — a defensible alternative, but `400` keeps all three input-validation
  cases — type, size, parseability — under one status the way FR-002/003/005 group them
  conceptually; a future API-versioning pass can split this further if a real client needs to).

## Decision 9: Four-tier test strategy reusing the `db`/`azure` tag convention

- **Decision**: unit tests for `Chunker` and the PDF page-splitter (pure functions, no I/O) and a
  `MockMvc` contract test for `DocumentController` (stubbed `EmbeddingClient` and
  `DocumentRepository`) run in the default `mvn test`. A `@Tag("db")` `DocumentIngestionIT`
  (Testcontainers `pgvector/pgvector:pg18`, **stubbed** embedding model returning fixed-length fake
  vectors) proves the real transaction/atomicity/cascade behavior against a real database, gated
  behind the existing `verify-db` profile (feature 003). A `@Tag("azure")` test extending
  `AzureOpenAiConnectivityIT`'s pattern makes one real batched embedding call against the configured
  deployment, gated behind the existing `verify-ai` profile.
- **Rationale**: Directly implements constitution Principle II's layered testing requirement (unit,
  integration, contract) and its explicit "Tests MUST NOT require live AI provider credentials to
  pass" clause — only the `azure`-tagged test touches a real Azure endpoint, and it is opt-in, not
  part of `mvn test`. Reusing feature 001/003's exact tag names and profile names, rather than
  inventing new ones, keeps one convention for "this test needs infrastructure X" across the whole
  backend instead of a second one just for ingestion.
- **Alternatives considered**: a single end-to-end test requiring both Docker and live Azure
  credentials (rejected — conflates two independent infrastructure requirements into one gate,
  and cannot run at all in an environment with one but not the other, e.g. CI with Docker but no
  Azure secret); mocking pgvector's `vector` column type in the contract test instead of stubbing
  the repository entirely (rejected — `MockMvc` contract tests exist to prove the web layer's
  request/response contract, not JDBC/pgvector behavior, which the `db`-tagged test already owns).

## Open questions

None. `spec.md`'s one clarification (empty-text documents → store with zero chunks, FR-015) is
already resolved and carried into Decision 5's transaction design (a zero-chunk document still
commits — parse succeeds, chunk list is empty, "every chunk embedded" is vacuously true for zero
chunks, the document row is still written).

Previously flagged for awareness, now resolved directly in Decision 4: the embeddings API's
per-request input-count ceiling (2048 inputs for `text-embedding-3-small`) is handled by a
sub-batching loop inside `EmbeddingClient`, not merely assumed unreachable. Decision 5's
transaction-after-embedding design already accommodated this without change (nothing about it
assumes exactly one embedding call), so resolving Decision 4 required no change anywhere else in
this document.

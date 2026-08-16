# Phase 0 Research: Chat Endpoint (Retrieve → Augment → Generate)

**Date**: 2026-08-16 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

Nine decisions. No `[NEEDS CLARIFICATION]` markers remain — both ambiguities the spec had (max
question length, page-less citation display) were already resolved by `/speckit-clarify` before this
document was written. Most of *what* to build was already fixed by the ratified constitution's Query
Pipeline section and by feature 003's `similarity-search-contract.md`; the decisions below are about
*how* it fits into the existing codebase.

## Decision 1: A new `chat` package and controller, not another verb on `DocumentController`

- **Decision**: `POST /chat` lives in a new `com.epam.aihelpdesk.chat` package with its own
  `ChatController`, sibling to `ingestion` and `health` — not a new method on the existing
  `DocumentController`.
- **Rationale**: Features 005 and 006 added verbs (`GET`, `DELETE`) to `DocumentController` because
  each one operated on the *same* `/documents` resource `POST /documents` already defined. `/chat` is
  a different resource entirely — it doesn't create, list, fetch, or delete a document; it answers a
  question using documents as a read-only knowledge source. Grouping it under `ingestion` would mix
  two bounded contexts (document lifecycle management vs. question answering) in one package for no
  benefit.
- **Alternatives considered**: adding `chat()` as a method on `DocumentController` (rejected — no
  shared resource identity with `/documents/{id}`; the ingestion package's existing Javadoc already
  scopes itself to "the `/documents` resource," which a `/chat` method would silently violate).

## Decision 2: Route is the bare `POST /chat`, JSON body in and out

- **Decision**: One endpoint, `POST /chat`, consuming and producing `application/json` — a
  `{question, documentIds?}` request body, a `{answer, sources}` response body. No query parameters,
  no multipart (unlike `POST /documents`, which is a file upload).
- **Rationale**: Matches the constitution's literal `POST /chat` naming and the ordinary REST
  convention for an action that isn't CRUD against a single resource instance — a question is data
  being submitted for processing, not an entity being created at a discoverable URI.
- **Alternatives considered**: `GET /chat?question=...` (rejected — a question can be arbitrarily long
  free text up to 1000 characters, awkward and non-idiomatic as a query string, and optional
  `documentIds` filtering would compound that); `/documents/{id}/chat` (rejected — a question is not
  scoped to one document by default; the optional filter is a request-body detail, not a URL
  hierarchy).

## Decision 3: `EmbeddingClient` gains one reused method; its exceptions are translated at the package boundary

- **Decision**: `EmbeddingClient` (feature 004, package `ingestion`) gets one new public method,
  `embedQuery(String text)`, sharing its existing `buildModel()`/deployment-name/credential
  construction with the existing `embed(List<ChunkDraft>)` method. `ChatService` calls it directly
  (a cross-package Spring bean dependency) and catches the `IngestionProcessingException` it can
  throw, re-raising it as this feature's own `ChatProcessingException` with the same `errorCode`
  (`provider_unconfigured` or `processing_failed`) and cause.
- **Rationale**: Constructing an `AzureOpenAiEmbeddingModel` from `AzureOpenAiProperties` is
  non-trivial, already implemented once, and already tested — duplicating it in the new `chat`
  package would risk exactly the kind of two-copies-drift the constitution's "one embedding
  deployment for the whole corpus" principle warns against (a second, subtly different construction
  path could end up pointing at a different deployment). Reuse is the simpler, safer choice. But
  `IngestionProcessingException`'s own Javadoc explicitly scopes it to the ingestion pipeline — the
  same reasoning feature 006's Decision 6 already used to justify a new `DocumentDeletionException`
  instead of reusing it. Translating at the one call site in `ChatService` keeps `EmbeddingClient`'s
  existing contract untouched for its original caller (`IngestionService`) while giving `chat` its
  own, correctly-scoped exception vocabulary.
- **Alternatives considered**: a second, `chat`-local embedding client duplicating `buildModel()`
  (rejected — drift risk, no benefit); having `embedQuery` throw `ChatProcessingException` directly
  from inside `EmbeddingClient` (rejected — would make an `ingestion`-package class depend on a
  `chat`-package exception type, inverting the dependency direction and coupling a lower-level,
  reusable client to one specific caller's error vocabulary).

## Decision 4: A new `ChatCompletionClient`, built the same way `AzureOpenAiConnectivityIT` already proves works

- **Decision**: A new `ChatCompletionClient` in the `chat` package builds an `AzureOpenAiChatModel`
  by hand (`OpenAIClientBuilder` + `AzureKeyCredential` + `AzureOpenAiChatOptions.deploymentName(...)`),
  gated by `AzureOpenAiProperties.isComplete()` (the existing chat-scoped completeness check, feature
  001) checked *before* any client is built — mirroring `EmbeddingClient`'s existing
  "check completeness first, never attempt a request with partial config" discipline.
- **Rationale**: `AzureOpenAiConnectivityIT` already demonstrates this exact construction pattern
  works end-to-end against a real Azure OpenAI chat deployment; reusing the identical
  builder/options shape (not Spring AI's auto-configuration, which `application.yml` deliberately
  disables via `spring.ai.model.chat: none` so the app still boots with no Azure credentials, feature
  001 Decision 4) means the same reasoning that keeps the app bootable without credentials continues
  to hold for this feature.
- **Alternatives considered**: Spring AI auto-configured `ChatModel` bean injection (rejected — would
  re-trigger the exact startup failure feature 001's Decision 4 deliberately avoids when credentials
  are absent, per `application.yml`'s own comment on why all four `spring.ai.model.*` properties must
  stay `none`).

## Decision 5: Retrieval reuses feature 003's documented query verbatim; the similarity threshold is a post-filter, not part of the `WHERE` clause

- **Decision**: `ChatRetrievalRepository` issues exactly the query feature 003's
  `similarity-search-contract.md` already specifies:

  ```sql
  SELECT c.document_id, c.chunk_id, c.source_filename, c.page_number, c.text,
         c.embedding <=> :query_vector AS distance
  FROM chunks c
  [WHERE c.document_id = ANY(:document_ids)]   -- only when a document filter is supplied
  ORDER BY c.embedding <=> :query_vector
  LIMIT 4;                                       -- top-K, constitution default
  ```

  The 0.5 cosine-similarity threshold (`similarity = 1 - distance`; keep rows where `distance <= 0.5`)
  is applied afterward, in `ChatService`, against the already-limited 4-row result — not folded into
  the SQL as `WHERE (1 - (embedding <=> :query_vector)) >= 0.5`.
- **Rationale**: The constitution's own wording is "if top-K similarity scores are all below
  threshold" — a check performed *on* the top-K set, not a filter that could make the candidate pool
  itself larger or smaller before ranking. Applying the threshold in SQL would change behavior in an
  edge case the constitution doesn't intend: a document with, say, 6 chunks above 0.5 similarity would
  still only contribute at most 4 (the top-K cap), and the two-step "limit, then threshold" order
  keeps that guarantee obvious from the query text alone. It also means the threshold constant lives
  in one place (`ChatService`, alongside `TOP_K`) rather than being embedded in a SQL string.
- **Document filter**: `document_id = ANY(?)` is added only when `documentIds` is non-empty, bound as
  a `java.sql.Array` (`connection.createArrayOf("uuid", ids)`) via a `PreparedStatementSetter` — the
  same approach `pgvector`'s own vector binding already requires custom parameter handling for, so
  this introduces no new binding technique to the codebase, only a second use of one.
- **Alternatives considered**: threshold in SQL (rejected, reasoning above); a separate `COUNT`-first
  existence check for the document filter (rejected — unnecessary; an empty result set from the
  `SELECT` itself already means "nothing relevant," which is exactly FR-007's required outcome for a
  non-matching filter, no special-casing needed).

## Decision 6: Citations are computed from retrieval results, never parsed from the model's answer text

- **Decision**: After the threshold filter, surviving `RetrievedChunk` rows are grouped by
  `(documentId, pageNumber)`, keeping the lowest-`distance` (highest-similarity) row per group, sorted
  by similarity descending. This becomes the `sources` list directly — the model's generated answer
  text is never scanned or parsed to decide what to cite.
- **Rationale**: This is what makes SC-005 ("zero mismatched or fabricated citations") true by
  construction rather than by hoping the model's citation behavior matches its actual context use:
  every entry in `sources` is, by definition, a passage that was included in the prompt sent to the
  model. FR-008's own wording ("every document that contributed a retrieved passage") is satisfied
  exactly this way. Deduplicating by `(documentId, pageNumber)` (not by `documentId` alone) satisfies
  spec.md's edge case that two contributing passages from the same document on different pages must
  both be visible, without listing the same document-and-page combination twice.
- **Page-less display** (spec.md Clarifications, Session 2026-08-16): a group whose `pageNumber` is
  `null` (feature 003's "no page structure" value, e.g. a `.txt` source) renders the fixed string
  `"no page structure"` in the citation's `page` field instead of a number or a numeric placeholder.
- **Alternatives considered**: asking the model to emit structured citations (e.g. "cite as
  `[doc:page]`") and parsing them out of its answer (rejected — adds a parsing failure mode, and
  provides no correctness guarantee the deterministic approach doesn't already give for free; the
  constitution's fixed system prompt already asks the model to "cite your sources" in prose for
  *readability*, not as the mechanism this feature relies on for the structured `sources` field).

## Decision 7: The "not covered" outcome is a plain `200`, using the constitution's own fixed wording; only a literal empty completion is auto-converted to it after generation

- **Decision**: When no passage survives the threshold (empty corpus, non-matching document filter,
  or every candidate too weak), `ChatService` returns `ChatResponse("I don't have this information in
  the documentation.", [])` directly — the exact fixed string the constitution's own system prompt
  separately instructs the model to use — without calling `ChatCompletionClient` at all. Separately,
  if a chat completion call *does* happen (context passed the threshold) but returns a blank/empty
  string (spec.md Edge Cases), the result is converted to the same fixed response after the fact.
  Spec.md scopes this literally to "empty completion" — no attempt is made to pattern-match the
  model's own prose for a "soft refusal" phrased differently from the fixed string.
- **Rationale**: Reusing the constitution's exact fixed wording for both the short-circuit case and
  the empty-completion case means a caller sees one string for "not covered" regardless of which code
  path produced it — there's no risk of two subtly different "I don't know" messages existing in the
  same feature. Treating this as a `200`, not an error, matches spec.md FR-007's framing (this is a
  valid, expected answer content, not a failure) and keeps it structurally distinct from the `503`
  "could not process" outcome (Decision 8) by both status code and response shape (`ChatResponse` has
  no `error` field; `ChatErrorResponse` has no `sources` field).
- **Alternatives considered**: detecting refusal phrases in the model's own generated text via string
  matching (rejected — brittle, not required by any FR/edge case, and unnecessary because the
  threshold gate already prevents generation from weak context in the first place).

## Decision 8: A new, chat-scoped exception hierarchy and error handler

- **Decision**: `ChatException` (abstract, carries `errorCode`, mirrors `IngestionException`) with two
  subclasses — `InvalidChatRequestException` (`blank_question`, `question_too_long`,
  `malformed_request` → `400`) and `ChatProcessingException` (`provider_unconfigured`,
  `processing_failed` → `503`) — plus a new `ChatErrorHandler` (`@RestControllerAdvice`) mapping them
  to a new `ChatErrorResponse` record (`{error, message}`, same shape as `DocumentErrorResponse` but a
  separate class).
- **Rationale**: Same reasoning as feature 006's Decision 6 (a new `DocumentDeletionException` rather
  than reusing `IngestionProcessingException`): `DocumentErrorResponse`'s own Javadoc already
  enumerates a fixed, closed set of `error` values scoped to the three `/documents` endpoints. Adding
  `/chat`'s five values to that enumeration would broaden a class whose documented purpose is
  `/documents`-specific, and would make `chat` (a new bounded context, Decision 1) depend on a DTO
  that conceptually belongs to `ingestion`. A same-shape sibling class costs nothing extra and keeps
  each feature's error vocabulary documented next to the code that actually throws it.
- **`malformed_request`**: required by spec.md FR-016 (added during `/speckit-checklist` review) and
  also a technical necessity — an unreadable JSON body (empty, truncated, or a non-UUID string inside
  `documentIds`) throws Spring's `HttpMessageNotReadableException` before `ChatController`'s own
  validation logic ever runs, exactly the situation `DocumentErrorHandler`'s existing
  `MissingServletRequestPartException` handler already covers for `POST /documents`. Collapsing it
  into its own `400 malformed_request` (rather than
  conflating it with `blank_question`) keeps each error code's meaning precise, matching this
  codebase's general preference (e.g. `invalid_file` vs. `unsupported_type` vs. `unparseable` staying
  separate in feature 004) over collapsing unrelated causes into one code purely for brevity.
- **Alternatives considered**: reusing `DocumentErrorResponse`/`InvalidDocumentException`/
  `IngestionProcessingException` (rejected — scope drift, reasoning above); a single `ChatException`
  with no subclasses, `errorCode` alone deciding status (rejected — every other exception hierarchy
  in this codebase encodes the HTTP status in the type, not just a string field, so `@ExceptionHandler`
  method signatures stay exhaustive and a missing mapping is a compile-visible gap, not a runtime one).

## Decision 9: Three-tier test strategy, no new dependency or `pom.xml` profile

- **Decision**: `contract` (default suite, `MockMvc`, `ChatRetrievalRepository`/`ChatCompletionClient`
  stubbed or the service itself stubbed) / `@Tag("db")` (`verify-db` profile, Testcontainers, real
  pgvector query against hand-seeded known vectors, `ChatCompletionClient` stubbed via
  `@MockitoBean`) / `@Tag("azure")` (`verify-ai` profile, one live grounded-answer call and one live
  not-covered call, mirroring `AzureOpenAiConnectivityIT`'s existing opt-in pattern). All three tags
  and Maven profiles (`db`, `azure`, `verify-db`, `verify-ai`) already exist in `pom.xml` — no build
  file change needed.
- **Rationale**: Mirrors the exact tiering features 005/006 already established, extended with the
  `azure` tier this feature is the first to actually need for its *own* logic (previous features'
  `azure` tier only ever verified generic Azure reachability, not feature-specific behavior). A
  `db`-tier test with real seeded vectors is the only way to prove the pgvector `<=>` ranking and
  threshold behavior against a real database, matching feature 005/006's own justification for why a
  fully-stubbed contract test alone is insufficient for anything touching the real database engine.
- **Alternatives considered**: skipping the `azure` tier entirely (rejected — this is the first
  feature where a live chat completion call is core business logic, not just a generic connectivity
  check; SC-001's ≥80% accuracy bar needs *some* automated proof the wiring produces a real answer,
  even though the full `evaluation-questions.csv` run itself stays a separate, manual/CI activity per
  the constitution).

## Open questions

None.

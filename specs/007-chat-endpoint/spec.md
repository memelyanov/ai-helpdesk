# Feature Specification: Chat Endpoint (Retrieve → Augment → Generate)

**Feature Branch**: `007-chat-endpoint`

**Created**: 2026-08-16

**Status**: Draft

**Input**: User description: "Implement the chat endpoint (retrieve → augment → Azure OpenAI chat completion → answer + sources)."

## Clarifications

### Session 2026-08-16

- Q: What should the maximum allowed question length (FR-012) be? → A: 1000 characters (~200 words) — a middle-ground limit generous enough for a detailed question while keeping embedding/token cost and abuse risk bounded.
- Q: For a citation whose source document has no page structure (plain `.txt`, where `page_number` is `NULL` per feature 003), what should the citation show in place of a page number? → A: A short, explicit indicator that the document has no page structure (e.g. "no page structure") rather than omitting the field or showing a numeric placeholder.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ask a question and get a grounded, cited answer (Priority: P1)

An employee has a question about company policy or process ("Can I expense a taxi from the
airport?"). They type the question into the helpdesk. The system finds the passages in the ingested
document corpus that are actually relevant to the question — regardless of whether the question uses
the same words as the documents — asks the AI to answer using only those passages, and shows the
employee both the answer and exactly which document(s) it came from, so they can trust it or go
verify it themselves.

**Why this priority**: This is the entire reason the corpus exists. Features 003–006 built the
ability to store, ingest, list, and delete documents, but none of them let anyone actually ask a
question. Without this, the document corpus is unusable for its stated purpose. This single
capability is the PoC's core value proposition and its primary evaluation metric (≥80% retrieval
accuracy).

**Independent Test**: Can be fully tested by ingesting a small set of known documents (via feature
004), asking a question whose answer is known to live in one specific document using wording that
does not literally appear in that document, and confirming the response contains a correct,
on-topic answer that cites that document — without needing the listing, download, or delete
endpoints to exist.

**Acceptance Scenarios**:

1. **Given** a corpus containing a document about business travel expense policy, **When** a user
   asks "Can I expense a taxi from the airport?" (wording that does not literally appear in the
   document), **Then** the response contains an answer synthesized from that document's content and
   cites that document as a source.
2. **Given** a corpus containing multiple documents, **When** a user asks a question whose answer
   draws on passages from more than one of them, **Then** the response cites every document that
   contributed to the answer, not just one.
3. **Given** a corpus containing multiple unrelated documents, **When** a user asks a question that
   is only answered by one of them, **Then** the response does not cite the unrelated documents.
4. **Given** a successful answer, **When** the response is returned, **Then** each cited source
   identifies the specific document (filename) and page it came from, plus a retrieval confidence
   score, so the employee can judge how strongly the answer is grounded.
5. **Given** a corpus with multiple documents, **When** a user asks a question while restricting the
   search to one specific document, **Then** the response is grounded only in passages from that
   document, even if a more relevant passage exists in a document the search was restricted away
   from.
6. **Given** a typical question (well within the 1000-character maximum, FR-012) asked against the
   project's full sample corpus, **When** the question is submitted, **Then** a complete response
   (answer or "not covered" outcome) is received within 10 seconds of wall-clock time.

---

### User Story 2 - Get an honest "I don't know" instead of a made-up answer (Priority: P1)

An employee asks a question that the ingested documents simply do not cover ("What's the CEO's cell
phone number?", or any question unrelated to the corpus). Instead of the system inventing a
plausible-sounding but false answer, it tells the employee plainly that the documentation doesn't
cover this, so they know to ask a human instead of acting on a fabricated answer.

**Why this priority**: This is co-equal with User Story 1 in priority. A helpdesk that answers
confidently and incorrectly is worse than one that has no chat feature at all — it actively misleads
the people relying on it. The constitution's No Hallucination principle makes this a hard
requirement, not a nice-to-have, and the PoC's credibility depends on demonstrating it works.

**Independent Test**: Can be fully tested by ingesting a small, known corpus, asking a question with
no relevant answer anywhere in it, and confirming the response is the fixed "not in documentation"
outcome rather than any generated, cited answer — without needing User Story 1's citation display to
be exercised.

**Acceptance Scenarios**:

1. **Given** a corpus that contains no content relevant to a question, **When** the user asks that
   question, **Then** the response clearly states the documentation does not cover it, cites no
   sources, and does not attempt to answer from the AI's general knowledge.
2. **Given** a corpus where the closest matching passages are only weakly related to the question
   (below the system's relevance threshold), **When** the user asks that question, **Then** the
   system treats it the same as "no relevant content found" rather than generating an answer from
   weak, likely-irrelevant context.
3. **Given** no documents have ever been ingested into the corpus, **When** any question is asked,
   **Then** the response is the same "not in documentation" outcome, not an error.

---

### User Story 3 - Get a clear error when the system itself is unable to answer (Priority: P2)

An employee asks a question at a moment when the underlying AI service or the document store is
unavailable or misconfigured. Instead of a confusing crash, a silently wrong answer, or being told
(incorrectly) that the documentation doesn't cover their question, they receive a distinct message
telling them the system couldn't process their question right now and to try again later.

**Why this priority**: This depends on User Stories 1 and 2 existing first — it is about correctly
distinguishing "the system is broken" from "the documentation doesn't have this," which only matters
once both of those outcomes exist to be confused with each other. It protects user trust: a
generic failure must never be reported as if it were the honest "I don't know" of User Story 2.

**Independent Test**: Can be fully tested by simulating an AI provider or database failure (e.g.
invalid/unreachable Azure OpenAI configuration) while asking any question, and confirming the
response is a distinct "couldn't process" error — never the "not in documentation" wording, and
never a raw stack trace or unhandled exception.

**Acceptance Scenarios**:

1. **Given** the Azure OpenAI service is unreachable or unconfigured, **When** a user asks a
   question, **Then** the response is a clear, distinct error indicating the system could not
   process the request, and it is never worded or coded the same as the "not in documentation"
   outcome.
2. **Given** the document store is unreachable when a question is asked, **When** the search for
   relevant passages fails, **Then** the response is the same kind of distinct processing error, not
   a silent empty result treated as "nothing relevant found."

---

### Edge Cases

- What happens when the question is empty or blank? The system MUST reject it with a clear
  validation error rather than sending an empty question to retrieval or the AI provider. This
  applies to any non-meaningful input the same way: the system does not attempt to judge whether
  non-blank text is meaningful natural language — anything that survives this blank check and
  FR-012's length check is treated as an ordinary question, and content with no genuinely relevant
  match in the corpus naturally resolves to the FR-007 "not covered" outcome rather than needing a
  separate rejection rule.
- What happens when the question exceeds the maximum length (1000 characters, FR-012)? The system
  MUST reject it with a clear validation error rather than silently truncating it or failing deep
  inside processing. A question of exactly 1000 characters is accepted; 1001 characters or more is
  rejected — the limit itself is inclusive.
- What happens when the request body cannot be understood as a question submission at all — for
  example malformed or empty JSON, or a document filter entry that isn't a validly formatted document
  identifier? The system MUST reject it with a validation error distinct from a blank or over-length
  question (FR-016), and MUST NOT attempt retrieval or generation.
- What happens when a caller supplies a document filter (restricting the search to specific
  documents) that matches no ingested document at all — whether every identifier in the filter is
  unknown, or the filter mixes a real identifier with one that matches nothing? The system MUST treat
  this the same as "no relevant content found" (User Story 2), not as an error; the filter simply
  narrows the search to whichever supplied identifiers do match, and finds nothing if none do.
- What happens when two or more of the top retrieved passages come from the same document? The
  response's source list MUST still be clear about which distinct passages/pages contributed,
  without listing the same document-and-page combination redundantly.
- What happens when the AI provider returns a response that itself claims no answer is available,
  or an empty completion? The system MUST treat this the same as the "not in documentation" outcome
  rather than surfacing an empty or blank answer to the user. This is distinct from FR-013's
  processing-failure outcome: a reachable, correctly configured provider that completes the request
  but returns nothing usable is not a system failure and MUST NOT be reported as one.
- What happens when a question is asked immediately after a document referenced by an in-flight
  answer is deleted (feature 006)? This feature does not need to guard against that race; the answer
  already retrieved and returned is unaffected, and any later question naturally reflects the
  corpus's current state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a way for a caller to submit a plain-text question and
  receive a generated answer.
- **FR-002**: The system MUST convert the incoming question into a vector embedding using the same
  embedding process used when documents were ingested (the ingestion pipeline
  [feature 004](../004-document-ingestion-endpoint/spec.md) established), so questions and document
  content are comparable.
- **FR-003**: The system MUST search the document corpus for the passages most semantically similar
  to the question and use only those passages — never a caller-supplied answer, never the AI
  provider's general knowledge — as the basis for the generated answer.
- **FR-004**: The system MUST limit retrieval to a bounded number of the most relevant passages per
  question (default: the 4 most similar), so the generated answer is grounded in a focused,
  manageable set of context rather than the entire corpus. Because a successful answer's source list
  (FR-008) is drawn only from these retrieved passages, it can never contain more entries than this
  bound, regardless of corpus size.
- **FR-005**: The system MUST discard retrieved passages whose similarity to the question falls
  below a defined relevance threshold (default: a similarity score of 0.5), and MUST treat "every
  retrieved passage fell below the threshold" identically to "nothing relevant was found" (FR-007). A
  passage whose similarity is exactly equal to the threshold counts as meeting it — the threshold is
  inclusive, so only a similarity strictly below 0.5 is discarded.
- **FR-006**: When at least one sufficiently relevant passage is found, the system MUST generate an
  answer using only the content of the retrieved passages, and the answer MUST NOT include claims
  that cannot be traced back to that content. This is enforced by construction — the model is never
  given anything beyond the retrieved, threshold-passing passages to answer from — rather than by
  checking the generated text against those passages after the fact; there is no post-hoc
  fact-checking step in this feature's scope.
- **FR-007**: When no sufficiently relevant passage is found for a question — including an empty
  corpus, a document filter matching nothing, or every candidate falling below the relevance
  threshold — the system MUST return a fixed, explicit "the documentation does not cover this"
  response instead of a generated answer, and this response MUST cite no sources.
- **FR-008**: A successful generated answer MUST be accompanied by the list of every document that
  contributed a retrieved passage to it; a document that did not contribute MUST NOT appear in that
  list. "Contributed" means the passage survived FR-005's relevance threshold and was included in the
  generation prompt — a passage that was retrieved but discarded for falling below the threshold did
  not contribute and MUST NOT produce a source entry. This list and the FR-007 "not covered" outcome
  are mutually exclusive: this list is only ever non-empty when FR-006 applies, and is always empty
  exactly when FR-007 applies.
- **FR-009**: Each entry in the source list MUST identify, at minimum, the source document's
  filename, the page the contributing passage came from, and a retrieval confidence score (the
  passage's similarity to the question), so a caller can judge how strongly the answer is grounded
  without having to open the source document first. For a source document with no page structure
  (e.g. a plain-text upload, where feature 003's `page_number` is `NULL`), the citation MUST show a
  short, explicit indicator that the document has no page structure instead of a page number or a
  numeric placeholder.
- **FR-010**: The system MUST allow a caller to optionally restrict retrieval to a specific set of
  documents (identified the same way features 005 and 006 identify a document); when supplied, only
  passages from that set are eligible for retrieval. Omitting the filter entirely and supplying an
  explicitly empty set of identifiers MUST behave identically — both mean "no restriction, search the
  whole corpus"; this filter has no way to express "search nothing." A filter entry that is not a
  validly formatted document identifier is a distinct validation failure (FR-016), not silently
  ignored and not treated as a non-matching identifier.
- **FR-011**: The system MUST reject a blank, empty, or missing question with a clear validation
  error and MUST NOT attempt retrieval or generation for it. Validation (this, FR-012, and FR-016)
  MUST always run, and MUST always complete, before any retrieval or generation is attempted — so a
  single request can only ever land in one of FR-007/FR-011/FR-012/FR-013/FR-016's outcomes, never
  two.
- **FR-012**: The system MUST reject a question that exceeds 1000 characters with a clear validation
  error, distinct from the validation error for a blank question.
- **FR-013**: When an unexpected server-side failure prevents the system from completing retrieval
  or generation — the AI provider is unreachable, unconfigured, or errors; the document store is
  unreachable — the system MUST report a distinct "could not process this question" error. This
  error MUST NOT be worded, coded, or otherwise presentable in a way that a caller could mistake for
  the "documentation does not cover this" outcome of FR-007.
- **FR-014**: Each question is handled independently; the system MUST NOT require or depend on any
  previous question or answer to process the current one (no multi-turn conversation memory in this
  feature's scope).
- **FR-015**: The system MUST NOT log, echo, or otherwise expose any AI provider credential in a
  question response, error message, or log line.
- **FR-016**: The system MUST reject a request whose body cannot be understood as a valid question
  submission — malformed or unreadable JSON, or a document filter entry that is not a validly
  formatted document identifier — with a validation error distinct from FR-011's (blank question) and
  FR-012's (over-length question), and MUST NOT attempt retrieval or generation for it.
- **FR-017**: The system MUST record a structured log entry for every request sufficient to determine
  its outcome (accepted with a generated answer, "not covered," a specific validation rejection, or a
  processing failure) for diagnostic review, without requiring the question's verbatim text to be
  logged — consistent with FR-015, a log entry MUST NOT be a vector for exposing anything a caller
  would not otherwise see in the response itself.

### Key Entities

This feature introduces no new persisted entities. It is a new reader of the `Document` and `Chunk`
entities that [specs/003-document-vector-schema/spec.md](../003-document-vector-schema/spec.md)
defines, [specs/004-document-ingestion-endpoint/spec.md](../004-document-ingestion-endpoint/spec.md)
is the first writer of, and that features 005 and 006 also read and remove:

- **Question**: a caller's plain-text input for one chat exchange, plus an optional set of document
  identifiers restricting which documents are eligible for retrieval. Not persisted.
- **Chunk**: an embedded, searchable segment of a document's text (defined in feature 003) — this
  feature is the first to retrieve chunks by semantic similarity to answer a question, rather than
  only counting or storing them.
- **Answer**: the generated response for one question — the synthesized text, plus zero or more
  cited sources. Not persisted; produced fresh for each question.
- **Source Citation**: one entry in an answer's source list — the contributing document's filename
  and page, and the retrieval confidence (similarity score) of the passage that contributed it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For at least 80% of the questions in the project's curated evaluation set
  (`sample-data/evaluation-questions.csv`, each row naming its expected source document), the
  response's cited sources include that expected document — measured by comparing each question's
  returned `sources` list against the row's expected-document column — matching the project's
  mandated retrieval accuracy bar.
- **SC-002**: 100% of the evaluation set's questions with no answer in the corpus receive the
  "documentation does not cover this" outcome, with zero cited sources and zero fabricated answer
  content.
- **SC-003**: A caller receives a complete answer (or the "not covered" outcome) for any question up
  to the maximum allowed length (1000 characters, FR-012) against the project's full sample corpus in
  under 10 seconds of wall-clock time. A response that eventually succeeds but exceeds 10 seconds is a
  slow success, not a failure this feature needs to detect or report specially — this criterion is a
  performance target to measure against, not a behavior to implement.
- **SC-004**: 100% of requests made while the AI provider or document store is unavailable receive
  the distinct "could not process" error rather than a crash, a hang, or a misleading "not covered"
  result.
- **SC-005**: Every cited source in every successful answer correctly identifies a document and page
  that genuinely exists in the corpus and genuinely contributed a retrieved passage — in FR-008's
  precise sense of "contributed" (survived FR-005's threshold and was included in the generation
  prompt), not merely "was retrieved before thresholding." Zero mismatched or fabricated citations
  across the evaluation set, verified the same way as SC-001: each returned source's document and
  page checked against what the corpus actually contains.

## Assumptions

- **Single-turn, stateless chat**: consistent with the constitution's "LLM calls are stateless"
  requirement, this feature handles one question and one answer per request with no server-side
  conversation history. Multi-turn conversation context is out of scope and deferred to a future
  feature if needed.
- **Document filter shape**: an optional filter restricts retrieval to a caller-supplied set of
  document identifiers — UUIDs, the same identifier format features 005 and 006 already expose and
  require — matching the constitution's "optional document filters" language. Filtering by other
  criteria (date range, filename pattern) is out of scope.
- **Fixed defaults, not per-request tuning**: the number of passages retrieved (default 4) and the
  relevance threshold (default similarity 0.5) are system-wide defaults consistent with the
  constitution's Query Pipeline section, not something an individual request can override in this
  feature's scope. These defaults are identical for every request — not merely a starting point that
  happens to be the same, but a fixed constant this feature defines no mechanism to vary.
- **No authentication/authorization**: consistent with features 004–006 and the constitution's
  current PoC-phase scope, any caller who can reach this endpoint may ask any question against the
  entire corpus (subject to any document filter they themselves supply).
- **No new persistence**: questions and answers are not stored anywhere; this feature only reads the
  existing `documents`/`chunks` tables. This was a deliberate exclusion, not an oversight: a helpdesk
  tool would ordinarily want question/answer audit history, but the constitution's "LLM calls are
  stateless" requirement and this feature's single-turn scope (above) rule it out here. Conversation
  history and audit logging of questions/answers, if ever needed, are a future feature's concern.
- **No concurrency or throughput target beyond SC-003's per-request latency**: this feature adds no
  request queuing, rate limiting, deduplication, or locking for concurrent or identical in-flight
  questions — every request is independent (FR-014) and only ever reads shared state, so concurrent
  requests (including two callers asking the same question at once) need no special coordination,
  consistent with this PoC's expected single- or few-user load (the same scale features 004–006
  already assume).
- **Distinct error vocabulary**: consistent with features 004–006, this feature's three validation
  failures (FR-011 blank, FR-012 over-length, FR-016 malformed), its "not covered" outcome (FR-007),
  and its processing failure (FR-013) are five distinct, non-overlapping response outcomes a caller
  can always tell apart — FR-011's validation ordering guarantee means a single request only ever
  produces exactly one of them.

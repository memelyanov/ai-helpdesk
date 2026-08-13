<!--
SYNC IMPACT REPORT
=================
Version Change: 1.2.1 → 1.3.0 (MINOR: mandated LLM provider switched to Azure OpenAI)
Status: Amended — provider substitution ratified, configuration contract added
Last Amendment: 2026-08-13

**Principles Established/Updated:**
- I. Spec-First (Documentation-First) — UNCHANGED
- II. Test-Driven Development (Mandatory) — UNCHANGED
- III. Grounded Answers (RAG-First) — UNCHANGED
- IV. No Hallucination (Context Adherence) — UNCHANGED
- V. Semantic Understanding (Meaning-Based Retrieval) — AMENDED (embedding provider is now an
  Azure OpenAI deployment of `text-embedding-3-small`; the retrieval obligation is unchanged)
- VI. Data Sovereignty (Self-Hosted Vectors) — AMENDED (strengthened: Azure OpenAI's enterprise
  terms materially improve the posture this principle exists to protect; the production gate is
  restated rather than removed)
- VII. Quality Validation (≥80% Retrieval Accuracy) — UNCHANGED

**Rationale for Version Bump (MINOR):**
The mandated inference provider changes from the OpenAI API to Azure OpenAI. No principle is
removed, and none is redefined in what it obliges — Principles V and VI keep their obligations and
change only the named provider that satisfies them. The amendment materially expands guidance: a
configuration contract, a one-model-per-deployment constraint, and an explicit deployment-name
indirection that did not previously exist.

MAJOR was considered and rejected. This constitution reserves MAJOR for "backward incompatible
principle removal or redefinition (e.g., switching from RAG to fine-tuning)" — a change to what
the project believes. A provider substitution that leaves every principle's obligation intact is a
change to how those beliefs are implemented. The change is backward-incompatible for code, but no
implementation exists yet: the repository is at the scaffolding-specification stage, which is
precisely why this amendment is being made now rather than later.

**Sections Updated:**
- Core Principles → V (embedding provider), VI (data sovereignty posture)
- Technology Stack & Tooling Requirements → LLM & Embeddings row rewritten
- Development & Integration Requirements → new "AI Provider Configuration" subsection; provider
  references updated in Chunking & Embedding Strategy, Ingestion Pipeline, Query Pipeline, and
  Error Handling & Logging
- Governance → Compliance Review gains a credential-handling check

**Dependent Artifacts:**
- ✅ README.md — stack table and ingest/ask flow updated
- ✅ docs/poc-concept.md — stack and integrations updated, with a dated amendment note preserving
  the original decision record rather than overwriting it silently
- ✅ .specify/templates/*.md — verified: contain no provider references, no edits required
- ⚠ docs/demo-deck.html — still states OpenAI. This is a delivered presentation from the Week 1
  concept session; it is a record of what was presented on that date, not a living document.
  Left unchanged deliberately. Update it only if the deck will be re-presented.
- ⚠ specs/001-project-scaffolding/plan.md, research.md, contracts/, quickstart.md — predate both
  this amendment and the Azure clarifications in spec.md. Research Decision 2 directly contradicts
  the current spec. Regenerate with `/speckit-plan` before implementation.

**Follow-up TODOs:**
- Provision the Azure embedding deployment and set `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME`;
  ingestion cannot proceed without it.
-->

# AI Helpdesk Constitution

## Core Principles

### I. Spec-First (Documentation-First)

No implementation work starts without a written specification. For every feature the order is
fixed: **spec → plan → tasks → code**. A feature specification MUST exist at
`specs/<###-feature-name>/spec.md` and MUST state the user-visible behaviour, the acceptance
scenarios, and the edge cases before any design or code is produced. An implementation plan
(`plan.md`) MUST follow, and a task breakdown (`tasks.md`) MUST derive from both. Code that
arrives without a corresponding spec entry is rejected in review, regardless of quality.

Specifications describe **what and why**, never **how** — implementation detail belongs in
`plan.md`. Ambiguity MUST be marked explicitly (`[NEEDS CLARIFICATION: ...]`) rather than
silently resolved by the implementer. When behaviour changes during implementation, the spec
MUST be amended in the same pull request; documentation that contradicts shipped behaviour is
treated as a defect, not as stale text.

Living project documents — `README.md`, `docs/poc-concept.md`, and this constitution — MUST stay
truthful about the current state of the repository. A change that makes any of them inaccurate is
incomplete until they are updated.

**Rationale**: The PoC exists to prove a concept, and a concept that is not written down cannot
be evaluated, reviewed, or handed over. Writing the spec first surfaces disagreement while it is
still cheap — before code, in prose everyone can read. It also makes the evaluation criteria
(retrieval accuracy, refusal behaviour, citations) explicit up front rather than negotiated after
the fact.

### II. Test-Driven Development (Mandatory)

All code MUST be written using Test-Driven Development (TDD). The discipline is: (1) write a failing test that captures the desired behavior, (2) implement the minimum code to make it pass, (3) refactor for clarity and efficiency. Tests MUST be written and passing **before** code is committed. Every feature completion gate MUST include test coverage proof. Test types are mandatory at appropriate layers:

- **Unit tests**: Verify individual functions, services, and utilities in isolation (e.g., chunking logic, embedding model calls, vector similarity scoring).
- **Integration tests**: Verify end-to-end RAG flows: document upload → chunk → embed → vector storage; query → retrieve → augment → LLM response.
- **Contract tests**: Verify REST API contracts: `/documents` POST returns expected schema; `/chat` POST handles edge cases (empty query, no results, malformed input).
- **Evaluation tests**: Run the evaluation set against every release candidate; ≥80% accuracy MUST be maintained or regression is flagged.

Testing MUST include negative cases: malformed PDFs, empty queries, out-of-scope questions, API failures, network timeouts.

Tests MUST NOT require live AI provider credentials to pass. Suites that would otherwise call the
provider MUST stub it, so that a clean checkout runs green for a developer with no Azure access.

**Rationale**: For a system making claims about information accuracy to users, comprehensive test coverage is non-negotiable. TDD ensures correctness, documents expected behavior, and prevents regressions. Without TDD discipline, hallucination, silent data loss, and incorrect citations would go undetected. Placing TDD immediately after Spec-First reflects the intended order of work: the spec states the behaviour, the test encodes it, the code satisfies it.

### III. Grounded Answers (RAG-First)

Every answer provided by the system MUST be sourced from the indexed document corpus. The system implements Retrieval-Augmented Generation (RAG): retrieve the semantically closest chunks from the vector database, then use an LLM to synthesize an answer from those chunks only. Every answer MUST include an explicit citation of the source document(s) and page/chunk reference where applicable. RAG is mandatory over fine-tuning to ensure documents remain fresh, retrievable, and not baked into model weights.

**Rationale**: This principle ensures trust, traceability, and compliance. Users know exactly where their answer comes from and can verify it. Documents can be updated without model retraining.

### IV. No Hallucination (Context Adherence)

When the vector search returns no sufficiently relevant chunks for a user question, the system MUST reply *"I don't have this in the documentation"* rather than inventing an answer or providing generic guidance. The system prompt MUST explicitly enforce: "Answer only from the provided context; do not invent information; if the answer is not in the context, say so clearly."

**Rationale**: Hallucinated answers erode trust. Honest refusal to answer out-of-scope questions is a feature, not a failure. This is a hard requirement for production deployability.

### V. Semantic Understanding (Meaning-Based Retrieval)

The system MUST match user queries to documents by *meaning* rather than exact keyword overlap.
Queries and documents are embedded into vectors via an **Azure OpenAI deployment of
`text-embedding-3-small`**. Retrieval uses vector similarity search (not keyword search) so
vocabulary mismatch does not prevent finding relevant answers. For example, "Can I expense a taxi
from the airport?" MUST retrieve "Business Travel Expense Reimbursement Policy" even though the
documents do not share the words "taxi" or "airport."

The same embedding deployment MUST be used for documents at ingestion time and for queries at
search time. Vectors produced by different models are not comparable, so changing the embedding
deployment invalidates the entire index and MUST be treated as a re-ingestion event, not a
configuration tweak.

**Rationale**: Keyword search fails on vocabulary mismatch — the core problem the helpdesk solves. Semantic search via embeddings bridges this gap and dramatically improves discoverability.

### VI. Data Sovereignty (Self-Hosted Vectors)

Proprietary company documents MUST NOT be embedded into LLM model weights via fine-tuning. The
system MUST store all vector embeddings and document chunks in a self-hosted PostgreSQL + pgvector
database under the organization's control. LLM calls are stateless: query embedding → similarity
search → context assembly → generation.

Inference MUST run through **Azure OpenAI** rather than the public OpenAI API. Azure OpenAI serves
this principle better on three counts that matter to it: the deployment lives in a tenant and
region the organization selects, prompt and completion content is not used to train shared models,
and access is governed by the organization's existing cloud identity and network controls. This
substitution narrows the production gap but does not close it — before proprietary data is
processed, a data-handling review MUST confirm the tenant, region and retention settings, and a
self-hosted or on-premises model MUST still be evaluated where the review demands that external
inference be eliminated entirely.

**Rationale**: Compliance, data residency, and intellectual property protection. The PoC runs on synthetic data, so no review gates it; a production system carries real documents and does.

### VII. Quality Validation (≥80% Retrieval Accuracy)

The system MUST achieve at least **80% precision** on a curated evaluation set: for each test question, the correct source document MUST appear in the top-K (K ≈ 4–6) retrieved chunks in at least 80% of cases. Evaluation questions deliberately avoid source document wording to ensure semantic retrieval works, not keyword luck. This is the primary success metric for the PoC and a non-negotiable gate before scaling to larger corpora.

**Rationale**: An 80% bar ensures the system is reliably useful. Below 80%, users will fall back to manual search or colleagues, defeating the helpdesk's purpose.

## Technology Stack & Tooling Requirements

| Layer | Mandated Choice | Rationale |
|---|---|---|
| **Language / Runtime** | Java 17, Spring Boot 3 | Aligns with Java discipline track; strong Spring AI integration |
| **RAG Orchestration** | Spring AI (alt: LangChain4j) | Native Spring integration for embeddings, vector stores, LLM chains |
| **LLM & Embeddings** | **Azure OpenAI** — a chat deployment (`gpt-4o-mini` class) and a separate embedding deployment (`text-embedding-3-small`) | Same model quality and cost profile as the public API, with tenant-scoped data handling, regional residency, and existing organizational credentials. Embeddings are computed once per document, not per query |
| **Vector Database** | PostgreSQL + pgvector | One database for vectors and metadata; trivial to run in Docker; no separate vector-DB infra overhead |
| **Document Parsing** | Apache Tika | Unified API for `.pdf`, `.txt`, and future formats; no format-specific parsing code |
| **API Layer** | Spring Web REST (`POST /documents`, `POST /chat`) | Minimal surface, stateless, easy to front with auth middleware |
| **Frontend** | Angular 21 | Single-page app for chat view and document upload; can be deployed independently |
| **Infrastructure** | Docker Compose (PostgreSQL + pgvector); backend & frontend run locally (PoC phase) | Minimal setup for a PoC; no orchestration overhead during concept phase |

**Stability & Deprecation**: No dependency is eligible for replacement without a constitution amendment. Stack choices are frozen for the PoC to ensure deliverability. Future phases may revisit (e.g., self-hosted LLM for production data security).

## Development & Integration Requirements

### AI Provider Configuration

- Azure OpenAI configuration MUST be read from the environment. The mandated variable names are
  those already present in the developer environment, and MUST NOT be renamed:

  | Variable | Purpose | Secret |
  |---|---|---|
  | `AZURE_OPEN_AI_KEY` | API key | **Yes — never committed** |
  | `AZURE_OPEN_AI_ENDPOINT` | Service endpoint URL | No |
  | `AZURE_OPEN_AI_DEPLOYMENT_NAME` | Chat deployment | No |
  | `AZURE_OPEN_AI_EMBEDDING_DEPLOYMENT_NAME` | Embedding deployment | No |

- Azure OpenAI binds **one model per deployment**. Chat and embeddings therefore require two
  distinct deployments and two distinct names; a single deployment name MUST NOT be reused for
  both.
- Code MUST reference models through deployment names, never through hardcoded model identifiers.
  A deployment name is chosen by whoever provisions the Azure resource and cannot be assumed to
  match the underlying model name.
- The application MUST start when AI provider variables are absent or incomplete, reporting the
  provider as unconfigured. Configuration counts as complete only when key, endpoint and chat
  deployment name are all present; a partially populated environment MUST report as unconfigured
  rather than being treated as usable.
- No API key, endpoint or deployment name may be committed. A committed template
  (`.env.example`) MUST document every variable with non-secret placeholders.

### Chunking & Embedding Strategy

- Documents MUST be split into chunks of 500–1000 tokens with 10–15% overlap to maintain semantic self-containment.
- Each chunk MUST retain metadata: `source_filename`, `page_number`, `chunk_id`.
- Embeddings MUST be generated via the Azure OpenAI embedding deployment at ingestion time, not query time (cost/latency optimization).
- Chunk vectors MUST be stored in the PostgreSQL pgvector extension with exact-match columns for metadata filtering.

### Ingestion Pipeline

- The `/documents` REST endpoint MUST accept `.txt` and `.pdf` uploads.
- Apache Tika MUST parse the document; parsing errors MUST fail explicitly (not silently skip content).
- Chunks MUST be embedded via the Azure OpenAI embedding deployment; failed embeddings MUST be retried or explicitly logged.
- Vectors and metadata MUST be written atomically to pgvector (all chunks from one document succeed or all fail).
- Upon successful ingestion, the endpoint MUST return the document ID and chunk count to the client.

### Query Pipeline

- The `/chat` REST endpoint MUST accept a user question (plain text) and optional document filters.
- The question MUST be embedded via the same Azure OpenAI embedding deployment used at ingestion time.
- A vector similarity search MUST retrieve the top-K (default K=4) nearest chunks by cosine similarity.
- A system prompt MUST be constructed: `"Answer the following question based ONLY on the context provided. If the answer is not in the context, respond with 'I don't have this information in the documentation.' Always cite your sources."`
- The Azure OpenAI chat deployment MUST be called with `[question] + [retrieved chunks] + [system prompt]`.
- The response MUST include: the generated answer, list of source documents (filename + page), and retrieval confidence (similarity scores).
- If top-K similarity scores are all below a threshold (default: 0.5 cosine similarity), the system MUST return the "not in documentation" response instead of generating from weak context.

### Error Handling & Logging

- All external API calls (Azure OpenAI, pgvector) MUST be wrapped in try-catch; failures MUST log stack traces and return a 500 error with a user-friendly message.
- Structured logging MUST be enabled: log each embedding request, retrieval query, and LLM call with request/response summaries for debugging.
- API keys MUST NOT appear in logs, error messages, or responses under any circumstance.
- Failed document uploads MUST not partially index; rollback is mandatory.

### Testing & Validation

- Functional tests MUST verify: document upload, chunking, embedding, retrieval, and answer generation as end-to-end flows.
- The evaluation set (`sample-data/evaluation-questions.csv`) MUST be run after each major change; ≥80% accuracy MUST be maintained.
- Negative tests MUST verify: out-of-scope questions return "not in documentation", malformed PDFs are rejected, empty queries are handled gracefully.

## Governance

### Amendment Procedure

This constitution supersedes all prior informal guidance. Amendments MUST be documented in writing and ratified by project leadership. Version bumps follow semantic versioning:
- **MAJOR**: Backward incompatible principle removal or redefinition (e.g., switching from RAG to fine-tuning).
- **MINOR**: New principle or substantial expansion of guidance (e.g., adding a new non-negotiable constraint, or substituting a mandated dependency).
- **PATCH**: Clarifications, wording fixes, typo corrections, or non-semantic refinements.

### Compliance Review

- All PRs MUST include a "Constitution Compliance" checklist verifying adherence to the seven core principles (Spec-First, TDD, Grounded Answers, No Hallucination, Semantic Understanding, Data Sovereignty, Quality Validation).
- Every PR that changes behaviour MUST link the `spec.md` it implements, and MUST include any spec amendment the change implies (Spec-First principle enforcement).
- All PRs MUST include proof of test coverage and passing tests (TDD principle enforcement).
- All PRs MUST be checked for committed credentials; any API key, endpoint or deployment name found in tracked files blocks the merge.
- The evaluation set (≥80% retrieval accuracy) MUST be run and reported in the PR before merging to main.
- Any deviation from the mandated tech stack MUST be approved as a constitution amendment, not a casual code choice.

### Runtime Guidance

See [`CLAUDE.md`](CLAUDE.md) in the repository root for ongoing development practices, CI/CD workflows, code style, and agent-specific tooling. This constitution is the *governance layer*; `CLAUDE.md` is the *operational layer*.

---

**Version**: 1.3.0 | **Ratified**: 2026-08-13 | **Last Amended**: 2026-08-13

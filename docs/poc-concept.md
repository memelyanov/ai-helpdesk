# AI Helpdesk — PoC Concept

**Jira:** EPMGDPL-3139 — [Week 1] Java General Task 2 — PoC Concept Definition
**Parent story:** EPMGDPL-3137 — #5 Java [Main Story] AI Learning & PoC Development
**Author:** Michael Emelyanov
**Discipline:** Java

> **Amended 2026-08-13** — the mandated inference provider changed from the public OpenAI API to
> **Azure OpenAI**, ratified in constitution v1.3.0. Models are unchanged; they are now addressed
> by Azure *deployment name* rather than model name, and chat and embeddings require two separate
> deployments. §8 is left as written: it records the outline of the Week 1 concept session as it
> was delivered, and `docs/demo-deck.html` still reflects that original version.

---

## 1. Problem Statement

A company accumulates a large and growing set of internal documents (`.txt`, `.pdf`) describing different aspects of its work processes — onboarding guides, security policies, expense rules, release procedures, HR regulations, tooling instructions.

The documents exist and are formally "available", but they are effectively unfindable:

- **Keyword search fails on vocabulary mismatch.** A user asks *"how do I get money back for a taxi to the airport?"* while the document says *"Business Travel Expense Reimbursement Policy"*. Zero keyword overlap → zero results.
- **The user does not know which document to open.** Finding the answer requires already knowing where it lives.
- **The answer is a paragraph, not a file.** Even a correct hit returns a 30-page PDF; the user still has to read it.
- **Knowledge lives in people.** Because search is painful, employees ask colleagues instead — turning a self-service question into an interruption.

**Cost of the problem:** repeated time loss on every routine question, inconsistent answers, senior staff used as a lookup service, and stale documents nobody notices because nobody reads them.

## 2. PoC Idea

Build an **AI Helpdesk chatbot** that answers natural-language questions about the company's internal documents, grounded in the documents themselves.

The concept rests on three capabilities:

1. **Upload documents to a vector database** — ingest `.txt` / `.pdf` files, split them into chunks, convert each chunk into an embedding vector, and store it.
2. **RAG search over the vector database** — turn the user's question into an embedding and retrieve the semantically closest chunks, matching on *meaning* rather than exact words.
3. **Use an Azure OpenAI LLM to answer** — pass the retrieved chunks to the model as context so it produces a direct, readable answer **with a citation of the source document**.

The key shift: the user stops searching for *a document* and starts asking for *an answer*.

## 3. Target User & Scenario

**Primary user:** any employee (new joiner, engineer, manager) who needs a process answer and does not know the document landscape.
**Secondary user:** the helpdesk / support team, who currently answer the same questions manually.

### Walkthrough

> **User:** "Can I expense a taxi from the airport when I travel for work?"
>
> **System:**
> 1. Embeds the question.
> 2. Retrieves the top-K most similar chunks — three of them come from `travel-expense-policy.pdf`, one from `corporate-card-rules.txt`.
> 3. Sends question + chunks to the Azure OpenAI LLM with an instruction to answer *only* from the provided context.
>
> **Answer:** "Yes. Ground transportation between the airport and your hotel or office is reimbursable for approved business travel. Submit the receipt within 30 days of return. Rides above €80 require manager pre-approval.
> *Sources: travel-expense-policy.pdf (p. 5), corporate-card-rules.txt*"

The user got the answer in one turn, plus a link to verify it. No keyword guessing, no colleague interrupted.

**Out-of-scope answer behaviour:** if retrieval returns nothing sufficiently relevant, the bot must reply *"I don't have this in the documentation"* rather than inventing an answer. This is a hard requirement, not a nice-to-have.

## 4. Expected Value

| Dimension | Before | After (PoC target) |
|---|---|---|
| Time to find a process answer | Minutes to hours, often ends in asking a person | Seconds, self-service |
| Query formulation | Must guess the document's exact wording | Plain language, no terminology knowledge needed |
| Output | A whole PDF to read | A paragraph + source citation |
| Load on senior staff / helpdesk | Repeated manual answers | Reduced to genuine edge cases |
| Trust | — | Every answer is traceable to a source document |

## 5. AI Capabilities and Platform Interaction

### 5.1 Ingestion pipeline (offline / on upload)

| Step | What happens |
|---|---|
| Upload | User uploads `.txt` / `.pdf` via REST endpoint |
| Parse | Text extracted (Apache Tika handles both formats) |
| Chunk | Split into ~500–1000 token chunks with overlap, to keep each chunk semantically self-contained |
| Embed | Each chunk → vector via the **Azure OpenAI embedding deployment** (`text-embedding-3-small`) |
| Store | Vector + text + metadata (`filename`, `page`, `chunk_id`) written to the vector database |

### 5.2 Query pipeline (online)

| Step | What happens |
|---|---|
| Ask | User submits a natural-language question |
| Embed query | Question → vector via the same Azure OpenAI embedding deployment used at ingestion |
| Retrieve | Vector similarity search returns top-K (K ≈ 4–6) nearest chunks |
| Augment | Chunks assembled into a prompt with a system instruction: *answer strictly from the context; if not present, say so; always cite the source* |
| Generate | The **Azure OpenAI chat deployment** (`gpt-4o-mini` class for the PoC) produces the answer |
| Return | Answer + list of source documents returned to the chat UI |

### 5.3 Why RAG rather than fine-tuning

- Documents change; re-indexing a file takes seconds, retraining does not.
- Retrieval gives **citations** — a fine-tuned model cannot tell you where its answer came from.
- No proprietary content is baked into model weights; the corpus stays in our own storage.

## 6. Data Inputs, Tooling and Integrations

### 6.1 Data inputs

- **Corpus:** 16 internal-style documents (7 `.txt`, 9 `.pdf`, ~107k characters) covering onboarding, expenses, security, vacation, release process, IT support, benefits, conduct and facilities — see [`sample-data/documents/`](../sample-data/documents/). Entirely synthetic: a fictional company, no real confidential data. Five of the sixteen are distractors that answer no evaluation question, so retrieval has a plausible wrong answer available.
- **Evaluation set:** a CSV of test questions, each paired with the document that *should* be retrieved and the expected answer (see [`sample-data/evaluation-questions.csv`](../sample-data/evaluation-questions.csv)). Questions deliberately avoid the documents' own wording, so the test proves semantic search rather than keyword luck. [`sample-data/README.md`](../sample-data/README.md) sets out the question-to-document vocabulary gaps.

### 6.2 Proposed tech stack

| Layer | Choice | Rationale |
|---|---|---|
| Language / runtime | **Java 17, Spring Boot 3** | Matches the Java discipline track and the course examples |
| RAG orchestration | **Spring AI** (alt: LangChain4j) | Native Spring integration for embeddings, vector stores and chat models |
| LLM & embeddings | **Azure OpenAI** — chat deployment (`gpt-4o-mini` class), separate embedding deployment (`text-embedding-3-small`) | Same quality/cost profile as the public API, plus tenant-scoped data handling and credentials the team already holds |
| Vector database | **pgvector** on PostgreSQL (alt: Chroma / Qdrant) | One database for vectors and metadata; trivial to run in Docker |
| Document parsing | **Apache Tika** | Handles `.pdf` and `.txt` behind one API |
| API | Spring Web REST — `POST /documents`, `POST /chat` | Minimal surface, consumed by the Angular frontend |
| Frontend | **Angular 21** — chat view + document upload view | Single-page chat UI talking to the REST API |
| Infra | PostgreSQL/pgvector in Docker; backend and frontend run locally | Minimal setup for a PoC — one container, no orchestration overhead |

### 6.3 Integrations

- **Azure OpenAI** — embeddings + chat completions, addressed by deployment name rather than model name. Key, endpoint and both deployment names come from environment variables and are never committed.
- **PostgreSQL + pgvector** — vector store, run via Docker Compose.
- **Local filesystem / upload endpoint** — document source for the PoC. A future step could point ingestion at Confluence or SharePoint instead.

### 6.4 Risks and mitigations

| Risk | Mitigation |
|---|---|
| Hallucinated answers | Strict "answer only from context" system prompt; mandatory citations; explicit "not in documentation" fallback |
| Poor retrieval on long PDFs | Tune chunk size/overlap; evaluate against the CSV question set |
| Sensitive data leaving the perimeter | Synthetic corpus for the PoC; note that a production version would need a data-handling review or a self-hosted model |
| Cost | `gpt-4o-mini` + `text-embedding-3-small`; embeddings computed once per document, not per query |

## 7. PoC Success Criteria

The PoC is considered successful when:

1. A `.txt` and a `.pdf` document can be uploaded and are searchable immediately afterwards.
2. For the evaluation question set, the correct source document appears in the top-K retrieved chunks in **≥ 80%** of cases.
3. Answers cite the source document they came from.
4. A question with no coverage in the corpus produces an explicit "not found in documentation" answer instead of an invented one.
5. End-to-end response time is under ~5 seconds for a typical question.

## 8. Concept Presentation Outline (3–5 minutes)

| # | Segment | Time | Content |
|---|---|---|---|
| 1 | The pain | 0:30 | "Can I expense an airport taxi?" — keyword search over the policy corpus returns nothing, because the document says *"Business Travel Expense Reimbursement"*. Everyone recognises this. |
| 2 | The idea | 0:30 | AI Helpdesk chatbot: ask in plain language, get an answer grounded in the company's own documents, with a citation. |
| 3 | How it works | 1:30 | One diagram, two flows. **Ingest:** document → parse → chunk → OpenAI embeddings → vector DB. **Ask:** question → embedding → similarity search → top-K chunks → OpenAI LLM → answer + sources. One line on why RAG beats fine-tuning: freshness and citations. |
| 4 | Demo / walkthrough | 1:00 | The taxi question end to end; then an uncovered question showing the honest "I don't have this in the documentation" answer. |
| 5 | Stack & scope | 0:30 | Java 17 / Spring Boot 3 / Spring AI / OpenAI / pgvector / Tika, with an Angular 21 frontend. In scope: upload, RAG search, grounded answers. Out of scope: auth, production data. |
| 6 | Value & next steps | 0:30 | Self-service answers, less load on senior staff, traceable responses. Next: build ingestion + retrieval, measure against the evaluation CSV, demo in Week 2. |

**Backup slides:** chunking strategy, retrieval quality numbers, cost estimate, production considerations (access control, real data-source integration).

## 9. Scope Boundaries for the PoC

**In scope**
- Document upload (`.txt`, `.pdf`) and indexing into the vector database
- Semantic retrieval over the indexed corpus
- Grounded answer generation via Azure OpenAI with source citations
- Angular 21 frontend: chat view and document upload view
- Small evaluation set to measure retrieval quality

**Out of scope (explicitly)**
- Authentication, authorisation, per-user document permissions
- Visual design polish beyond a functional chat interface
- Real confidential company data
- Multi-language corpora, OCR of scanned PDFs, tables/images inside PDFs
- Conversation memory across turns (single-question/single-answer is enough to prove the concept)

## 10. Next Steps

1. ~~Assemble the synthetic document corpus and the evaluation CSV.~~ **Done** — see `sample-data/`.
2. ~~Start pgvector in Docker; scaffold the Spring Boot application locally against it.~~ **Done** —
   see `specs/001-project-scaffolding/`.
3. ~~Wire the Angular frontend to the backend's health check (connection-status indicator).~~
   **Done** — see `specs/002-frontend-health-wire/`.
4. ~~Design the `documents`/`chunks` database schema in pgvector (original-document storage,
   chunk vector + text + metadata, cascade delete, similarity-search traceability back to the
   source document).~~ **Done** — see `specs/003-document-vector-schema/`.
5. Implement the ingestion endpoint (Tika → chunking → Azure OpenAI embeddings → write into the
   schema from step 4).
6. Implement the chat endpoint (retrieve → augment → Azure OpenAI chat completion → answer + sources).
7. Build the Angular 21 chat view and document upload view against the REST API (the frontend so
   far only has the connection-status indicator from step 3, not these PoC-facing views).
8. Run the evaluation set, tune chunk size and K.
9. Present at the weekly update.

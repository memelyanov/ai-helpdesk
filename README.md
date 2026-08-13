# AI Helpdesk

An AI helpdesk chatbot that answers natural-language questions about a company's internal
documents (`.txt` / `.pdf`), grounded in the documents themselves and citing its sources.

**Status: concept phase.** No application code exists yet — this repository currently holds the
PoC concept and its evaluation dataset.

Proof of concept for Jira ticket
[EPMGDPL-3139](https://jiraeu.epam.com/browse/EPMGDPL-3139) — *[Week 1] Java General Task 2 —
PoC Concept Definition*.

## The problem

A company accumulates a large set of process documents — onboarding guides, security policies,
expense rules, release procedures. They exist, but they are effectively unfindable: keyword
search collapses on vocabulary mismatch. A user asks *"how do I get money back for a taxi to the
airport?"* while the document says *"Business Travel Expense Reimbursement Policy"* — zero
keyword overlap, zero results. So people ask colleagues instead, and a self-service question
becomes an interruption.

## The approach

Retrieval-Augmented Generation (RAG). The user stops searching for *a document* and starts asking
for *an answer*.

**Ingest** — document → parse (Apache Tika) → chunk → OpenAI embeddings → vector database.

**Ask** — question → embedding → vector similarity search → top-K chunks → OpenAI chat model →
answer + source citations.

Matching happens on *meaning* rather than exact words, and every answer is traceable back to the
document it came from. When retrieval finds nothing relevant, the assistant says so rather than
inventing an answer.

## Planned stack

| Layer | Choice |
|---|---|
| Language / runtime | Java 17, Spring Boot 3 |
| RAG orchestration | Spring AI (alt: LangChain4j) |
| LLM & embeddings | OpenAI — `gpt-4o-mini`, `text-embedding-3-small` |
| Vector database | pgvector on PostgreSQL (alt: Chroma / Qdrant) |
| Document parsing | Apache Tika |
| API | Spring Web REST — `POST /documents`, `POST /chat` |
| Frontend | Angular 21 — chat view + document upload view |
| Infra | PostgreSQL/pgvector in Docker; backend and frontend run locally |

## Current contents

| Path | Contents |
|---|---|
| [`docs/poc-concept.md`](docs/poc-concept.md) | Full PoC concept: problem, scenario, architecture, tooling, risks, success criteria, presentation outline |
| [`sample-data/evaluation-questions.csv`](sample-data/evaluation-questions.csv) | Evaluation set — 20 questions with expected source documents |
| `.specify/` | speckit workflow templates |

## Evaluation data

`sample-data/evaluation-questions.csv` is built to test the actual claim. Most questions are
flagged `tests_vocabulary_mismatch=yes` — they deliberately avoid the source document's own
wording, so a passing score means semantic retrieval works rather than keyword luck. One row is a
negative test with an empty expected source: the correct behaviour there is refusing to answer.

## Success criteria

1. A `.txt` and a `.pdf` can be uploaded and are searchable immediately afterwards.
2. The correct source document appears in the top-K retrieved chunks for **≥ 80%** of the
   evaluation questions.
3. Answers cite the source document they came from.
4. A question with no coverage produces an explicit "not found in documentation" answer.
5. End-to-end response time under ~5 seconds for a typical question.

## Out of scope for the PoC

Authentication and per-user document permissions, visual design polish beyond a functional chat
interface, real confidential data, OCR of scanned PDFs, and multi-turn conversation memory.

## Next steps

See [`docs/poc-concept.md`](docs/poc-concept.md) §10 for the implementation plan.

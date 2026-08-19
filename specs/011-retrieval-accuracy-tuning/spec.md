# Feature Specification: Retrieval Accuracy Tuning

**Feature Branch**: `011-retrieval-accuracy-tuning`

**Created**: 2026-08-19

**Status**: Draft

**Input**: User description: "The goal is to improve search answer accuracy. Based on manual testing and analysis already carried out, do the following: 1. Tune the threshold — instead of 0.5, which cuts off almost all retrieved information, lower it to 0.35. 2. Halve the chunk size, because the chunks currently seem too large and, besides the needed content, also contain a lot of extraneous information that only pollutes the query context."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A near-verbatim question gets a grounded answer instead of a refusal (Priority: P1)

A user asks a question whose wording closely matches text that genuinely exists in an uploaded
document. Today, the system's own similarity search finds that passage, but discards it before it
ever reaches the answer-generation step because the passage's relevance score falls just under the
current acceptance threshold — leaving the user with either "I don't have this information in the
documentation" or an answer built from too little context. After this change, the same question
retrieves and uses that passage, and the user receives an answer grounded in it.

**Why this priority**: This is the exact failure mode manual testing surfaced, and it directly
contradicts the product's core promise (Principle III/IV — grounded, non-hallucinated answers). A
user who knows the answer is in the documentation and still gets "I don't have this information" is
the single most damaging trust failure this system can produce.

**Independent Test**: Can be fully tested by asking questions drawn from — or closely paraphrasing —
sentences that exist verbatim in an already-ingested sample document, and confirming the response is
a grounded answer citing that document rather than the "not in documentation" fallback.

**Acceptance Scenarios**:

1. **Given** an uploaded document contains a sentence closely matching a user's question, **When**
   the user asks that question, **Then** the response is a grounded answer that cites the source
   document and page, not the "I don't have this information in the documentation" fallback — checked
   by the presence of that citation and the absence of the fallback string, not by matching the
   answer's exact wording (the generated answer's phrasing is not itself a requirement this spec
   constrains).
2. **Given** a user's question has no genuinely relevant content in any uploaded document, **When**
   the user asks it, **Then** the response is still the "I don't have this information in the
   documentation" fallback — lowering the acceptance bar must not cause the system to fabricate
   relevance where none exists.

---

### User Story 2 - Retrieved passages carry less unrelated surrounding content (Priority: P2)

A user asks a focused question. Today, each retrieved passage is large enough that it typically mixes
the directly relevant sentences with a substantial amount of unrelated surrounding material from the
same document section, diluting the context the answer is generated from. After this change,
retrieved passages are smaller and more topically focused, so a larger share of each passage's
content is actually relevant to the question.

**Why this priority**: This compounds with User Story 1 — even once a relevant passage clears the
acceptance bar, a passage padded with unrelated content still produces a weaker, less precise answer
than a tightly-scoped one. It matters less than US1 because a diluted-but-included passage is a
quality problem, not a total-refusal problem.

**Independent Test**: Can be fully tested by re-ingesting a sample document under the new chunking
behavior and comparing the passages retrieved for a known question against the previous, larger
passages — confirming the new passages are smaller and stay on-topic.

**Acceptance Scenarios**:

1. **Given** a document is ingested after this change, **When** a user asks a question matching one
   specific part of that document, **Then** the passage retrieved is noticeably smaller than before
   and still contains the matching content, with less unrelated surrounding text than a passage
   retrieved before this change would have had.
2. **Given** a single topic in a source document spans more text than one new, smaller passage can
   hold, **When** a user asks about that topic, **Then** the system retrieves one more passage per
   question than it did before this change (the retrieval breadth increases alongside the smaller
   passage size specifically to preserve coverage), so shrinking passage size does not cause a topic
   to lose coverage.

---

### User Story 3 - Retrieval quality does not regress on questions that already worked (Priority: P3)

A maintainer wants confidence that loosening the acceptance bar and shrinking passage size to fix the
false-refusal problem doesn't quietly make some other, previously-working class of question worse
(e.g. by admitting more only-tangentially-related passages, or by fragmenting a topic across so many
small passages that none of them individually contains a full answer).

**Why this priority**: Important as a safety check on the change, but it's a validation activity
around US1/US2 rather than new user-facing value of its own — hence lowest priority.

**Independent Test**: Can be fully tested by running the existing curated evaluation question set
before and after the change and comparing pass rates.

**Acceptance Scenarios**:

1. **Given** the existing evaluation question set, **When** it is run against the system after this
   change, **Then** its overall accuracy is equal to or better than before the change, on the same
   pass/fail criteria already in use.
2. **Given** a question that the system correctly refused (returned the "not in documentation"
   fallback) before this change, **When** the same question is asked after this change, **Then** it
   is still correctly refused — the lowered acceptance bar must not turn a previously-correct refusal
   into a new, incorrect answer.

---

### Edge Cases

*Two terms recur below and are used with a specific, consistent meaning: "genuinely relevant"
content is content a reviewer, shown the source document, would point to as what actually answers
the question; "weakly related" content shares vocabulary or general topic with the question but does
not itself contain the answer.*

- What happens to documents that were already uploaded before this change? Their existing passages
  were produced using the previous, larger passage size; this feature does not itself reprocess them
  (see Assumptions), so they keep returning larger passages at the previous acceptance bar's
  characteristics until someone removes and re-uploads them. The lowered relevance bar, in contrast,
  applies immediately to those same existing passages — it is a query-time check, not a property of
  how a passage was produced (see FR-007).
- What happens when a question is only weakly related to the documentation? The lowered acceptance
  bar must still exclude it — the system must keep returning the "not in documentation" fallback for
  genuinely out-of-scope questions (see User Story 1, Scenario 2), not merely admit more passages
  indiscriminately.
- What happens on a document short enough that shrinking passage size would previously have produced
  one passage and now produces several? All of the resulting smaller passages must still carry
  correct source, page, and ordering metadata, and remain eligible for retrieval individually. If more
  than one of those smaller passages lands on the same page, the existing citation behavior already
  collapses same-document-same-page passages into one citation entry — this feature does not change
  that, so more, smaller passages on one page never produce duplicate-looking citations.
- What happens on a document already short enough that it produced small passages even before this
  change (already under the new, smaller target)? Nothing changes for it — it continues to produce
  exactly the same single passage it always did; the new target only affects documents whose content
  was large enough to be split into multiple windows.
- What happens to a passage that sits exactly at the new acceptance bar? It must be treated as
  acceptable (inclusive bound), consistent with how the previous bar already treated an exact match
  as acceptable.
- What happens to a passage that would have cleared the *previous* acceptance bar (and was therefore
  already acceptable before this change)? It remains acceptable — the lowered bar only ever adds
  newly-acceptable passages, it never makes a previously-acceptable one unacceptable.
- What happens when a question is combined with a document filter (scoping the search to specific
  documents) at the same time as the lowered acceptance bar? The filter narrows which passages are
  considered at all, and the relevance bar is then applied to that narrowed set exactly as it would be
  to an unfiltered search — the two behave independently, filtering does not change what "relevant
  enough" means.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST lower the minimum relevance score a retrieved passage has to meet to be
  used in answer generation, from the current value to a new, lower value (see Assumptions for the
  exact value adopted), so that passages a manual test has shown to be genuinely relevant are no
  longer discarded before reaching the answer step.
- **FR-002**: The system MUST continue to return the fixed "I don't have this information in the
  documentation" response whenever none of the retrieved passages for a question meet the (new,
  lower) relevance bar — the change in question only widens what counts as "relevant enough" for
  questions that are actually near-verbatim matches to the documentation (the population SC-001
  measures); it does not remove the floor itself, and a genuinely out-of-scope question (SC-002's
  population — disjoint from SC-001's) MUST still be refused exactly as reliably as before.
- **FR-003**: The system MUST reduce the target size used when splitting a newly ingested document
  into retrievable passages, so that each passage contains proportionally less unrelated surrounding
  content than under the previous, larger size (see Assumptions for the exact value adopted).
- **FR-004**: The proportion of overlap kept between two consecutive passages from the same page MUST
  scale down together with the new, smaller passage size, preserving the same overlap ratio the
  system already maintains today (so passages continue to avoid losing context right at a
  passage boundary).
- **FR-005**: The new, smaller passage size MUST remain governed by the same project-wide bounds on
  passage size already in force for this system (see Assumptions for that bound and its source),
  rather than being picked independently of them.
- **FR-006**: Every passage produced under the new, smaller size MUST still carry the same source
  document, page number, and ordering metadata that passages carry today, unchanged in meaning.
- **FR-007**: The system MUST apply the new relevance bar and the new retrieval breadth (FR-009) to
  every question answered after this change goes live, and the new passage size to every document
  ingested after this change goes live — this is a behavior change, not an opt-in setting a user has
  to enable per question or per document. The relevance bar and retrieval breadth take effect
  immediately for questions against *already-ingested* passages too (they are query-time checks); the
  passage size does not retroactively apply to passages already produced before this change (see
  FR-008) — these two changes differ in when they take effect precisely because one is evaluated at
  query time and the other only at ingestion time.
- **FR-008**: Existing, already-ingested documents' previously-produced passages MUST NOT be silently
  deleted, corrupted, or mixed with differently-sized passages as an automatic side effect of this
  change; they continue to exist and remain retrievable exactly as before unless a document is
  explicitly removed and re-uploaded through the system's existing document-management capability.
- **FR-009**: The system MUST retrieve one additional passage per question, compared to the number it
  retrieved before this change (see Assumptions for the exact new count), so that a topic now split
  across more, smaller passages remains as coverable within one answer's retrieved set as a topic
  held in fewer, larger passages was before this change (User Story 2, Scenario 2).

### Key Entities

- **Retrieved Passage**: A unit of a source document's text the system can retrieve and hand to
  answer generation. This feature changes how large a newly-created passage is and how relevant a
  passage has to be judged before it is used, but does not change what information a passage carries
  (source document, page, text, ordering) or how it is produced from the source document
  (still per-document, still preserving page boundaries).
- **Relevance Bar**: The minimum relevance score a retrieved passage must meet to be used in
  answering a question. This feature lowers that minimum; it remains a single, project-wide value
  applied the same way to every question.
- **Retrieval Breadth**: The number of passages retrieved and considered per question. This feature
  increases that number by one, specifically to offset the smaller passage size (FR-009); it remains
  a single, project-wide value applied the same way to every question, not a per-question or
  per-document setting.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For questions phrased closely enough to match existing documentation wording that a
  manual reviewer would expect a grounded answer, the system produces a grounded, cited answer
  instead of the "not in documentation" fallback in at least 95% of such cases. **Verified manually**:
  a reviewer re-runs the same set of near-verbatim questions used during this feature's motivating
  manual test (the one that surfaced the false-refusal problem), records a pass/fail per question, and
  compares the resulting pass rate against that same manual test's outcome — no separate automated
  baseline dataset is required or introduced.
- **SC-002**: Questions with no genuinely relevant content in the uploaded documentation continue to
  receive the "not in documentation" fallback at the same reliability as before this change — this
  change MUST NOT be observable as an increase in ungrounded or fabricated answers. **Verified
  manually**: a reviewer re-asks the negative/out-of-scope questions already used in this project's
  existing manual and automated negative-test coverage (constitution Testing & Validation section)
  and confirms every one is still refused.
- **SC-003**: The project's existing curated evaluation question set
  (`sample-data/evaluation-questions.csv`) scores at least as high after this change as it did before,
  on the same evaluation methodology already in use. **Verified manually**: a reviewer runs the set
  before applying this change and again after, records the pass count each time, and compares the two
  side by side — no new automated regression-tracking tooling is introduced.
- **SC-004**: For a question matching one specific, narrow part of a document, a reviewer judges the
  passages returned after this change to contain a higher proportion of directly relevant content
  (less unrelated surrounding text) than the passages that would have been returned before this
  change for the same question. **Verified manually**: the reviewer compares the "before" and "after"
  passage text for the same document/question side by side, using this spec's definition of
  "genuinely relevant" (see Edge Cases) as the comparison criterion.

## Assumptions

- The user-specified new relevance bar (0.35) is adopted directly as the new minimum — it replaces
  the previous value as the system's one, project-wide relevance bar; it is not exposed as a
  per-question or per-user setting.
- "Half the current passage size" is adopted as the target passage size, clamped to the floor of
  this project's existing governance range for passage size (the 500–1000 token range set by
  [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)'s Chunking & Embedding
  Strategy section) — confirmed with the user: literal exact halving would fall under that floor, so
  the new target used is the floor value (500 tokens) instead of the literal half, and the governance
  range itself stays unchanged (no constitution amendment). This preserves the spirit of
  "meaningfully smaller, less diluted passages" without breaking an existing, ratified project-wide
  constraint.
- Retrieval breadth (top-K, FR-009) increases by one passage per question (from its current value to
  one more than that) — confirmed with the user specifically to offset the smaller passage size, so a
  topic that used to fit within fewer, larger passages stays coverable within one answer's retrieved
  set. Like the relevance bar and passage size, this is a project-wide value, adopted the same way for
  every question, not a per-question setting; the constitution's Query Pipeline section already frames
  top-K as a tunable "default" in the same way it frames the relevance bar, so this also needs no
  constitution amendment.
- This feature governs behavior going forward only. Reprocessing every already-uploaded document
  under the new, smaller passage size is out of scope — an operator who wants an already-uploaded
  document to benefit from the smaller passage size can remove and re-upload it using the system's
  existing document management capability, but this feature does not add a bulk "reprocess all"
  capability. The relevance bar and retrieval breadth changes carry no equivalent gap: both are
  query-time checks, so they apply to already-ingested passages immediately, with no reprocessing
  needed.
- No new user-facing control is introduced by this feature (e.g. no UI to adjust the relevance bar,
  passage size, or retrieval breadth per user) — all three remain internal tuning values chosen by the
  team and changed by a code deployment, exactly the same operational pattern this project already
  used to introduce their previous values (feature 007); this feature does not introduce a new
  deployment or rollback mechanism.
- The existing curated evaluation question set (`sample-data/evaluation-questions.csv`, confirmed
  present in the repository) and manual-testing process remain the mechanism for validating this
  change; no new evaluation tooling is introduced. SC-001, SC-002, and SC-003 are all verified
  manually rather than by a new automated baseline dataset (confirmed with the user) — a reviewer
  re-runs the relevant question set before and after this change and compares outcomes directly,
  rather than checking results against a separately recorded numeric baseline stored in this spec.
- Smaller passages mean a newly-ingested document produces more, smaller units of retrievable content
  than before, and one more passage is retrieved per question (FR-009) — a proportional increase in
  storage rows and in-answer content, not a new kind of load. No new performance target is introduced
  for this: the sample corpus this PoC targets is small enough that this increase stays well within
  what the system already handles today, so this feature does not add a distinct non-functional
  performance requirement for ingestion or retrieval latency.

# Sample data

Everything the PoC needs to be exercised without real company documents: a synthetic
corpus to ingest, and an evaluation set to measure retrieval against.

All content is invented. "Northwind Systems GmbH" is a fictional company, and no
document here derives from a real policy, a real employer or a real person.

## Layout

| Path | What it is |
|---|---|
| `documents/` | The corpus the chatbot ingests — 7 `.txt` + 9 `.pdf` |
| `pdf-sources/` | Markdown sources for the 9 PDFs |
| `build-pdfs.py` | Regenerates `documents/*.pdf` from `pdf-sources/*.md` |
| `evaluation-questions.csv` | 20 evaluation questions with their expected source document |

The `.txt` files in `documents/` are the originals and are edited directly. The PDFs
are build output — edit the Markdown source and re-run the script rather than trying
to change a PDF.

```bash
pip install fpdf2 && python sample-data/build-pdfs.py
```

## The corpus

16 documents, roughly 107,000 characters, in the shape internal process documentation
actually takes: a document ID, a version, an owner, numbered sections, and
cross-references to sibling documents.

| Document | Format | Pages | Covers |
|---|---|---|---|
| `travel-expense-policy.pdf` | PDF | 7 | Authorisation, air/rail, accommodation, per diems, ground transport, receipts |
| `security-policy.pdf` | PDF | 8 | Classification, credentials, MFA, data storage, devices, badges, phishing |
| `incident-response-plan.pdf` | PDF | 5 | Reporting, severity, roles, containment, notification, post-incident review |
| `release-process.pdf` | PDF | 4 | Environments, release gates, approval authority, windows, rollback |
| `vacation-policy.pdf` | PDF | 5 | Entitlement, accrual, requesting, carry-over, sickness, other absence |
| `learning-development-policy.pdf` | PDF | 5 | Development budget, approval, eligible activity, certification commitment |
| `benefits-overview.pdf` | PDF | 4 | Pension, health, insurance, wellbeing, family, financial |
| `code-of-conduct.pdf` | PDF | 5 | Conduct, conflicts of interest, gifts, bribery, competition, raising concerns |
| `data-retention-schedule.pdf` | PDF | 4 | Retention periods by record category, legal hold, individual rights |
| `onboarding-guide.txt` | TXT | — | Preparation, arrival checklist, buddy scheme, probation |
| `it-support-handbook.txt` | TXT | — | Contact routes, priority classification, response targets, equipment |
| `remote-work-policy.txt` | TXT | — | Hybrid baseline, fully distributed exceptions, cross-border, obligations |
| `corporate-card-rules.txt` | TXT | — | Eligibility, permitted and prohibited use, receipts, personal use |
| `performance-review-process.txt` | TXT | — | Cadence, inputs, calibration, outcome bands, disagreement |
| `expense-tool-faq.txt` | TXT | — | How to operate the expense tool (distractor) |
| `facilities-and-office-guide.txt` | TXT | — | Sites, building access, visitors, rooms, kitchens, evacuation (distractor) |

### Why five of them answer no question

`benefits-overview.pdf`, `code-of-conduct.pdf`, `data-retention-schedule.pdf`,
`expense-tool-faq.txt` and `facilities-and-office-guide.txt` are distractors. They sit
deliberately close to documents that *do* hold answers — benefits next to leave, the
expense tool next to the expense policy, facilities next to the security policy on
badges — so that retrieval has a plausible wrong answer available for most questions.

A corpus where every document is the right answer to something measures nothing.

## How the corpus and the CSV fit together

Each question in `evaluation-questions.csv` names the document that *should* be
retrieved. The corpus was written so that:

**The answer is genuinely in there.** Every fact in the CSV's
`expected_answer_summary` appears in the named document, in the same numbers — 14
characters, 5 previous passwords, EUR 80, EUR 1500, 30 days, 2 business days.

**The question's wording is not.** For the 14 rows flagged
`tests_vocabulary_mismatch=yes`, the document deliberately avoids the phrasing a user
would reach for. Retrieval has to bridge the gap on meaning:

| The user asks | The document says |
|---|---|
| get my money back for a taxi | ground transport is an eligible cost; licensed cabs, ride-hailing |
| what do I do on my first day | arrival checklist; report to reception on your start date |
| how many days off per year | annual leave entitlement; twenty-five paid vacation days |
| my laptop is broken | notebook and workstation hardware faults |
| how fast will someone answer | acknowledgement target |
| rules for my account password | passphrase standard; credential vault |
| store work files on a personal Google Drive | consumer cloud storage registered to the individual |
| before code goes live | promoted to the production environment; release gates |
| work from home permanently | fully distributed arrangements; exception to this policy |
| if I think data has leaked | exposed, disclosed or accessed without authorisation |
| order a second monitor | equipment beyond the standard issue; additional display |
| attend a paid conference | external conferences and professional events |
| how is my performance evaluated | review cycle; self-assessment, manager assessment, peer feedback |

A keyword search over this corpus fails most of these. That is the point — if it
succeeded, a passing retrieval score would prove nothing about the embeddings.

**Q20 has no answer anywhere.** "Which parking spot is assigned to the CEO?" is the
negative test. Parking appears twice in the corpus — visitor parking in the facilities
guide, and parking charges as a reimbursable travel cost — so the question looks
answerable to a retriever, and the top-K chunks will contain something about parking.
Correct behaviour is still to refuse. That row is the one most worth watching.

## Regenerating and verifying

After editing anything under `pdf-sources/`, re-run the build and re-check that the
facts still land where the evaluation expects them. The page-level citation in
[`docs/poc-concept.md`](../docs/poc-concept.md) §3 points at
`travel-expense-policy.pdf` p. 5; content growing past a page boundary will move it.

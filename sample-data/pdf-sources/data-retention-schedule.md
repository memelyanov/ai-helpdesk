# Data Retention Schedule

Northwind Systems GmbH

Document ID: GOV-RET-002
Version: 4.1
Effective date: 1 February 2026
Owner: Legal and Compliance
Classification: Internal
Review cycle: Annual

## 1. Purpose

This schedule states how long Northwind Systems keeps each category of record, what happens at the end of that period, and who is accountable.

Keeping information longer than necessary increases the harm caused by a breach, increases storage and discovery cost, and in the case of personal data is unlawful. Deleting it too early destroys evidence the company may be obliged to produce. The periods below are the balance struck between those two failures.

The schedule applies to every copy of a record in every location, including backups, exports, working copies and mailboxes -- not only to the system of record.

## 2. How to Read the Schedule

The retention period begins at the trigger event stated for each category, not at the date the record was created.

At the end of the period the disposal action applies:

- **Delete** -- Irrecoverable destruction of the record and its copies.
- **Anonymise** -- Removal of every identifier such that the individual can no longer be identified, directly or indirectly. The residual data may then be kept indefinitely for analysis.
- **Archive** -- Movement to restricted storage with access limited to a named role, for the stated further period, after which it is deleted.

## 3. Legal Hold

A legal hold suspends disposal for the records it names. It is issued by Legal and Compliance, in writing, and it overrides every period in this schedule for as long as it remains in force.

Once notified of a hold, do not delete anything within its scope, including routine housekeeping and including material you believe to be irrelevant. Automatic deletion rules are suspended by the system owner on notification.

Destroying material subject to a hold is a serious matter and may itself constitute an offence.

<!-- PAGEBREAK -->

## 4. Schedule

### 4.1 Employment records

- Recruitment records for unsuccessful candidates -- 6 months from the decision -- Delete
- Recruitment records where consent to retain was given -- 24 months from the decision -- Delete
- Employment contract and amendments -- 10 years from end of employment -- Archive
- Payroll records -- 10 years from the end of the tax year -- Archive
- Performance assessments -- 3 years from the end of employment -- Delete
- Absence and leave records -- 3 years from the end of the leave year -- Delete
- Sickness certificates -- 3 years from issue -- Delete
- Disciplinary and grievance records, upheld -- 6 years from conclusion -- Delete
- Disciplinary records, not upheld -- 12 months from conclusion -- Delete
- Training and certification records -- 6 years from end of employment -- Delete
- Right to work documentation -- 2 years from end of employment -- Delete

### 4.2 Financial records

- General ledger and statutory accounts -- 10 years from the end of the financial year -- Archive
- Invoices, receivable and payable -- 10 years from the end of the financial year -- Archive
- Expense claims and supporting receipts -- 10 years from submission -- Archive
- Corporate card statements -- 10 years from the statement date -- Archive
- Tax filings and correspondence -- 10 years from filing -- Archive
- Banking mandates and signatory records -- 6 years from revocation -- Delete

### 4.3 Customer and contract records

- Signed customer contracts -- 10 years from expiry or termination -- Archive
- Tender and proposal material, unsuccessful -- 3 years from the decision -- Delete
- Customer correspondence -- 3 years from the end of the relationship -- Delete
- Customer personal data held on their behalf -- As specified in the contract, and in its absence 30 days from termination -- Delete
- Marketing consent records -- 3 years from withdrawal or last activity -- Delete

<!-- PAGEBREAK -->

### 4.4 Engineering and operational records

- Source control history -- Indefinite -- Retained
- Release tickets and approval records -- 3 years from the release -- Delete
- Application logs containing personal data -- 90 days -- Delete
- Application logs without personal data -- 13 months -- Anonymise
- Security and audit logs -- 24 months -- Archive
- Access review records -- 3 years from the review -- Delete
- Backups of production systems -- 35 days rolling -- Overwritten
- Vulnerability scan results -- 24 months -- Delete

### 4.5 Security and incident records

- Security incident case files -- 6 years from closure -- Archive
- Personal data breach records and notifications -- 6 years from closure -- Archive
- Post-incident review reports -- 6 years from closure -- Archive
- Visitor register -- 12 months from the visit -- Delete
- Building access badge event logs -- 6 months from the event -- Delete
- Video surveillance recordings -- 72 hours, unless preserved for a specific incident -- Overwritten

### 4.6 Governance records

- Board and committee minutes -- Indefinite -- Retained
- Statutory registers -- Indefinite -- Retained
- Policies, all versions -- 10 years from supersession -- Archive
- Gifts and hospitality register -- 6 years from the entry -- Delete
- Conflict of interest declarations -- 6 years from the end of employment -- Delete
- Insurance policies -- 10 years from expiry -- Archive

### 4.7 Communication

- Mailboxes of current employees -- Indefinite, subject to individual housekeeping -- Retained
- Mailboxes of leavers -- 90 days from the end of employment, then delegated access ends -- Delete
- Instant messaging channels -- 24 months rolling -- Delete
- Meeting recordings -- 12 months from the meeting -- Delete

<!-- PAGEBREAK -->

## 5. Personal Data and Individual Rights

Where a record contains personal data, the individual has rights of access, rectification, erasure, restriction, portability and objection under applicable data protection law.

Requests are directed to the data protection contact at privacy@northwind.example and are answered within one month. Legal and Compliance coordinates the response across every system holding the data.

A request for erasure does not override a legal retention obligation. Where a record must be kept, the individual is told which obligation applies and for how long.

## 6. Responsibilities

- **System owners** implement the periods in this schedule as automated rules wherever the system supports it, and confirm compliance in the annual review.
- **Legal and Compliance** maintains the schedule, issues legal holds, and coordinates rights requests.
- **Information Security** ensures disposal is irrecoverable and that backups are covered.
- **All employees** avoid creating uncontrolled copies. A document exported to a personal working folder is a copy this schedule cannot reach, which is one of several reasons the information security policy restricts where company data may be held.

## 7. Review

The schedule is reviewed annually, and additionally whenever a new system holding personal data is introduced or a retention obligation changes. Changes are approved by Legal and Compliance and communicated to system owners.

## 8. Related Documents

- Information security policy
- Incident response plan
- Code of conduct
- Performance review process

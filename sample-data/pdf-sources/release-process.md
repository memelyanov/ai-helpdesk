# Software Release and Change Management Process

Northwind Systems GmbH

Document ID: ENG-REL-008
Version: 4.5
Effective date: 20 January 2026
Owner: Engineering Operations
Classification: Internal
Review cycle: Semi-annual

## 1. Purpose

This process describes how a change to Northwind software reaches the production environment: the gates it must pass, who approves it, how it is deployed, and how it is reversed if it turns out to be wrong.

It applies to every production-facing service and to every change to one, including configuration changes and infrastructure changes. It applies whether the change is a feature, a correction or a dependency update.

The intent is not to slow change down. The gates below are the minimum set that has been found to catch defects earlier and more cheaply than production does.

## 2. Environments

- **Development** -- Owned by the engineering team. No gates. Contains no real customer data.
- **Integration** -- Shared. Every merge to the main branch deploys here automatically. Contains synthetic data only.
- **Staging** -- Production-equivalent in configuration and scale. The release candidate is exercised here. Contains anonymised data.
- **Production** -- Live. Changes reach it only through the process in section 3.

Real customer data exists only in production. Copying it into any other environment is a breach of the information security policy.

<!-- PAGEBREAK -->

## 3. Release Gates

A change is promoted to the production environment only when all four of the following gates have been satisfied. They are enforced by the delivery pipeline; none of them can be satisfied by assertion.

### 3.1 Peer review approval

Every change is reviewed by at least one engineer other than its author, and merged only with that approval recorded on the merge request. Changes touching authentication, authorisation, payment handling or personal data require two approvals, one of which must come from the owning team.

An author never approves or merges their own change, including a change they consider trivial.

### 3.2 A passing continuous integration pipeline

The pipeline must complete green on the exact commit being promoted. It runs the unit and integration suites, static analysis, dependency vulnerability scanning, secret scanning and a licence check.

A failing or skipped stage blocks promotion. Re-running a flaking test to obtain a green result without investigating it is not acceptable; a test that flakes is either fixed or removed, and the decision is recorded.

### 3.3 Quality assurance sign-off

Quality assurance exercises the release candidate on staging against the test plan for the change, and records an explicit sign-off against the release ticket.

The sign-off covers the acceptance criteria for the change, a regression pass over the affected areas, and confirmation that any feature flag defaults are as intended.

Where a change is genuinely covered end to end by automated tests, the team may request a standing exemption from manual sign-off for that change class. The exemption is granted by Engineering Operations and is recorded; it is not decided per release by the team.

### 3.4 An approved release ticket

Every promotion to production is represented by a release ticket, which records what is being deployed, the commit reference, the affected services, the test evidence, the deployment window, the rollback plan and the customer-visible impact.

The ticket is approved by the release manager before deployment begins, as set out in section 4.

<!-- PAGEBREAK -->

## 4. Approval Authority

Authority to approve a promotion to the production environment rests with the release manager.

The release manager reviews the release ticket, confirms that the three preceding gates are genuinely satisfied rather than merely marked, and checks the change against what else is going out in the same window.

Where the release manager is unavailable -- absent, on leave, or outside their working hours during an out-of-hours deployment -- authority passes to a tech lead nominated as a delegate. The delegation is recorded in advance in the release calendar and names the specific individual; it is not an open standing permission held by every tech lead.

Two categories of change carry an additional approval:

- A change affecting authentication, authorisation or the handling of personal data additionally requires approval from Information Security.
- A change with customer-visible behaviour changes additionally requires the product owner's confirmation that customer communication has gone out.

An author cannot approve their own release ticket, in any role.

## 5. Release Windows

Standard releases are deployed on business days between 09:00 and 16:00 CET, so that the team that wrote the change is present and alert while it goes out.

- No standard release after 16:00 CET, and none on a Friday.
- No standard release during a declared freeze period.
- Emergency fixes are exempt from the window restriction under section 7.

Freeze periods are declared by Engineering Operations for the year-end period and around major customer events, and are published in the release calendar at least four weeks in advance.

## 6. Deployment and Verification

Deployment is performed by the pipeline, not by hand. No engineer deploys by connecting to a production host.

Services are deployed progressively: to a canary instance first, held for a minimum of fifteen minutes under observation, then rolled out fully if the error rate, latency and saturation signals are unchanged.

After full rollout the release engineer confirms the post-deployment checks recorded on the release ticket and closes it. A release ticket left open is treated as a release that has not been verified.

<!-- PAGEBREAK -->

## 7. Emergency Changes

An emergency change is one that resolves an active production incident or a critical security exposure. It may bypass the release window and the quality assurance sign-off, but never the peer review or the pipeline.

An emergency change requires verbal or written approval from the release manager or the on-call engineering lead before deployment, and the release ticket must be created retrospectively within one business day, recording what was done, why the normal path was bypassed and who approved it.

Every emergency change is reviewed at the following week's release review.

## 8. Rollback

Every release ticket carries a rollback plan. "Roll forward with a fix" is a valid plan only where the change is behind a feature flag that can be disabled instantly.

The decision to roll back is made by the release engineer or the incident commander, and is never delayed pending an investigation into the cause. Diagnose after service is restored.

Database migrations must be backward compatible with the previous application version, so that the application can be rolled back without a data migration. Destructive schema changes are split across at least two releases.

## 9. Records and Review

Release tickets, approvals and pipeline results are retained according to the data retention schedule and constitute the change management audit evidence.

Engineering Operations holds a weekly release review covering the previous week's releases, any emergency changes, any rollbacks and any gate exemptions granted.

## 10. Related Documents

- Incident response plan
- Information security policy
- Data retention schedule

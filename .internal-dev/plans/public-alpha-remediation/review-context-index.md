# Public Alpha Remediation Review Context Index

## Usage

Implementation agents should read the active domain plan and subplan first. If they need more detail, use this index to read only the relevant original review files and bug reports.

Validation agents must read the listed original review files and bug reports before validating a domain. Their job is to prove the original concern is fixed, not merely that new tests pass.

## Domain Context

| Domain | Required Review Files | Required Bug Reports |
| --- | --- | --- |
| `01-security-access-control` | `domain-api-web.md`, `horizontal-security-error-htmx.md`, `domain-orchestration-runtime.md`, `domain-workflow.md`, `remediation-handoff.md` | bug-01, bug-02, bug-11, bug-12 |
| `02-workspace-tools-outputs` | `domain-workspaces-tools-outputs.md`, `domain-orchestration-runtime.md`, `domain-persistence-schema.md`, `horizontal-security-error-htmx.md`, `remediation-handoff.md` | bug-08, bug-09, bug-10, bug-13, bug-22, bug-23, bug-24 |
| `03-execution-history-streams` | `domain-chat-plan-task.md`, `domain-api-web.md`, `domain-workflow.md`, `horizontal-di-rest-schema-stale.md`, `remediation-handoff.md` | bug-05, bug-06, bug-14, bug-15, bug-21 |
| `04-workflow-authoring-runtime-js` | `domain-workflow.md`, `domain-frontend-static.md`, `horizontal-security-error-htmx.md`, `domain-api-web.md`, `remediation-handoff.md` | bug-03, bug-04, bug-11 |
| `05-schema-data-ownership` | `domain-persistence-schema.md`, `horizontal-di-rest-schema-stale.md`, `automated-validation-evidence.md`, `remediation-handoff.md` | bug-07, bug-19, bug-25 |
| `06-operational-ui-htmx-mobile` | `domain-frontend-static.md`, `domain-api-web.md`, `domain-workspaces-tools-outputs.md`, `horizontal-security-error-htmx.md`, `playwright-public-pages-evidence.md`, `playwright-console-network-log.md`, `remediation-handoff.md` | bug-16, bug-18, bug-20 |
| `07-validation-harness-regression` | `domain-test-harness.md`, `automated-validation-evidence.md`, `playwright-public-pages-evidence.md`, `final-readiness-review.md`, `remediation-handoff.md` | bug-17 |
| `08-code-quality-stale-cleanup` | `domain-workflow.md`, `domain-frontend-static.md`, `domain-api-web.md`, `domain-chat-plan-task.md`, `domain-persistence-schema.md`, `horizontal-security-error-htmx.md` | bug-18, bug-19, bug-22 |

## Root Review Files

- `bug-ledger.md`: authoritative bug list and alpha-blocker classification.
- `final-readiness-review.md`: readiness decision and top risk clusters.
- `remediation-handoff.md`: initial phase seed for remediation.
- `automated-validation-evidence.md`: test/startup/DB proof from the review.
- `playwright-public-pages-evidence.md`: browser proof and mobile layout evidence.
- `playwright-console-network-log.md`: console/network notes from browser validation.

## Bug Report Path Pattern

Bug reports live under `.internal-dev/bugs/public-alpha-quality-review/<bug-slug>/report.md`. Use `rg --files .internal-dev/bugs/public-alpha-quality-review | rg 'bug-NN'` to locate a specific bug report.

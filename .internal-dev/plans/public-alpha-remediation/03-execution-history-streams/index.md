# Execution, History, and Streams Domain

## Objective

Unify public execution on saved-definition submit-to-agent semantics while preserving chat history and fixing stream/job/schedule contract gaps.

## Branch

Implementation branch: `public-alpha-remediation/execution-history-streams`.

## Owned Findings

- bug-05, bug-06, bug-14, bug-15, bug-21.
- ro-01 workflow run API context loss.
- ro-02 submit-to-agent priority mismatch.
- ro-03 task stream inline synchronous execution paths.

## Subplans

| Order | Subplan | Findings |
| --- | --- | --- |
| 1 | `subplan-01-submit-to-agent-contract.md` | bug-05, ro-01, ro-02, ro-03 |
| 2 | `subplan-02-transcript-preservation.md` | bug-06 |
| 3 | `subplan-03-plan-sse-contract.md` | bug-14 |
| 4 | `subplan-04-job-run-submission.md` | bug-15 |
| 5 | `subplan-05-schedule-reaction-template-validation.md` | bug-21 |

## Context

Validators must read `domain-chat-plan-task.md`, `domain-api-web.md`, `domain-workflow.md`, `horizontal-di-rest-schema-stale.md`, `remediation-handoff.md`, and owned bug reports.

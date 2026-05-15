# DEFECT-04-01: Resume Ignores Approval Response — Rejected Workflows Complete

## Summary
`WorkflowRunner.resumeRun()` unconditionally marks the waiting approval gate node as COMPLETED and continues execution, regardless of whether the approval was approved or rejected. The `InboxService.parseApprovalFromResponse()` utility exists but is never called during resume.

## Scope
- `WorkflowRunner.resumeRun()` (line ~137-181) finds WAITING node and marks it COMPLETED
- Does not check `responseJson` for `approved: true/false`
- `InboxService.parseApprovalFromResponse()` is available but unused in this code path
- Affects all workflows with user_approval or agent_approval gates

## Reproduction
1. Create a workflow with a user_approval gate node
2. Run the workflow: `POST /api/workflows/{id}/runs`
3. Wait for workflow to enter WAITING state at gate
4. Reject the approval: `POST /api/users/inbox/{msgId}/respond {"approved": false, "comment": "Rejected"}`
5. Resume the workflow: `POST /api/workflow-runs/{runId}/resume`
6. Workflow completes successfully despite rejection

## Expected
Rejected workflows should not proceed past the gate. On resume after rejection, the gate should remain WAITING or transition to FAILED, and the workflow should terminate or require re-approval.

## Actual
Rejected workflows proceed to completion. The approval gate is effectively cosmetic.

## Evidence
- Phase 04 evidence file: `.internal-dev/reviews/docker-backed-alpha-e2e-validation/04-workflows-gates-inbox-resume-evidence.md`
- Run `02812ad5-...`: approval rejected via API, then resumed → all 4 nodes COMPLETED

## Impact
**Alpha blocker.** The approval gate — the primary mechanism for human-in-the-loop workflow control — does not actually block workflow progress. This is a security and correctness issue.

## Status
Fixed

## Resolution
Implemented in `WorkflowRunner.resumeRun()`:
- Retrieves `messageId` from waiting node's output values
- Loads the message via `InboxService.findMessageById(messageId)` (new method, loads regardless of recipient type)
- If message has no response yet, throws `IllegalStateException` with clear "has not been responded to yet"
- If `inboxService.parseApprovalFromResponse(responseJson)` returns true → marks gate COMPLETED, continues execution, marks message handled
- If false → marks gate FAILED, sets workflow status FAILED, stores error "Approval rejected for gate <nodeKey>", cleans up temp workspace, marks message handled after terminal state persisted

## Evidence
- `mvn -Dtest=WorkflowRunnerTest test` passes all 23 tests including:
  - `rejectedApprovalResumeMarksFailed` — rejected approval → gate FAILED, run FAILED
  - `approvedApprovalResumeCompletesLaterNodes` — approved → both nodes COMPLETED
  - `resumeBeforeResponseFails` — resume without response → IllegalStateException
- `mvn -Dtest=OrchestrationControllerTest test` passes all 47 tests

## Next Action
None — fix verified.

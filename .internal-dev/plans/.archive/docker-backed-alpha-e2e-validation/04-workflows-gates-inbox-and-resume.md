# Phase 04: Workflows, Gates, Inbox, And Resume

## Context

The workflow contract requires chained tasks with no conversation persistence between steps, linked input/output schemas, and blocking gates such as user or agent approval. Approval messages must appear in inbox and allow workflow progress to resume.

## Goal

Validate through Playwright that a workflow can be composed, saved, validated, executed, blocked on an approval gate, surfaced in inbox, approved, resumed, and completed with outputs.

## In Scope

- Workflow builder UI.
- Task nodes.
- Output-to-input mappings.
- User approval gate.
- Agent approval gate if exposed.
- User message node if exposed.
- Inbox approval UX.
- Resume/continue workflow behavior.
- Workflow status and node-run status.
- Output artifacts from task and output nodes.

## Out of Scope

- Building a new workflow designer during validation.
- Testing every possible graph topology.

## Implementation Steps

1. Use tasks from phase `03` or create minimal validation tasks from the UI.
2. Compose a workflow:
   - node A: Docker-backed task produces JSON/text output
   - node B: user approval gate with a message to the user inbox
   - node C: task consumes node A output after approval
   - node D: materializes final output
3. Save and reload the workflow to prove graph, mappings, and model overrides persist.
4. Run workflow from the UI.
5. Verify workflow enters a waiting state at the approval node.
6. Open `/inbox` through Playwright and verify the approval message is visible, readable, and tied to the workflow/run.
7. Approve the message through the UI.
8. Return to workflow run and verify it resumes past the gate and completes.
9. Repeat with reject/deny if the UI supports it; verify workflow status becomes failed/cancelled/needs review with clear messaging.
10. Verify no conversation context leaks between task nodes except declared input/output bindings.

## Validation

Required Playwright checks:
- Invalid workflow save is blocked with specific validation errors.
- Valid workflow save persists and reloads.
- Run status transitions: queued/running, waiting, resumed/running, completed.
- Inbox approval is created and actionable.
- Approving from inbox resumes the correct workflow run.
- Final output appears in outputs UI and is attributable to workflow/run/agent/job/project when applicable.

## Exit Criteria

- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/04-workflows-gates-inbox-resume-evidence.md` exists.
- At least one approval-gated workflow completes after inbox approval.
- Any gate, inbox, resume, or mapping defect is logged as alpha blocking unless explicitly out of scope.

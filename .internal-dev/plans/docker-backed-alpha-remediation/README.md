# Docker-Backed Alpha Remediation Plan Suite

## Purpose

This suite turns the Docker-backed alpha E2E verification results into executable remediation subplans for implementation subagents. It is based on:

- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/01-harness-and-docker-preflight-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/02-agent-docker-lifecycle-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/03-plans-tasks-docker-execution-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/04-workflows-gates-inbox-resume-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/05-jobs-projects-schedules-assignment-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/06-chat-model-overrides-agent-surfaces-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/07-outputs-workspaces-artifact-contract-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/final-alpha-e2e-readiness-review.md`
- `.internal-dev/bugs/DEFECT-03-03/report.md`
- `.internal-dev/bugs/DEFECT-04-01/report.md`
- `.internal-dev/bugs/DEFECT-04-02/report.md`
- `.internal-dev/bugs/DEFECT-07-01/report.md`

## Execution Order

Run these as separate subagent work packages. Subagents are not alone in the codebase; each worker must preserve unrelated edits and stay inside the write scope named by its phase unless a test failure proves an adjacent file must change.

1. `01-workflow-execution-and-approval-gates.md`
2. `02-docker-output-execution-context.md`
3. `03-operational-editor-model-and-status-fixes.md`
4. `04-agent-chat-and-browser-harness.md`
5. `05-final-alpha-validation-gate.md`

Phases 1 and 2 may begin in parallel only if their workers coordinate any shared task-execution interfaces before coding. Phases 3 and 4 both touch web UI surfaces and should not run concurrently with each other. Phase 5 is validation-only and must run after all implementation phases are merged.

## Defect Routing

| Defect | Routed To | Required Outcome |
| --- | --- | --- |
| DEFECT-04-02 task nodes are no-ops | Phase 1 | Workflow task nodes execute through real model-backed task execution, not `PlanService.startRun()` fallback. |
| DEFECT-04-01 resume ignores rejection | Phase 1 | Rejected gates terminate or hold workflows; they never proceed to completion. |
| DEFECT-04-03 duplicate routes | Phase 1 | Duplicate routes are rejected or warned before save. |
| DEFECT-03-03 wrong output path | Phase 2 | Docker-backed tasks run tools inside the agent container and write required artifacts under `/output`. |
| DEFECT-07-02 output file path mismatch | Phase 2 | Artifact paths and agent attribution agree. |
| DEFECT-07-05 loose unregistered files | Phase 2 | Completion registers files written to known output roots and flags orphan root files. |
| DEFECT-07-03 temp parent dirs | Phase 2 | Task temp directories are fully removed after terminal runs. |
| DEFECT-07-04 duplicate workspace tables | Phase 2 | One workspace table is authoritative, with migration or code cleanup documented. |
| DEFECT-03-01 field type persistence | Phase 3 | Plan input/output type wire names persist correctly. |
| DEFECT-03-02 blank steps/deliverables discarded | Phase 3 | Add-row flows create editable persisted placeholders or submit first text. |
| DEFECT-03-04 auto-approval on save | Phase 3 | Save preserves `DRAFT`; finalize explicitly moves to `APPROVED`. |
| DEFECT-03-05 output naming mismatch | Phase 3 | UI/API explains or normalizes stored file name versus declared output value. |
| DEFECT-05 job status/history/bindings issues | Phase 3 | Job status, agent history, and required binding guidance match persisted runs. |
| DEFECT-06 model alias/raw-name confusion | Phase 3 | Dropdown values and backend validation use the same canonical model keys. |
| DEFECT-07-01 no output content view | Phase 3 | Output content can be viewed inline and downloaded through confined endpoints. |
| Agent side-panel chat not wired | Phase 4 | Agent detail/list surfaces expose the existing side-panel chat or a deliberate chat tab. |
| Playwright MCP/browser validation incomplete | Phase 4 and 5 | Browser-origin checks are formalized and rerun; MCP failure blocks alpha signoff. |

## Completion Rule

Do not archive this plan suite or the `DEFECT-*` bugs until Phase 5 passes with live Docker/Podman execution and browser-origin validation evidence. Unit-only or curl-only validation is not an alpha signoff.

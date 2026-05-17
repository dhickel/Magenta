# Horizontal DI/REST/Schema/Stale Review

## Agent

- Agent: horizontal-di-rest-schema-stale
- Agent id: `019e3721-2ce7-7760-80a3-4b76aee581c2`
- Model / reasoning: GPT-5.5 Codex high
- Mode: read-only

## Scope

Reviewed Spring bean wiring, public REST/SSE routes, submit-to-agent vs direct-run paths, SQLite schema/repository drift, and stale/deprecated workflow/workspace code.

## Files, Routes, and Tables Reviewed

- Files: `ChatController`, `PlanController`, `TaskController`, `WorkflowController`, `JobController`, `OrchestrationController`, `AssignmentService`, `OrchestrationRunService`, `OrchestrationRunnerService`, `JobService`, `WorkflowService`, `WorkflowRepository`, `WorkspaceRepository`, `PlanRepository`, `schema.sql`.
- Tables: `work_assignments`, `assignment_conversation_links`, `workflow_definitions`, `workflow_runs`, `plan_runs`, `job_definitions`, `job_runs`, `workspace_leases`, `workspace_roots`, `run_output_artifacts`, `inbox_messages`, `agent_inbox_messages`.

## Commands and Probes

- Targeted `sed`, `nl`, `find`, and `rg` over AGENTS, web/API, orchestration/chat packages, schema, and tests.

## Findings

- Critical: public direct-run surfaces still exist beside submit-to-agent semantics.
- High: job UI has a `Start Run` control that creates a queued `job_run` without submitting an assignment.
- High: `/api/plans/{planId}/runs/stream` emits unstable/wrong SSE event names.
- Medium: canonical schema is stale for workspace/output tables.
- Medium: schema/repository drift around execution tables continues beyond workspaces.

## Explicitly Ruled Out

- No obvious Spring bean-name collision was found.
- No blocking DI issue was found in the submit-to-agent path.

# Domain Workflow Review

## Agent

- Agent: domain-workflow
- Agent id: `019e371c-62e9-7910-8804-e79b0d05ffd0`
- Model / reasoning: GPT-5.5 Codex high
- Mode: read-only

## Scope

Reviewed canonical workflow editor/runtime, workflow graph validation, submit-to-agent semantics, workflow JS island, persistence, and tests.

## Files, Routes, and Tables Reviewed

- Files: `WorkflowController`, `OrchestrationController`, `WorkflowService`, `WorkflowRunner`, `WorkflowValidator`, `WorkflowTaskExecutor`, `WorkflowRepository`, `OrchestrationRunService`, `OrchestrationRunnerService`, `AssignmentService`, `workflows.js`, `magenta-tools.js`, legacy `ai.chat.workflow`.
- Routes: `/workflows`, `/workflows/_editor/*`, `/workflows/_submit/*`, `/workflows/_runs/*`, `/api/workflows/*`, `/api/workflow-runs/*`.
- Tables: `workflow_definitions`, `workflow_runs`, `workflow_node_runs`, legacy `ai_workflow_*`.

## Commands and Probes

- `find .. -name AGENTS.md`
- Targeted `rg` over workflow/API/runtime/static/tests/schema
- `nl -ba` and `sed` line inspections
- `mvn -q -Dtest=WorkflowRunnerTest,WorkflowRepositoryTest,OrchestrationControllerTest test` passed in the agent report

## Findings

- Critical: the HTMX builder cannot incrementally create common valid workflows because every node add uses validated save while valid approval-gate/task graphs require intermediate invalid states.
- Critical: empty workflows validate and can be submitted/executed as successful no-ops.
- High: public workflow API routes bypass submit-to-agent semantics and start runs directly.
- High: workflow JS island replaces the HTMX editor and uses its own API transport, rather than a narrow graph enhancement.
- High/security: the JS graph composer has persistent XSS risk through raw `innerHTML` interpolation of saved node values.
- Medium: stale deprecated workflow code still compiles in `ai.chat.workflow`.

## Explicitly Ruled Out

- Active web/runtime imports point to `io.mindspice.magenta2.ai.orchestration.workflow`; legacy chat workflow appears inert.
- Graph-column migration paths for `nodes_json` and `routes_json` are covered by repository code and tests.
- Canonical task-node execution delegates to `ChatService.executeTaskBlocking(...)`, not the old fake default-output path.

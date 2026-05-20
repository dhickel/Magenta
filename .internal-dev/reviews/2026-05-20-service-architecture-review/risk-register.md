# Service Architecture Risk Register

## Scope

This register deduplicates findings from the orchestrated architecture review and groups them by system risk rather than package ownership.

## Findings

| Severity | Risk | Affected Areas | Evidence |
| --- | --- | --- | --- |
| Critical | Workflow synchronous execution can duplicate work | Workflow, runtime assignments, jobs | `WorkflowRunner.runSynchronously`, `WorkflowRunner.startRun`, `OrchestrationRunnerService` |
| High | Execution lifecycle is split across assignments, job runs, and workflow runs | Runtime, jobs, workflows, inboxes | `work_assignments`, `job_runs`, `workflow_runs`, `OrchestrationRunnerService`, `JobService` |
| High | Chat streaming audit/memory parity is incomplete | Chat, audit, SSE, memory | `ChatService.plainStream`, `ContextManagementAdvisor`, `AuditRepository` |
| High | Whole-conversation memory rewrite can lose state | Chat memory, tool transcript compaction, retries | `ChatMemoryRepository.saveAll`, `ChatService.restoreConversation` |
| High | Tool/system-chat runtime settings are visible but unenforced | Settings, tools, chat, UI | `RuntimeSettings`, `RuntimeSettingsService`, `ChatService`, `AgentShellToolService` |
| High | Plan/task readiness gates differ by route | Plans, tasks, assignments, workflow validation | `PlanController`, `TaskController`, `PlanService`, `WorkflowValidator` |
| High | Workspace identity and output attribution are inconsistent | Workspaces, runtime, workflows, outputs | `WorkspaceService`, `WorkspaceDirectoryService`, `WorkflowRunner`, `OutputArtifactService` |
| High | HTMX workflow edits can drop domain fields | API/web, workflow | `OrchestrationController`, `WorkflowDefinition`, `WorkflowController` |
| High | Delete/purge paths can leave blocked/orphan rows | Workspaces, assignments, plan chat, workflow runs | `WorkspaceRepository`, `OrchestrationRuntimeRepository`, `PlanRepository`, `WorkflowRepository` |
| Medium | Repository bootstrap differs from `schema.sql` indexes | Persistence, warm DBs, tests | `schema.sql`, `WorkflowRepository.ensureTables`, `ChatMemoryRepository.ensureMetadataColumn` |
| Medium | Workflow cancellation and approval lifecycle are under-specified | Workflow, runtime, inbox | `WorkflowRunner`, workflow `InboxService`, `WorkflowController` |
| Medium | API/docs/security policy drift | API/web, docs, package guide | `docs/technical/security.md`, `api/web/AGENTS.md`, tests |
| Medium | Config validation fails late | AI config, model router | `ExternalAiConfigLoader`, `ChatModelRouter` |

## Risk Assessment

The critical path risk is duplicated or divergent execution state. Workflow duplicate execution should be treated as a correctness bug before expanding workflow-backed jobs or assignments.

The next tier is durable-state trust: chat history, audit events, output artifacts, and database cleanup must become predictable enough for a business system to rely on historical records.

The operational tier is policy drift: visible settings, docs, and UI affordances need to match the behavior actually enforced by services.

## Recommendations

### Priority 1

- Split `WorkflowRunner` run creation into explicit async and sync paths.
- Define assignment-driven execution as the authoritative lifecycle for submitted work.
- Add regression tests for workflow task nodes executing exactly once.

### Priority 2

- Normalize job run terminal transitions for failure, cancellation, empty jobs, and recurrence.
- Remove `WAITING` from normal queue acquisition unless it has an explicit backoff meaning.
- Make cancellation checks observable inside workflow and long-running task execution boundaries.

### Priority 3

- Enforce plan/task approval/completeness before submission.
- Fix `PlanController.finalizeTask` to call the lifecycle method that actually approves drafts.
- Decide whether anonymous execution can finish without `plan_complete`; encode that decision in tests.

### Priority 4

- Validate workspace IDs before assignment/job persistence.
- Fix job workspace path convention.
- Materialize workflow outputs under durable output dirs with full attribution context.

### Priority 5

- Align runtime settings with actual consumers.
- Make shell allowlists live or label them startup-only.
- Remove stale API/security documentation conflicts.

## Follow-ups

- Convert this register into a phased remediation plan if the team wants execution work next.
- Track separately whether the local `config/ai-config.example.json` contains an operator-local secret or a committed regression.

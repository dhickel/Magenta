---
schema_version: 1
document_type: architecture-specification
status: active
owner: architecture
created: 2026-05-25
---

# Architecture Specification

## Intended Contract

Magenta is a Spring Boot and Spring AI operational assistant. Runtime behavior should stay small, observable, cancellable, and grounded in concrete user workflows. Controllers stay thin, services own use-case behavior, repositories own persistence details, and user-facing executable work should prefer `task` terminology while preserving compatibility with existing `plan` names until a deliberate rename is planned.

## Active Architecture Entries

| id | area | status | intended_contract | observed_anchors | drift_gaps | validation | related_decisions | related_knowledge |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ARCH-20260523-01 | Avatar Work Areas and dashboard refactor | active | Treat Work Areas as runtime-owned metadata around confined agent/project directories while Avatar layout and planner data live in `avatar.sqlite`. | Avatar shell, Work Areas, `WorkspaceDirectoryService`, Work Area explorer | None for current contract. | Service/controller tests, startup, and focused Playwright for UI-affecting changes. | `DECISION-20260524-03`, `DECISION-20260524-01` | `avatar-work-area-ui-refactor.md`, `workspace-file-explorer-details-list-rewrite.md` |
| ARCH-20260522-04 | Avatar data ownership | active | Store Avatar profile, preferences, organizer data, dashboard layout, facts, and events in separate `avatar.sqlite`; keep orchestration/runtime state in `magenta.sqlite`; do not rely on cross-database foreign keys. | Avatar datasource/schema and planner/dashboard components | None currently recorded. | Persistence tests and startup when schema/config changes. | `DECISION-20260522-05` | `avatar-work-area-ui-refactor.md` |
| ARCH-20260522-03 | Avatar runtime boundary | active | Build Avatar on existing chat, tool, agent profile, assignment, workspace, schedule, reaction, and output services rather than creating a second runtime. | Avatar chat/tool/dashboard integrations | Plugin/scripting runtime remains deferred. | Focused integration tests and browser checks for changed surfaces. | `DECISION-20260522-05`, `DECISION-20260522-06` | `avatar-work-area-ui-refactor.md` |
| ARCH-20260525-01 | Chat turn serialization | drift | Sync chat and streaming chat use separate gates; UI avoids concurrent same-conversation calls, but a unified reactive-compatible turn gate is the intended direction. | `ChatService.chat()`, `ChatService.stream()`, `ConversationTurnCoordinator` | Sync and stream calls for the same conversation could theoretically overlap. | Concurrency tests before changing chat runtime. | `DECISION-20260522-03` | `chat-completion-context-maintenance.md` |
| ARCH-20260525-02 | Orchestration hardening | candidate | Add schema migration tooling, runner polymorphism, prompt templates, shared JSON helpers, DAG execution, and distributed fencing only when concrete workflows require them. | Orchestration runtime/repository/service classes | Future hardening should not expand current scope without user-facing need. | Focused tests for each concrete adoption. | `DECISION-20260524-02` | `orchestration-runtime-phase-01.md`, `workflow-v2-graph-composer-runtime-contract.md` |
| ARCH-20260526-01 | Workspace, Work Area, run output, and job filesystem semantics | active | Data root contains application-owned `workspace/`, `chats/`, `agents/`, and `projects/`; agent execution roots live under `workspace/<agentWorkspaceId>/`; Work Areas use DB-owned stable ids under `workareas/<workAreaId>/`; execution writes to run-local `runs/<runId>/outputs/`; backend completion/validation/promotion writes final outputs; jobs bind to an agent, project, and Work Area without owning workspace directories. | Workspace, Work Area, task/plan, workflow, job, file/shell tool, and output services | Code may still contain legacy compatibility fields and old-path migration support until implementation phases finish. | Path-layout helper tests, service/API tests, retention tests, startup, and focused browser validation for changed file-browser surfaces. | `DECISION-20260526-01` | `workspace-file-architecture-rules.md`, `agent-shell-workspace-alias-resolution.md`, `file-tool-workspace-scope-pattern.md`, `project-workspace-materialized-links.md` |

## Ownership Boundary

Architecture specs state intended system boundaries and drift. Product code remains observed truth, and implementation plans own task-specific sequencing.

## Drift/Gaps

| id | status | observed_drift | routing | review_after |
| --- | --- | --- | --- | --- |
| DRIFT-20260525-01 | open | Chat sync/stream serialization is split across two gates. | Deferred architecture/service work. | 2026-06-24 |
| DRIFT-20260525-02 | watching | Orchestration schema changes still rely on inline compatibility checks rather than formal migration tooling. | Horizon architecture hardening. | 2026-06-24 |
| DRIFT-20260526-01 | open | Some code and historical docs still expose legacy job-owned workspace, scratch, runtime temp, and final-output directory paths. | `workspace-workarea-run-output-job-semantics` implementation phases; compatibility references must be explicitly marked legacy until removed. | 2026-06-26 |

## Validation Expectations

Architecture changes require focused automated tests, bounded Spring startup, and browser validation when a web surface is affected.

## Related Decisions

See `decisions.md`.

## Related Knowledge

Use knowledge filenames containing `architecture`, `orchestration`, `avatar`, `workspace`, `spring-ai`, `docker`, and `workflow` as the first-pass search terms.

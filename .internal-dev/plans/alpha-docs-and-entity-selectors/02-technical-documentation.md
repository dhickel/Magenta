# Technical Documentation

## Context

Technical docs must explain how Magenta fits together after the refactor. They must be based on code inspection, not old README text or older review assumptions.

Primary code targets:

- `src/main/java/io/mindspice/magenta2/api/web`
- `src/main/java/io/mindspice/magenta2/ai/chat`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan`
- `src/main/java/io/mindspice/magenta2/ai/chat/task`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool`
- `src/main/java/io/mindspice/magenta2/ai/config/user`
- `src/main/java/io/mindspice/magenta2/ai/orchestration`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces`
- `src/main/resources/schema.sql`

## Goal

Produce detailed technical documentation covering architecture, service boundaries, API/SSE contracts, persistence, security, configuration, frontend architecture, and operational runtime behavior.

## In Scope

- Technical architecture overview.
- Controller/API/SSE route reference.
- Service and package responsibility map.
- Persistence and schema guide.
- Chat, planning, task, workflow, orchestration runtime, workspace/tool/output docs.
- Security and alpha access docs.
- Configuration and operations docs.
- Frontend SimplyPages/HTMX/JS-island docs.

## Out of Scope

- Implementation changes except docs.
- Generating SDKs.
- Overpromising unimplemented future capabilities.

## Implementation Steps

1. Architecture overview.
   - Write `docs/technical/architecture.md`.
   - Include a request-flow map from browser/API -> controller -> service -> repository/schema.
   - Explain packages and boundaries:
     - Controllers are thin transport adapters.
     - Services own use cases.
     - Repositories own persistence/schema assumptions.
     - Records are preferred for payloads/data carriers.

2. API reference.
   - Write `docs/api/00-index.md` and `docs/technical/api-reference.md`.
   - Inventory REST/SSE families:
     - `/api/chat`
     - `/api/fragments`
     - `/api/plans`
     - `/api/tasks`
     - `/api/workflows`
     - `/api/jobs` and `/api/job-runs`
     - `/api/agents` and `/api/agents/{agentId}`
     - `/api/projects`
     - `/api/workspaces`
     - `/api/outputs`
     - `/api/dashboard/summary`
     - `/api/runtime/status`
     - `/api/settings/runtime`
     - `/api/models`
   - For each family document purpose, auth/CSRF expectations, key payload records, success responses, common errors, and SSE event names where applicable.

3. Service docs.
   - Write `docs/technical/services.md`.
   - Cover chat service/model routing, plan service, task service, workflow service, agent profile service, assignment service, job service, project service, workspace service, output artifacts, settings service.
   - Link services to controllers and tables.

4. Data model docs.
   - Write `docs/technical/data-model.md`.
   - Use `schema.sql` as the canonical table inventory, but note repositories may self-migrate compatibility columns.
   - Group tables by domain: chat/session/audit, plans/tasks/runs, workflows, agents/assignments/jobs, projects/workspaces/outputs/settings.

5. Chat/planning/task docs.
   - Write `docs/technical/chat-planning-tasks.md`.
   - Cover conversation state, planning lifecycle, queued questions, approval, saved plan/task definitions, plan/task runs, execution evidence, and model/tool routing.

6. Orchestration runtime docs.
   - Write `docs/technical/orchestration-runtime.md`.
   - Cover agents, assignments, queue lifecycle, statuses, leases, checkpoints, retained history, schedules, reactions, inbox messages, jobs, and cancellation/pause/resume semantics.

7. Workflow docs.
   - Write `docs/technical/workflow-engine.md`.
   - Cover workflow definitions, nodes, routes, validation, runs, approval/resume nodes, output mapping, and job integration.

8. Workspaces/tools/outputs docs.
   - Write `docs/technical/workspaces-tools-outputs.md`.
   - Cover workspace roots, leases, links, file/shell/web tool boundaries, output artifact querying/content/download.

9. Frontend docs.
   - Write `docs/technical/frontend-htmx.md`.
   - Cover SimplyPages usage, HTMX-first policy, fragment endpoints, static JS islands, CSS locations, and when JavaScript is justified.

10. Security/config/operations docs.
   - Write `docs/technical/security.md` for alpha auth, public read routes, unsafe mutation protection, CSRF cookie/header behavior, and HTMX failures.
   - Write `docs/technical/configuration-operations.md` for run modes, SQLite/data root, AI config, model keys, runtime settings, startup smoke, and validation expectations.

## Validation

- Every technical doc links to the relevant source file/package.
- Every exposed API family has an entry.
- SSE routes identify stream purpose and event behavior.
- Security docs are checked against `AlphaSecurityConfiguration`.
- Data docs are checked against `schema.sql` and repository self-migration notes.
- Frontend docs explicitly justify existing JavaScript islands and preserve HTMX-first policy.

## Exit Criteria

- A new contributor can trace each exposed technical surface from docs to code.
- No major service or API family is undocumented.


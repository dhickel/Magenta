---
schema_version: 1
document_type: api-specification
status: active
owner: api
created: 2026-05-25
---

# API Specification

## Intended Contract

API routes should expose stable request/response shapes, controlled status codes, and compatibility behavior. Controllers should stay thin and delegate use-case behavior to services.

## API Entries

| id | route_or_surface | status | intended_contract | payload_or_status | compatibility_rule | validation | related_decisions | related_knowledge |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| API-20260525-01 | Work Area file APIs | active | File create, move, copy, delete preflight/execute, labels, recent action rows, preview, save, and image view return controlled responses. Agent detail owns the user-facing Work Area browser route and guards Work Areas by agent owner. | `400` validation, `404` missing, `409` collision where distinguishable. | Preserve compatibility delete endpoint until deliberate removal. | Controller tests and API docs updates. | `DECISION-20260524-03` | `workspace-api-list-and-agent-tab-operational-pattern.md` |
| API-20260525-02 | Chat surface values | active | Known chat surface enum values accept case-insensitive names and reject blank/unknown values. | JSON boundary normalization. | Keep normalization narrow at the JSON boundary. | Controller/API tests. | `DECISION-20260523-05` | `api-boundary-enums-and-empty-job-run-completion.md` |
| API-20260525-03 | Empty submitted jobs | active | Empty submitted jobs complete as no-op job runs unless product policy later changes to reject them at submission. | Terminal job run instead of `RUNNING`. | Do not change without explicit product decision. | Assignment/job tests. | `DECISION-20260523-05` | `api-boundary-enums-and-empty-job-run-completion.md` |
| API-20260525-04 | Direct task run SSE | deferred | Consider native reactive SSE or explicit executor-backed execution if concurrency requirements grow. | SSE stream behavior. | Current blocking bridge is acceptable for current scope. | SSE tests and startup. | none | `plan-execution-stream-finalization.md` |
| API-20260525-05 | Workspace list endpoint | deferred | Add or stabilize workspace listing APIs before relying on dashboard-level workspace browser panels. | `GET /api/workspaces` or equivalent. | Avoid inventing UI around missing route contracts. | Controller/API tests and docs. | none | `workspace-api-list-and-agent-tab-operational-pattern.md` |
| API-20260525-06 | Browser chat pending messages | active | Normal `/chat` messages submitted during an active stream are stored through pending-message routes and later drained through the normal stream route. | `GET/POST /api/chat/{conversationId}/pending-messages`, `POST /claim`, `POST /{messageId}/ack`, `POST /{messageId}/release`; Java record DTOs. | Keep `/api/chat/turns/{turnId}/interrupt` intact for explicit interrupt semantics, but ordinary browser mid-turn messages must not use it by default. | Repository/service/controller tests, browser queue validation. | none | `chat-planning-composer-architecture.md`, `event-delegation-sse-dom-replacement.md` |
| API-20260526-01 | Task/workflow/job submission and run reads | active | Non-job task/workflow submissions require a user-visible `runDisplayName`; project and Work Area routing are first-class user-facing selectors; `workspaceId` remains compatibility metadata. Run reads may expose run-local staging and final output destinations distinctly. | Additive `runDisplayName` request/record field for non-job work; assignment/run output fields distinguish staging from promoted artifacts. | Legacy workspace/job-output fields may remain readable for old records but must be described as compatibility, not current routing. | Controller/API tests for required names, routing, rejection of job-owned workspace assumptions, and docs updates. | `DECISION-20260526-01` | `workspace-api-list-and-agent-tab-operational-pattern.md` |
| API-20260526-02 | Agent Skills management and assignment APIs | active | Expose APIs for skill metadata list/read/refresh/create, diagnostics, root-confined file tree/view/save/create, and agent-skill assignment add/remove/list. MVP assignment scope is agent profiles only. | Implemented `/api/skills` routes: `GET /`, `POST /refresh`, `POST /`, `GET /{skillName}`, `GET /{skillName}/diagnostics`, `GET /{skillName}/files`, `GET /{skillName}/files/view`, `PUT /{skillName}/files/text`, `POST /{skillName}/files`, `GET /{skillName}/assignments`, `POST|DELETE /{skillName}/assignments/agents/{agentId}`. | Project-local `.agents/skills/`, user-home scopes, and project/job/task/workflow/chat/session layered assignment are deferred and must not appear as active API behavior. Script execution and trust/registry flows remain out of MVP API scope. | Controller/API tests for success and negative paths (malformed diagnostics, refresh-after-`SKILL.md` save, traversal/symlink rejection, unknown agent/skill assignment handling, duplicate assignment idempotence), plus docs updates. | `DECISION-20260526-03`, `DECISION-20260526-06` | `agent-skills-specification-reference.md` |
| API-20260529-01 | Assistant dashboard widget instance fragments | active | Widget summary/detail/settings routes use stable widget instance ids: `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}`, `GET /detail`, `GET /settings`, and `PUT /settings`. Settings save returns OOB modal close plus refreshed summary. | HTML fragments; invalid settings return `400` with the settings modal and validation messages. | `/_dashboards/_widgets/{widgetKey}` compatibility routes remain for old quick-action fragments but are not the primary architecture. | Controller tests for stable roots, settings validation, and OOB settings save. | `DECISION-20260529-01` | `dashboard-fragment-navigation.md` |
| API-20260529-02 | Assistant dashboard planner fragments and tools | active | Planner widget fragments expose quick capture, day restart/review, planner task/subtodo creation, occurrence skip/snooze/restart, time block creation, and in-dashboard reminder creation through HTMX routes under `/_dashboards`. Static Avatar tools expose `avatar_today_plan_get/update`, `avatar_quick_capture`, `avatar_day_restart`, `avatar_tasks_routines_get`, `avatar_task_upsert`, `avatar_task_occurrence_update`, `avatar_calendar_schedule_get`, `avatar_timeblock_upsert`, and `avatar_reminder_upsert`. | HTML fragments and compact JSON tool responses. | Existing `avatar_todo_*`, `avatar_daily_task_*`, and `avatar_calendar_*` tools remain for compatibility. | Controller/tool registry tests plus startup; browser proof delegated. | `DECISION-20260529-02` | `dashboard-fragment-navigation.md` |

## Ownership Boundary

This file owns route and payload contracts. Service behavior belongs in `services.md`; page/fragment behavior belongs in `web.md`.

## Drift/Gaps

| id | status | observed_drift | routing | review_after |
| --- | --- | --- | --- | --- |
| DRIFT-20260525-05 | open | Workspace file explorer status mapping is partly message based. | Deferred typed domain errors before expanding compatibility claims. | 2026-06-23 |

## Validation Expectations

API changes require focused controller/API tests, docs updates for route or payload changes, and bounded startup when Spring wiring changes.

## Related Decisions

`DECISION-20260523-05`, `DECISION-20260524-03`.

## Related Knowledge

Search knowledge filenames for `api`, `workspace`, `enum`, `empty-job`, `sse`, and `workflow`.

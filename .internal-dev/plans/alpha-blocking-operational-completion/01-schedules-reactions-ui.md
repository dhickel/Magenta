# Phase 01: Schedules And Reactions Operational UI

## Context

APIs exist for `/api/agents/{agentId}/schedules` and `/api/agents/{agentId}/event-reactions`, but the operational UI lacks CRUD surfaces. Operators cannot configure recurring jobs or event-triggered assignments without raw API calls.

Relevant files:

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/EventReactionService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AgentSchedule.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AgentEventReaction.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`

## Goal

Add a complete HTMX-first operator surface for schedules and event reactions inside the agent detail experience. Operators should be able to view existing records, add records, edit records, toggle enabled state, and see validation errors without leaving the dashboard.

## In Scope

- Add `schedules` and `reactions` tabs or panels to the agent detail tab navigation.
- Add server-rendered fragments for schedule list, schedule form, reaction list, and reaction form.
- Add HTMX form endpoints in `OrchestrationController` that adapt form fields to the existing services.
- Keep the JSON API in `AgentOrchestrationController` compatible.
- Add delete support if the repository/service already supports it; if not, add a service/repository delete method for schedules and reactions.
- Show feature-disabled states when `magenta.features.schedules-enabled=false` or `magenta.features.reactions-enabled=false`.

## Out of Scope

- Building a visual cron expression editor.
- Adding new reaction action types beyond `ENQUEUE_ASSIGNMENT`.
- Adding a scheduler dashboard separate from the agent detail UI.
- Changing schedule firing semantics.

## Implementation Steps

1. Inspect existing tab rendering in `OrchestrationController`.
   - Find `tabNav(...)`, `/agents/_detail/{agentId}/{tab}`, and existing agent tab fragment methods.
   - Add `schedules` and `reactions` to the tab list only if feature flags are on, or include them always with disabled-state fragments.

2. Add schedule fragment endpoints in `OrchestrationController`.
   - `GET /agents/_detail/{agentId}/schedules`
   - `POST /agents/_detail/{agentId}/schedules`
   - `PUT /agents/_detail/{agentId}/schedules/{scheduleId}`
   - `DELETE /agents/_detail/{agentId}/schedules/{scheduleId}`
   - The response target should be the schedules tab panel, not a full page.

3. Add reaction fragment endpoints in `OrchestrationController`.
   - `GET /agents/_detail/{agentId}/reactions`
   - `POST /agents/_detail/{agentId}/reactions`
   - `PUT /agents/_detail/{agentId}/reactions/{reactionId}`
   - `DELETE /agents/_detail/{agentId}/reactions/{reactionId}`

4. Keep form payloads explicit and small.
   - Schedule fields: `jobId`, `assignmentType`, `priority`, `modelOverride`, `workspaceId`, `inputJson`, `cronExpression`, `timezone`, `enabled`.
   - Reaction fields: `eventType`, `filterJson`, `assignmentType`, `priority`, `modelOverride`, `workspaceId`, `inputJson`, `enabled`.
   - Parse JSON with Jackson, not manual string splitting.
   - Validation errors should render inside the same tab as `.dashboard-error` or the existing equivalent class.

5. Add or extend service delete methods.
   - Prefer `ScheduleService.delete(agentId, scheduleId)` and `EventReactionService.delete(agentId, reactionId)`.
   - Repository methods must scope deletes by `agentId` so one agent cannot delete another agent's records.

6. Render useful list rows.
   - Schedule rows show cron, timezone, next run, linked job, enabled state, and assignment type.
   - Reaction rows show event type, filter summary, action type, enabled state, and assignment type.
   - Row actions use HTMX `hx-put` or `hx-delete`.

7. Preserve API behavior.
   - Do not remove or reshape existing JSON endpoints.
   - If service delete methods are added, optional JSON delete endpoints can be added to `AgentOrchestrationController`, but UI endpoints are the priority.

## Validation

Required tests:

- Controller test that agent detail includes schedules/reactions tab targets or disabled panels.
- Controller test for schedule form create with valid cron, invalid cron, and invalid JSON input.
- Controller test for reaction form create with valid event type, invalid event type, and invalid JSON filter.
- Service/repository tests for scoped delete if delete support is added.
- Existing `AgentOrchestrationControllerTest` schedule/reaction tests must still pass.

Required commands:

```bash
mvn -q -Dtest=OrchestrationControllerTest,AgentOrchestrationControllerTest test
mvn -q test
timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Browser validation:

- Open agent detail page.
- Switch to schedules tab.
- Create an enabled schedule with a valid cron.
- Edit it to disabled.
- Submit invalid cron and verify inline error.
- Switch to reactions tab.
- Create an enabled `ENQUEUE_ASSIGNMENT` reaction.
- Submit invalid JSON filter and verify inline error.

## Exit Criteria

- Operators can manage schedules and reactions from the UI without raw API calls.
- Feature-disabled settings render a clear disabled state.
- No new JS transport surface is introduced for normal CRUD.
- Tests and startup smoke pass or blockers are documented with exact failure output.

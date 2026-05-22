# Phase 04 - Avatar Assistant Behaviors

## Context

Avatar should behave like a personal assistant over the existing Magenta chat, agent, task, project, schedule, reaction, and output systems. It should not create a second chat runtime. Organizer data such as todos, daily tasks, calendar items, notes, preferences, and widget state belongs in Avatar persistence from phase 01.

Relevant anchors:

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/RequestResolver.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicy.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/EventReactionService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`

## Goal

Add Avatar conversational behavior for daily planning, todos, local calendar items, notes, research assignments, project/output-directed work, and email-triggered alerts through existing events/reactions. All behavior should run through the current chat/model/tool path and existing orchestration services.

## In Scope

- Profile-scoped chat wrapper for Avatar turns.
- Avatar organizer tools over Avatar persistence.
- Project/task/job/output helper tools that call existing services.
- Redacted email alert event ingress and reaction support.
- Prompt/tool contracts for Avatar behaviors.

## Out of Scope

- External calendar provider integration.
- Mailbox polling, OAuth, or storing raw email bodies.
- New model client, second tool registry, or second runtime.
- Generic todo/calendar/note storage in plan state.
- Granting shell access before per-agent shell allowlist scoping is proven.

## Implementation Steps

1. Add profile-scoped chat support.
   - Add `ChatTurnProfile` or equivalent under `ai.chat.service.turn`.
   - Include agent id, system prompt, approved tool names, shell allowlist, project id, and page context.
   - Add `ChatService.chatAsAgent(...)`.
   - Ensure it uses the same `toolChatWithRetry`/plain chat path.
   - Ensure prompt, tools, and model resolve from the selected Avatar profile.

2. Add Avatar organizer services if phase 01 has not already exposed them.
   - Todos: list, upsert, complete.
   - Daily tasks: list/upsert/complete by date.
   - Calendar: local item list/upsert/delete.
   - Notes: append/search.
   - Preferences and state snapshots.
   - If persistence is still missing, add it to the Avatar domain package and `avatar-schema.sql`, not to primary `schema.sql`.

3. Add Avatar tools.
   - `avatar_todo_list`, `avatar_todo_upsert`, `avatar_todo_complete`.
   - `avatar_daily_task_list`, `avatar_daily_task_upsert`, `avatar_daily_task_complete`.
   - `avatar_calendar_list`, `avatar_calendar_upsert`, `avatar_calendar_delete`.
   - `avatar_note_append`, `avatar_note_search`.
   - `avatar_submit_task`, `avatar_submit_research_assignment`.
   - `avatar_list_outputs`, `avatar_read_output`.
   - Tool outputs are compact JSON records, not HTML.

4. Add research/project assignment flow.
   - Use `TaskService` for task definitions and inputs.
   - Use `AssignmentService` for queued task assignments.
   - Carry `projectId`, `workspaceId`, model override, priority, and output target options through existing request types where possible.
   - Query outputs through `OutputArtifactService`.

5. Add email alert event flow.
   - Extend `EventType` with `EMAIL_ALERT_RECEIVED`.
   - Add a small adapter/controller only if HTTP ingress is required.
   - Secure ingress with a configured token header; never render or persist the token.
   - Persist redacted payload only: message id hash, from domain, optional address hash, subject snippet, received timestamp, labels, importance, thread key hash.
   - Continue matching reactions with current top-level payload key/value semantics.

6. Guard shell/tool scope.
   - Do not give Avatar `shell_exec` until per-agent shell allowlist is resolved at execution time.
   - Ensure `ChatToolRegistry` validates all Avatar tool names.

7. Update docs and closeout.
   - End-user Avatar assistant behavior docs.
   - API docs for Avatar/event routes.
   - Technical docs for profile-scoped chat and redacted email events.

## Validation

Focused tests:

- Profile-scoped chat uses Avatar prompt, tools, model, and project context.
- Organizer tools perform CRUD over Avatar persistence.
- Avatar tools call services rather than repositories from other domains.
- Research task submission creates correct assignment.
- Output listing/read respects existing output limits and path confinement.
- Email event ingress rejects missing/bad token.
- Email event payload is redacted and reactions enqueue expected assignments.
- Shell access remains blocked unless per-agent allowlist behavior is implemented and tested.

Commands:

- `mvn -Dtest=AvatarOrganizerServiceTest,AvatarToolsTest,AvatarEmailEventControllerTest test`
- `mvn -Dtest=ChatServiceTest,ChatToolRegistryTest,AgentProfileControllerTest,ScheduleReactionFeatureParitySpringTest test`
- `mvn -Dtest=TaskControllerTest,OutputControllerTest,OrchestrationRuntimeTest test`
- `mvn test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

Browser validation:

- Required if this phase exposes UI flows.
- Must be run by a validation subagent using `gpt-5.3-codex` medium.
- Validate Avatar chat adding a todo, scheduling an item, appending/searching a note, submitting project-scoped research work, viewing output state, and reacting to a redacted email alert.

## Exit Criteria

- Avatar chat can perform organizer and project/output workflows through existing runtime services.
- No raw email secret/body leaks into DB, logs, prompts, UI, or `.internal-dev`.
- No second chat/model/tool runtime exists.

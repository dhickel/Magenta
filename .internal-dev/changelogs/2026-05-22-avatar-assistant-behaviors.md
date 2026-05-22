# Avatar Assistant Behaviors

## Date

2026-05-22

## Change Summary

Implemented Phase 04 Avatar assistant behaviors. Avatar now exposes Spring AI tools for todos, daily tasks, local calendar items, notes, task submission, research-oriented task submission, and output artifact list/read through the existing chat/tool path. Tool implementations use `AvatarService`, `AssignmentService`, `TaskService`, and `OutputArtifactService` instead of creating a second runtime or bypassing persistence boundaries. A short-lived token-gated email alert HTTP ingress was removed during orchestration review because email processing should enter later through scripting/internal messaging/tool-created messages instead of a public Avatar endpoint.

## Files

- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/**`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/EventType.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarToolsTest.java`
- `docs/end-user/agents.md`
- `docs/technical/workspaces-tools-outputs.md`

## Behavioral Impact

The Avatar profile can approve and use:

- `avatar_todo_list`, `avatar_todo_upsert`, `avatar_todo_complete`
- `avatar_daily_task_list`, `avatar_daily_task_upsert`, `avatar_daily_task_complete`
- `avatar_calendar_list`, `avatar_calendar_upsert`, `avatar_calendar_delete`
- `avatar_note_append`, `avatar_note_search`
- `avatar_submit_task`, `avatar_submit_research_assignment`
- `avatar_list_outputs`, `avatar_read_output`

Tool responses are compact JSON records. The tools do not grant shell access, create a second runtime, add UI routes, or store organizer data outside Avatar persistence.

No public email-ingress endpoint is exposed for Avatar. Email-triggered alerts remain a later internal integration target through scripting API, internal messaging, or agents using approved tools to add messages.

## Risks

The reserved Avatar profile is still disabled by default from Phase 01. Operators must activate it and approve exact tool names before chat can use these tools. Authorization also depends on `ChatService.chatAsAgent(...)` or another existing path installing an Avatar `OrchestrationTaskContext`. Email processing remains out of scope until the internal scripting/messaging path and endpoint lockdown rules are designed.

## Follow-up Items

- Coordinator should run broader integrated chat/profile validation after Phase 05 surfaces Avatar chat UX.
- If research workflows need task creation from free text, add a separate narrow API for drafting or selecting research task templates instead of overloading `avatar_submit_research_assignment`.

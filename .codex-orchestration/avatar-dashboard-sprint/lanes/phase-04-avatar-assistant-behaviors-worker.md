# Phase 04 Avatar Assistant Behaviors Worker Handoff

## Status

Implemented organizer assistant tools for the Avatar profile. No commit was created.

## Correction

The email alert HTTP ingress described in this original worker handoff was removed during orchestration review after Dwight clarified that email processing must enter through scripting API, internal messaging, or agents using approved tools to add messages. See `phase-04-email-ingress-remediation.md`.

## Changed Files

- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- Removed during remediation: first-pass Avatar email alert HTTP ingress files.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantToolAuthorizationService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantToolConfiguration.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantToolResponses.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantTools.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/EventType.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarServiceTest.java`
- Removed during remediation: first-pass Avatar email alert HTTP ingress test.
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarToolsTest.java`
- `docs/end-user/agents.md`
- `docs/technical/workspaces-tools-outputs.md`
- `.internal-dev/changelogs/2026-05-22-avatar-assistant-behaviors.md`

## Implemented Behavior

- Added Avatar organizer helper methods for todo lookup/complete, daily task lookup/complete, calendar lookup/delete, and note append/search.
- Added Spring AI tool registration for:
  - `avatar_todo_list`, `avatar_todo_upsert`, `avatar_todo_complete`
  - `avatar_daily_task_list`, `avatar_daily_task_upsert`, `avatar_daily_task_complete`
  - `avatar_calendar_list`, `avatar_calendar_upsert`, `avatar_calendar_delete`
  - `avatar_note_append`, `avatar_note_search`
  - `avatar_submit_task`, `avatar_submit_research_assignment`
  - `avatar_list_outputs`, `avatar_read_output`
- Tool implementations return compact JSON and route through `AvatarService`, preserving the `avatar.sqlite` persistence boundary.
- Task helper tools validate an existing task through `TaskService` and create `TASK_RUN` assignments through `AssignmentService`.
- Output helper tools use `OutputArtifactService` query/read APIs, preserving existing path confinement and content-size checks.
- Tools require the active orchestration context to be the configured Avatar supervisor agent id and require exact per-tool approval on that profile.
- Removed during remediation: first-pass email alert HTTP ingress and event enum. Email processing is deferred to scripting API, internal messaging, or agents using approved tools to add messages.
- No shell access was added.

## Validation

- Original focused Avatar tests passed before remediation. Remediation-focused tests are recorded in `phase-04-email-ingress-remediation.md` and the shared notes.
- `mvn -Dtest=ChatToolRegistryTest,AgentOperationalToolConfigurationTest test` - passed.
- `mvn -DskipTests compile` - passed.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` - reached healthy startup on random port `40249`, then exited `124` when `timeout` stopped it and Spring shut down cleanly.

## Remaining Gaps

- UI exposure is deferred to Phase 05.
- Free-text research task creation is not implemented. `avatar_submit_research_assignment` intentionally requires an existing task id and submits a `TASK_RUN` assignment with research input values; creating new task templates from arbitrary research requests needs a separate narrow service/API contract.
- No external mailbox polling or calendar provider integration was added.

## Coordinator Next Steps

- Enable the Avatar profile and approve the new Avatar assistant tool names when testing Avatar chat behavior.
- Do not test a public Avatar email alert endpoint; that ingress was removed. Future email processing should use internal scripting/messaging/tool-created message paths after endpoint lockdown design.
- Run integrated Avatar chat validation once the Phase 05 UI lane exposes the assistant surface.

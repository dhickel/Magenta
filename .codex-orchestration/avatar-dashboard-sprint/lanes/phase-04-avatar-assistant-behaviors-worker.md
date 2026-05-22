# Phase 04 Avatar Assistant Behaviors Worker Handoff

## Status

Implemented organizer assistant tools for the Avatar profile. No commit was created.

## Changed Files

- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarEmailAlertIngressService.java`
- `src/main/java/io/mindspice/magenta2/api/avatar/AvatarEmailAlertController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantToolAuthorizationService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantToolConfiguration.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantToolResponses.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantTools.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/EventType.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarServiceTest.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarEmailAlertIngressServiceTest.java`
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
- Added `EventType.EMAIL_ALERT_RECEIVED` plus `POST /api/avatar/email-alerts`, token-gated by `X-Magenta-Avatar-Email-Token` and `magenta.avatar.email-alert-token`.
- Email alert events publish only redacted payload fields: message id hash, from domain, optional address hash, subject snippet, received timestamp, labels, importance, and thread key hash.
- No shell access was added.

## Validation

- `mvn -Dtest=AvatarRepositoryTest,AvatarServiceTest,AvatarEmailAlertIngressServiceTest,AvatarToolsTest test` - passed.
- `mvn -Dtest=ChatToolRegistryTest,AgentOperationalToolConfigurationTest test` - passed.
- `mvn -DskipTests compile` - passed.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` - reached healthy startup on random port `40249`, then exited `124` when `timeout` stopped it and Spring shut down cleanly.

## Remaining Gaps

- UI exposure is deferred to Phase 05.
- Free-text research task creation is not implemented. `avatar_submit_research_assignment` intentionally requires an existing task id and submits a `TASK_RUN` assignment with research input values; creating new task templates from arbitrary research requests needs a separate narrow service/API contract.
- No external mailbox polling or calendar provider integration was added.

## Coordinator Next Steps

- Enable the Avatar profile and approve the new Avatar assistant tool names when testing Avatar chat behavior.
- Configure `magenta.avatar.email-alert-token` before testing email alert ingress.
- Run integrated Avatar chat validation once the Phase 05 UI lane exposes the assistant surface.

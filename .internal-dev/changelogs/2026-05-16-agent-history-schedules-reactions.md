# Date

2026-05-16

# Change Summary

Enabled schedule and reaction surfaces by default, split live Queue rows from retained History rows, prevented terminal assignment hard delete from queue/API delete paths, and added manual plus automatic terminal-history purge.

# Files

- `src/main/resources/application.yml`
- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettings*.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

# Behavioral Impact

- Queue shows only non-terminal assignments.
- History shows terminal assignments and can load diagnostics or static transcript details.
- Queue delete no longer removes failed, completed, cancelled, or needs-review history entries.
- `DELETE /api/agents/{agentId}/assignment-history?olderThanDays=N` purges terminal assignment rows older than `N` days.
- Runtime settings include `assignmentHistoryAutoPurgeDays`, defaulting to `-1` for disabled automatic purge.
- New schedule and reaction forms default to disabled while edit forms preserve saved enabled state.

# Risks

- Automatic purge is intentionally conservative and defaults off; operators must opt in with a positive day count.
- History purge removes assignment rows and assignment-conversation links only, so related chat/audit records remain available through their own surfaces.

# Follow-up Items

- None.

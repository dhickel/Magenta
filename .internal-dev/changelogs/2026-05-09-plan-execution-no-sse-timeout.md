# Date

2026-05-09

# Change Summary

Disabled the saved-plan execution SSE wall-clock timeout by default. Plan execution streams now use no server-side timeout when `magenta.plan.execution-stream-timeout-seconds` is `0` or negative, while positive configured values still apply as an explicit cap.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/resources/application.yml`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- `.internal-dev/bugs/.archive/plan-execution-timeout-clears-visible-chat/report.md`
- `.internal-dev/knowledge/plan-execution-stream-finalization.md`

# Behavioral Impact

Long-running saved-plan executions are no longer failed solely because the SSE connection reaches the previous 360-second wall-clock timeout. Model/tool errors, explicit positive timeout configuration, client disconnect cleanup, and user cancellation behavior are unchanged.

# Risks

Low. The change is limited to plan-execution stream timeout configuration. Deployments that want a fixed cap can still set a positive timeout value.

# Follow-up Items

None.

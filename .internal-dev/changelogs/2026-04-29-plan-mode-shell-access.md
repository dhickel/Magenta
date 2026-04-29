## Date
2026-04-29

## Change Summary
Allowed `shell_exec` in PLAN mode so planning conversations can inspect local databases and environment state through the configured shell tool.

## Files
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/AGENTS.md`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`

## Behavioral Impact
Models in PLAN mode now receive `shell_exec` in addition to file exploration and `plan_save`, subject to the normal configured shell command allowlist.

## Risks
PLAN mode now has broader tool capability. The prompt still directs shell use toward planning research and discourages broad or irreversible side effects unless explicitly requested.

## Follow-up Items
- Revisit whether plan-mode shell access should show stronger UI/status affordances for tool side effects.

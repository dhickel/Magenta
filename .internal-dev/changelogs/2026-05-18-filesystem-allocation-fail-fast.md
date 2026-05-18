# Date

2026-05-18

# Change Summary

Plan/task run startup now fails fast when required filesystem workspace or output allocation fails. `PlanService.startRun` saves a terminal `FAILED` run with explicit allocation failure text and execution evidence instead of continuing as `RUNNING` with null workspace/output paths. Chat task execution returns that failed startup result without invoking the model.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-22-medium-filesystem-allocation-continues/report.md`
- `.internal-dev/knowledge/filesystem-allocation-fail-fast.md`

# Behavioral Impact

Filesystem-backed task/plan execution no longer proceeds after workspace/output allocation failure. Operators see the allocation failure in the run status, `errorText`, and execution evidence.

# Risks

Callers that previously assumed `startRun` always returned `RUNNING` now need to respect terminal startup failure states. The blocking and streaming chat task paths were updated to return non-running startup results immediately.

# Follow-up Items

Parent validation should run the public alpha subplan 05 gate and record final evidence before this subplan is marked passed.

Implementer validation passed with focused `PlanServiceTest`, adjacent orchestration/workflow/stream tests, `git diff --check`, and bounded Spring Boot startup on ephemeral port `33235`.

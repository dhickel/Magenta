# Output Attribution Current Layout

## Date

2026-05-18

## Change Summary

Implemented public alpha remediation bug-24 / domain 02 subplan 07. `PlanService` fallback output attribution now recognizes the current `agents/{agentId}/workspace/outputs/{slug-run}` layout and keeps legacy `agents/{agentId}/outputs/{slug-run}` compatibility.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationTaskContext.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactServiceAttributionTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-24-medium-output-attribution-stale-path/report.md`
- `.internal-dev/knowledge/output-artifact-attribution-query-and-backfill-pattern.md`

## Behavioral Impact

- Completed runs without an active orchestration holder can derive `agent_id` from current workspace output directories.
- Explicit orchestration context attribution remains stronger than path-derived fallback, while missing explicit agent ids can still be filled from the output path.
- Project-only orchestration contexts are treated as attribution context instead of being ignored.
- Filtered output artifact queries by agent, job, project, and workspace continue to include newly attributed artifacts.

## Risks

The fallback parser is path-shape based. Future workspace layout changes must update the parser and tests together.

## Validation

- Focused validation passed with `mvn -Dtest=PlanServiceTest,OutputArtifactServiceAttributionTest,WorkspaceRepositoryAttributionTest test`.
- `git diff --check` passed.
- Bounded Spring Boot startup passed.

## Follow-up Items

- Parent review/validation should mark bug-24 passed before the domain is closed.

# Topic
Output artifact attribution query and runtime backfill pattern

# Source References
- `.internal-dev/plans/alpha-blocking-operational-completion/02-output-artifact-attribution.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`

# Key Takeaways
- Keep output filtering first-class in persistence (`OutputArtifactQuery`) instead of reconstructing ownership via job/run traversal.
- For migration safety on SQLite, use additive `alter table ... add column` guarded by `pragma_table_info` checks.
- When end-to-end context is not available at materialization time, use post-run backfill keyed by `run_id` to fill null attribution columns.
- Preserve compatibility by falling back to run traversal only when direct attribution filters return empty for legacy data.

# Engine Relevance
- This pattern keeps controller logic thin while moving filtering and attribution concerns into workspace repository/service layers.
- Runtime attribution/backfill in `OrchestrationRunnerService` avoids invasive changes to chat task execution internals while still improving output query precision.

# Open Questions
- Should historical artifacts receive an offline migration/backfill to remove long-term fallback traversal?
- Should run-type values be normalized to a closed enum persisted as canonical wire values?

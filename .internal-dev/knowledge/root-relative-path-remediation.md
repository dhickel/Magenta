## Topic

Root-relative path remediation patterns

## Source References

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/RootRelativePathService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`

## Key Takeaways

- Persisted Magenta-owned filesystem paths should be root-relative when a `RootRelativePathService` is available.
- Tests that inspect persisted paths should resolve them against `WorkspaceDirectoryService.dataRoot()` before checking host filesystem state.
- Runtime execution contexts should use host paths; tests that assert context paths need to provide the runner with a `RootRelativePathService`.
- Runner-style services that receive both `WorkspaceDirectoryService` and `RootRelativePathService` should synthesize the resolver from workspace directories when only the directories are present.
- Legacy absolute paths under the current data root can be normalized for display/listing without rewriting database rows.
- Stale absolute paths outside the current data root should not be surfaced as valid active links.

## Engine Relevance

These patterns keep warm-database compatibility while avoiding raw host path leakage in user-facing workspace and output surfaces.

## Open Questions

None.

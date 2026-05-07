## Topic

Runtime orchestration settings, agent profiles, and workspace persistence

## Source References

- `.internal-dev/plans/orchestration-driver/phase-01-runtime-agents-settings-workspaces.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettingsService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileSeeder.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`

## Key Takeaways

Runtime agent state now belongs in SQLite `agent_profiles`, not in file config. File config remains responsible for model endpoint definitions and legacy agent seed/import data.

Startup has an ordering edge: shell tools are created before the `ApplicationRunner` seeder runs. Runtime settings reads therefore keep legacy config fallbacks for prompt/tool/shell/model defaults until the DB has the seeded `magenta` profile.

Workspace roots are relative database values and resolved under `dataRoot` at use time. `PATH` links are normalized against the workspace root and rejected if they escape `dataRoot`.

## Engine Relevance

Later orchestration phases should reuse `RuntimeSettingsService` for model-choice priority and `WorkspaceService` for managed filesystem paths instead of reading file agents or resolving workspace paths ad hoc.

## Open Questions

- How should hard deletion behave once durable jobs, assignments, or inbox records can reference agent profiles?
- What structured metadata shape should `REPOSITORY` workspace links use when repo automation is introduced?

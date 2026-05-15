# Docker Run Scoped Output Path

## Topic
Docker-backed task runs use a persistent agent output root mounted at container `/output`, so each task run must use a run-specific child directory rather than writing directly to `/output`.

## Source References
- `PlanService.startRun(...)`
- `PlanService.dockerRuntimeContext(PlanRun)`
- `OutputArtifactService.materializeFilePath(...)`
- `AgentShellToolService.execInContainer(...)`

## Key Takeaways
- The managed agent container mounts `dataRoot/agents/{agentId}/outputs` to `/output`.
- A task run output directory is a child of that mounted root: `dataRoot/agents/{agentId}/outputs/{slug}-{runId}`.
- The container-visible path for the run is `/output/{slug}-{runId}`.
- Model prompts must name the exact run path. Saying only `/output` can cause files to land in the agent output root rather than the run directory.
- Artifact materialization should accept `/output/{slug}-{runId}/file`, `/output/file`, bare filenames, and relative paths only when they resolve inside the active run directory.

## Engine Relevance
This prevents Docker-backed tasks from mixing outputs between runs and keeps the UI/API artifact contract aligned with the container mount layout. It also gives validation a concrete path to check in both the container and host filesystem.

## Open Questions
- Whether the app should expose the current run output path as an environment variable such as `MAGENTA_OUTPUT_DIR` for model/tool clarity.
- Whether direct writes to bare `/output/file` should eventually become a warning or hard failure after alpha.

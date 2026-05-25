# Domain Workspaces/Tools/Outputs Review

## Agent

- Agent: domain-workspaces-tools-outputs
- Agent id: `019e371e-86a5-74a0-8d4e-697360e60bf3`
- Model / reasoning: GPT-5.5 Codex high
- Mode: read-only

## Scope

Reviewed filesystem workspace runtime, path confinement, shell/file/web tools, output artifacts/downloads, workspace links/leases, project lease behavior, agent workspace status, and stale Docker references.

## Files, Routes, and Tables Reviewed

- Files: `WorkspaceDirectoryService`, `WorkspaceService`, `WorkspaceLeaseService`, `WorkspaceRepository`, `OutputArtifactService`, `AgentFileToolService`, `AgentShellToolService`, `AgentWebToolService`, `ProjectService`, `OrchestrationRunnerService`, `RuntimeController`, `WorkspaceController`, `OutputController`, `ProjectController`, `OrchestrationController`, `PlanService`, `application.yml`, `ai-config.example.json`.
- Routes: `/api/workspaces`, `/api/outputs`, `/api/projects/{id}/workspace`, `/api/runtime/status`, `/outputs`, `/projects`, `/agents/_detail/{agentId}/workspace`, `/agents/_detail/{agentId}/exec`.
- Tables: `workspaces`, `workspace_links`, `workspace_leases`, `run_output_artifacts`, `work_assignments`.

## Commands and Probes

- `find .. -name AGENTS.md -print`
- Targeted `rg` over workspace/tool/output/runtime terms
- Targeted `nl -ba ... | sed -n ...` line evidence reads
- `git status --short`

## Findings

- Critical: shell execution is host-level and only constrains working directory/executable, not command effects. Wildcard shell command configuration makes this especially risky in the filesystem runtime.
- High: file tools are confined to `dataRoot`, not to the current agent workspace, project lease, or assignment.
- High: project workspace leases do not appear to materialize usable filesystem links, matching the runtime-domain finding.
- High: `web_fetch` can follow a public URL redirect into private/local hosts because private-host validation is not reapplied to the final redirected URI.
- Medium: `file_path` output materialization can follow symlinks after only a lexical data-root check.
- Medium: output attribution still contains stale pre-workspace path logic that can lose `agent_id` for non-orchestration artifacts.
- Medium: operational UI still has Docker-named status targets and stale target IDs.
- Low: agent workspace health shown in operational UI masks richer filesystem state available from `AgentWorkspaceStatusService`.

## Explicitly Ruled Out

- File tool traversal/symlink reads through existing paths are blocked by `toRealPath()` and root checks.
- Output download/content endpoints realpath-check artifact files against `dataRoot`.
- Workspace write-lease exclusivity is enforced by a partial unique index and atomic insert conflict handling.
- Project membership is checked before project lease acquisition.

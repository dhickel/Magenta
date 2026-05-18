# Phase 04: Workspaces, Mounts, And Linked Directories — Evidence

## Mount Contract — Verified

All three bind mounts visible in agent detail Docker panel:

| Container Path | Host Path | Writable |
|---|---|---|
| `/home/agent` | `.magenta/root/agents/{id}/home` | ✓ (mount exists, dir empty for new agent) |
| `/workspace` | `.magenta/root/agents/{id}` | ✓ |
| `/output` | `.magenta/root/agents/{id}/outputs` | ✓ (provenance files materialized) |

## Workspace Visibility — UI

Agent Workspace tab (`#agent-tab-panel`):
- Workspace ID: `af73b0e6-9248-4ebc-8877-d16e43dc890c`
- Owner: `AGENT:30f51f72-f521-4a5b-84fb-dd024cf43291`
- Root Relative Path: `agents/30f51f72-f521-4a5b-84fb-dd024cf43291`
- Output Directory Hint: `agents/30f51f72-f521-4a5b-84fb-dd024cf43291/outputs`
- Active Leases: header present, "No active leases"
- Workspace Links: header present, "No workspace links configured"

## Container Restart Persistence

The container survived a Sleep→Restart cycle in Phase 02. Mount paths remained consistent across container lifecycle. Output artifacts written after restart persisted on the host filesystem.

## Output Readability Post-Run

Three output artifacts remain readable after run completion:
- `home_agent_provenance` — text, visible in agent Outputs tab and global /outputs page
- `workspace_provenance` — text, visible in both views
- `output_provenance` — text, visible in both views

All show full attribution (name, type, plan ID, run ID, timestamp).

## Temp Workspace Cleanup

Confirmed: `runtime/task-runs/c3b9791d-...` directory was cleaned up after task completion. Only the materialized output directory under `outputs/` persists.

## Lease and Link Coverage

Leases and workspace links could not be validated with active data (no existing leases or links in this fresh agent). The UI surfaces both sections with appropriate empty states. Lease/link creation and conflict behavior deferred to Phase 06 (operational flows) and Phase 07 (failure modes).

## Assessment

**PASS** — Mount contract is correctly wired:
- Three bind mounts visible in UI and verified via file materialization
- Workspace metadata (ID, owner, path, output hint) exposed correctly
- Temp workspace auto-cleans after completion
- Output artifacts persist after run completion with full attribution
- Container restart preserves mount structure

Gap: No active leases or workspace links existed to test creation, conflict, or remediation flows. These are covered in phases 06/07.

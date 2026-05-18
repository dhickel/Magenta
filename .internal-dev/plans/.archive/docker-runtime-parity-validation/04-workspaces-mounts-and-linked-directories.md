# Phase 04: Workspaces, Mounts, And Linked Directories

## Context

Docker integration is only correct if the filesystem contract survives real usage: persistent agent state, temporary run state, output persistence, linked workspaces, and lease semantics must all line up.

## Goal

Validate the complete directory/mount contract visible to agents and operators.

## In Scope

- `/home/agent`, `/workspace`, `/output` mounts.
- Agent home persistence.
- Task/workflow temp workspaces and cleanup.
- Job/project persistent workspaces.
- Workspace links and active leases.
- Output roots, attribution, and download/readback.

## Out of Scope

- New file browser/editor features.

## Implementation Steps

1. Use the phase `03` validation agent and create known marker files under all three mounted directories.
2. Restart the managed container and verify the correct markers persist.
3. Create a task temp workspace and workflow temp workspace, then verify terminal cleanup behavior.
4. Create or reuse a job/project workspace, validate persistence, and acquire a write lease.
5. Add a workspace link through the exposed UI or API-backed UI flow and verify the operator can understand linked ownership and target path.
6. Validate exclusive write lease behavior and user-visible failure handling for conflicts.
7. Verify output attribution across agent, run, job, and project views.
8. Verify no loose file or output silently appears in the wrong root or without usable attribution.

## Validation

Required checks:
- All three Docker mounts are real and writable where expected.
- Persistent state persists across restart; temp state is cleaned after terminal completion.
- Linked directories are visible and correctly associated with their owners.
- Lease conflicts are enforced and surfaced clearly.
- Outputs remain readable after the originating run completes.

## Exit Criteria

- `.internal-dev/reviews/docker-runtime-parity-validation/04-workspace-mount-evidence.md` exists.
- Any mount, link, cleanup, or attribution mismatch is logged.

# Phase 03: Agent Execution Provenance

## Context

A completed task is not enough. This phase proves where work actually ran.

## Goal

Prove that every supported agent work entry point executes inside the managed container and that no successful run can be mistaken for host-side execution.

## In Scope

- Task/plan execution submitted to an agent.
- Workflow task-node execution.
- Job/assignment-backed execution.
- Chat-originated plan execution when it routes through agent shell tools.
- Container provenance, shell cwd, environment markers, and output path evidence.

## Out of Scope

- Rich UI parity unrelated to execution provenance.

## Implementation Steps

1. Prepare deterministic validation work items that must use shell execution and write files.
2. For each entry point, execute work from the browser and collect:
   - agent id
   - run id / assignment id / workflow run id
   - container id/name
   - command transcript
   - `pwd`
   - marker file content from `/home/agent`, `/workspace`, and `/output`
   - resulting output artifacts
3. Include at least one task, one workflow task node, and one job/assignment path.
4. Validate that output artifacts resolve from container-visible `/output/...` paths into the expected persisted metadata.
5. Re-run one execution after container restart to prove persistence belongs to mounted paths rather than container-local ephemeral storage.
6. Inspect logs and persisted records only as supporting evidence; browser-visible state and output readback remain primary.
7. Record any path that completes without explicit container provenance as a blocker pending proof.

## Validation

Required checks:
- Every tested execution path shows Docker/container provenance.
- Shell execution happens under the managed container, not the host.
- Output artifacts are visible in the UI and downloadable/readable where supported.
- Restarting the container does not erase mounted state that should persist.

## Exit Criteria

- `.internal-dev/reviews/docker-runtime-parity-validation/03-execution-provenance-evidence.md` exists.
- At least three distinct runtime paths have container proof.

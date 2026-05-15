# 2026-05-15 - Filesystem Agent Runtime Refactor Plan Suite

## Summary

Added a breaking-refactor plan suite for removing Docker from the active agent runtime and replacing it with filesystem-backed workspaces plus Bash execution.

## Artifacts Added

- `.internal-dev/plans/filesystem-agent-runtime-refactor/README.md`
- `.internal-dev/plans/filesystem-agent-runtime-refactor/00-orchestration-plan.md`
- `.internal-dev/plans/filesystem-agent-runtime-refactor/architecture-map.md`
- `.internal-dev/plans/filesystem-agent-runtime-refactor/api-and-schema-examples.md`
- `.internal-dev/plans/filesystem-agent-runtime-refactor/implementation-playbook.md`
- `.internal-dev/plans/filesystem-agent-runtime-refactor/01-filesystem-layout-and-config-contract.md`
- `.internal-dev/plans/filesystem-agent-runtime-refactor/02-bash-execution-runtime.md`
- `.internal-dev/plans/filesystem-agent-runtime-refactor/03-workspace-monitoring-and-output-routing.md`
- `.internal-dev/plans/filesystem-agent-runtime-refactor/04-ui-and-public-contract-removal.md`
- `.internal-dev/plans/filesystem-agent-runtime-refactor/05-docker-deletion-and-migration-cleanup.md`
- `.internal-dev/plans/filesystem-agent-runtime-refactor/06-final-validation-gate.md`
- `.internal-dev/plans/filesystem-agent-runtime-refactor/phase_handoff_notes.md`

## Key Decisions Captured

- `AiConfig.dataRoot()` remains the only filesystem root.
- Each agent uses `agents/<agentId>/workspace/` as its execution root.
- Agent-owned outputs move under `workspace/outputs/`.
- Docker lifecycle and monitoring are replaced by workspace health/activity reporting.
- Implementation is serialized across blocking subagent phases with mandatory handoff reports.

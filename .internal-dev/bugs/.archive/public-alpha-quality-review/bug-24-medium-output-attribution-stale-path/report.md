# Output Attribution Uses Stale Pre-Workspace Path Logic

## Summary

Fallback output attribution still expects old `data/agents/{agentId}/outputs` layout, while current outputs are under `agents/{agentId}/workspace/outputs`.

## Scope

Plan output materialization and output filtering/attribution.

## Reproduction

1. Execute non-orchestration task output allocation under current layout.
2. Inspect `run_output_artifacts.agent_id` and agent/project output filters.

## Expected

Output attribution should identify agent/job/project/workspace under current filesystem layout.

## Actual

Fallback path parser can miss the agent id because it assumes the old path shape.

## Evidence

- `PlanService.java:815` allocates outputs under `agents/{agentId}/workspace/outputs`.
- `PlanService.java:1767` fallback attribution comments/index math expect `data/agents/{agentId}/outputs`.

## Impact

Medium: output artifacts can lose attribution and disappear from filtered operational views.

## Status

Implemented and validated in public alpha remediation domain 02 subplan 07.

## Next Action

Run the public alpha remediation domain 02 validation gate.

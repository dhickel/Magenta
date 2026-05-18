# Subplan 07: Output Attribution

## Goal

Update fallback output attribution for current workspace output layout.

## Implementation Steps

1. Locate attribution parsing in `PlanService` and output repository/service code.
2. Support current `agents/{agentId}/workspace/outputs` paths.
3. Preserve explicit attribution fields when present.
4. Add regression tests for agent/job/project/workspace attribution.

## Validation

Output artifact tests prove filtered operational views include newly attributed artifacts.

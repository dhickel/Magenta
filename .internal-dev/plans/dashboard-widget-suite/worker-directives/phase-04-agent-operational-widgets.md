---
schema_version: 1
document_type: worker-directive
status: planning
phase: 04
role: agent-operational-widgets
worker_model: gpt-5.5
worker_reasoning: high
validator_model: gpt-5.5
validator_reasoning: xhigh
---

# Phase 04 Agent Operational Widgets Directive

## Objective

Implement Agent Status/Queue, Agent Outputs, and Agent Files/Notes widgets with explicit selected-agent/project/Work Area settings and strict service/tool boundaries.

## Editable Targets

- Dashboard widget services/renderers/routes.
- Existing agent profile, assignment, inbox, job, project, output, Work Area services only through service APIs.
- `AvatarDashboardController`/components or decomposed fragments.
- Tool descriptor metadata and tests; avoid unnecessary tool class changes unless a gap is real.
- CSS for compact operational rows/chips.

## Forbidden Scope

- Do not add normal-agent tools with arbitrary `agentId`.
- Do not make dashboard selected-agent binding alter tool authorization.
- Do not expose internal run/workspace roots as normal browser surfaces.

## Implementation Steps

1. Implement shared binding selectors and source chips for agent/project/Work Area.
2. Agent Status/Queue: selected agent profile/status/model/queue/inbox/running/waiting/health with no-agent and missing-agent states.
3. Agent Outputs: explicit source mode dashboard-wide, selected agent, selected project, selected job, selected Work Area; preview/download through existing output services.
4. Agent Files/Notes: selected Work Area mini-browser/tagged notes using existing file explorer/viewer language and confinement. Newly rendered Work Area UI must use the agent-detail route family or a service-backed equivalent with the same owner guard, not legacy `/avatar/_work-areas` routes.
5. Project Activity may be included if needed to round out 2-3 widget group, but keep scope coherent.
6. Validate declared tool descriptors against current `agent_*` and `avatar_*` tool names.

## Acceptance Criteria

- Agent-bound widgets clearly show selected source.
- Outputs are never unscoped unless dashboard-wide mode is explicitly selected.
- Work Area file widgets use service confinement and familiar details/list patterns.
- Tool validation proves normal-agent context scoping remains intact.

## Validation Commands

- `mvn -Dtest=AvatarDashboardControllerTest,AgentOperationalToolConfigurationTest,AgentOperationalToolServiceTest,AgentToolAuthorizationServiceTest,ChatToolRegistryTest,WorkAreaServiceTest,WorkAreaExplorerServiceTest,OutputArtifactServiceAttributionTest,OutputArtifactPathSemanticsTest test`

## Browser Checklist

Selected/no-agent states, queue rows, output modes, output preview, Work Area mini-view, `/agents` and `/agents/{agentId}` style comparison, desktop/mobile screenshots.

## Stop Conditions

Stop if required service APIs are missing and cannot be added cleanly. Stop if a widget would need to expose internal roots or bypass context authorization.

## Do Not Close Unless

- Source binding is visually obvious.
- Tool scoping tests pass.
- Output and file operations are bounded/confined.

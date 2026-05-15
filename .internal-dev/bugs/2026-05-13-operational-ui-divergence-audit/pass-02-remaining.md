# Pass 02 Remaining Operational UI Gaps

## 1) Schedules/Reactions UI not implemented
- Severity: should-fix
- Current state: APIs exist under `/api/agents/{agentId}/schedules` and `/api/agents/{agentId}/event-reactions`, but orchestration UI still lacks CRUD surfaces.
- Impact: feature set remains API-only and not operator-friendly for alpha workflows.

## 2) Agent-output attribution model is indirect
- Severity: should-fix
- Current state: agent outputs tab filters by owner agent jobs; artifacts do not carry first-class `agentId` metadata.
- Impact: querying outputs by agent is less precise when output ownership semantics evolve.

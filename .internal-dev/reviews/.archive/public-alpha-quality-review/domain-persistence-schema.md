# Domain Persistence/Schema Review

## Agent

- Agent: domain-persistence-schema
- Agent id: `019e371e-8664-7d60-b1f5-53e6c6679c76`
- Model / reasoning: GPT-5.5 Codex high
- Mode: read-only

## Scope

Reviewed SQLite schema, repository bootstrap/upgrade paths, warm-DB compatibility, and table ownership.

## Files and Tables Reviewed

- Files: `src/main/resources/schema.sql`, `application.yml`, chat repositories, plan repository, orchestration runtime/workflow/workspace/settings/agent repositories, `AgentJobRepository`.
- Tables: chat memory/session/audit, plan/run, workflow/run/node, inbox variants, agent profiles, runtime assignments/jobs/events, runtime settings, workspaces/leases/outputs, jobs/projects.

## Commands and Probes

- `rg --files -g AGENTS.md`
- `nl -ba` on scoped guides, schema, repositories
- `rg -n` for SQL DDL/DML/migration paths
- `sqlite3 :memory: '.read src/main/resources/schema.sql' ...`
- `sqlite3 'file:chat-memory.db?mode=ro' ...`
- In-memory SQLite reproduction of schema init plus workspace migration behavior
- `git status --short`

## Findings

- Blocker: startup can repeatedly drop `workspace_leases` because `schema.sql` recreates deprecated `workspace_roots`; repository migration then drops `workspace_leases` and `workspace_roots`.
- High: `schema.sql` is no longer canonical for workspace/artifact/plan-run persistence. Repository init must patch columns omitted by `schema.sql`.
- Medium: duplicate inbox persistence is active and schema ownership is split between `inbox_messages` and `agent_inbox_messages`.
- Low: `job_work_items` appears to be orphan schema baggage.

## Explicitly Ruled Out

- Chat memory/session warm-DB compatibility has guarded add-column paths.
- Workflow legacy definitions are covered for graph columns.
- Runtime assignment heartbeat columns are guarded and backfilled.
- Local warm DB audit index is unique and had no duplicate sequence rows in the read-only probe.

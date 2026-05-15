# Date
2026-05-13

# Change Summary
- Fixed the remaining hard-delete scope gap for agent lifecycle by purging historical orchestration/job/project references before profile deletion.
- Added a regression test that proves stale references are removed after hard-delete.

# Files
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`

# Behavioral Impact
- `hardDelete` now removes agent-owned runtime assignments/inbox/schedules/reactions/orchestration jobs, job definitions and dependents, and project memberships/owned projects prior to removing the agent profile.
- Historical UI/API views no longer retain the previously tracked stale references for deleted agents in these covered tables.

# Risks
- Event cleanup is intentionally scoped to `orchestration_events` rows where `source_type='agent'` and `source_id=<agentId>`; malformed legacy rows with nonstandard source typing are not targeted by this pass.

# Follow-up Items
- If legacy environments are found to have inconsistent `orchestration_events.source_type` values, add a one-time migration/repair script and broaden cleanup matching accordingly.

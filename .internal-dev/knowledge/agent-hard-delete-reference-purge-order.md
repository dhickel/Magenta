# Topic
Agent hard-delete reference purge order

# Source References
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`

# Key Takeaways
- Hard-delete should purge database references before deleting profile and workspace/container surfaces.
- A dependency-safe purge order is:
  1. Runtime references (`agent_schedules` + `schedule_firings`, `agent_event_reactions`, `agent_inbox_messages`, `orchestration_events` where source is agent, `work_assignments`, `orchestration_job_items`, `orchestration_jobs`)
  2. Job definition references (`job_recurrences`, `job_runs`, `job_definitions` owned by agent)
  3. Project references (direct memberships, then owned project events/memberships/projects)
  4. Container and workspace filesystem cleanup
  5. Profile deletion
- Keeping this order avoids partial orphaning and stale operational UI/API surfaces.

# Engine Relevance
- This pattern is directly reusable for lifecycle destructive actions where runtime + data surfaces are spread across multiple repositories.
- The test-backed purge sequence reduces regressions when extending orchestration schemas.

# Open Questions
- Should event cleanup remain strict to `source_type='agent'`, or should we add a backward-compat sweep for legacy nonstandard source-type values?

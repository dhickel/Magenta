# Topic

Assignment history retention and purge semantics.

# Source References

- `AssignmentService`
- `OrchestrationRuntimeRepository`
- `OrchestrationController`
- `AgentOrchestrationController`

# Key Takeaways

- Queue cleanup and historical retention are separate operations.
- Non-terminal assignment rows can be removed through queue delete when they are not running or cancel-requested.
- Terminal assignment rows are retained as History and cannot be removed through queue delete.
- Manual and automatic purge delete terminal `work_assignments` rows older than a cutoff and remove matching `assignment_conversation_links`.
- Purge does not delete chat conversations, audit events, plan runs, workflow runs, job runs, jobs, plans, workflows, or output artifacts.

# Engine Relevance

Agent diagnostics and transcripts depend on the retained assignment row as the stable History entry. Removing terminal rows through Queue made failed-run diagnostics disappear from the operator UI even when underlying audit records still existed.

# Open Questions

- Whether future operator UI should expose a per-row terminal-history purge action in addition to the cutoff-based purge.

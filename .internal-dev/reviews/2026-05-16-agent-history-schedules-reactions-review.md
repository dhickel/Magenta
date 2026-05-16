# Scope

Reviewed the agent detail queue/history, schedule, and event-reaction surfaces after the failed-run history loss report.

# Findings

- Schedule and reaction scaffolding already existed across UI tabs, HTMX forms, JSON APIs, runtime schema, services, event handling, and scheduler wiring.
- The schedule and reaction surfaces were effectively disabled by default because `magenta.features.schedules-enabled` and `magenta.features.reactions-enabled` were set to `false`.
- Queue and History were both sourced from the same unfiltered `assignmentService.assignments(agentId)` list.
- Queue delete physically removed the `work_assignments` row for eligible assignments. When a failed or completed row was deleted, History lost its source row even if linked chat or audit records still existed.

# Risk Assessment

- Enabling schedules/reactions is safe only if newly created schedule/reaction records default to disabled and existing edit forms preserve their current enabled state.
- Terminal assignment rows must be treated as retained history, not queue clutter.
- History purge should remove terminal assignment rows and assignment-conversation links only; chat conversations, audit events, runs, outputs, jobs, plans, and workflows remain separate records.

# Recommendations

- Split queue and history at the service/repository boundary.
- Reject terminal assignment delete through queue/API delete paths and require explicit history purge.
- Add neutral assignment diagnostics/transcript endpoints so Queue and History can share the same read-only detail rendering.
- Add manual and runtime-settings-driven automatic purge for terminal assignment history older than a configured cutoff.

# Follow-ups

- Browser validation should verify the Queue/History split and disabled-by-default schedule/reaction creation in the running application.

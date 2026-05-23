# Topic

Avatar Work Area UI Refactor Planning

## Source References

- `.internal-dev/plans/avatar-agent-ui-refactor/implementation-plan.md`
- `.internal-dev/plans/avatar-agent-ui-refactor/orchestration.md`
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
- `.internal-dev/notes/current-architecture-focus.md`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`

## Key Takeaways

- `/avatar` should remain an operational Magenta surface aligned with `/dashboard` and `/agents`, not a consumer dashboard.
- Avatar layout state belongs in `avatar.sqlite`, but Work Area records belong in runtime persistence because assignments and output routing use them.
- Work Area selection is different from existing assignment `workspaceId`; implementation should add explicit selected Work Area and output route fields.
- New assignments should resolve `workspace/` to the selected Work Area and expose the broader owned root as `root/`.
- File explorer actions must share the workspace path policy and refuse symlink escapes, protected delete targets, unsafe edits, and cross-owner selections.
- Planner tasks are personal organizer records, not executable Magenta task definitions.

## Engine Relevance

Future Avatar and workspace work should start from this split:

- `avatar.sqlite`: Avatar profile, preferences, organizer/planner data, dashboard layout, and Avatar events.
- `magenta.sqlite`: Work Areas, assignments, runtime metadata, workspace roots, leases, jobs, and outputs.

This avoids building a parallel Avatar runtime while still giving `/avatar` first-class controls for agent work.

## Open Questions

- Whether historical simple organizer data in `avatar.sqlite` needs migration or can be hard-replaced for alpha users.
- Whether Work Area controls should eventually apply to all API clients, not only web submit forms.
- Whether SimplyPages needs upstream reusable dashboard-editor components after the first Magenta implementation proves the shape.

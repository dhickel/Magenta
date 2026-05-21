# Date

2026-05-21

# Change Summary

Magenta now uses a root-owned runtime layout by default. The default Magenta root is `${user.home}/.magenta`, the default SQLite database is `<magenta.root.path>/magenta.sqlite`, and the default data root is `<magenta.root.path>/root` when AI config omits `dataRoot`.

New Magenta-owned path rows are stored relative to the configured data root. Existing absolute rows under the current data root remain readable for compatibility, while stale absolute rows from an old root fail only when a file operation tries to use them.

The manual carry-forward path preserves existing chat sessions and chat files by copying the existing SQLite database and old `chats/` tree into the new root. Workspace, output, runtime, and active-run files are intentionally not auto-copied, repaired, archived, or deleted in this pass.

The services UX review also produced a small CSS remediation: mobile operational sidebars now open as viewport-anchored drawers, and operational tables use mobile horizontal containment instead of clipping columns.

# Files

- `src/main/resources/application.yml`
- `config/ai-config.example.json`
- `src/main/java/io/mindspice/magenta2/core/config/*`
- `src/main/java/io/mindspice/magenta2/ai/config/user/AiUserConfigConfiguration.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/*`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/{JobService,OrchestrationRunnerService}.java`
- `src/main/java/io/mindspice/magenta2/api/web/OutputController.java`
- `src/main/resources/static/css/orchestration.css`
- `docs/technical/*`
- `docs/end-user/*`
- Focused tests across config, workspace, output, plan, workflow, job, chat, and web route packages.

# Behavioral Impact

- Fresh installs no longer default to `./chat-memory.db`.
- Bundling and moving Magenta runtime state is simpler because the database and data root now live under the same Magenta root by default.
- Operators carrying forward older local installs should copy the old DB to `<magenta.root.path>/magenta.sqlite` and copy old `chats/` to `<magenta.root.path>/root/chats/`.
- New output/run/workspace-link path metadata is portable across root moves when the root-relative layout is preserved.
- Old workspace/output/runtime files are not preserved by this cleanup unless an operator archives them outside Magenta.
- Mobile operational navigation is usable on long pages after the sidebar fix.

# Risks

- Old absolute artifact/run paths outside the current data root remain stale and can fail at operation time.
- JSON blobs and transcripts may still contain historical absolute paths; this phase does not rewrite unstructured data.
- Startup repair, import tooling, and admin migration APIs are future work, not implemented behavior.
- Existing job assignment checkpoint JSON keeps stored path values; runtime context resolves them before installing job workspace paths.

# Follow-up Items

- One-time migration CLI with dry-run/apply modes.
- Admin import/API for root/database/chat-file migration.
- Startup diagnostics or repair reporting for stale path rows.
- Controlled rewrite of old absolute path rows and unstructured JSON path references.
- Richer responsive redesign for dense job run summaries and output filters beyond the small mobile containment fix.

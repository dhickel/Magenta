# Specification Lock

Status: locked for orchestration
Created: 2026-05-24
Repo: `/home/hickelpickle/Code/Java/magenta2`
Branch at planning time: `feature/workspace-file-explorer`

## Objective

Replace the current workspace file explorer UI and behavior with a feature-complete, workspace-confined details/list explorer that users familiar with Windows File Explorer, GNOME Files/Nautilus, KDE Dolphin, Nemo, or similar file managers recognize immediately.

The old card/modal/dropdown-heavy explorer experience is discarded as a product reference. Existing domain services may be reused after verification, but current card-style rendering and any card-first SimplyPages module output must not drive the target UX.

## Source Inputs

Required local sources read for this plan:

- `.internal-dev/AGENTS.md`
- `.internal-dev/focus/AGENTS.md`
- `.internal-dev/focus/current-focus.md`
- `.internal-dev/focus/unfinished-work.md`
- `.internal-dev/focus/architecture-focus.md`
- `.internal-dev/focus/decisions.md`
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/notes/current-architecture-focus.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/frontend-htmx.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- Current workspace explorer code and tests listed in `01-current-state-analysis.md`
- SimplyPages docs/source for HTMX endpoint patterns, `Table`, `DataTable`, `Modal`, `Markdown`, `RenderContext`, and component catalog.

External UI references to cite during implementation and validation:

- KDE Dolphin Details view and Additional Information columns: https://docs.kde.org/stable_kf6/en/dolphin/dolphin/dolphin-view.html
- GNOME Files/Nautilus list view columns: https://help.gnome.org/gnome-help/nautilus-list.html
- Dolphin information panel behavior: https://opensource.com/life/15/8/comprehensive-guide-dolphin-file-manager
- Windows 11 File Explorer navigation, path line, details pane, toolbar actions, copy/paste, rename, delete, sort: https://bvres.org/schoolhouse_files/General%20Tutorials/Using%20File%20Explorer%2011.pdf
- Windows 11 details pane overview: https://www.howtogeek.com/890549/windows-11s-file-explorer-is-getting-a-new-details-pane/

## Locked Product Decisions

1. The explorer is a details/list file manager, not a card grid. There is no v1 card view for files or directories.
2. The left/main bounded explorer region contains top navigation controls, path/breadcrumb controls, column headers, and compact rows.
3. Required columns are `Name`, `File Type`, `Size`, `Created`, `Last Modified`, first few `Tags`, and final `Actions`.
4. The right side is a separate metadata/inspect panel for the selected file or directory. It is not inside the file table and is not a modal-only inspector.
5. Row actions include view/eye for supported text/image files, rename, and delete. Rename and delete are also available in the right inspect panel.
6. Copy and move are required and must be available from the right inspect panel and/or operation controls.
7. Viewer opens in a modal popup for supported text and image files.
8. Text viewer has tabs. Markdown defaults to rendered Markdown tab with a Text tab for raw/editing. Plain text defaults to raw text with Markdown render disabled.
9. Markdown parse/render failure fails happily: keep the modal usable, keep raw text accessible, and display a non-fatal error at the bottom of the rendered tab.
10. Unsupported or binary files do not render or edit. They show metadata and safe fallback/download behavior only.
11. Files and directories can both be tagged.
12. Tags are user-created Magenta abstractions persisted in Magenta and assigned to files/directories. V1 must preserve the existing `note` and `work-area` semantics while allowing custom tags.
13. First few tags show in table rows; the inspect panel shows the full tag set and tag add/remove controls.
14. Tags follow Magenta-managed rename and move. Tags copy with Magenta-managed copy unless a later user decision changes copy semantics.
15. Explorer operations stay confined under the selected Work Area/workspace root. No navigation or mutation above root is allowed.
16. UI implementation remains SimplyPages/HTMX-first. JavaScript is allowed only for narrow local behavior where HTMX cannot reasonably own the interaction, such as modal tab switching or text dirty-state affordances.
17. The previous `.internal-dev/plans/workspace-file-explorer/` suite is superseded by this suite. It must not be moved or archived until this plan's closeout phase explicitly does so.
18. Work start email has already been sent by the main thread. This plan must require email reports at every completion gate and final detailed email plus low-token listening.

## Acceptance Criteria

AC1. The explorer cannot navigate, inspect, view, copy, move, rename, tag, or delete outside the resolved Work Area/workspace root, including through `..`, absolute paths, Windows-drive paths, separator tricks, symlinks, stale paths, or copied directory contents.

AC2. The main explorer is a bounded details/list region with toolbar/navigation buttons, path/breadcrumb controls, sticky or visually stable column headers, compact rows, and no file/directory cards.

AC3. The details table includes `Name`, `File Type`, `Size`, `Created`, `Last Modified`, first few `Tags`, and `Actions`, with directories and files sorted in a familiar, predictable order.

AC4. Selecting a row updates a separate right inspect panel with file/directory metadata, full tags, supported actions, and copy/move/rename/delete controls where allowed.

AC5. Row actions include eye/view for supported text/image files, rename, and delete. Unsupported/binary rows do not show a misleading view action.

AC6. Rename/delete are available both as row actions and in the inspect panel, with consistent validation and refresh behavior.

AC7. Copy and move are implemented for files and directories inside the same confined root, including collision handling, self/descendant protection, symlink rejection, and tag behavior.

AC8. Text/image viewer opens as a modal. Image viewer streams safe supported image types only. Text viewer has tabs and never hides raw text access.

AC9. Markdown defaults to rendered Markdown, raw/editing remains available in Text tab, and parse/render failure displays a non-fatal error at the bottom while preserving raw text access.

AC10. Plain text defaults to raw text, does not offer Markdown render, and respects size/UTF-8/editability policy.

AC11. Files and directories support custom user-created tags persisted in Magenta. Existing `note` and `work-area` semantics continue to work.

AC12. Tags appear in rows as the first few compact chips with overflow count/affordance when necessary, and all tags are visible/editable in the inspect panel.

AC13. The UI looks modern and consistent with Magenta operational UI: dense, table-like, restrained borders, compact buttons, thin blue-gray styling, small radii, and no hero/card-grid treatment.

AC14. Browser validation covers desktop and mobile, including screenshots, visual critique, navigation, selection/inspect, tags, row/inspect actions, copy/move/rename/delete, text/Markdown/image viewer, unsupported binary fallback, and no-card regression.

AC15. Backend validation covers targeted tests, full `mvn test`, bounded Spring startup, path traversal/symlink cases, operation confinement, tag follow/copy behavior, unsupported/binary viewer checks, and Markdown failure UX.

AC16. Docs and `.internal-dev` closeout artifacts accurately describe implemented behavior, remaining risks, validation evidence, and supersession of the previous plan.

## Validation Criteria

- VC1. Service tests cover path confinement, symlink escape, metadata enrichment, created/modified fields, tag assignment to files/directories, tag follow on rename/move, tag copy on copy, operation collision checks, delete protection, unsupported/binary preview, and Markdown/render failure handling.
- VC2. Repository/schema tests cover tag label creation, custom tag assignment, subtree update/copy/delete, action logging, idempotent schema startup, and empty database startup.
- VC3. Controller/fragment tests cover list, inspect, tag create/add/remove, view modal, text save, Markdown error state, image view, copy, move, rename, delete preflight/execute, and error status/fragment behavior.
- VC4. UI tests and Playwright validation confirm the details/list contract, right inspect panel, modal viewers, operation flows, mobile stacking, and no card-view regression.
- VC5. Validation subagents perform adversarial checks against all acceptance criteria after every mutating phase.
- VC6. Bounded Spring startup succeeds unless blocked by explicit local dependency/secrets failure that is recorded and user-approved as a blocker.

## Non-Goals

- No arbitrary filesystem browser.
- No navigation above Work Area/workspace root.
- No card view for files/directories.
- No external filesystem metadata reconciliation in v1.
- No rich binary preview beyond supported image display.
- No chunked large-file editor.
- No broad workspace runtime redesign.
- No replacement of ordinary `/chat` conversation file handling.
- No JavaScript SPA rewrite.
- No production implementation by the planning agent.

## Constraints

- Use the direct AgentMail daemon/wait workflow for email coordination. Do not recreate a repo-local email ledger for inbound mail.
- Keep controllers thin and delegate filesystem/tag behavior to services.
- Keep workspace behavior under `io.mindspice.magenta2.ai.orchestration.workspaces`.
- Keep web rendering/routes under `io.mindspice.magenta2.api.web`.
- Use Java records where practical for request/response/view data.
- Use SimplyPages components/modules and HTMX fragments by default.
- Raw HTML strings are a fallback only after SimplyPages primitives prove insufficient.
- For multi-phase execution, work on the dedicated branch `feature/workspace-file-explorer` unless a setup gate discovers otherwise.
- Commit at the end of each phase after validation passes.
- Every mutating phase must be followed by non-mutating validation before the next mutating phase starts.
- Email gate report after every phase completion; final email after final quality review.

## Assumptions To Verify

- Existing `WorkAreaExplorerService` operations are mostly reusable but require richer view metadata and stricter UI contract support.
- Existing `workspace_file_labels` and `workspace_file_label_assignments` can serve v1 custom tags for files/directories after API/UI expansion.
- Existing `workspace_file_actions` logging is sufficient after validation, or can be minimally extended.
- Current `simplypages:1.1.0a` contains a file explorer module not present in the checked-out SimplyPages source; workers may replace or bypass it in Magenta if it cannot render the required details/list contract.
- SimplyPages `Markdown` is safe by default and can be wrapped to catch failures and render a friendly bottom error.
- Created timestamps may not be portable on every filesystem; if unavailable, the backend should return blank/unknown in UI rather than invent values.

## User Decision Gates

- U1. Stop if satisfying the details/list contract requires changing SimplyPages upstream release strategy or dependency coordinates beyond Magenta-local usage.
- U2. Stop if custom tag semantics require a new schema model instead of extending existing label tables.
- U3. Stop if any required flow needs browsing outside the confined root.
- U4. Stop if browser validation cannot run; do not mark the UI complete without explicit user approval of a blocked state.
- U5. Stop if implementation proposes a repo-local email ledger instead of direct `mailctl` daemon/wait dispatch.

## Stop Rules

- Stop on any successful root escape or symlink escape.
- Stop if delete can remove the Work Area root, a system/home Work Area, or protected active Work Area descendants.
- Stop if the UI contains card-style file rows/cards or hides the details columns.
- Stop if tags fail to persist for both files and directories.
- Stop if copy/move/rename/delete refreshes the table but leaves the inspect panel inconsistent.
- Stop if Markdown render failure breaks the modal or hides raw text.
- Stop if Playwright screenshots show major overlap, clipped controls, unusable mobile layout, excessive dead space, or card-view regression.
- Stop if a phase validation fails due to ambiguous criteria; return to planning before more coding.

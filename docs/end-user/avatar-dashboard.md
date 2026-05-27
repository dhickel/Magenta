# Avatar Dashboard

The Avatar dashboard lives at `/avatar`. It is the personal command surface for quick assistant chat, dashboard customization, queue visibility, recent work history, outputs, and Work Area access. It stays separate from `/dashboard`, which remains the broader operational console.

## Shell And Tabs

`/avatar` now uses a compact tabbed shell:

- `Dashboard` is the only layout-editable page.
- `Queue` shows live assignment queue state available to Avatar.
- `History` shows the current baseline recent-work view.
- `Profile` shows Avatar identity and default assistant settings.
- `Outputs` shows recent generated artifacts and previews.
- `Work Areas` exposes the confined workspace browser.

The chat rail stays visible on every tab. On desktop, it sits on the left with a bottom-right resize corner inside the chat panel; drag that corner right or left to change chat width while the dashboard fills the remaining right-side space, or drag it down or up to adjust chat height. On mobile, the shell stacks and the resize corner is hidden.

## Editing The Dashboard

Use the compact edit icon on the `Dashboard` tab to enter layout edit mode. The layout uses 12-column rows. Edit controls render on the live dashboard:

- widget controls sit in the top corner of each widget;
- row controls render as a thin decorator above the row;
- add-widget controls appear between row content;
- insert-row controls appear as compact separators.

Placement, movement, and sizing happen where the widget is actually shown. Empty rows collapse into a compact add-widget affordance instead of a large blank band.

## Widgets And Organizer Features

Dashboard widgets still cover daily tasks, todos, calendar items, notes, Work Areas, outputs, system state, alerts, and recent work. The old top-level `Organizer` button is gone. Planner, todo, calendar, and note workflows now stay inside the dashboard widgets and their detail flows instead of using a separate toolbar entry.

Routine widget actions such as adding todos, completing daily tasks, saving notes, previewing outputs, and dismissing alerts still run through HTMX. Manual `Refresh Widgets` is removed from the shell; refresh automation is deferred to a later pass.

## Work Areas And Files

The `Work Areas` widget and the top-level `Work Areas` tab use the confined workspace file explorer. The browser uses a familiar details/list file-manager layout:

- clickable Work Area cards that open the explorer directly;
- compact icon toolbar actions for Back (parent directory only), refresh, folder creation, and a new-file menu (`.txt` or `.md`);
- breadcrumb/path navigation that stays inside the selected Work Area;
- compact table rows with `Name`, `File Type`, `Size`, `Created`, `Last Modified`, `Tags`, and `Actions`;
- a separate right-side inspector panel with collapse/expand controls, selected full name/path, tag editor, preview hints, and metadata.

Supported file actions include directory navigation, text and Markdown preview/edit/save, contained image preview, downloads, directory creation, `.txt`/`.md` creation, rename, copy, move, custom tags, note labels, and delete confirmation with an extra recursive confirmation for directories.

Markdown files open with the rendered tab active and a Text tab for raw editing. Plain text opens directly in raw text mode. Unsupported or binary files do not expose a misleading row View action; stale viewer requests show a safe unsupported message instead. Copy and move open a directory-picker popover/module and stay confined under the selected Work Area without typing internal destination paths.

Tag editing uses the progressive search selector pattern. You can select existing tags or type a new tag to create-and-assign it. File-only and directory-only tags are filtered by the selected item type, and mismatched assignments are rejected server-side.

New assignment work defaults to the selected Home Work Area. During execution, `workspace/` points at the selected Work Area and `root/` points at the broader owned root. Agent-facing `outputs/` is run-local staging at `runs/<runId>/outputs/`; after the run completes, Magenta promotes declared final outputs from that staging area to the selected Work Area, project, or other effective destination chosen by the submit form.

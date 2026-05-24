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

The chat rail stays visible on every tab. On desktop, you can drag the divider between the main content and chat rail to resize it. On mobile, the chat rail stacks below the tab content.

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

The `Work Areas` widget and the top-level `Work Areas` tab use the confined workspace file explorer. The browser shows a familiar file-manager layout with breadcrumb navigation, toolbar actions, cards for files and folders, a label inspector, and a preview/editor pane. It supports directory navigation, text and Markdown preview/edit/save, image preview, downloads, directory creation, `.txt`/`.md` creation, rename, copy, move, note labels, and delete confirmation with an extra recursive confirmation for directories.

New assignment work defaults to the selected Home Work Area. During execution, `workspace/` points at the selected Work Area and `root/` points at the broader owned root. Outputs default to the selected Work Area `outputs/` folder unless the submit form redirects them elsewhere.

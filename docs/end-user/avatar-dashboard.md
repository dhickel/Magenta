# Assistant Dashboards

The home route `/` is the user dashboard surface. It opens with the `Assistant` dashboard selected, a compact dashboard selector row, and a trailing `+` control for creating another dashboard.

Dashboards are configurable widget containers. They are not agents, Work Areas, or execution contexts.

## Editing

Use the compact edit control on a dashboard to enter layout edit mode. Rows use the existing 12-column layout controls:

- Add rows from the empty-dashboard state or row insert affordances.
- Add widgets from the row widget picker.
- Move rows and widgets in place.
- Resize widgets with the width picker.
- Remove widgets and empty rows.

New dashboards are created empty. The default `Assistant` dashboard starts with chat plus daily tasks, todos, calendar, notes, outputs, system, alerts, and recent work.

## Work Areas

Work Areas are no longer dashboard widgets. Open an agent detail page from `Agents`, then use that agent's `Work Areas` tab to browse and edit the Work Areas owned by that agent.

## Manage

The old operational dashboard is now `Manage` at `/manage`. The top navigation order is `Home`, `Chat`, `Agents`, `Manage`.

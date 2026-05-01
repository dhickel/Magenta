Context

The chat sidebar now renders selected bulk operations directly in the session list. Each conversation card shows a checkbox and a shortened action row.

Goal

Add a top-level select-all checkbox that mirrors the visible conversation checkboxes, and replace noisy full UUID display in conversation cards with a short visual identifier.

In Scope

- Add a top select-all checkbox to the bulk action bar.
- Keep the top checkbox checked, unchecked, or indeterminate based on visible row selections.
- Toggle all visible conversation selections from the top checkbox.
- Display a short pseudo hash for conversation ids in history cards while preserving full ids internally.
- Update focused frontend tests.

Out of Scope

- Backend API or persistence changes.
- Changing confirmation dialogs beyond existing display-name behavior.

Implementation Steps

- Update chat sidebar markup and CSS for the select-all control.
- Add client helpers for short conversation labels and select-all sync.
- Wire the top checkbox to visible session selection.
- Bump the browser client asset version and update tests.

Validation

- Run JavaScript syntax validation.
- Run focused frontend controller tests.
- Run the project test suite.
- Run whitespace diff validation.

Exit Criteria

- The top checkbox mirrors row selection state and toggles visible rows.
- Conversation cards no longer display full UUIDs as their visible id text.

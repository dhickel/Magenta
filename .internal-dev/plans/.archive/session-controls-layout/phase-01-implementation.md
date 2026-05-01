Context

The chat sidebar already supports per-session rename, delete, favorite, archive, and selected bulk operations. The current UI puts row action buttons vertically beside the conversation entry and uses a separate collapsible bulk selector.

Goal

Move per-session controls into a horizontal top row inside each conversation entry, keep a selection checkbox visible on every conversation, and keep the three bulk operation buttons always visible.

In Scope

- Update the browser chat sidebar HTML/CSS layout.
- Render row checkboxes directly in the session list.
- Clear selected checkboxes after bulk delete, archive, or favorite operations.
- Update focused frontend tests for the new markup expectations.

Out of Scope

- Backend API changes.
- New archive listing or restore behavior.
- Changing the existing confirmation prompt behavior.

Implementation Steps

- Replace the collapsible bulk management block with a static bulk action bar.
- Render each session as a single conversation entry bubble with a top row containing the checkbox on the left and action buttons on the right.
- Move checkbox event handling from the removed bulk list into the session list.
- Clear selected session ids after bulk operations complete.
- Update frontend tests and asset version.

Validation

- Run JavaScript syntax validation.
- Run focused frontend controller tests.
- Run the project test suite if feasible.
- Run whitespace diff validation.

Exit Criteria

- Session row actions are horizontal in the entry bubble.
- Bulk buttons are always visible.
- Bulk selections clear after successful bulk operations.

Context

Conversation cards show a title and short conversation id marker in the chat sidebar.

Goal

Make the visible id marker use half of the UUID, and render both the title and id marker as styled chips with the id slightly inset.

In Scope

- Frontend session card markup and CSS.
- Client-side display helpers for the shortened id.
- Focused frontend test expectations.

Out of Scope

- Backend session identifiers or API payload changes.

Implementation Steps

- Add title/hash chip classes to the session card markup.
- Update CSS to style the title and hash as compact boxes.
- Change the visible hash helper to use the first half of the UUID hex.
- Update tests and asset version.

Validation

- Run JavaScript syntax validation.
- Run focused frontend tests.
- Run project tests.

Exit Criteria

- Full UUIDs are not shown in conversation cards.
- Title and hash display as coordinated card chips.

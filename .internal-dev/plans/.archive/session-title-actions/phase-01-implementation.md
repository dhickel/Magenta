# Session Title Actions

## Context

The browser chat session list now shows generated conversation titles but users cannot manually rename or delete sessions from the collapsible sidebar.

## Goal

Add sidebar controls to rename a chat title in place and delete a chat after a confirmation that shows the chat name.

## In Scope

- API endpoint to update a conversation title.
- Service/repository support for manual title updates.
- Browser session list rename and delete buttons.
- Confirmation prompt for deletion that includes title and UUID.
- Focused controller/frontend tests.

## Out of Scope

- Bulk session management.
- Undo after delete.
- Separate modal framework.

## Implementation Steps

1. Add title update repository/service/controller behavior.
2. Render action buttons in the session list.
3. Implement in-place rename save/cancel and delete confirmation/client calls.
4. Add/update focused tests.

## Validation

- Run controller/frontend focused tests and full Maven suite if practical.

## Exit Criteria

- A session title can be renamed without leaving the session list.
- Delete action confirms the visible chat name and removes the session.

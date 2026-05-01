Date

2026-05-01

Change Summary

- Moved chat session row controls into a horizontal top row inside each conversation entry.
- Added always-visible selection checkboxes to session rows.
- Replaced the collapsible bulk selector with an always-visible delete, archive, and favorite action bar.
- Cleared selected session ids after successful bulk operations.

Files

- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`

Behavioral Impact

The chat sidebar now exposes bulk actions without expanding a management panel. Users select chats directly from the conversation list, then apply delete, archive, or favorite from the persistent action bar.

Risks

The frontend still relies on browser confirmation dialogs for destructive and archive operations.

Follow-up Items

None.

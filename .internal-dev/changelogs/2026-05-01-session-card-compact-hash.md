Date

2026-05-01

Change Summary

- Moved the visible conversation hash into the session card top row between the checkbox and action buttons.
- Shortened the visible hash back to a compact one-row label.
- Removed the title tag glyph and the lower hash chip.

Files

- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`

Behavioral Impact

Conversation cards are more compact: selection, hash, and actions share the top row, while the chat name is shown as plain bold text below.

Risks

None noted.

Follow-up Items

None.

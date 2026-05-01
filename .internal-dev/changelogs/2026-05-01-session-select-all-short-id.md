Date

2026-05-01

Change Summary

- Added a top-level select-all checkbox for visible chat sessions.
- Synced the select-all checkbox with row checkbox state, including indeterminate partial selection.
- Replaced full UUID display in conversation history cards with short `#xxxxxxxx` labels.

Files

- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`

Behavioral Impact

Users can select or clear all visible conversations from the bulk action bar. Conversation cards keep their full UUID internally but only show a short visual identifier in the session list.

Risks

Short labels can collide visually, but the full UUID remains used for all actions and is still included in destructive confirmation prompts.

Follow-up Items

None.

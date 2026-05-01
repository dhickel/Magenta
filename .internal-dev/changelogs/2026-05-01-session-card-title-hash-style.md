Date

2026-05-01

Change Summary

- Rendered session card titles and conversation hash labels as styled chips.
- Changed visible conversation hashes to use the first half of the UUID hex.
- Offset the hash chip inward under the title chip for better visual hierarchy.

Files

- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`

Behavioral Impact

Conversation cards show a cleaner title chip and a shorter half-UUID hash chip while retaining full UUIDs internally for routing and operations.

Risks

The visible half-UUID remains a display hint only and is not a unique user-facing identifier guarantee.

Follow-up Items

None.

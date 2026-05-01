## Date
2026-05-01

## Change Summary
Fixed the chat favorite request DTO so the browser payload field `favorite` binds to the Java record used by the favorite endpoint.

## Files
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatRequest.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`

## Behavioral Impact
Clicking the chat favorite button should now persist the requested favorite state instead of relying on a mismatched boolean accessor name.

## Risks
Low. The request keeps accepting `isFavorite` as a JSON alias for compatibility while making `favorite` the canonical field.

## Follow-up Items
None.

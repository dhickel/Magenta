# Date

2026-05-20

# Change Summary

Updated the `/chat` page to use the full viewport width and a normalized side/chat/side grid so the chat remains the dominant surface after adding the outputs panel. Session output counts now render as green `<N> Outputs` capsule badges instead of plain `Outputs: N` rows.

# Files

- `src/main/resources/static/css/magenta.css`
- `src/main/resources/static/js/chat-client.js`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendFragmentController.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `docs/end-user/chat.md`

# Behavioral Impact

The chat page uses more horizontal screen space on desktop. Sessions, chat, and outputs remain visible together when enough width is available, with chat wider than either side panel. Output-bearing chat sessions now display a green badge that is easier to scan.

# Risks

The requested `60%, 25%, 25%` proportions cannot be literal percentages because they exceed 100%, so the implementation treats them as a `25:60:25` ratio. Mid-width behavior depends on the collapse breakpoint and was validated separately in the browser.

# Follow-up Items

None.

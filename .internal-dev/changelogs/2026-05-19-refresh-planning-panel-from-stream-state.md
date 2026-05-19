# Date

2026-05-19

# Change Summary

Updated the chat browser client so every SSE event that carries `planState`, including assistant `chunk` events, refreshes the planning panel. This keeps the approval/save/send controls visible as soon as a planning turn reaches the relevant state instead of depending on a page refresh.

# Files

- `src/main/resources/static/js/chat-client.js`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`

# Behavioral Impact

- Planning conversations update the approval/save/send panel during live stream handling when backend state changes.
- `/chat?startPlanning=true` remains supported and still auto-starts planning from link navigation.
- The chat JS URL cache key was bumped to load the updated browser client.

# Risks

- Low risk; the client only refreshes existing plan UI from state already present in SSE payloads.

# Follow-up Items

- Add a browser-level planning regression check with a deterministic model stub if planning stream behavior regresses again.

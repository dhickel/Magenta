# Date

2026-04-29

# Change Summary

Moved chat stream subscription work off the servlet request thread so SSE responses can be established before blocking model/tool execution continues.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`

# Behavioral Impact

Tool activity SSE events can now be sent to the browser during long tool loops instead of being buffered until all tool iterations complete.

# Risks

The stream now executes on Reactor's bounded elastic scheduler; cancellation still disposes the subscription through the existing emitter callbacks.

# Follow-up Items

Spring AI still executes all tool calls from one model response as a batch, so per-call visibility inside a single batch would require a lower-level tool-callback hook.

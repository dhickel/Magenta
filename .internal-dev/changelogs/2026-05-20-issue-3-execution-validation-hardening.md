Date
2026-05-20

Change Summary
Implemented issue #3 execution hardening for anonymous chat plan execution and tool-call argument validation.

Files
- `src/main/java/io/mindspice/magenta2/ai/execution/ActiveTurnRegistry.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ToolTranscriptService.java`
- `src/test/java/io/mindspice/magenta2/ai/execution/ActiveTurnRegistryTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `docs/technical/chat-planning-tasks.md`
- `docs/end-user/plans-and-tasks.md`

Behavioral Impact
- A second anonymous plan execution for the same chat conversation now fails with HTTP 409 while the first execution is active.
- Malformed tool-call argument JSON is detected before Spring AI tool execution; no tools in that batch execute.
- Malformed argument recovery emits/persists a compact tool diagnostic, audits the rejected call, and sends a system control message so the model can retry.
- Recovered malformed argument failures do not directly mark plans failed, add execution evidence, or add validation feedback.

Risks
- The malformed JSON recovery path depends on the model responding to the control message with corrected JSON or a non-tool next action.

Follow-up Items
- None.

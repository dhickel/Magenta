Topic
Tool-call argument JSON preflight in chat execution

Source References
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ToolTranscriptService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`

Key Takeaways
- The chat tool loop records tool calls in `ToolLoopGuard` before validating argument JSON so repeated malformed calls still hit existing loop safeguards.
- Argument preflight must happen before `ToolCallingManager.executeToolCalls(...)`; if any call in a batch is malformed, the whole batch is skipped.
- Synthetic diagnostics should use normal `ToolTranscriptEntry` storage so history rendering, SSE tool activity, and audit recording stay on the same path as executed tools.
- Recovery is modeled as a control-message retry in the tool loop, not as a plan execution failure.

Engine Relevance
- This keeps malformed model output observable without letting invalid arguments reach tool implementations.
- Plan execution state remains tied to actual execution and validator outcomes instead of parser recovery.

Open Questions
- None.

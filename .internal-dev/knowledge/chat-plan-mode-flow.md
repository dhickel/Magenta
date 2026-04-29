Topic
Chat plan mode flow and internals

Source References
- src/main/java/io/mindspice/magenta2/api/web/ChatController.java
- src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java
- src/main/java/io/mindspice/magenta2/ai/chat/plan/
- src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/
- src/main/resources/schema.sql

Key Takeaways
- Planning is command-driven, not inferred from normal chat text.
- /plan enters PLAN mode and optionally stores a goal string; it does not call the model by itself.
- In PLAN mode the runtime prompt includes Mode: PLAN, optional Goal, optional saved draft title, and concise rules telling the model not to execute.
- The model saves or replaces the draft only through plan_save; v1 intentionally has no plain-text plan parser fallback.
- /exec-plan marks the saved plan EXECUTING, injects the compact saved plan into runtime state, sends a fixed execution user message, and marks the plan COMPLETED after the chat turn returns.
- /clr-exec-plan preserves the saved plan and selected model, clears chat memory, then follows the same execution path as /exec-plan.
- /exit-plan trims chat memory back to the message count from when /plan was entered, then deletes the saved plan row.

Engine Relevance
- SQLite is the source of truth for mode and saved plan state; the model is never trusted to remember or own the plan lifecycle.
- ChatService appends compact runtime state to the system prompt with effectiveSystemPrompt, keeping planning state outside long chat memory.
- PlanService owns plan lifecycle behavior and prompt-state rendering, while ChatController only parses commands and returns DTOs.
- PlanSaveTools uses PlanToolExecutionContext to bind tool calls to the active conversation and reject plan_save outside PLAN mode.
- ChatToolRegistry filters tools in PLAN mode to file exploration plus plan_save, and hides plan_save outside PLAN mode.
- ChatPlanState is returned in history, command, and stream payloads so the UI can show a small status banner without inspecting chat text.

Open Questions
- Whether execution should eventually track current step status with a separate progress tool.
- Whether plan_save should support richer structured fields after lower-parameter model behavior is observed.
- Whether /plan with a goal should optionally start an immediate model turn, or remain a pure state transition.

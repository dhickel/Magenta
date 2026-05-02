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
- /plan accepts no arguments, enters PLAN mode, stores the pre-planning model, and immediately starts a non-streaming model turn with a synthetic "ready to plan" user message.
- In PLAN mode ChatService routes turns to the configured planningModel and replaces the normal default system prompt with PlanService's standalone planning prompt; the default agent prompt is not appended.
- The model drives a progressive planning conversation, uses read-only tools for planning research, updates draft state through keyed tools (`plan_set_goal`, `plan_set_task`, `plan_put_item`, `plan_delete_item`), and queues up to five free-response questions through `plan_ask_questions`.
- Planning state stores the current planning task, clarified goal, deliverables, optional inputs/outputs, assumptions, notes, ordered steps, validation criteria, queued prompt state, validation feedback, and execution evidence.
- PLAN turns are repaired server-side if they try to finish without a queued clarification question or a ready-for-approval plan.
- /exec-plan always clears chat memory, restores the pre-planning model, marks the saved plan EXECUTING, injects compact saved plan runtime state, sends a fixed execution user message, and leaves the plan NEEDS_REVIEW with execution evidence after the chat turn returns.
- Approved plans can be executed immediately or saved for later as SAVED_TASK; this is attached to the conversation and is not a generic task system.
- /exit-plan trims chat memory back to the message count from when /plan was entered, then deletes the saved plan row.

Engine Relevance
- SQLite is the source of truth for mode and saved plan state; the model is never trusted to remember or own the plan lifecycle.
- ChatService uses effectiveSystemPrompt to swap to a plan-only prompt in PLAN mode and to append saved execution-plan state in EXECUTE_PLAN mode.
- PlanService owns plan lifecycle behavior and prompt-state rendering, while ChatController only parses commands and returns DTOs.
- PlanSaveTools uses PlanToolExecutionContext to bind tool calls to the active conversation and reject planning-state tools outside PLAN mode. Model-facing draft edits should use single keyed mutations instead of broad whole-plan updates.
- ChatToolRegistry filters tools in PLAN mode to file/web/shell exploration plus structured planning tools, and hides those planning tools outside PLAN mode.
- ChatPlanState is returned in history, command, and stream payloads so the UI can show status and planning panel controls without inspecting chat text.

Open Questions
- Whether execution should eventually track current step status with a separate progress tool.
- How the future validation agent should consume deliverables, validation criteria, and execution evidence.

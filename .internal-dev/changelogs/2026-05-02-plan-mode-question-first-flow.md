# Date

2026-05-02

# Change Summary

Fixed plan mode flow so the model asks the user about their goal before setting it, reordered the planning panel UI to render between chat history and the text input, and updated the PLAN-mode system prompt to enforce a question-first workflow with explicit integer-key API usage for all plan fields.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`

# Behavioral Impact

The planning panel (`#chat-planning-panel`) now renders between `#chat-history` and `#chat-form` instead of above the chat window, placing questions and approval controls directly above the user's text input where they are more discoverable.

The PLAN-mode system prompt now instructs the model to ask the user to describe their goal via `plan_ask_questions` as the first workflow step, and only call `plan_set_goal` after the user has responded. It then directs the model to ask for task details and context before building a structured approach with steps, assumptions, notes, and validation criteria — all using `plan_put_item` with integer keys. The `BEGIN_PLAN_MESSAGE` sent at plan start reinforces this by including "by asking the user about their goal."

All plan sections now use the same integer-key `plan_put_item`/`plan_delete_item` API, and the tool rules section explicitly states this.

# Risks

Prompt-level guidance relies on the planning model's ability to follow instructions. The turn contract repair mechanism in `ChatService` provides a safety net — if the model finishes a turn without queuing a question or marking approval-ready, a repair message is injected. However, the repair fires after tool calls, so a model that calls `plan_set_goal` before asking a question will have already persisted the premature goal before the repair triggers.

# Follow-up Items

- Consider adding a backend guard that rejects `plan_set_goal` calls when no user message has been received since plan mode began.
- Monitor whether the question-first flow produces measurably better plan quality with the current planning model.

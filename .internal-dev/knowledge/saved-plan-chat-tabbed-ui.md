# Topic

Saved plan chat tab rendering and deterministic prompt flow.

# Source References

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatModuleRenderer.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanChatService.java`
- `src/main/resources/static/js/orchestration/plans.js`
- `docs/end-user/plans-and-tasks.md`

# Key Takeaways

- `/chat` remains the canonical session-chat page, but reusable web-layer rendering can share SimplyPages `ChatModule` structure for embedded chat-like surfaces.
- Saved plan chat should not use `/api/chat`, session metadata, or the chat sessions sidebar. Its transcript belongs to `plan_chat_messages`.
- Opening a saved plan chat tab should go through the saved-plan chat service so deterministic assistant prompts are persisted only when the chat surface is actually opened.
- The `/plans` tab control should render a single active `plan-tab-window`. Do not keep hidden editor and chat panels in the same detail surface because it can make the chat appear below the editor or duplicate tab bodies after HTMX swaps.
- New saved-plan chat opening prompts are deterministic and ordered as runtime inputs, goal, deliverables, then structured outputs. Existing drafts and approved plans use separate resume prompts instead of restarting the four-question sequence.
- Manual editor saves can preserve chat continuity by appending a concise `system` context message summarizing changed scalar fields before the next saved-plan chat response. This keeps the edit visible without treating it as the user’s answer to the current planning prompt.
- The `/plans` dirty-state guard is HTMX-focused: it tracks editor-panel inputs and intercepts editor-replacing HTMX requests unless the request comes from a save/form submission or the naming modal.

# Engine Relevance

Future `/plans` UI changes should keep tabs server-rendered and HTMX-compatible. If saved-plan chat becomes model-backed later, the transport can change behind the plan-scoped service contract without coupling it to generic `/chat` sessions.

# Open Questions

- Should list/field editor changes also produce chat context messages, or should context sync remain limited to scalar saves until a broader diff format is designed?

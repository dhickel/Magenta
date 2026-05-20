# Anonymous Chat Plan Clean Execution

## Date
2026-05-20

## Change Summary
Fixed anonymous `/chat` plan execution so approved plan execution uses the streaming endpoint from the browser and clean execution omits prior transcript only for the model prompt. Corrected saved plan chat resume behavior so tab switching does not replace the four scripted opening questions with draft resume prompts.

## Files
- `src/main/resources/static/js/chat-client.js`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ResolvedChatRequest.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanChatService.java`
- `docs/end-user/chat.md`
- `docs/end-user/plans-and-tasks.md`
- `docs/technical/chat-planning-tasks.md`
- `docs/technical/api-reference.md`
- `docs/api/00-index.md`

## Behavioral Impact
- `Approve And Exec` and `Approve And Exec Clean` now stream visibly through `/api/chat/{conversationId}/plan/execute/stream`.
- Clean execution preserves persisted chat history while omitting stored transcript from the execution prompt.
- Tool-capable clean execution keeps the approved execution instruction through tool checkpoints.
- Saved plan chat remains isolated to the Planning Chat tab and preserves the scripted opening question sequence across tab switches.

## Risks
- Successful live plan execution streaming still depends on model availability and an approved anonymous plan.
- Existing non-streaming anonymous execution route remains for API compatibility but is no longer the browser path.

## Follow-up Items
- Consider adding a dedicated browser fixture that seeds an approved anonymous plan without model calls so Playwright can validate successful `start` events deterministically.

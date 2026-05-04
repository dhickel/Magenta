# Date
2026-05-04

# Change Summary
Added a user-selectable "Planning Model" dropdown to the chat UI alongside the existing "Agent Model" dropdown. Previously, the planning model was hardcoded in `AiConfig.planningModel` and could not be changed per-conversation. Now users can pick a planning model independently from the agent model, and the selection is persisted per-conversation in the database.

# Files
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatRequest.java` — added `planningModel` field to `MsgRequest` and `CmdRequest` records
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatSessionMetadataRepository.java` — added `planning_model` column migration, `savePlanningModel()`, and `findPlanningModel()` methods
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` — added `storedConversationPlanningModel()` and `resolvedPlanningModel()` helpers; updated `resolve()` to persist and use stored planning model; updated `beginPlan()` signature to accept explicit planning model; updated `submitPlanAnswer()` to use resolved planning model
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java` — updated `handlePlan()` to pass `request.planningModel()` through to `chatService.beginPlan()`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java` — added "Planning Model" dropdown next to "Agent Model"; refactored `buildModelOptionsHtml()` to accept a default model parameter
- `src/main/resources/static/js/chat-client.js` — added `selectedPlanningModel()` and `syncPlanningModelSelection()`; updated `sendMessage()` and `sendCommand()` payloads to include `planningModel`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java` — updated `MsgRequest` constructor and `beginPlan` stub to match new signatures

# Behavioral Impact
- The chat toolbar now has two dropdowns: "Agent Model" (for normal chat) and "Planning Model" (used when entering `/plan` mode).
- When the user sends a message or command, the selected planning model is persisted to `ai_chat_session_metadata.planning_model`.
- In plan mode, `resolve()` uses the stored planning model if available, falling back to `AiConfig.planningModel` as the default.
- `beginPlan()` accepts an explicit planning model from the request, resolving through `resolvedPlanningModel()` if not provided.
- The config-level `planningModel` in `AiConfig` remains as the default when no per-conversation selection has been made.

# Risks
- Existing conversations will have no `planning_model` stored, so the config default is used — no regression.
- The `beginPlan(String, String)` two-arg overload was replaced with `beginPlan(String, String, String)`. Any code calling the old signature will fail to compile.

# Follow-up Items
- Consider syncing the planning model dropdown when loading conversation history (currently the dropdown retains its last user-selected value across page loads).

# Anonymous Vs Saved Task Planning

## Topic
Distinguishing anonymous in-chat planning from saved task planning in Magenta.

## Source References
- `docs/end-user/chat.md`
- `docs/end-user/plans-and-tasks.md`
- `docs/technical/chat-planning-tasks.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`

## Key Takeaways
- Anonymous in-chat planning lives under `/chat`, uses `/api/chat/{conversationId}/plan/*`, and persists `SESSION_PLAN` state keyed by conversation id.
- Saved task planning lives under `/plans`, persists `TASK_TEMPLATE` definitions, and stores plan-scoped transcript rows in `plan_chat_messages`.
- Clean anonymous execution is prompt-scoped. It should suppress prior chat memory for the model call without deleting persisted `ai_chat_memory` rows.
- Saved plan chat opening questions are deterministic and must be completed before later model-backed planning behavior can run.

## Engine Relevance
Route and persistence boundaries matter for context handling. `/api/chat` session memory should not be coupled to saved plan chat, and saved plan chat should not rely on anonymous session planning state. Clean-context execution should be represented as request metadata flowing into prompt assembly, not as destructive repository mutation.

## Open Questions
- A deterministic approved-plan browser fixture would make successful plan execution SSE validation easier without depending on a live model.

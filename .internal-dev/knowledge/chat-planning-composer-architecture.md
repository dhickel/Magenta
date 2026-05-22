# Chat Planning Composer Architecture

## Topic
Shared prompt-card UI for anonymous `/chat` planning and saved plan chat.

## Source References
- `src/main/java/io/mindspice/magenta2/api/web/ChatModuleRenderer.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/static/css/magenta.css`
- `src/main/resources/static/css/orchestration.css`
- `docs/technical/chat-planning-tasks.md`
- `docs/technical/frontend-htmx.md`

## Key Takeaways
- Both chat surfaces use SimplyPages `ChatModule`, but they intentionally keep different transport ownership.
- `/chat` is client-state and SSE owned through `chat-client.js`; active anonymous planning questions are routed by JS from the main composer to `/api/chat/{conversationId}/plan/answers`.
- Saved plan chat is plan-editor and HTMX owned; its `#plan-chat-form` posts to `/plans/_editor/{planId}/planning-chat/answers`.
- Shared UI should live at the component/rendering boundary where possible. `ChatModuleRenderer.planningQuestionCard(...)` provides the common prompt-card markup while each surface keeps its own submission mechanics.
- Active `/chat` question metadata must be cleared whenever the planning panel leaves question mode to avoid sending ordinary chat text as a planning answer.

## Engine Relevance
This pattern keeps reusable presentation in Java/SimplyPages components while respecting the different runtime contracts of anonymous chat planning and saved plan planning. It is the preferred approach for future chat-surface convergence unless the transport architecture is deliberately unified.

## Open Questions
- Should prompt-card CSS move to a shared base asset if more non-chat surfaces need the same component?

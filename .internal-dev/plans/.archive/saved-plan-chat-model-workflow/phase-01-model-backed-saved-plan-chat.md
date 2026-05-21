# Context

Saved `/plans` planning chat previously used four fixed opening questions and parsed the answers directly into saved plan fields. This produced poor input names and deliverables when users answered in natural language.

# Goal

Make opening answers seed context for a model-backed saved-plan planning turn. The model should synthesize fields, use plan-scoped tools, and continue asking focused follow-up questions until the saved plan is ready for approval.

# In Scope

- Keep the existing four opening questions.
- Store opening answers in `plan_chat_messages`.
- Add plan-id scoped saved-plan model turns and saved-plan mutation tools.
- Preserve `/plans` separation from `/api/chat`, `ai_chat_memory`, and chat session metadata.
- Ensure manual editor saves, including section edits, append saved-plan chat context.
- Update focused tests and documentation.

# Out of Scope

- Reworking anonymous `/chat` planning.
- Adding a new frontend framework or JavaScript transport.
- Changing plan run execution semantics.

# Implementation Steps

1. Replace direct opening-answer parsers in `SavedPlanChatService`.
2. Add `SavedPlanModelClient` and `SavedPlanPlanningModelClient`.
3. Add saved-plan-specific tool callbacks backed by new plan-id `PlanService` methods.
4. Include recent plan chat transcript in model user messages so manual editor change notices are visible to the model.
5. Route editor field and section saves through `appendEditorSaveContext`.
6. Update saved-plan chat tests and docs.

# Validation

- `mvn -Dtest=SavedPlanChatServiceTest test`
- `mvn -Dtest='io.mindspice.magenta2.ai.chat.plan.*Test' test`
- `mvn -Dtest=SavedPlanChatServiceTest,OrchestrationControllerTest test`
- Spring Boot context smoke test.
- Focused Playwright validation of `/plans` saved planning chat.

# Exit Criteria

- Opening answers are not copied directly into saved plan fields.
- The first saved-plan model turn receives labeled opening answers and synthesis instructions.
- Follow-up answers and free messages invoke the model-backed path.
- Manual editor changes are visible in later saved-plan model context.
- Required docs, changelog, and knowledge records are updated.

# Context

The first saved-plan tab implementation rendered editor and chat panels in the same detail container, with the chat panel appended after editor content. New saved-plan chat also asked for the goal first, while the desired opening sequence starts by asking whether there are runtime inputs.

# Goal

Correct `/plans` so the top tab control switches between a single editor window and a single chat window. New planning chat must ask four deterministic questions before any model-backed behavior can be introduced, starting with runtime inputs. Existing draft and approved plans keep their separate deterministic resume prompts.

# In Scope

- Render only the active tab body under the top tab control.
- Keep `Editing Details` and `Planning Chat` tab controls HTMX-driven.
- Reorder new saved-plan chat opening questions to input details, goal, deliverables, outputs.
- Map opening answers to the correct plan fields after reordering.
- Preserve named plan-chat draft titles.
- Append editor-save context as a non-answer context message so it does not consume the current planning prompt.
- Update focused service/controller tests, docs, changelog, and knowledge.

# Out of Scope

- Adding model-backed saved-plan chat generation.
- Reworking list/field editors beyond what is required for the active tab body.
- Changing `/chat` session chat behavior.

# Implementation Steps

1. Refactor `OrchestrationController.planEditorFragment` so it renders header, tabs, and one `plan-tab-window` child. Move the editor form, submit form target, and recent runs into an editor body helper. Move chat module rendering into a chat body helper.
2. Remove reliance on hidden tab panels for main tab switching. Leave HTMX tab controls as the source of truth and keep dirty-state warning for editor-replacing HTMX requests.
3. Change `SavedPlanChatService.OPENING_QUESTIONS` order so the first question asks for runtime inputs. Update `applyOpeningAnswer` switch indices.
4. Change editor-save context messages to `system` role and keep them appended only when visible chat history exists and a diff is non-empty.
5. Update tests to assert chat-tab renders no editor form below it, editor-tab renders no chat module body, and opening question order starts with inputs.
6. Run focused service/controller tests, bounded startup smoke, and focused Playwright validation of the corrected UI.

# Validation

- `mvn -Dtest=SavedPlanChatServiceTest,OrchestrationControllerTest test`
- Bounded Spring Boot startup smoke.
- Playwright validation for `/plans`: naming modal, new plan editor tab, new plan chat tab, tab switching, no stacked bottom chat, first question is runtime inputs, Q/A persistence, save context message, and mobile layout.

# Exit Criteria

- Chat appears directly in the active tab window under the top tabs, not at the bottom of the editor.
- New plan chat first asks about runtime inputs and still asks exactly four deterministic opening questions.
- Existing draft chat asks “Any details you want to provide before continuing?”
- Approved chat asks “What do you need to change in this plan?”
- Editor saves update persisted plan state and append a context message without consuming a planning answer.

# Context

Saved plan editing currently mixes scalar/list editing with a collapsible saved-plan chat panel. New draft creation is immediate and unnamed. The target behavior is a tabbed `/plans` detail surface with deterministic, plan-scoped chat history that visually reuses the SimplyPages chat module chrome used by `/chat`.

# Goal

Implement a tabbed saved plan editor with `Editing Details` and `Planning Chat`, named draft creation for both `New Plan` and `New Plan Chat`, deterministic saved-plan chat prompts, editor save-to-chat context sync, and focused validation.

# In Scope

- Add the server-rendered new-plan naming modal/fragment.
- Add editor/chat tabs in the plan editor fragment.
- Render the plan chat tab with SimplyPages `ChatModule` structure without `/chat` session sidebar coupling.
- Track dirty editor state in `plans.js` and guard HTMX swaps when edits are unsaved.
- Preserve saved plan chat persistence in `plan_chat_messages`.
- Append concise field-level user edit context into saved-plan chat when saved editor fields change and chat history exists.
- Update focused tests and user docs for `/plans`.

# Out of Scope

- Coupling saved-plan chat to `/api/chat`, generic chat sessions, or anonymous chat planning.
- Adding model-generated question selection for saved-plan chat.
- Broad SimplyPages library changes.

# Implementation Steps

1. Update saved-plan chat service behavior and tests for deterministic new/resume prompts and editor diff context.
2. Update `/plans` controller rendering, routes, and controller tests for modal naming, tabs, and ChatModule-backed saved-plan chat.
3. Update `plans.js` for dirty-state tracking, modal behavior, tab activation, and HTMX swap confirmation.
4. Update orchestration CSS and `/plans` docs.
5. Run focused Maven tests, bounded Spring Boot startup smoke, and Playwright validation with screenshots through a validation agent.

# Validation

- Focused service and controller tests for saved-plan chat, tab rendering, modal routes, and save context sync.
- Spring Boot context startup smoke.
- Playwright validation for `/chat`, `/plans` editor tab, `/plans` chat tab, naming modal, advanced section, tab switching, dirty-state warning, save refresh, chat persistence, and mobile layout.

# Exit Criteria

- `/plans` opens existing plans on `Editing Details` by default.
- `New Plan` and `New Plan Chat` both prompt for a name, create a saved `TASK_TEMPLATE` draft, and open the expected tab.
- Saved plan chat uses deterministic assistant prompts and persisted plan-scoped messages.
- Editor saves append a concise chat context message when saved fields changed and chat history already exists.
- Relevant tests and validation pass, or blockers are explicitly recorded.

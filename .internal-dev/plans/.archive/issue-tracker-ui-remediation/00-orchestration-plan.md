# Context
Seven linked UI defects span `/chat`, `/agents`, and `/plans`.

# Goal
Coordinate one bounded remediation pass that preserves route separation, keeps CRUD HTMX-first, and reserves JavaScript for live chat transport/state only.

# In Scope
Collapsed structured chat plans, agent-chat origin/history separation, compact agent selection, one inline agent-chat surface, plan continuation messaging, readable plan lists, and arrow move controls.

# Out of Scope
Retro-migrating historical mixed chat rows, workflow redesign, drag-and-drop reordering, and unrelated orchestration polish.

# Implementation Steps
1. Implement shared chat/session contracts first.
2. Land operational UI changes in one merge lane because they share `OrchestrationController` and `orchestration.css`.
3. Validate each issue locally, then run the final cross-surface gate.

# Validation
Use `08-final-validation.md` and `play_wright_tests.md` as the gate.

# Exit Criteria
All seven issue phases are complete, full automated tests pass, browser validation is recorded, and closeout docs exist.

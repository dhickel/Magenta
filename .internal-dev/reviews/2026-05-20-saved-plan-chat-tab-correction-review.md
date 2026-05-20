# Scope

Review of the saved `/plans` tabbed editor/chat correction after the first implementation rendered chat below the editor and started new planning chat with the goal question.

# Findings

- The previous UI kept editor and chat panels in the same detail container, hiding the inactive panel with CSS. This allowed the chat to appear as bottom content rather than the active tab window. The correction renders one active `plan-tab-window` under the top tab controls.
- The previous new-plan-chat opening order started with goal. The correction asks for runtime inputs first, then goal, deliverables, and structured outputs.
- Editor-save context was previously stored as a user-role message, which could be confused with an answer to the current planning prompt. The correction stores it as a system-role context message and keeps the current prompt intact.
- Approved plans can lose persisted pending questions through `PlanService.saveTask`; the correction derives deterministic resume prompts from the latest assistant prompt when needed.

# Risk Assessment

Remaining risk is low for the corrected behavior. The implementation remains deterministic and does not invoke model-backed planning during the four-question saved-plan chat flow. List and field row edits are still outside scalar save-diff context.

# Recommendations

- Keep the top `/plans` tab behavior HTMX-rendered as a single active window.
- Keep saved-plan chat separate from `/api/chat` sessions and model streaming until a model-backed saved-plan chat service is intentionally designed.
- If list/field edit context is required later, add a structured diff path for those endpoints rather than overloading scalar save diffs.

# Follow-ups

- None required for this correction.

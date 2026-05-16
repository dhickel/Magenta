# Date
2026-05-15

# Change Summary
Updated the plan editor list-view rows to support multiline wrapped text so long entries are fully visible, and added extra spacing around list-section add buttons for clearer separation between sections.

# Files
- src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java
- src/main/resources/static/css/orchestration.css

# Behavioral Impact
- Deliverables, steps, validation criteria, and assumptions now render as multiline textareas in list rows.
- Long list text wraps instead of horizontally clipping.
- Existing list text remains visible after the textarea conversion because textarea values are rendered as element content.
- "Add ..." buttons for list sections now have additional vertical margin, improving readability between sections.

# Risks
- Multiline list rows increase vertical density and can make long sections taller.
- Existing behavior still updates on `change` (blur), so edits are not persisted until focus leaves the field.

# Follow-up Items
- Optional UX improvement: move from `hx-trigger=change` to explicit save-on-input debounce only if users request faster autosave behavior.

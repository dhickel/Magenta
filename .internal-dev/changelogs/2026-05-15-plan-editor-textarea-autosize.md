# Date
2026-05-15

# Change Summary
Plan editor textareas now expand vertically for existing and newly typed content instead of clipping long plan notes, deliverables, steps, validation criteria, assumptions, and advanced fields.

# Files
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/js/orchestration/plans.js`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

# Behavioral Impact
The server renders plan editor textareas with content-aware initial row counts and a `data-autosize` marker. The plans page applies a narrow browser-side autosize helper after initial load, user input, and HTMX swaps so wrapped textarea content remains visible while keeping CRUD and persistence HTMX-first.

# Risks
Textarea height is calculated by browser `scrollHeight`, so unusual browser font or zoom behavior may produce slightly taller controls, but it should avoid hidden content.

# Follow-up Items
None.

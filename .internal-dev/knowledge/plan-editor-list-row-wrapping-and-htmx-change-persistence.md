# Topic
Plan editor list-row wrapping and persistence behavior in HTMX list sections.

# Source References
- src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java
- src/main/resources/static/css/orchestration.css

# Key Takeaways
- Single-line `TextInput` fields are a poor fit for long plan list items (deliverables/steps/criteria/assumptions) because they hide content horizontally in dense row layouts.
- Switching those list rows to `TextArea` with `white-space: pre-wrap` and `overflow-wrap: anywhere` makes long entries fully visible without custom JS.
- In SimplyPages, `TextArea` values must be set with `.withValue(...)`; a raw `value` attribute does not populate visible textarea content.
- With HTMX `hx-trigger="change"`, updates persist on blur/change, not per keystroke; this is stable but can surprise users expecting immediate save.
- Section-level button spacing (`.plan-list-add-btn`) helps separate stacked list editors and improves scannability.

# Engine Relevance
Useful for any SimplyPages+HTMX form editor where ordered text lists are edited inline. Prefer multiline controls for content-like fields and reserve single-line inputs for short identifiers.

# Open Questions
- Should list section updates remain blur-based, or should we adopt debounced `input` updates for faster persistence feedback?

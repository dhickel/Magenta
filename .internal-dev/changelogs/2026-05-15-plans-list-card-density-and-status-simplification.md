# Date
2026-05-15

# Change Summary
Updated the `/plans` left sidebar list to use compact card rows, removed redundant `TASK_TEMPLATE` display, standardized visible status chips to `draft` and `approved`, and added an inline trash icon delete action per plan card.

# Files
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/css/orchestration.css`

# Behavioral Impact
- Plan cards in the left list are smaller and denser.
- Each card shows only a status chip; `kind` (`TASK_TEMPLATE`) is no longer rendered.
- Status chip color coding is now explicit in list view:
  - `draft`: yellow
  - `approved`: green
- Each card includes a delete icon button with confirmation.
- On delete, the list refreshes and the editor pane is reset to the empty-state prompt.

# Risks
- The trash icon uses an emoji glyph (`🗑`), which can render slightly differently across platforms/fonts.
- The status chip currently supports fallback neutral style for any unexpected status values.

# Follow-up Items
- If desired, replace the emoji trash glyph with a shared icon component/system icon for stricter visual consistency.

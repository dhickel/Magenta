# Topic
Plans sidebar compact-card pattern with HTMX delete + OOB editor reset

# Source References
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/css/orchestration.css`

# Key Takeaways
- The `/plans/_list` fragment can render richer list rows (open action + secondary delete action) by replacing single clickable list buttons with a row wrapper containing two buttons.
- To keep the UX consistent after deletion, return:
  1. refreshed `#plan-list` HTML as the main response target, and
  2. an `hx-swap-oob="innerHTML"` payload for `#plan-editor-container` to clear stale editor content.
- Status display can be centralized in a small helper (`planStatusBadge`) to keep list rendering logic simple and enforce stable color/label semantics.

# Engine Relevance
- Reusable for other list/detail screens in orchestration where delete actions should refresh the list and clear detail panes without a full page refresh.
- Aligns with HTMX-first interaction style and avoids introducing broad client-side JS state for simple CRUD/list interactions.

# Open Questions
- Should destructive iconography across orchestration pages be standardized with a shared icon utility/component instead of unicode glyphs?

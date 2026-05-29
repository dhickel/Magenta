# Context

Repair the Agents operational UI and Home dashboard chat resize behavior as one small UI work unit.

# Goal

- `/agents` and `/agents/{agentId}` must not use the Manage side-nav shell, while the global top nav remains ordered `Home`, `Chat`, `Agents`, `Manage`.
- Agents pages must use an Agents-specific shell or shell mode with compact selector rows and HTMX detail swaps.
- The Home dashboard chat rail must regain its shell hook so the existing corner resize script can resize width and height while dashboard content fills remaining space.

# In Scope

- Web-layer page/shell rendering for `/agents` and `/agents/{agentId}`.
- Agent selector markup and styling.
- Home dashboard shell hook, resize CSS/JS compatibility, and focused controller tests.
- Focused test updates in `OrchestrationControllerTest` and `AvatarDashboardControllerTest`.
- Affected `.internal-dev` specs, knowledge/changelog, `docs/` updates, and validation evidence under `artifacts/agents-selector-chat-resize/validation-summary.json`.

# Out of Scope

- Lifecycle action semantics, agent persistence behavior, new API routes, new SimplyPages upstream library work, and broad dashboard redesign.
- Replacing HTMX detail/tab behavior with JavaScript transport.
- Reverting unrelated pre-existing worktree changes.

# Editable Files

Expected editable areas:

- `src/main/java/io/mindspice/magenta2/api/web/`
- `src/main/resources/static/css/`
- `src/main/resources/static/js/`
- `src/test/java/io/mindspice/magenta2/api/web/`
- Relevant files under `.internal-dev/specifications/`, `.internal-dev/knowledge/`, `.internal-dev/changelogs/`, `docs/`, and `artifacts/agents-selector-chat-resize/`

Do not edit outside this boundary unless the directly necessary adjacent hygiene is reported.

# Acceptance Criteria

- Top nav order is exactly `Home`, `Chat`, `Agents`, `Manage`.
- `/agents` and `/agents/{agentId}` do not render the Manage side nav or Manage-specific shell chrome.
- Agent selector rows are compact, in-page rows with display name, one status chip (`ACTIVE -> Active`, `DISABLED -> Inactive`, workspace/health failure -> `Error`), and compact right-side queue/inbox glyphs or count chips.
- Selector rows preserve selected state after HTMX detail swaps.
- Selector rows do not include `Refresh`, `Disable`, or `Delete`; lifecycle actions remain in the agent detail/manage area.
- HTMX selection and detail rendering continue to target the right-hand detail area.
- Home dashboard HTML includes the shell hook used by `avatar-shell.js`, the chat rail, main dashboard panel, and corner resize handle.
- Existing resize behavior can change rail width and panel height, and the main dashboard content takes the remaining space.
- JavaScript remains narrow and limited to selected-row affordance and resize behavior when CSS/HTMX alone is not enough.

# Required Validation

- Run focused tests:
  - `mvn -Dtest=OrchestrationControllerTest test`
  - `mvn -Dtest=AvatarDashboardControllerTest test`
- Run a bounded Spring Boot startup smoke unless blocked by local dependencies.
- Prepare browser validation checklist and evidence path for a separate Playwright agent:
  - `/agents` desktop and mobile.
  - `/agents/{agentId}` desktop and mobile.
  - `/` before/after horizontal chat resize.
  - `/` after vertical chat resize.

# Closeout

- Update affected specs/knowledge/changelog/docs.
- Write or update `artifacts/agents-selector-chat-resize/validation-summary.json` with conservative status only.
- Leave the plan artifact active for the main coordinator to archive after full validation.

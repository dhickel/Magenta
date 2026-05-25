# Avatar Shell Baseline Refactor Implementation Plan

## 1. Objective

Refactor `/avatar` into a stable operational shell that matches the compact tabbed rhythm of `/agents` while preserving Avatar's distinct right-side assistant rail. The shell must remove the current bulky top toolbar, keep dashboard layout editing in place only on the dashboard tab, fix row-decoration layering so row controls render above module edit chrome, and introduce persistent tab and divider state without inventing a new runtime model.

This pass is a baseline pass, not a full Avatar functionality sweep. It should deliver the durable shell, state, and styling contract that later iterations can build on.

## 2. Inputs And Assumptions

### Confirmed Inputs

- `/avatar` currently renders through `AvatarDashboardController` and `AvatarDashboardComponents`.
- Current shell structure is one dashboard page with a three-control toolbar: `Organizer`, edit toggle text link, and `Refresh Widgets`.
- Widget edit controls already exist as compact top-corner decorators, but row controls are still absolutely positioned and can layer behind widget UI.
- `/agents` already exposes the tab-row interaction pattern through `tabNav(...)`, `.orch-tabs`, and `#agent-tab-panel`.
- Current Avatar chat is a narrow JS client in `avatar-chat.js`; dashboard editing helpers live in `avatar-layout-edit.js`.
- Current Avatar layout editing is already in-place and must remain so; modals remain acceptable for widget detail only.
- The reserved backing Avatar agent id is `avatar`.

### User Decisions

- Tabs in scope now: `Dashboard`, `Queue`, `History`, `Profile`, `Outputs`, and `Work Areas`.
- Planner/todos/calendar/notes stay as dashboard widgets for now rather than promoted top-level tabs.
- Queue and History should reflect both the Avatar user surface and the reserved Avatar agent entry, using existing services and layers built on top rather than a new data model.
- Remove the top-level Organizer action entirely.
- Remove manual refresh now and defer interval refresh work.
- Desktop gets a draggable divider for the right chat rail; mobile does not.
- The active tab and divider width must persist across in-page tab switches, and should persist across reloads.
- Only the Dashboard tab is user-layout-editable.

### Assumptions To Verify Before Coding

- The reserved Avatar agent profile remains the canonical source for assignment queue/history data used by the new tabs.
- Reusing `.orch-tabs` visual language directly, or extracting a shared tab helper, is preferable to inventing a second Avatar-only top-nav system.
- Existing chat surface metadata is sufficient for Avatar chat continuity; this pass does not need a new conversation-state model.
- Browser-local state is acceptable for persisted rail width; no server-backed shell persistence is required for this baseline.

## 3. Scope

### In Scope

- Replace the current Avatar top toolbar with a unified shell header and agent-style tab row.
- Add Avatar tab-panel routes and in-place HTMX tab swapping without full page reloads.
- Keep the right chat rail persistent across tab content changes.
- Add desktop divider drag/resize behavior with persisted width state.
- Persist active tab through URL state and desktop rail width through browser-local state.
- Convert dashboard edit-mode entry/exit to compact icon-first controls integrated into the unified shell.
- Fix row-decoration rendering order so row controls sit above widget edit chrome.
- Remove top-level Organizer and manual refresh UI controls.
- Add the non-dashboard Avatar tabs using existing services:
  - Queue
  - History
  - Profile
  - Outputs
  - Work Areas
- Update tests, docs, and deferred-work records.

### Out Of Scope

- Interval refresh or polling behavior.
- Promoting planner/todos/calendar/notes into top-level tabs.
- Multi-pane `/chat` parity inside Avatar.
- A new server-pushed state model for Avatar tab panels.
- New persistence tables or server-backed state models for shell UI state.
- General redesign of `/agents` or `/dashboard`.

## 4. Current-State Analysis

### 4.1 Current Avatar Shell

`AvatarDashboardComponents.page(...)` currently renders:

- a page header;
- `.avatar-layout` with dashboard content in the left column and `compactChat(...)` in the right column;
- a top `toolbar(...)` with `Organizer`, edit-mode text link, and `Refresh Widgets`;
- the widget grid only, with no concept of tabbed Avatar subpages.

This structure breaks the intended baseline in three ways:

- it makes top-level control chrome feel like generic buttons rather than an operational tabbed shell;
- it ties the entire left side to dashboard widgets, so alternate Avatar surfaces cannot swap in place;
- it leaves chat persistent only because it is next to the grid, not because there is an explicit shell contract.

### 4.2 Current Edit Chrome

Widget corner controls are already compact and mostly aligned with the SimplyPages editing direction. Row controls are still rendered as `.avatar-row-micro-controls` positioned relative to the row wrapper, with a lower z-index than some neighboring widget chrome. In the reported screenshot, row-move controls appear visually behind module edit controls.

This indicates the row itself needs a first-class decoration layer above row content, similar to the way modules carry decorator chrome, instead of relying on a negative-offset absolute cluster.

### 4.3 Current Chat Rail

The Avatar chat rail is currently `position: sticky` with bounded height and a scrollable message list. It follows the viewport adequately, but it is not designed as a persistent shell rail:

- it has no divider or resize affordance;
- it has no persisted width state;
- tab changes do not exist yet, so persistence across left-panel swaps is not solved explicitly.

### 4.4 Current Data And State Options

The new shell does not need server-backed persistence to satisfy the baseline behavior:

- active tab can live in URL/query-string state plus HTMX push history;
- desktop chat-rail width can live in browser-local state plus a CSS custom property;
- existing runtime and Avatar persistence remain unchanged.

This avoids adding new tables, server endpoints, or runtime coupling for purely presentational shell state.

### 4.5 Current Sources For New Tabs

The repo already has the required service families:

- `AssignmentService` for queue/history;
- `OutputArtifactService` for outputs;
- `WorkAreaService` and `WorkAreaExplorerService` for work areas;
- Avatar profile services plus the reserved Avatar agent profile for profile/status composition;
- existing chat surface metadata for Avatar user-surface history.

The new tabs should compose these sources instead of defining Avatar-specific queue/history storage.

## 5. Target Design

### 5.1 Shell Contract

Keep `/avatar` as the main route, but make the page a shell with three stable regions:

1. `avatar-shell-header`
   - page title/subtitle
   - compact action strip
   - no bulky toolbar row
2. `avatar-shell-main`
   - `nav.orch-tabs`-style Avatar tab row
   - `#avatar-tab-panel` content region swapped by HTMX
3. `avatar-shell-rail`
   - persistent Avatar chat rail outside the tab panel
   - desktop resizable by divider
   - stacked below content on mobile

The tab row should visually match agent tabs. Do not invent a second large header bar with button-sized pills.

### 5.2 Tab Model

Canonical top-level tabs:

- `dashboard`
- `queue`
- `history`
- `profile`
- `outputs`
- `work-areas`

Behavior:

- `/avatar` may accept an optional initial `tab` query parameter for deep-linking.
- Tab buttons issue HTMX requests to Avatar tab fragment routes and target `#avatar-tab-panel`.
- Tab switches must not rerender the right chat rail.
- The active tab should update both:
  - the visible active tab styling;
  - the pushed browser URL.

Persistence contract:

- active tab: `?tab=<tab>` query parameter plus HTMX push-state handling;
- desktop chat rail width: browser-local storage key such as `magenta.avatar.chatRailWidthPx`.

If `edit=true` is supplied for a non-dashboard tab, normalize it away. Edit mode applies only to `dashboard`.

### 5.3 Dashboard Tab

The dashboard tab is the only editable tab. It should render:

- the widget grid;
- a compact shell-level edit toggle as an icon button or similarly minimal control;
- any dashboard-only micro status text needed for edit mode, but not a bulky toolbar.

The dashboard tab must not expose:

- Organizer button;
- manual refresh button.

Organizer behavior remains accessible through widget content and widget detail flows only.

### 5.4 Row Decoration Layering

Replace the current row micro-controls pattern with a dedicated row decoration container rendered before or above row content in DOM and styling order, for example:

- `avatar-row-decoration`
- `avatar-row-decoration-actions`

Requirements:

- visually low-emphasis, similar to the module decorator language;
- positioned above the row content area, not buried at the edge of the grid;
- z-index above widget corner controls when overlapping space is possible;
- edit-only rendering;
- disabled states remain visible but muted.

The row decoration should own row-level actions:

- move up
- move down
- delete empty row
- open add-widget picker for that row when appropriate

The row should then feel like it has its own decorator strip, not a floating button cluster.

### 5.5 Right Chat Rail

Desktop behavior:

- persistent on every Avatar tab;
- sticky/following viewport behavior retained;
- explicit divider handle between content and rail;
- drag updates a CSS width variable live;
- on drag end, width persists to browser-local state.

Mobile behavior:

- no drag handle;
- stacked layout;
- stored width ignored for rendering until desktop breakpoint returns.

JavaScript scope is justified here because pointer-drag resizing is materially simpler with narrow JS than with HTMX alone. Keep JS limited to:

- drag lifecycle;
- width clamping;
- local persistence save on drag end;
- restoring saved width on initial load when the shell initializes on desktop.

### 5.6 Non-Dashboard Tabs

All non-dashboard tabs must be non-layout-editable and use operational tab-panel conventions similar to agent detail tabs.

#### Queue

Build from existing Avatar-agent runtime queue data and delegated/observed queue data across other agents as appropriate. Use existing `AssignmentService` queue views and compact action/status rendering patterns already proven in agent pages. Keep the tab focused on operational state, not a second dashboard collage.

#### History

Build from existing Avatar user-surface chat history plus Avatar-agent terminal assignment history and agent chat sessions where available from existing services. Do not create a separate Avatar-only history store.

#### Profile

Combine:

- Avatar profile data from current Avatar services;
- reserved Avatar agent metadata relevant to the user-facing operational picture.

This tab is the natural place for future Avatar-specific shell settings, but this pass only needs enough structure to feel consistent and useful.

#### Outputs

Use existing output artifact sources and preview/download patterns, presented in a compact panel/table/list format aligned with agent/dashboard styling.

#### Work Areas

Promote Work Areas into a top-level tab because they are directly relevant to Avatar as an agent abstraction and already exist as a first-class operational concern. Reuse the existing explorer patterns rather than inventing another file-surface paradigm.

### 5.7 Refresh Policy

This pass removes manual refresh affordances from the visible Avatar shell and widget chrome where they exist only as generic user controls. Future auto-refresh is explicitly deferred.

Implementation guidance:

- remove UI exposure now;
- keep server fragment endpoints only if they remain useful internally or for future reintroduction;
- do not add interval polling or silent refresh in this pass.

## 6. Implementation Plan

### Step 1: Define Avatar shell state and tab contract

Files:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`

Changes:

- decide one stable default tab: `dashboard`;
- decide one stable default desktop rail width or min/max clamp range;
- add tab-panel fragment render routes and URL normalization for valid Avatar tabs;
- ensure pushed URLs preserve tab state and drop `edit=true` when the target tab is not `dashboard`.

Gotchas:

- keep controller thin and do not turn tab state into runtime persistence;
- use server-rendered active-tab state from the normalized request rather than ad hoc DOM toggles where possible.

### Step 2: Refactor page composition into a persistent shell

Files:

- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`

Changes:

- replace the old toolbar-first left column composition with:
  - shell header
  - shared tab row
  - `#avatar-tab-panel`
  - persistent right chat rail
- keep the chat rail outside the tab panel so HTMX tab swaps cannot destroy it;
- reuse `.orch-tabs` styling or extract a shared helper, but keep visual parity with `/agents`.

Acceptance:

- `/avatar` initial render shows unified shell;
- dashboard tab loads by default;
- chat rail remains mounted while tab content swaps.

### Step 3: Move dashboard actions into compact shell/dashboard controls

Files:

- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`
- `AvatarDashboardControllerTest.java`

Changes:

- remove top-level Organizer button;
- remove visible manual refresh button;
- replace the bulky edit text control with a compact shell-integrated icon control for dashboard edit mode only;
- preserve existing in-place layout-editing semantics.

Acceptance:

- screenshot-level control density matches operational styling;
- tests confirm removed controls are absent;
- dashboard still enters edit mode correctly.

### Step 4: Introduce first-class row decoration rendering

Files:

- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`
- `AvatarDashboardControllerTest.java`

Changes:

- replace `.avatar-row-micro-controls` as the primary row chrome with a dedicated row decoration container rendered above the row;
- adjust DOM order and z-index so row decorations sit above widget edit controls;
- keep empty-row affordances compact and compatible with the new decoration language.

Acceptance:

- edit-mode markup contains the new row decoration structure;
- screenshot review shows row move controls no longer hidden behind widget chrome.

### Step 5: Add Avatar tab fragments and panel content

Files:

- `AvatarDashboardController.java`
- `AvatarDashboardComponents.java`
- possibly a small extracted helper component file if that keeps the render logic readable
- `AvatarDashboardControllerTest.java`

Changes:

- add fragment routes for each Avatar tab;
- add a tab rendering helper consistent with agent page tab behavior;
- render non-dashboard tabs from existing services:
  - queue
  - history
  - profile
  - outputs
  - work-areas
- ensure non-dashboard tabs never expose layout-edit controls.

Gotchas:

- avoid duplicating large portions of `OrchestrationController`; compose patterns, not entire implementations;
- if the same presentation logic genuinely repeats, extract reusable component helpers rather than copying.

### Step 6: Implement narrow divider-resize JS and persistence

Files:

- `src/main/resources/static/js/avatar-shell.js` (new)
- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`
- `AvatarDashboardController.java`

Changes:

- add a minimal desktop-only divider handle;
- update rail width through a CSS custom property during drag;
- clamp width within a planned min/max range;
- persist width at drag end to browser-local state;
- restore persisted width on initial render and after HTMX updates without interfering with mobile.

Gotchas:

- avoid re-binding drag listeners repeatedly after HTMX swaps;
- width persistence should survive tab swaps because the rail is outside the swapped panel;
- width persistence should survive reload because it is stored in browser-local state.

### Step 7: Update tests for shell, tabs, and local state contract

Files:

- `AvatarDashboardControllerTest.java`

Changes:

- add assertions for:
  - unified shell structure;
  - presence of `#avatar-tab-panel`;
  - absence of Organizer and manual refresh controls;
  - presence of row decoration markup in edit mode;
  - route coverage for queue/history/profile/outputs/work-areas fragments;
  - correct shell selectors and tab URL behavior where feasible in controller tests.

### Step 8: Update docs and deferred-work artifacts

Files:

- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- optional new technical note if divider persistence deserves it
- `.internal-dev/focus/unfinished-work.md`
- any required changelog/knowledge closeout files when implementation actually lands

Changes:

- document the new shell, tabs, persistent chat rail, dashboard-only editing, and removed controls;
- record deferred auto-refresh work explicitly in `.internal-dev/focus/unfinished-work.md` if implementation lands without it;
- if this planning artifact itself is the only deliverable now, at minimum capture the deferred follow-up in the focus file per user request.

## 7. Validation Plan

### Automated

- Run focused Avatar controller tests covering shell and fragment routes.
- Run the relevant web/controller test suite for changed routes.

### Startup / Wiring

- Run a bounded Spring startup smoke test after route wiring and JS/CSS asset registration changes.

Suggested command:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

If an isolated data root or required local config is needed, make that explicit in the execution notes before sign-off.

### Playwright Validation

Delegate browser validation to a `gpt-5.3-codex` agent with reasoning `high`.

Required scenarios:

- `/avatar` desktop default render
- `/avatar?edit=true` desktop render
- tab switch across all Avatar tabs without full page reload
- chat rail remains mounted while tab panel changes
- desktop divider drag updates width and width persists after tab switch and reload
- mobile `/avatar` stacked layout with no visible drag handle
- edit-mode row decoration visible above widget controls
- visual comparison against `/agents` for density, tab styling, panel rhythm, borders, spacing, and overall operational coherence

Required artifacts:

- screenshots for desktop and mobile
- screenshot of edit mode with row decoration
- short written critique of visual consistency and any remaining gaps

### Acceptance Criteria

- No top-level Organizer or manual refresh control remains in the Avatar shell.
- Dashboard edit entry is compact and visually consistent with agent operational controls.
- Non-dashboard tabs swap in place and preserve the chat rail.
- Desktop divider width persists across tab switches and reload.
- Mobile stacks cleanly without overflow and without a drag handle.
- Row move controls visibly render above widget edit chrome.

## 8. Handoff Checklist

- Start implementation on a dedicated feature branch before phase work.
- Keep code edits serial; do not overlap writes to `AvatarDashboardController`, `AvatarDashboardComponents`, or `avatar-dashboard.css`.
- Use URL state and browser-local state for shell behavior instead of adding new server-backed shell persistence.
- Reuse agent tab styling and interaction patterns where possible.
- Keep JS limited to drag/resize persistence and existing chat behavior.
- Preserve dashboard-only layout editing; do not leak edit controls into other tabs.
- Remove visible manual refresh now and record auto-refresh as deferred follow-up work.
- Run tests, startup validation, and delegated Playwright review before closeout.
- Update docs and `.internal-dev` deferred-work state when implementation completes.

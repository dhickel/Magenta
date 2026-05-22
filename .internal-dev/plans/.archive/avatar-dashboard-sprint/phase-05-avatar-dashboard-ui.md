# Phase 05 - Avatar Dashboard UI

## Context

Magenta has `/chat` for full chat and `/dashboard` for operational monitoring. Avatar needs `/avatar`, a personal dashboard distinct from `/dashboard`, with Avatar chat always present and first-party widgets backed by Avatar and orchestration services. UI work must follow SimplyPages and HTMX-first conventions.

Relevant anchors:

- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatModuleRenderer.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OutputController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/static/js/orchestration/agent-chat.js`
- `src/main/resources/static/css/orchestration.css`
- SimplyPages docs under `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs`

## Goal

Add a rich `/avatar` personal dashboard using server-rendered SimplyPages components, HTMX fragment routes, a compact Avatar chat surface, and reusable widget render helpers. The page should not duplicate the operational dashboard or embed the full `/chat` client blindly.

## In Scope

- New `/avatar` route and fragments.
- Always-present compact Avatar chat.
- HTMX edit mode for rows, columns, widget insertion/removal, widget movement, enabled state, and sizes.
- Widgets:
  - daily tasks;
  - todos;
  - calendar;
  - notes;
  - file/output viewer;
  - mini agent/system overview;
  - inbox/alerts;
  - recent work.
- Avatar-specific CSS and minimal JS only where needed for SSE chat.
- Navigation link updates.

## Out of Scope

- Replacing `/dashboard`.
- Full arbitrary filesystem browser.
- Raw HTML-heavy workarounds when SimplyPages docs/demos provide a clean component pattern.
- Full `/chat` client reuse unless the `/avatar` DOM satisfies its assumptions.
- Plugin-powered widgets in this sprint.

## Implementation Steps

1. Add a dedicated controller.
   - Create `AvatarDashboardController` in `io.mindspice.magenta2.api.web`.
   - Keep it separate from `OrchestrationController`.
   - Inject read services and Avatar layout/service dependencies.
   - Keep controller methods thin.

2. Add render helpers.
   - Create `AvatarDashboardComponents`.
   - Render shell body, compact chat, widget wrappers, edit controls, empty states, and modal fragments.
   - Use stable IDs:
     - `avatar-widget-daily-tasks`
     - `avatar-widget-todos`
     - `avatar-widget-calendar`
     - `avatar-widget-notes`
     - `avatar-widget-files`
     - `avatar-widget-outputs`
     - `avatar-widget-system`
     - `avatar-widget-alerts`
     - `avatar-widget-recent-work`

3. Add route contracts.
   - `GET /avatar`: full page shell.
   - `GET /avatar/_widgets`: current widget grid.
   - `GET /avatar/_widgets/{widgetKey}`: one widget fragment.
   - `GET /avatar/_edit`: edit modal/container.
   - `PUT /avatar/_layout`: save layout and return grid/modal swaps.
   - Widget-specific HTMX endpoints for organizer CRUD, refresh, output preview, and alert actions.

4. Add assets.
   - `src/main/resources/static/css/avatar-dashboard.css`.
   - `src/main/resources/static/js/avatar-chat.js` for compact Avatar SSE only.
   - Do not load `chat-client.js` on `/avatar` unless deliberately satisfying full `/chat` DOM dependencies.

5. Wire navigation.
   - Add `/avatar` to portal/home navigation.
   - If linked in operational shell, put it in a clear personal section and keep `/dashboard` operational.

6. Render widgets from services.
   - Todos/daily/calendar/notes use Avatar services.
   - Outputs and file viewer use `OutputArtifactService` and existing chat-file services only; no raw path browsing.
   - System overview uses existing agent/runtime services.
   - Alerts use inbox/reaction/event services.
   - Recent work uses assignments/jobs/tasks/outputs.

7. Use HTMX-first editing.
   - Row/column/widget movement can be up/down/left/right HTMX buttons.
   - Use drag/drop JS only if it is clearly simpler and remains narrowly scoped.
   - Preserve alpha credential/CSRF behavior for unsafe routes.

8. Update docs and closeout.
   - End-user `/avatar` docs.
   - API docs for fragment routes if the docs track HTML fragment contracts.
   - Changelog and validation evidence.

## Validation

Focused tests:

- `/avatar` renders page shell and compact chat.
- All widget roots are present.
- `/dashboard` remains operational and distinct.
- HTMX fragment routes return expected stable targets.
- Edit layout rejects unknown widget keys and persists valid layouts.
- Organizer widget CRUD routes call Avatar services.
- Output/file preview stays confined to existing artifact/chat-file APIs.
- `avatar-chat.js` is loaded only for `/avatar` and routine widget editing uses HTMX attributes.

Commands:

- `mvn -Dtest=AvatarDashboardControllerTest,FrontendControllerTest,OrchestrationControllerTest test`
- `mvn -Dtest=OperationalUiContractControllerTest,OutputControllerTest test`
- `mvn test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

Browser validation:

- Required for this phase.
- Must be run by a validation subagent using `gpt-5.3-codex` medium.
- Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before live chat/SSE checks.
- Capture desktop and mobile screenshots of `/avatar`.
- Validate edit modal open/save, widget refresh, organizer CRUD, output preview, alerts, and compact chat send/error behavior.
- Verify HTMX swaps occur and `/webjars/htmx.org/dist/htmx.min.js` loads.

## Exit Criteria

- `/avatar` is usable as the first screen for Avatar work.
- Avatar chat is always present.
- Dashboard editing is HTMX-first.
- Visual/browser validation shows no layout overlap or broken interaction.

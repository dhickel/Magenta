# 2026-05-13 Operational UI Parity Fixes Pass 02

## Scope
- Continued implementation for operational UI contract parity, focusing on HTMX-first orchestration UX and runtime execution correctness.

## Implemented
- Added plan submit input coercion and runtime payload forwarding (`inputValues`) for typed plan inputs.
- Added plan list-item inline update endpoints (`PUT /plans/_editor/{planId}/{deliverables|steps|validation|assumptions}`) and persisted updates.
- Added workflow node inline-edit persistence support for node type, planId, message template, and resume policy.
- Added job item bindings support (`bindingsJson`) to the job editor and parser.
- Converted settings to HTMX form submit (`PUT /settings`) with server-populated model lists.
- Converted inbox and outputs pages to server-rendered HTMX fragments and action handlers.
- Added dashboard side recent-events feed (`GET /dashboard/_side-events`) and connected dashboard panel.
- Added job deep-link page (`GET /jobs/{jobId}`) that preloads the job editor.
- Scoped agent outputs tab to the selected agent’s jobs (instead of global outputs).
- Upgraded agent workspace tab to render workspace metadata/links (when workspace service is available).
- Added job run work-item progress tracking in `OrchestrationRunnerService` with item RUNNING/COMPLETED/FAILED updates and child run IDs.

## Validation
- `mvn -q -DskipTests compile`
- `mvn -q -Dtest=OrchestrationControllerTest,FrontendControllerTest test`
- `mvn -q test`
- `timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0` (startup succeeded; timeout exit 124 expected)

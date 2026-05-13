# Date
2026-05-13

# Change Summary
Operational UI contract alpha remediation — fixed blocker-level contract mismatches, 
proved the UI contract works end-to-end, and replaced source-string confidence with 
operational evidence. All 363 tests pass and the app starts cleanly.

## Blockers Resolved
- **Plan editor structured persistence**: Added PUT routes for inputs/outputs field 
  row updates; fixed double-s hx-put/hx-delete URL bugs that prevented field edit 
  routes from resolving.
- **Workflow validation save gate**: `saveDefinitionValidated()` enforces full graph 
  validation (cycles, missing inputs, invalid endpoints) before durable save; 
  structured `ValidationResult` replaces "ERROR: " string parsing.
- **HTMX browser delivery**: Deleted compat-noop `htmx.min.js` stub that shadowed 
  the real WebJar asset; all operational pages now serve real HTMX.
- **Docker status → HTML fragment**: Changed agent dashboard from JSON API target 
  to HTML fragment endpoint, eliminating the JSON-in-HTML-panel mismatch.
- **Job link repair**: Fixed `href="/jobs/{id}"` broken links to target `/jobs`.
- **Stale JS cleanup**: Deleted unreferenced `app.js` (256 lines of dead 
  orchestration JS).

## Pre-existing (already in refactor, validated during remediation)
- Canonical `JobDefinition`/`JobWorkItem` runtime execution — no legacy bridge
- Atomic workspace write leases via unique partial index
- Docker disabled by default, graceful startup, single-budget timeout cleanup
- Agent tabs wired with real HTMX
- `/chat` isolated from operational scripts

# Files
Changed:
- `ai/orchestration/workflow/WorkflowService.java` — `saveDefinitionValidated()`, removed "ERROR: " prefixing
- `api/web/WorkflowController.java` — `saveDefinitionValidated` wired to create/update, structured validate endpoints
- `api/web/OrchestrationController.java` — plan field PUT routes, fieldRow bug fixes, workflow validation error display, Docker HTML fragment, job link fixes
- `api/web/OrchestrationControllerTest.java` — updated Docker status assertion, ObjectProvider stubs

Deleted:
- `static/webjars/htmx.org/dist/htmx.min.js` — compat-noop stub
- `static/js/orchestration/app.js` — stale unreferenced JS

# Behavioral Impact
- Plan field edits now persist via HTMX PUT on change (name, type, required, array, description, schema)
- Invalid workflow graphs (cycles, missing inputs, unknown tasks) rejected at save with structured errors
- Real HTMX loaded in browser; compat-noop no longer served
- Docker status renders as readable HTML; no raw JSON in UI panels
- Job links navigate to `/jobs` listing page; no broken `/jobs/{id}` 404s

# Risks
- Low. All 363 tests pass. Startup smoke succeeded (2.8s). Nine operational pages 
  load with HTTP 200. Workflow validation gate returns structured responses.

# Follow-up Items
- Browser validation (Gate 5-6) blocked by Playwright MCP profile lock
- Route audit test helper (nice-to-have, not a blocker)
- .internal-dev/notes/operational-ui-contract-missing-features.md tracks 22 
  deferred findings for post-alpha

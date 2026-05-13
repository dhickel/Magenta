# Phase 07 - Validation And Rollout

## Context

This refactor touches web routes, API contracts, HTMX interaction flows, targeted JavaScript enhancements, persistence-backed runtime services, and operational UX. Validation must prove both code correctness and live-browser usability.

## Goal

Provide a strict validation path for the full refactor so the implementation does not stop at passing unit tests while browser/API contracts remain broken.

## In Scope

- Automated tests.
- Startup smoke.
- Browser validation through Playwright MCP workflow when live UI/SSE/chat behavior is involved.
- Regression checks for `/chat` isolation.
- `.internal-dev` closeout after implementation.

## Out of Scope

- Implementing this plan.
- Production deployment procedure.

## Implementation Steps

1. Add focused tests by phase.
   - Phase 01:
     - Project controller contract tests.
     - Job controller item/event/output tests.
     - Dashboard summary API tests.
     - Output search API tests.
     - Inbox response tests.
   - Phase 03:
     - Worktype profile mapping and prompt append tests.
     - Plan field editor payload tests at controller/service layer.
   - Phase 04:
     - Workflow graph validator and runner tests.
   - Phase 05:
     - Job/project integration tests.
   - Phase 06:
     - Agent summary and Docker status tests.

2. Add browser validation fixtures.
   - Use real wire enum values, for example `QUEUED` where backend expects it.
   - Seed agents, project, task template, workflow, job, inbox message, and output artifacts.
   - Validate with the same API routes the UI uses through HTMX and any approved JS interactions.

3. Add HTMX-first compliance checks.
   - For each page (`/dashboard`, `/plans`, `/workflows`, `/jobs`, `/projects`, `/agents`, `/inbox`, `/outputs`), verify normal create/edit/delete/filter/submit flows are HTMX-driven.
   - If a flow uses JavaScript, document why it was the path of least resistance and why HTMX-only would be materially more complex.
   - Ensure JS is scoped to targeted enhancements (for example state-heavy workflow interactions, SSE/event handling, or focused UX affordances), not broad page transport rewrites.

4. Run standard verification.
   - `mvn test`
   - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
   - If Docker is required for startup and unavailable, report that explicitly with Docker host/image details.

5. Browser validation checklist.
   - Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before Playwright work if chat, SSE, agent/model routing, concurrent interaction, or live workflow behavior is validated.
   - Validate desktop and mobile widths.
   - Pages:
     - `/dashboard`
     - `/plans`
     - `/workflows`
     - `/jobs`
     - `/projects`
     - `/agents`
     - `/agents/{agentId}`
     - `/inbox`
     - `/outputs`
     - `/chat`
   - Check:
     - no console errors;
     - no 404 API calls from visible controls;
     - normal operational UI actions issue HTMX requests for transport unless explicitly justified otherwise;
     - no text overlap;
     - structured editors preserve arrays/lists/schema values;
     - submit-to-agent creates assignment;
     - direct run controls are gone from plan/workflow normal UI;
     - `/chat` loads original chat client and remains functional.

6. Internal-dev workflow after implementation.
   - Changelog in `.internal-dev/changelogs/`.
   - Reusable knowledge in `.internal-dev/knowledge/` for:
     - dashboard API contract;
     - workflow route model;
     - worktype profile prompt behavior.
   - Bugs discovered out of scope in `.internal-dev/bugs/`.
   - Deferred items in `.internal-dev/notes/future_features.md`.
   - Archive finalized plan artifacts only when the implementation is complete and accepted.

## Validation

The validation for this phase is the aggregate of all phase validation gates plus the browser checklist above.

## Exit Criteria

- All relevant automated tests pass.
- Spring Boot context starts in bounded smoke.
- Browser validation proves the operational UI works across the main routes.
- `/chat` remains isolated.
- `.internal-dev` closeout artifacts are written after implementation.

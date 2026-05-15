# Phase 06 - Final Validation Gate

## Context

This is a validation-only phase. It must run after phases 01-05 are implemented and merged. It should not silently patch production code except for test fixture fixes or documentation corrections approved by the orchestrator.

## Goal

Prove the alpha operational UI works end to end in a real browser, with Docker/Podman and model-backed execution validated when local dependencies are available. If a required dependency is unavailable, stop and report the blocker instead of treating unit tests as a substitute.

## In Scope

- Full automated test run.
- Spring Boot startup smoke.
- Playwright MCP browser validation.
- Docker/Podman runtime validation.
- `.internal-dev` closeout after implementation.

## Validation Steps

1. Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before browser/chat/SSE validation.
2. Run focused tests from each phase:
   - plan editor tests;
   - workflow validator/editor tests;
   - project/agent controller tests;
   - runtime settings/model override tests;
   - Docker status/runtime tests.
3. Run `mvn test`.
4. Run bounded startup:
   - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
5. If Docker/Podman is required by the touched behavior, validate with the configured local runtime. Do not mark Docker-related fixes complete if the daemon/socket is unavailable; record the blocker and stop for user direction.
6. Playwright MCP browser validation:
   - `/dashboard`: no broken freshness, message count wording, accordion system chat, section visual separation, active nav correct.
   - `/plans`: create/edit/reload every plan field class, New Plan Chat launch works through existing plan chat path.
   - `/workflows`: build the adapter-chain scenario from phase 03, validate, save, reload.
   - `/projects`: manager type label, agent dropdowns, constraints visible.
   - `/agents`: profile tab under top module, no horizontal overflow, Docker status actionable, agent chat accordion sends to the selected agent, active tab state moves.
   - `/settings`: model override dropdowns save and reload.
7. Capture console and network evidence for browser runs. Any console error from touched pages is a blocker unless proven unrelated.
8. Confirm JavaScript usage:
   - JS is justified for live SSE chat and any client graph-state affordance;
   - standard CRUD and filters remain HTMX-first.

## Closeout

After validation passes:

- Write a changelog in `.internal-dev/changelogs/`.
- Add reusable findings to `.internal-dev/knowledge/`.
- Add only user-approved deferred work to `.internal-dev/notes/alpha-deferred-targets.md`.
- Archive this plan suite only after validation passes and blockers are resolved.

## Exit Criteria

- All listed user notes are fixed or explicitly blocked with evidence.
- No new missing functionality is hidden in chat-only summaries.
- Browser validation covers real user flows, not only static route loads.
- Alpha signoff is not based on unit-only or curl-only validation.


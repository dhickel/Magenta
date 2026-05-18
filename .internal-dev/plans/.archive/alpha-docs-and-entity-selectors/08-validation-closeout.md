# Validation And Closeout

## Context

This effort changes docs, root policy, backend web endpoints, reusable UI components, and multiple operational UI forms. Validation must prove both technical correctness and user-facing behavior.

## Goal

Define the final validation matrix and `.internal-dev` closeout requirements.

## In Scope

- Focused unit/controller/component tests.
- Full `mvn test`.
- Bounded Spring startup smoke.
- Focused Playwright validation through a subagent.
- Documentation review.
- `.internal-dev` changelog, knowledge, bug logging, and plan archival.
- Final commit.

## Out of Scope

- Full production-style browser campaign unless the user explicitly approves it.
- External docs publishing.

## Validation Steps

1. Focused automated tests by phase.
   - Documentation-only phase: markdown link/index checks if available, plus manual source-reference review.
   - Selector backend: entity lookup service and fragment controller tests.
   - Selector component: rendered HTML/HTMX attribute tests.
   - Selector integrations: controller/render tests for changed forms and validation failures.

2. Full automated tests.
   - Run `mvn test`.
   - Use `gpt-5.3-codex` `medium` for testing subagent execution if delegated.

3. Bounded startup smoke.
   - Preferred command:
     ```bash
     timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
     ```
   - If required secrets/services block startup, stop and report the exact dependency.

4. Focused Playwright validation subagent.
   - Must run against the live app.
   - Must read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before browser validation if the changed flow involves live chat, SSE, agent/model routing, planning, interruption, chat switching, or concurrent interaction.
   - Scope:
     - `/plans`: submit form selector behavior.
     - `/workflows`: submit form selector behavior.
     - `/jobs`: project selector, add plan item, add workflow item, schedule selectors.
     - `/agents`: submit target selector and schedule/reaction selectors.
     - `/settings`: default agent selector.
   - Verify HTMX remains the main transport and any JS is limited to combobox affordances.

5. Documentation review.
   - Confirm every docs page is linked from an index.
   - Confirm every exposed API family has a documentation entry.
   - Confirm end-user docs reflect selector UI, not old manual ID entry.
   - Confirm technical docs mention the selector backend/component if implemented.
   - Confirm root `AGENTS.md` and `docs/AGENTS.md` both encode docs maintenance policy.

6. `.internal-dev` closeout.
   - Write changelog:
     - `.internal-dev/changelogs/<date>-alpha-docs-and-entity-selectors.md`
   - Write knowledge notes:
     - selector architecture and docs governance lessons.
   - Log any out-of-scope bugs immediately under `.internal-dev/bugs/`.
   - Ask before recording deferred future ideas under `.internal-dev/notes/`.
   - Move this plan directory to `.internal-dev/plans/.archive/alpha-docs-and-entity-selectors/` only after all required work is complete.

7. Commit closeout.
   - Commit implementation, docs, tests, and `.internal-dev` updates together for the final phase.
   - Include phase commits before that according to `00-orchestration-plan.md`.

## Acceptance Criteria

- `mvn test` passes.
- Bounded Spring startup succeeds or a blocking dependency is explicitly reported.
- Focused Playwright subagent reports successful selector interactions on changed pages.
- Documentation review finds no missing alpha route family.
- No blind manual ID-entry field remains in the scoped operational flows unless a file-level note documents why it is intentionally still manual.

## Exit Criteria

- The branch is ready for user review or PR creation.
- The plan suite is archived after completion, not before.


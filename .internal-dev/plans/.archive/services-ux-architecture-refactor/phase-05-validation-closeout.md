# Phase 05: Validation And Closeout

## Context

The refactor changes backend contracts, execution routing, output attribution, and operational UI. Final sign-off requires automated tests, application startup smoke, Playwright browser validation, docs, `.internal-dev` workflow artifacts, and final review.

## Goal

Prove the implemented phases work together, document the changed behavior, record reusable knowledge, and prepare the branch for final review/merge without unrelated dirty files.

## In Scope

- Full Maven regression.
- Spring Boot context smoke.
- Playwright MCP subagent validation with screenshots.
- Documentation updates.
- Package guide updates where responsibilities changed.
- `.internal-dev` changelog, knowledge, notes, and bug reports as needed.
- Final xhigh architecture/code/UX review.
- Phase and closeout commits.

## Out of Scope

- New feature work after validation begins, except remediation required by failing tests/review.
- Broad Playwright campaigns outside changed surfaces unless user approves.
- Filing GitHub Issues without asking the user first.

## Implementation Steps

1. Confirm `git status --short` and identify unrelated dirty files to avoid.
2. Run all phase-specific tests from phases 1-4 if not already run after the latest changes.
3. Run full regression:

```bash
mvn test
```

4. Run bounded Spring context smoke:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-services-ux-smoke.sqlite?foreign_keys=true'
```

5. Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before browser validation.
6. Launch the application for Playwright with isolated SQLite:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-services-ux-playwright.sqlite?foreign_keys=true --magenta.executor.chat-threads=4'
```

7. Have a validation subagent using `gpt-5.3-codex` with reasoning effort `medium` run focused Playwright checks:

```bash
MAGENTA_PLAYWRIGHT_BASE_URL=http://localhost:18080 npx playwright test tests/playwright/public-alpha-harness.spec.js
```

8. Ensure the Playwright subagent also performs the manual browser scenarios named in phase 4 and captures screenshots for changed surfaces.
9. Review screenshots for layout overflow, hidden controls, misleading labels, and inconsistent provenance.
10. Update docs:
    - `docs/technical/workspaces-tools-outputs.md`
    - `docs/technical/orchestration-runtime.md`
    - `docs/technical/services.md`
    - relevant API docs for new/changed params and DTOs
    - `docs/end-user/projects-and-workspaces.md`
    - `docs/end-user/jobs.md`
    - output/operational docs if present and affected
11. Update package `AGENTS.md` files only when package responsibility or public surface changed.
12. Complete `.internal-dev` workflow:
    - changelog in `.internal-dev/changelogs/`.
    - reusable knowledge in `.internal-dev/knowledge/`.
    - deferred ideas in `.internal-dev/notes/` after confirming out of scope.
    - bug reports in `.internal-dev/bugs/` for out-of-scope bugs, then ask user before filing GitHub Issues.
13. Run `git diff --check`.
14. Request final xhigh architecture/code/UX review. Remediate blocker findings, then rerun impacted tests.
15. Commit final closeout artifacts and implementation changes, keeping unrelated dirty files out of the commit.

## Validation

Required final validation commands:

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-services-ux-smoke.sqlite?foreign_keys=true'
git diff --check
```

Required browser validation:

- Run by subagent, not inline.
- Use MCP-first workflow from `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`.
- Use isolated SQLite.
- Capture console/network diagnostics.
- Capture desktop/mobile screenshots for changed `/projects`, `/jobs`, `/outputs`, plan submit, workflow submit, and agent submit/history surfaces.
- Treat inability to run expected Playwright checks as a blocker unless the user explicitly approves a fallback.

Final acceptance criteria:

- All tests pass or failures are documented as pre-existing/user-approved blockers.
- Spring context starts successfully.
- Browser validation passes on changed surfaces.
- Docs describe `projectId` versus `workspaceId`, job assignment/run identity, persistent job workspaces, active mutation policy, and output provenance.
- `.internal-dev` changelog/knowledge/bug/notes workflow is complete.
- Final review has no blocker findings.
- Commits are scoped and exclude unrelated dirty files.

## Exit Criteria

- The branch has validated serial phase commits and a final closeout commit.
- The implementation is documented for end users and future agents.
- Any residual risks are explicit in final notes and not hidden behind passing unit-only validation.

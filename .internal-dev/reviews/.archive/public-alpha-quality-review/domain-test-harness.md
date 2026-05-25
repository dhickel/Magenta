# Domain Test Harness Review

## Agent

- Agent: domain-test-harness
- Agent id: `019e3723-63b2-73e3-8280-ccd01ebf5250`
- Model / reasoning: GPT-5.5 Codex high
- Mode: read-only

## Scope

Reviewed existing Maven tests, Spring Boot startup/test config, Playwright feasibility, fixture/data isolation, and coverage gaps against public-alpha routes.

## Files Reviewed

- `.internal-dev/AGENTS.md`
- Package guides for app root, web API, orchestration, chat plan, and workspaces
- `src/test/**`
- Surefire reports
- `src/main/resources/application.yml`
- `src/test/resources/application.yml`
- Public route inventory
- Playwright/package files

## Commands and Probes

- `rg --files`
- Route mapping scans
- Test annotation scans for `@SpringBootTest`, `@WebMvcTest`, `MockMvc`, `TestRestTemplate`
- Surefire report aggregation
- Playwright availability check
- Targeted source reads
- `git status --short`

## Findings

- High: public-alpha REST/SSE API groups have broad route coverage gaps; many controllers have no dedicated tests.
- High: no Spring web/application-context test coverage was found; current web tests mostly instantiate controllers directly.
- Medium: test config disables schedules/reactions while production enables them.
- Medium: SQLite fixture isolation often omits `foreign_keys=true`.
- Medium: Playwright is feasible but not yet a reusable checked-in harness.
- Medium: existing string tests missed a likely HTMX target defect in agent lifecycle UI.

## Explicitly Ruled Out

- Existing Surefire reports are clean.
- No disabled JUnit tests were found by annotation scan.
- Docker/Podman is not a current runtime validation gate for this campaign; stale Docker identifiers remain UI/docs quality issues.

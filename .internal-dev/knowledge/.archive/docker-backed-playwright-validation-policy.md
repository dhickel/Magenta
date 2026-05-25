# Topic

Docker-backed Playwright validation policy for Magenta alpha readiness

# Source References

- `.internal-dev/plans/docker-backed-alpha-e2e-validation/`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`
- `.internal-dev/knowledge/docker-runtime-host-setup-and-prereqs.md`
- `.internal-dev/notes/2026-05-13-phase-05-live-docker-validation-blocked.md`

# Key Takeaways

- Docker is the production execution environment for Magenta agents, so agent/task/workflow/job validation is not complete unless Docker-backed execution is exercised.
- Playwright is the primary validation harness for user-facing alpha behavior. Endpoint-only, curl-only, or unit-test-only evidence can support a finding but cannot replace browser-origin proof.
- If Docker/Podman or Playwright is blocked, the correct action is to stop and report the blocker with exact evidence. Do not silently substitute host execution or narrower API tests.
- For operational flows, Playwright should use normal UI controls first. Browser-origin `fetch` is acceptable only when validating the same same-origin endpoint that the UI intentionally calls or when the feature is API-first.
- Validation evidence must include console failures, failed network responses, screenshots or DOM state for major failures, and persisted-state checks after navigation/reload.

# Engine Relevance

Future alpha validation agents should treat Docker and Playwright readiness as gates before testing plans, tasks, workflows, jobs, projects, inbox, outputs, workspaces, model overrides, or agent chat. A missing daemon, missing image, browser MCP failure, or app startup failure is a blocker, not a deferrable feature gap.

# Open Questions

- Should the repository include a committed Playwright test project for these alpha flows, or should this remain MCP-driven exploratory validation until the UI stabilizes?
- Should CI grow a daemon-backed validation lane for `magenta.docker.live=true` plus headless browser tests?

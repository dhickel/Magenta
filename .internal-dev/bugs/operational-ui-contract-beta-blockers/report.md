# Summary

Final validation review of the operational UI contract refactor found multiple blocker-level issues that must be resolved before the plan suite is archived or the work is considered beta-ready.

# Scope

Operational UI contract refactor surfaces:

- dashboard
- plans
- workflows
- jobs
- projects
- agents
- inbox
- outputs
- Docker/runtime status
- `/chat` isolation validation

# Reproduction

Review the current working tree and supporting review artifacts:

- `.internal-dev/reviews/2026-05-12-operational-ui-contract-ui-htmx-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-backend-contract-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-beta-readiness-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-test-coverage-review.md`

Then run:

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Browser validation should then exercise visible controls on `/dashboard`, `/plans`, `/workflows`, `/jobs`, `/projects`, `/agents`, `/agents/{agentId}`, `/inbox`, `/outputs`, and `/chat`.

# Expected

The operational UI should be archive-ready only when:

- real HTMX is served and active in browser;
- visible controls target live routes;
- plan/job/workflow edits persist and execute through the same backend contracts users edited;
- Docker status renders as UI;
- `/chat` remains isolated and functional;
- automated, startup, route, and browser validation all pass.

# Actual

Current review findings identify these blockers:

- HTMX browser execution may still be blocked by a checked-in noop asset.
- Agent detail tabs render as controls but are not wired to HTMX or JavaScript.
- Docker status is HTMX-swapped from a JSON endpoint into an HTML panel.
- Dashboard/agent job links target a removed `/jobs/{jobId}` GET route.
- `JOB_RUN` submit-to-agent still crosses public `JobDefinition` and legacy `OrchestrationJob` models, so edited job items are not proven to execute.
- Plan structured edit controls emit unmapped or non-persisting edit actions.
- Workflow graph validation exists but is not enforced as the durable save gate.

# Evidence

Evidence is recorded in:

- `.internal-dev/reviews/2026-05-12-operational-ui-contract-final-validation-plan.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-ui-htmx-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-backend-contract-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-beta-readiness-review.md`
- `.internal-dev/reviews/2026-05-12-operational-ui-contract-test-coverage-review.md`

# Impact

High. The refactor can pass source/string tests while browser controls fail, and users can create or submit operational records that do not reliably map to the runtime path. This blocks plan archival and beta readiness.

# Status

Open.

# Next Action

Create and execute a remediation plan for the blockers listed in `.internal-dev/reviews/2026-05-12-operational-ui-contract-final-validation-plan.md`, then rerun the full validation gates before archiving `.internal-dev/plans/operational-ui-contract-refactor/`.

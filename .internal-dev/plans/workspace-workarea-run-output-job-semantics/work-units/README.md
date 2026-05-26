# Work Units

## Dispatch Order

1. `phase-01-contract-docs-guidance` and `phase-02-layout-schema-workareas` may begin in parallel after the main thread creates the dedicated branch.
2. `phase-03-runtime-tools-output-promotion` depends on Phase 02.
3. `phase-04-api-ui-browser-surfaces` depends on Phases 02 and 03, though API payload work may begin after the Phase 02 schema names are committed.
4. `phase-05-dev-reset-integration-closeout` depends on Phases 01-04 passing unit validation.
5. Full `mvn test`, bounded startup, integration validation, and final Playwright proof must wait until Phase 05 directory restructuring/reset is complete.

## Unit Summary

| phase | unit | worker | validator | parallelism |
| --- | --- | --- | --- | --- |
| 01 | Contract, docs, and agent guidance lock | implementation_worker_agent, same session for remediation | validation_redteam_agent, same session for remediation | Parallel with Phase 02 |
| 02 | Static layout source, schema fields, Work Area path model | implementation_worker_agent | validation_redteam_agent | Parallel with Phase 01, blocks 03/04 |
| 03 | Runtime execution, tool aliases, output promotion, job semantics | implementation_worker_agent | validation_redteam_agent | Depends on 02 |
| 04 | API/UI browser surfaces and controller tests | implementation_worker_agent | validation_redteam_agent, then Playwright agent checklist | Depends on 02/03 |
| 05 | Development migration/reset, final tests, closeout artifacts | implementation_worker_agent | validation_redteam_agent plus integration validator | Depends on 01-04 |

## Remediation Policy

- Use the same worker session for remediation on a unit unless the worker violated boundaries or the fix moves to a different domain.
- Use the same validator session to revalidate failed criteria unless criteria changed, more than two failed cycles occurred, or remediation changed the risk profile.
- If validation failure exposes flawed or ambiguous plan criteria, return to `advanced_planning_agent` before more code changes.

## Browser Validation Scope

Browser validation applies only after code-level validation for Phase 04 and final integration:

- Work Areas browse/edit route.
- Project browse/edit route.
- Task/workflow submission controls that require run display name.
- Outputs/job-bound output views affected by routing changes.
- Desktop and mobile screenshots with visual critique.


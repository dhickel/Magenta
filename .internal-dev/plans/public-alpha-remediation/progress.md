# Public Alpha Remediation Progress

## Status Legend

- `planned`: plan exists, implementation not started.
- `in_progress`: branch or subplan implementation active.
- `blocked`: cannot proceed without a user or dependency decision.
- `validating`: implementation complete and validation agent running.
- `passed`: validation gate passed.
- `committed`: implementation plus `.internal-dev` closeout committed.

## Domain Progress

| Domain | Branch | Status | Current Owner | Validation Gate | Notes |
| --- | --- | --- | --- | --- | --- |
| `01-security-access-control` | `public-alpha-remediation/security-access-control` | passed | Codex | passed: focused tests, full `mvn test`, bounded startup, live auth/CSRF and workflow XSS probes | Ready to merge into integration. Evidence: `/tmp/magenta-security-domain-live-3317476.log`, `/tmp/magenta-security-domain-live-extra-3318140.log`, `/tmp/magenta-security-domain-playwright-3317476.log`. |
| `02-workspace-tools-outputs` | `public-alpha-remediation/workspace-tools-outputs` | in_progress | Codex | subplan 02 passed | Subplan 03 web fetch redirect SSRF active next. |
| `03-execution-history-streams` | `public-alpha-remediation/execution-history-streams` | planned | unassigned | planned | Owns public execution contract and transcript preservation. |
| `04-workflow-authoring-runtime-js` | `public-alpha-remediation/workflow-authoring-runtime-js` | planned | unassigned | planned | Must preserve HTMX-first CRUD and narrow JS graph behavior. |
| `05-schema-data-ownership` | `public-alpha-remediation/schema-data-ownership` | planned | unassigned | planned | Should run before runtime/workspace lease validation if possible. |
| `06-operational-ui-htmx-mobile` | `public-alpha-remediation/operational-ui-htmx-mobile` | planned | unassigned | planned | Requires focused Playwright mobile/HTMX validation. |
| `07-validation-harness-regression` | `public-alpha-remediation/validation-harness-regression` | planned | unassigned | planned | Adds regression harnesses for prior blockers. |
| `08-code-quality-stale-cleanup` | `public-alpha-remediation/code-quality-stale-cleanup` | planned | unassigned | planned | Should run after functional domains to avoid conflict churn. |

## Finding Progress

| Finding | Domain | Status | Subplan | Validation Evidence | Commit |
| --- | --- | --- | --- | --- | --- |
| bug-01 | `01-security-access-control` | passed | `subplan-01-auth-csrf-gate.md` | validator: `mvn -Dtest=AlphaSecurityConfigurationTest,FrontendControllerTest test`; live app curl smoke for public reads, unauth mutation 401, HTMX CSRF 403 fragment, auth+CSRF controller reachability | `bf10a85` |
| bug-02 | `01-security-access-control` | passed | `subplan-02-id-segment-validation.md` | validator: `mvn -Dtest=PlainPathSegmentValidatorTest,AgentProfilePathSegmentValidationTest,WorkspacePathSegmentValidationTest,AgentProfileControllerTest test`; `mvn -Dtest=WorkspaceControllerTest test`; confirmed validator, agent profile, workspace, directory, and controller 400 coverage | `b1418f4` |
| bug-03 | `04-workflow-authoring-runtime-js` | planned | `subplan-01-draft-editing-validation.md` | pending | pending |
| bug-04 | `04-workflow-authoring-runtime-js` | planned | `subplan-02-executable-workflow-validation.md` | pending | pending |
| bug-05 | `03-execution-history-streams` | planned | `subplan-01-submit-to-agent-contract.md` | pending | pending |
| bug-06 | `03-execution-history-streams` | planned | `subplan-02-transcript-preservation.md` | pending | pending |
| bug-07 | `05-schema-data-ownership` | planned | `subplan-01-lease-preserving-schema.md` | pending | pending |
| bug-08 | `02-workspace-tools-outputs` | passed | `subplan-01-shell-tool-confinement.md` | validator: `mvn -Dtest=AgentShellToolServiceTest,ExternalAiConfigLoaderTest,OrchestrationRuntimeTest,ChatToolRegistryTest test`; config JSON parse; `git diff --check`; bounded startup; confirmed wildcard override gating, wrapper/path rejection, active workspace/output/project cwd confinement, safe example config | `cddbb6c` |
| bug-09 | `02-workspace-tools-outputs` | passed | `subplan-02-file-tool-workspace-scope.md` | validator: `mvn -Dtest=AgentFileToolServiceTest,ChatToolRegistryTest,AgentShellToolServiceTest,OrchestrationRuntimeTest test`; `git diff --check`; bounded startup on ephemeral port `37939`; confirmed active workspace/output/current-project file scope, unrelated runtime/agent/project denial, no-context data-root fallback, traversal/symlink protection, and updated model-visible descriptions | `0ad9c3d` |
| bug-10 | `02-workspace-tools-outputs` | planned | `subplan-03-web-fetch-redirect-ssrf.md` | pending | pending |
| bug-11 | `01-security-access-control` | passed | `subplan-03-workflow-xss-security.md` | validator: `mvn -Dtest=WorkflowGraphComposerSecurityTest,OrchestrationControllerTest#workflowJsProvidesGraphComposerSurface test`; `node --check src/main/resources/static/js/orchestration/workflows.js`; live `/workflows` XSS probe on isolated DB confirmed inert payloads and no injected `img`/`script` nodes | `0c114bb` |
| bug-12 | `01-security-access-control` | passed | `subplan-04-agent-scoped-lifecycle.md` | validator: `mvn -Dtest=OrchestrationRuntimeTest,AgentOrchestrationControllerTest,OrchestrationControllerTest test`; confirmed REST/HTMX scoped service calls, cross-agent 404/non-mutation, and no public controller use of unscoped lifecycle methods | `f4b1978` |
| bug-13 | `02-workspace-tools-outputs` | planned | `subplan-04-project-workspace-materialization.md` | pending | pending |
| bug-14 | `03-execution-history-streams` | planned | `subplan-03-plan-sse-contract.md` | pending | pending |
| bug-15 | `03-execution-history-streams` | planned | `subplan-04-job-run-submission.md` | pending | pending |
| bug-16 | `06-operational-ui-htmx-mobile` | planned | `subplan-01-mobile-shell-layout.md` | pending | pending |
| bug-17 | `07-validation-harness-regression` | planned | `subplan-01-spring-web-route-coverage.md` | pending | pending |
| bug-18 | `06-operational-ui-htmx-mobile` | planned | `subplan-02-agent-lifecycle-htmx-targets.md` | pending | pending |
| bug-19 | `05-schema-data-ownership` | planned | `subplan-02-canonical-schema-drift.md` | pending | pending |
| bug-20 | `06-operational-ui-htmx-mobile` | planned | `subplan-03-htmx-error-statuses.md` | pending | pending |
| bug-21 | `03-execution-history-streams` | planned | `subplan-05-schedule-reaction-template-validation.md` | pending | pending |
| bug-22 | `02-workspace-tools-outputs` | planned | `subplan-05-filesystem-allocation-fail-fast.md` | pending | pending |
| bug-23 | `02-workspace-tools-outputs` | planned | `subplan-06-output-symlink-materialization.md` | pending | pending |
| bug-24 | `02-workspace-tools-outputs` | planned | `subplan-07-output-attribution.md` | pending | pending |
| bug-25 | `05-schema-data-ownership` | planned | `subplan-03-inbox-table-ownership.md` | pending | pending |
| ro-01 | `03-execution-history-streams` | planned | `subplan-01-submit-to-agent-contract.md` | pending | pending |
| ro-02 | `03-execution-history-streams` | planned | `subplan-01-submit-to-agent-contract.md` | pending | pending |
| ro-03 | `03-execution-history-streams` | planned | `subplan-01-submit-to-agent-contract.md` | pending | pending |
| ro-04 | `04-workflow-authoring-runtime-js` | planned | `subplan-03-js-island-narrowing.md` | pending | pending |
| ro-05 | `04-workflow-authoring-runtime-js` | planned | `subplan-04-graph-error-handling.md` | pending | pending |
| ro-06 | `08-code-quality-stale-cleanup` | planned | `subplan-01-legacy-workflow-cleanup.md` | pending | pending |
| ro-07 | `08-code-quality-stale-cleanup` | planned | `subplan-02-static-module-cleanup.md` | pending | pending |
| ro-08 | `08-code-quality-stale-cleanup` | planned | `subplan-02-static-module-cleanup.md` | pending | pending |
| ro-09 | `06-operational-ui-htmx-mobile` | planned | `subplan-04-stale-runtime-labels.md` | pending | pending |
| ro-10 | `06-operational-ui-htmx-mobile` | planned | `subplan-05-agent-detail-quality.md` | pending | pending |
| ro-11 | `06-operational-ui-htmx-mobile` | planned | `subplan-05-agent-detail-quality.md` | pending | pending |
| ro-12 | `05-schema-data-ownership` | planned | `subplan-04-orphan-schema-cleanup.md` | pending | pending |
| ro-13 | `05-schema-data-ownership` | planned | `subplan-02-canonical-schema-drift.md` | pending | pending |
| ro-14 | `07-validation-harness-regression` | planned | `subplan-02-playwright-harness.md` | pending | pending |
| ro-15 | `07-validation-harness-regression` | planned | `subplan-03-fixture-parity.md` | pending | pending |
| ro-16 | `07-validation-harness-regression` | planned | `subplan-03-fixture-parity.md` | pending | pending |
| ro-17 | `07-validation-harness-regression` | planned | `subplan-04-regression-gap-tests.md` | pending | pending |
| ro-18 | `08-code-quality-stale-cleanup` | planned | `subplan-03-stale-doc-comment-cleanup.md` | pending | pending |

## Update Protocol

When a domain starts, change its row to `in_progress` and record the branch. When a validation agent starts, mark the related domain and finding rows as `validating`. After validation passes, attach the command/browser evidence path and later the commit hash.

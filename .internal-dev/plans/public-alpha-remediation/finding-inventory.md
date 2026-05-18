# Public Alpha Remediation Finding Inventory

## Coverage Rule

Every addressable finding from `.internal-dev/reviews/public-alpha-quality-review/` is assigned here. Items are not skipped unless they appear in `no-action-registry.md` with an explicit reason based on the original review evidence.

## Filed Bugs

| ID | Priority | Primary Domain | Planned Subplan | Summary |
| --- | --- | --- | --- | --- |
| bug-01 | Critical blocker | `01-security-access-control` | `subplan-01-auth-csrf-gate.md` | Public mutation/control routes lack auth and CSRF protection. |
| bug-02 | Critical blocker | `01-security-access-control` | `subplan-02-id-segment-validation.md` | Agent ids can escape the `agents/` subtree inside `dataRoot`. |
| bug-03 | Critical blocker | `04-workflow-authoring-runtime-js` | `subplan-01-draft-editing-validation.md` | Workflow builder rejects required intermediate states. |
| bug-04 | Critical blocker | `04-workflow-authoring-runtime-js` | `subplan-02-executable-workflow-validation.md` | Empty workflows validate and complete as successful no-ops. |
| bug-05 | Critical blocker | `03-execution-history-streams` | `subplan-01-submit-to-agent-contract.md` | Public direct-run surfaces bypass submit-to-agent semantics. |
| bug-06 | Critical blocker | `03-execution-history-streams` | `subplan-02-transcript-preservation.md` | Saved plan execution deletes conversation transcript. |
| bug-07 | Critical blocker | `05-schema-data-ownership` | `subplan-01-lease-preserving-schema.md` | Startup can drop workspace leases through deprecated workspace roots. |
| bug-08 | Critical blocker | `02-workspace-tools-outputs` | `subplan-01-shell-tool-confinement.md` | Shell tool runs host commands with wildcard config. |
| bug-09 | High blocker | `02-workspace-tools-outputs` | `subplan-02-file-tool-workspace-scope.md` | File tools are scoped to the whole data root. |
| bug-10 | High blocker | `02-workspace-tools-outputs` | `subplan-03-web-fetch-redirect-ssrf.md` | Web fetch redirect validation can be bypassed. |
| bug-11 | High blocker | `01-security-access-control` | `subplan-03-workflow-xss-security.md` | Workflow graph composer has stored XSS risk. |
| bug-12 | High blocker | `01-security-access-control` | `subplan-04-agent-scoped-lifecycle.md` | Assignment lifecycle routes are not scoped to the route agent. |
| bug-13 | High blocker | `02-workspace-tools-outputs` | `subplan-04-project-workspace-materialization.md` | Project workspace leases are not materialized for tools. |
| bug-14 | High blocker | `03-execution-history-streams` | `subplan-03-plan-sse-contract.md` | Plan run stream emits wrong SSE event names. |
| bug-15 | High blocker | `03-execution-history-streams` | `subplan-04-job-run-submission.md` | Job Start Run bypasses assignment submission. |
| bug-16 | High blocker | `06-operational-ui-htmx-mobile` | `subplan-01-mobile-shell-layout.md` | Mobile orchestration shell is unusable at phone width. |
| bug-17 | High validation blocker | `07-validation-harness-regression` | `subplan-01-spring-web-route-coverage.md` | Public REST/SSE and Spring web coverage gaps. |
| bug-18 | Medium remediation | `06-operational-ui-htmx-mobile` | `subplan-02-agent-lifecycle-htmx-targets.md` | Agent Delete/Archive targets stale Docker element. |
| bug-19 | High blocker | `05-schema-data-ownership` | `subplan-02-canonical-schema-drift.md` | `schema.sql` drift from repository shape. |
| bug-20 | Medium remediation | `06-operational-ui-htmx-mobile` | `subplan-03-htmx-error-statuses.md` | HTMX fragment errors often return 200 OK. |
| bug-21 | Medium remediation | `03-execution-history-streams` | `subplan-05-schedule-reaction-template-validation.md` | Schedule/reaction assignment templates not validated at save. |
| bug-22 | Medium remediation | `02-workspace-tools-outputs` | `subplan-05-filesystem-allocation-fail-fast.md` | Filesystem allocation failure continues execution. |
| bug-23 | Medium remediation | `02-workspace-tools-outputs` | `subplan-06-output-symlink-materialization.md` | `file_path` materialization can follow symlinks. |
| bug-24 | Medium remediation | `02-workspace-tools-outputs` | `subplan-07-output-attribution.md` | Output attribution uses stale pre-workspace path logic. |
| bug-25 | Medium remediation | `05-schema-data-ownership` | `subplan-03-inbox-table-ownership.md` | Inbox persistence is split across two tables. |

## Review-Only Addressable Findings

| ID | Primary Domain | Planned Subplan | Source | Summary |
| --- | --- | --- | --- | --- |
| ro-01 | `03-execution-history-streams` | `subplan-01-submit-to-agent-contract.md` | `domain-api-web.md`, `domain-workflow.md` | Workflow run API request context carries agent/job/workspace/model/priority fields but direct streaming drops them. |
| ro-02 | `03-execution-history-streams` | `subplan-01-submit-to-agent-contract.md` | `domain-chat-plan-task.md` | Chat send-to-agent default priority differs from operational HTMX high-priority default. |
| ro-03 | `03-execution-history-streams` | `subplan-01-submit-to-agent-contract.md` | `domain-chat-plan-task.md` | Task run stream still contains inline synchronous execution paths. |
| ro-04 | `04-workflow-authoring-runtime-js` | `subplan-03-js-island-narrowing.md` | `domain-frontend-static.md`, `horizontal-security-error-htmx.md` | Workflow graph island overrides HTMX editor and performs CRUD through `fetch`. |
| ro-05 | `04-workflow-authoring-runtime-js` | `subplan-04-graph-error-handling.md` | `domain-frontend-static.md` | Workflow graph network failures are under-reported or become silent state changes. |
| ro-06 | `08-code-quality-stale-cleanup` | `subplan-01-legacy-workflow-cleanup.md` | `domain-workflow.md` | Deprecated `ai.chat.workflow` package still compiles despite canonical workflow package. |
| ro-07 | `08-code-quality-stale-cleanup` | `subplan-02-static-module-cleanup.md` | `domain-api-web.md`, `domain-chat-plan-task.md`, `domain-frontend-static.md` | `magenta-tools.js` has stale direct-run/workflow references but no active import was found. |
| ro-08 | `08-code-quality-stale-cleanup` | `subplan-02-static-module-cleanup.md` | `domain-frontend-static.md` | Active `/inbox` and `/outputs` use HTMX fragments; their JS modules appear stale/dead-code risk. |
| ro-09 | `06-operational-ui-htmx-mobile` | `subplan-04-stale-runtime-labels.md` | `domain-api-web.md`, `horizontal-security-error-htmx.md`, `domain-workspaces-tools-outputs.md` | Stale Docker/Podman naming remains in UI/resources/docs while filesystem runtime is authoritative. |
| ro-10 | `06-operational-ui-htmx-mobile` | `subplan-05-agent-detail-quality.md` | `domain-api-web.md` | Agent detail event log uses static placeholder events. |
| ro-11 | `06-operational-ui-htmx-mobile` | `subplan-05-agent-detail-quality.md` | `domain-workspaces-tools-outputs.md` | Agent workspace health UI masks richer state from `AgentWorkspaceStatusService`. |
| ro-12 | `05-schema-data-ownership` | `subplan-04-orphan-schema-cleanup.md` | `domain-persistence-schema.md`, `bug-19` | `job_work_items` appears to be orphan schema baggage. |
| ro-13 | `05-schema-data-ownership` | `subplan-02-canonical-schema-drift.md` | `horizontal-di-rest-schema-stale.md` | Schema/repository drift continues beyond workspaces around execution tables. |
| ro-14 | `07-validation-harness-regression` | `subplan-02-playwright-harness.md` | `domain-test-harness.md`, `playwright-public-pages-evidence.md` | Playwright is feasible but not a reusable checked-in harness. |
| ro-15 | `07-validation-harness-regression` | `subplan-03-fixture-parity.md` | `domain-test-harness.md` | Test config disables schedules/reactions while production enables them. |
| ro-16 | `07-validation-harness-regression` | `subplan-03-fixture-parity.md` | `domain-test-harness.md` | SQLite fixtures often omit `foreign_keys=true`. |
| ro-17 | `07-validation-harness-regression` | `subplan-04-regression-gap-tests.md` | `domain-test-harness.md`, `automated-validation-evidence.md` | Existing tests missed HTMX target defects and core blockers despite green Maven. |
| ro-18 | `08-code-quality-stale-cleanup` | `subplan-03-stale-doc-comment-cleanup.md` | `bug-22`, `horizontal-security-error-htmx.md` | Stale Docker comments/docs remain and can mislead implementation or operators. |

## Cross-Domain Notes

- Workflow stored XSS is primarily planned under security because it is a security blocker, but UI implementation details are cross-referenced in `04-workflow-authoring-runtime-js`.
- Submit-to-agent semantics are owned by `03-execution-history-streams`, but workflow-specific direct-run fixes must coordinate with `04-workflow-authoring-runtime-js`.
- Project workspace materialization is owned by `02-workspace-tools-outputs`; schema prerequisites are coordinated through `05-schema-data-ownership`.
- Test harness work in `07-validation-harness-regression` should add coverage for fixes made in other domains rather than replacing each domain's focused tests.

# Public Alpha Quality Review Campaign

## Campaign Contract

- Slug: `public-alpha-quality-review`
- Branch: `public-alpha-quality-review`
- Date opened: 2026-05-17
- Runtime assumption: filesystem-backed runtime is the public-alpha contract. Docker/Podman references are documentation or stale-surface findings unless current code requires them.
- Execution model: review, validation, and Playwright agents use `GPT-5.5 Codex high` (`model=gpt-5.5`, `reasoning_effort=high`).
- Scope: review-first quality campaign. The deliverable is a bug ledger, evidence-backed readiness review, and remediation handoff, not live remediation.

## Severity Rubric

See `severity-rubric.md`.

## Phase Ledger

| Phase | Status | Agent / Model | Scope | Artifact Links | Findings Filed | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| 1. Setup and baseline | Complete | main Codex session plus GPT-5.5 Codex high review agents | Artifact folders, route/package/DB inventories, baseline command log | `baseline-command-log.md`, `route-page-inventory.md`, `package-inventory.md`, `db-table-inventory.md` | N/A | Branch created after observing pre-existing dirty `.internal-dev` archive moves. |
| 2. Parallel domain review | Complete | GPT-5.5 Codex high domain agents | API/web, chat/plan/task, orchestration runtime, workflow, persistence/schema, workspaces/tools/outputs, frontend/static, test harness | Reports in `.internal-dev/reviews/public-alpha-quality-review/` | 25 consolidated bugs | Read-only agents. |
| 3. Horizontal review | Complete | GPT-5.5 Codex high agents | DI/bean graph, REST/SSE, direct-run semantics, schema drift, stale code, logging, security, HTMX/SimplyPages | `horizontal-security-error-htmx.md`, `horizontal-di-rest-schema-stale.md` | Consolidated into bug ledger | Reinforced security/direct-run/schema themes. |
| 4. Validation campaign | Complete | GPT-5.5 Codex high Playwright agent plus local Maven/startup commands | Focused tests, full `mvn test`, clean/warm SQLite startup, DB probes, Playwright public-page validation | `automated-validation-evidence.md`, `playwright-public-pages-evidence.md` | `bug-16-high-mobile-orchestration-shell-unusable` | All requested public pages returned 200; mobile blocker found. |
| 5. Consolidation and triage | Complete | main Codex session | Deduplicate findings, file bugs, final readiness review, remediation handoff | `bug-ledger.md`, `final-readiness-review.md`, `remediation-handoff.md` | 25 open bugs | Alpha blockers separated from lower-priority findings. |

## Agent Roster

| Agent | ID | Model | Reasoning | Assigned Scope | Status | Report |
| --- | --- | --- | --- | --- | --- | --- |
| domain-api-web | `019e371c-6209-7051-940e-08a583a1f81e` | GPT-5.5 Codex | high | Spring web/API controllers, public routes, templates/static resources | Complete | `domain-api-web.md` |
| domain-chat-plan-task | `019e371c-625e-7bd1-808d-d6af2ede9a9e` | GPT-5.5 Codex | high | Chat, planning, tasks, SSE contracts | Complete | `domain-chat-plan-task.md` |
| domain-orchestration-runtime | `019e371c-62a2-7e00-bc55-a366c12e0647` | GPT-5.5 Codex | high | Assignments, queues, jobs, diagnostics, runtime | Complete | `domain-orchestration-runtime.md` |
| domain-workflow | `019e371c-62e9-7910-8804-e79b0d05ffd0` | GPT-5.5 Codex | high | Workflow editor/runtime, graph JS island, persistence | Complete | `domain-workflow.md` |
| domain-persistence-schema | `019e371e-8664-7d60-b1f5-53e6c6679c76` | GPT-5.5 Codex | high | SQLite schema and repository drift | Complete | `domain-persistence-schema.md` |
| domain-workspaces-tools-outputs | `019e371e-86a5-74a0-8d4e-697360e60bf3` | GPT-5.5 Codex | high | Workspace runtime, tools, outputs | Complete | `domain-workspaces-tools-outputs.md` |
| domain-frontend-static | `019e3723-6351-78c1-9d79-52a567b21e69` | GPT-5.5 Codex | high | Static JS/CSS and active UI modules | Complete | `domain-frontend-static.md` |
| domain-test-harness | `019e3723-63b2-73e3-8280-ccd01ebf5250` | GPT-5.5 Codex | high | Tests, startup config, Playwright feasibility | Complete | `domain-test-harness.md` |
| horizontal-security-error-htmx | `019e3721-2ca9-7fe2-95c3-53113f123541` | GPT-5.5 Codex | high | Security, errors, HTMX, stale Docker | Complete | `horizontal-security-error-htmx.md` |
| horizontal-di-rest-schema-stale | `019e3721-2ce7-7760-80a3-4b76aee581c2` | GPT-5.5 Codex | high | DI, REST/SSE, schema drift, stale code | Complete | `horizontal-di-rest-schema-stale.md` |
| validation-playwright-public-pages | `019e3723-63fd-72f0-9f75-bf96ce8ac6cb` | GPT-5.5 Codex | high | Browser validation over public pages | Complete | `playwright-public-pages-evidence.md` |

## Bug Ledger

| Bug ID | Severity | Surface | Summary | Source Evidence | Status | Remediation |
| --- | --- | --- | --- | --- | --- | --- |
| bug-01 | Critical | security | Unauthenticated public mutation/control surface | `bug-01-critical-security-unauthenticated-control-surface/report.md` | Open | Alpha blocker |
| bug-02 | Critical | security | Agent ids can escape agent subtree inside data root | `bug-02-critical-security-agent-id-path-traversal/report.md` | Open | Alpha blocker |
| bug-03 | Critical | workflow | Builder rejects necessary intermediate states | `bug-03-critical-workflow-builder-invalid-intermediate/report.md` | Open | Alpha blocker |
| bug-04 | Critical | workflow | Empty workflows validate and complete as no-ops | `bug-04-critical-workflow-empty-noop/report.md` | Open | Alpha blocker |
| bug-05 | Critical | execution | Public direct-run surfaces bypass submit-to-agent | `bug-05-critical-execution-direct-run-surfaces/report.md` | Open | Alpha blocker |
| bug-06 | Critical | chat/plans | Saved plan execution deletes transcript | `bug-06-critical-chat-plan-transcript-deletion/report.md` | Open | Alpha blocker |
| bug-07 | Critical | schema | Startup can drop workspace leases | `bug-07-critical-schema-workspace-lease-drop/report.md` | Open | Alpha blocker |
| bug-08 | Critical | tools | Shell tool runs host commands with wildcard config | `bug-08-critical-tools-host-shell-wildcard/report.md` | Open | Alpha blocker |
| bug-09 | High | tools | File tools scoped to whole data root | `bug-09-high-tools-file-scope-data-root/report.md` | Open | Alpha blocker |
| bug-10 | High | tools | Web fetch redirect SSRF risk | `bug-10-high-tools-web-fetch-redirect-ssrf/report.md` | Open | Alpha blocker |
| bug-11 | High | workflow/security | Workflow graph stored XSS risk | `bug-11-high-workflow-stored-xss/report.md` | Open | Alpha blocker |
| bug-12 | High | runtime | Assignment lifecycle routes not agent-scoped | `bug-12-high-assignment-lifecycle-not-agent-scoped/report.md` | Open | Alpha blocker |
| bug-13 | High | workspaces | Project workspace leases not materialized | `bug-13-high-project-workspace-leases-not-materialized/report.md` | Open | Alpha blocker |
| bug-14 | High | SSE | Plan run stream emits wrong event names | `bug-14-high-plan-stream-sse-contract/report.md` | Open | Alpha blocker |
| bug-15 | High | jobs | Job Start Run bypasses assignment submission | `bug-15-high-job-start-run-bypasses-assignment/report.md` | Open | Alpha blocker |
| bug-16 | High | UI | Mobile orchestration shell unusable | `bug-16-high-mobile-orchestration-shell-unusable/report.md` | Open | Alpha blocker |
| bug-17 | High | tests | Public REST/SSE and Spring web coverage gaps | `bug-17-high-test-coverage-route-context-gaps/report.md` | Open | Validation blocker |
| bug-18 | Medium | UI | Agent Delete/Archive target missing stale Docker element | `bug-18-medium-agent-lifecycle-stale-target/report.md` | Open | Remediation |
| bug-19 | High | schema | `schema.sql` drift from repository shape | `bug-19-high-schema-sql-drift/report.md` | Open | Alpha blocker |
| bug-20 | Medium | errors | HTMX fragment errors often return 200 OK | `bug-20-medium-fragment-errors-return-200/report.md` | Open | Remediation |
| bug-21 | Medium | schedules/reactions | Assignment templates not validated at save | `bug-21-medium-schedule-reaction-template-validation/report.md` | Open | Remediation |
| bug-22 | Medium | filesystem | Filesystem allocation failure continues execution | `bug-22-medium-filesystem-allocation-continues/report.md` | Open | Remediation |
| bug-23 | Medium | outputs | `file_path` materialization can follow symlinks | `bug-23-medium-output-file-path-symlink/report.md` | Open | Remediation |
| bug-24 | Medium | outputs | Output attribution uses stale pre-workspace path logic | `bug-24-medium-output-attribution-stale-path/report.md` | Open | Remediation |
| bug-25 | Medium | inbox/schema | Inbox persistence split across two tables | `bug-25-medium-inbox-table-split/report.md` | Open | Remediation |

## Validation Ledger

| Validation | Agent / Model | Command or Probe | Result | Evidence |
| --- | --- | --- | --- | --- |
| Focused controller/UI tests | main Codex | `timeout 180s mvn -q -Dtest=OperationalUiContractControllerTest,OrchestrationControllerTest,FrontendControllerTest test` | Passed | `automated-validation-evidence.md` |
| Focused runtime/persistence tests | main Codex | `timeout 180s mvn -q -Dtest=OrchestrationRuntimeTest,WorkflowRepositoryTest,WorkflowRunnerTest,WorkspaceLeaseServiceTest,PlanRepositoryTest,JobRepositoryTest,ProjectRepositoryTest test` | Passed | `automated-validation-evidence.md` |
| Full Maven suite | main Codex | `timeout 300s mvn test` | Passed: 444 tests, 0 failures, 0 errors | `automated-validation-evidence.md` |
| Clean SQLite startup | main Codex | bounded `mvn spring-boot:run` with isolated clean DB | Reached `Started Magenta2Application`; timeout 124 after healthy startup | `automated-validation-evidence.md` |
| Warm SQLite startup | main Codex | bounded `mvn spring-boot:run` with copied warm DB | Reached `Started Magenta2Application`; timeout 124 after healthy startup | `automated-validation-evidence.md` |
| DB probes | main Codex | SQLite schema/column probes on clean and warm DBs | Completed; drift recorded | `automated-validation-evidence.md`, `domain-persistence-schema.md` |
| Playwright public pages | validation-playwright-public-pages / GPT-5.5 Codex high | Browser pass over required pages plus plan mutation persistence | Routes 200; plan persisted; mobile blocker found | `playwright-public-pages-evidence.md` |

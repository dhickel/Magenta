# Scope

Phase 04 code validation/red-team for the dashboard widget suite on branch `feature/dashboard-widget-suite`.

Validated current uncommitted Phase 04 worktree against:

- `.internal-dev/plans/dashboard-widget-suite/worker-directives/phase-04-agent-operational-widgets.md`
- `.internal-dev/plans/dashboard-widget-suite/00-specification-lock.md`
- `.internal-dev/plans/dashboard-widget-suite/03-target-architecture-and-widget-contract.md`
- `.internal-dev/plans/dashboard-widget-suite/05-tooling-and-agent-access-design.md`
- `.internal-dev/plans/dashboard-widget-suite/06-ui-ux-contract-and-visual-validation-criteria.md`
- `.internal-dev/plans/dashboard-widget-suite/shared/implementation-notes.md`
- `.internal-dev/plans/dashboard-widget-suite/shared/validation-matrix.md`
- `artifacts/dashboard-widget-suite/validation-summary.json`

Governance reviewed:

- `AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AGENTS.md`

Relevant specifications and knowledge reviewed:

- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/service-graph.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`
- `.internal-dev/knowledge/output-artifact-attribution-query-and-backfill-pattern.md`
- `.internal-dev/knowledge/workarea-operational-ui-consistency.md`
- `.internal-dev/knowledge/entity-selector-htmx-pattern.md`

# Findings

## F1 - Agent Files/Notes settings cannot select non-Avatar agent Work Areas

Severity: high

Classification: `code_defect`

Status: `passed_revalidation`

The Phase 04 Agent Files/Notes read model can render a non-Avatar agent Work Area only after settings are written directly, but the actual settings UI is fed by `data.workAreas()`, and `data()` populates that from `avatarWorkAreas()`. `avatarWorkAreas()` lists only `WorkspaceOwnerType.AGENT, "avatar"` Work Areas, so a dashboard user configuring Agent Files/Notes for `agent-1` will not see `agent-1`'s Work Areas in the Work Area selector. If the modal is saved without the hidden/manual direct value, validation rejects the binding as missing; if an existing non-Avatar workAreaId is not present as an option, the browser select cannot preserve it reliably.

Evidence:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java:1798` passes `avatarWorkAreas()` into dashboard data.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java:2067` to `2075` defines `avatarWorkAreas()` as only the reserved Avatar agent owner.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:404` to `407` renders the `workAreaId` settings field from `data.workAreas()`.
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java:1183` to `1197` proves the renderer works only by writing `agent-1`'s Work Area id directly through `avatarService.updateDashboardWidgetSettings`, not through the settings modal selector path.

Contract impact:

- Fails Phase 04 directive step 1: shared binding selectors for agent/project/Work Area.
- Fails the settings contract in `.internal-dev/plans/dashboard-widget-suite/06-ui-ux-contract-and-visual-validation-criteria.md`: widget settings modal exposes selectors for agent/project/Work Area/source mode.
- Blocks code sign-off because Agent Files/Notes is an agent-operational widget but its selector surface is effectively Avatar-agent scoped.

Revalidation:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java:1858` now routes Agent Files/Notes settings through `widgetSettingsData(...)`.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java:2097` to `2126` now supplies Work Area selector options from the selected/bound agent and preserves an already-selected agent-owned Work Area when it matches the selected agent or no agent is selected.
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java:1227` covers an `agent-1` Work Area appearing and remaining selected in the settings modal while the reserved Avatar Work Area is omitted.
- Focused Phase 04 suite passed with 91 tests and startup smoke passed during independent revalidation.

# Risk Assessment

The core service confinement and scoped read paths are mostly sound: Work Area preview calls `WorkAreaExplorerService.preview`, output queries use `OutputArtifactQuery`, scoped output preview rejects artifacts outside the widget read model, and Phase 04 registry descriptors declare existing current-context `agent_*` tool names rather than arbitrary-agent normal tools.

The selector defect has been repaired in code and covered by a focused controller test. Residual risk is now limited to browser proof: the modal selector and selected-agent/Work Area interaction still need desktop/mobile visual and HTMX validation before full Phase 04 sign-off.

# Criterion Results

| Criterion | Result | Evidence |
| --- | --- | --- |
| Agent-bound widgets clearly show selected source and missing/no-agent states. | PASS_CODE | `Agent Status/Queue` source chips and no-agent/missing-agent states are covered in `AvatarDashboardControllerTest`; renderer shows source strip and recoverable empty states. Browser proof still pending. |
| Outputs are never unscoped unless dashboard-wide mode is explicitly selected. | PASS_CODE | `agentOutputsView` defaults to `agent` mode and requires `agentId`; dashboard-wide mode is explicit. Controller test rejects out-of-scope preview and shows dashboard-wide only after `sourceMode=dashboard`. |
| Output preview/download use existing output services and do not expose internal run/workspace roots. | PASS_CODE_WITH_BROWSER_RISK | Preview delegates to existing `OutputArtifactService` after scoped allow-list check. Download links use existing `/api/outputs/{id}/download` for listed artifacts. Browser proof should verify no internal roots are displayed in rows/modals. |
| Work Area file widgets use service confinement/familiar list/detail patterns and do not use legacy `/avatar/_work-areas` routes. | PASS_CODE | The preview route uses `WorkAreaExplorerService`, rendered rows avoid `/avatar/_work-areas`, owner guard remains intact, and the repaired settings selector includes/preserves selected-agent Work Areas. |
| Dashboard selected-agent binding does not alter tool authorization or add arbitrary-agent normal tools. | PASS_CODE | No `agent_*` tool implementation changes were made. Existing authorization tests passed in the focused suite. |
| Tool descriptors match current registered `agent_*`/`avatar_*` tool names and tests prove scoping remains intact. | PASS_CODE | `AgentOperationalToolConfigurationTest` validates Phase 04 widget descriptors against registered `agent_*` tools. `AgentToolAuthorizationServiceTest` and service tests passed. |
| Docs/spec/changelog/evidence accurately describe Phase 04 and do not overclaim browser proof. | PASS_CODE | Docs/specs correctly state browser proof is delegated/pending. The canonical evidence index is updated to code-validation passed/browser pending. |
| Required tests/startup evidence is adequate or rerun. | PASS_TESTS | Focused Maven suite passed 91 tests; startup smoke reached Tomcat and exited through bounded timeout. |

# Commands And Evidence

- `git status --short --branch`: confirmed branch `feature/dashboard-widget-suite`; Phase 04 uncommitted; `.gitignore` and `.internal-dev/reviews/2026-05-28-model-alias-internal-review.md` treated as unrelated/pre-existing.
- `rg --files -g 'AGENTS.md' ...`: located required governance and plan files.
- `rg --files .internal-dev/knowledge`: listed knowledge filenames, then read only task-relevant knowledge files.
- `jq . artifacts/dashboard-widget-suite/validation-summary.json`: parsed successfully before evidence update.
- `mvn -Dtest=AvatarDashboardControllerTest,AgentOperationalToolConfigurationTest,AgentOperationalToolServiceTest,AgentToolAuthorizationServiceTest,ChatToolRegistryTest,WorkAreaServiceTest,WorkAreaExplorerServiceTest,OutputArtifactServiceAttributionTest,OutputArtifactPathSemanticsTest test`: PASS, 90 tests, 0 failures, 0 errors, 0 skipped.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`: app context PASS; Tomcat started on random port 38977; timeout exited 124 after graceful shutdown.
- `jq . artifacts/dashboard-widget-suite/validation-summary.json >/dev/null`: PASS before review artifact update.
- `git diff --check -- . ':(exclude).gitignore' ':(exclude).internal-dev/reviews/2026-05-28-model-alias-internal-review.md'`: PASS before review artifact update.
- `mvn -Dtest=AvatarDashboardControllerTest,AgentOperationalToolConfigurationTest,AgentOperationalToolServiceTest,AgentToolAuthorizationServiceTest,ChatToolRegistryTest,WorkAreaServiceTest,WorkAreaExplorerServiceTest,OutputArtifactServiceAttributionTest,OutputArtifactPathSemanticsTest test`: PASS after scoped repair, 91 tests, 0 failures, 0 errors, 0 skipped.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`: app context PASS after scoped repair; Tomcat started on random port 35301; timeout exited 124 after graceful shutdown.
- `jq '.status' artifacts/dashboard-widget-suite/validation-summary.json >/dev/null`: PASS after scoped repair.

# Browser Checklist

F1 is repaired in code. Delegate Phase 04 browser proof with:

- Desktop `1440x900` and mobile `390x844`.
- `/` with Agent Status/Queue, Agent Outputs, and Agent Files/Notes widgets seeded.
- Agent Status/Queue: no-agent, missing-agent, selected-agent, queue rows, running/waiting counts, inbox rows, source chips.
- Agent Outputs: dashboard-wide, selected agent, selected project, selected job, selected Work Area; scoped preview positive and scoped preview negative; download link visible only for artifacts in the scoped list.
- Agent Files/Notes: settings modal can select the target agent's Work Area; selected Work Area mini-browser renders files and tagged notes; preview opens through `/dashboards/{dashboardId}/widgets/{widgetInstanceId}/_work-area-file`; mismatched selected agent/Work Area owner returns recoverable state or 404 as appropriate.
- Capture `/manage`, `/agents`, `/agents/{agentId}`, and Work Area explorer reference screenshots for visual comparison.
- Check no duplicate shell/nav/modal roots, no horizontal overflow, long path/name wrapping, modal scrollability, top-nav z-index, compact blue-gray operational styling, and no legacy `/avatar/_work-areas` links from the new widget mini-view.

# Missing Tests, Docs, And Workflow Work

- Missing browser proof: Phase 04 delegated Playwright remains pending.
- No GitHub issue was created because this is an in-scope Phase 04 validation defect, not an out-of-scope bug.
- No `.internal-dev/bugs/` artifact was created for the same reason.

# Required Remediation Handoff

Verdict: `PASS_CODE_VALIDATION`

Classification: `code_defect`

Repair is complete and independently revalidated. No code remediation handoff remains.

# Recommendations

Proceed to delegated Phase 04 browser proof. Keep validation scoped to the Phase 04 widget surfaces and the reference screenshots listed above.

# Follow-ups

- After repair and code revalidation, run the delegated Playwright checklist above and reconcile screenshots/logs into `artifacts/dashboard-widget-suite/validation-summary.json`.

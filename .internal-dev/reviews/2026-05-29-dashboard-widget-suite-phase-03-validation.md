# Dashboard Widget Suite Phase 03 Code Validation

## Scope

Validated the uncommitted Phase 03 worktree on `feature/dashboard-widget-suite` against `.internal-dev/plans/dashboard-widget-suite/worker-directives/phase-03-notes-project-context.md`, the dashboard widget suite support plans, current specifications, package guides, worker summary, repair summary, and `artifacts/dashboard-widget-suite/validation-summary.json`.

Read governance/spec context in the initial validation pass:

- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/service-graph.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`
- `.internal-dev/knowledge/dashboard-fragment-navigation.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- package guides under `src/main/java/io/mindspice/magenta2/`, `api/web`, `avatar`, `ai/chat/tool`, `ai/chat/tool/orchestration`, `ai/orchestration`, and `docs`

Focused revalidation on 2026-05-29 checked only the previously failed Phase 03 findings and nearby regressions.

## Findings

No open code-validation findings remain for Phase 03.

Resolved findings:

1. `PASSED_REVALIDATION` - Project file-note namespace confinement now normalizes paths before policy checks. `readProjectFile()` and `saveProjectFile()` call `normalizeProjectNotePath()` before delegating to the Work Area explorer ([ProjectArtifactService.java:90](../../src/main/java/io/mindspice/magenta2/avatar/dashboard/ProjectArtifactService.java:90), [ProjectArtifactService.java:96](../../src/main/java/io/mindspice/magenta2/avatar/dashboard/ProjectArtifactService.java:96)). The normalizer rejects absolute paths and requires the normalized path to remain below `.magenta/project/` ([ProjectArtifactService.java:335](../../src/main/java/io/mindspice/magenta2/avatar/dashboard/ProjectArtifactService.java:335)). Regression tests cover `.magenta/project/../outside.md` rejection for read and save and absolute path rejection ([ProjectServiceTest.java:176](../../src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectServiceTest.java:176)).

2. `PASSED_REVALIDATION` - Typed project artifact writes now resolve the project root as a real path, create parent directories segment by segment, reject symlinked parent directories, and reject symlinked artifact files ([ProjectArtifactService.java:160](../../src/main/java/io/mindspice/magenta2/avatar/dashboard/ProjectArtifactService.java:160), [ProjectArtifactService.java:188](../../src/main/java/io/mindspice/magenta2/avatar/dashboard/ProjectArtifactService.java:188), [ProjectArtifactService.java:196](../../src/main/java/io/mindspice/magenta2/avatar/dashboard/ProjectArtifactService.java:196)). Regression tests cover symlinked `.magenta/project` and symlinked `goals.json` rejection ([ProjectServiceTest.java:192](../../src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectServiceTest.java:192), [ProjectServiceTest.java:209](../../src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectServiceTest.java:209)).

3. `PASSED_REVALIDATION` - New file-note/project tools now have representative behavior coverage. `AvatarToolsTest` exercises Work Area file-note read/update through `WorkAreaExplorerService`, project context/artifact update through `ProjectArtifactService`, and project file-note boundary rejection propagation ([AvatarToolsTest.java:374](../../src/test/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarToolsTest.java:374), [AvatarToolsTest.java:392](../../src/test/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarToolsTest.java:392), [AvatarToolsTest.java:449](../../src/test/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarToolsTest.java:449)).

## Verdict

`PASS_CODE_VALIDATION`

Classification of repaired issues: `code_defect`, now resolved by scoped repair.

## Criteria Results

| Criterion | Result | Evidence |
| --- | --- | --- |
| Personal notes remain in `avatar_notes`; file notes remain file-backed. | Pass | Personal notes still use `AvatarService.appendNote/searchNotes`; file notes are represented as `DashboardFileNote` and read through explorer/project adapters. |
| File-backed notes use Work Area/project file services and confinement; no raw unsafe filesystem edits. | Pass | Work Area notes use `WorkAreaExplorerService`; project note paths are normalized and confined to `.magenta/project/` before explorer delegation. |
| Project household artifacts are confined under project/Work Area roots and validated/default-created by service adapters. | Pass | Defaults/schema validation remain; artifact path resolution now rejects symlinked parents/files and checks real paths under the project root. |
| Notes widget shows source clearly and handles missing bindings. | Pass, browser pending | Source chips/missing binding messages are rendered; controller test covers personal and Work Area modes. |
| Projects widget supports household and code projects without pretending all projects are repos. | Pass, browser pending | `DashboardProjectContextView.codeProject()` derives from `gitRepoUrl`; UI labels code vs household project. |
| Contacts/Materials widget or project sub-widget has source binding/project links and useful visible data. | Pass, browser pending | Registry includes `contacts-materials`; widget filters contacts/materials artifacts and requires project binding. |
| Tool descriptors/static tools are registered, authorized consistently, tested, and do not bypass boundaries. | Pass | Tool names are registered and representative read/update/context/update paths plus boundary rejection are covered in `AvatarToolsTest`. |
| Docs/spec/changelog/evidence accurately describe Phase 03 and do not overclaim browser proof. | Pass | Specs/docs/changelog describe browser proof as pending; validation summary now marks code validation passed with browser pending. |
| Required tests/startup evidence is adequate or rerun. | Pass | Focused Maven suite, startup smoke, `jq`, and `git diff --check` were rerun locally after repair. |

## Commands And Evidence

- `mvn -Dtest=AvatarRepositoryTest,AvatarServiceTest,AvatarDashboardControllerTest,ProjectServiceTest,ProjectRepositoryTest,WorkAreaServiceTest,WorkAreaExplorerServiceTest,WorkAreaControllerTest,AvatarToolsTest test`
  - Passed: 102 tests, 0 failures, 0 errors, 0 skipped.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
  - Started successfully on random port `33995`; timeout stopped it with expected exit code `124`.
- `jq . artifacts/dashboard-widget-suite/validation-summary.json >/dev/null`
  - Passed before evidence update.
- `git diff --check`
  - Passed before evidence update.

## Browser Checklist

Phase 03 code validation passed. Next delegated browser proof should cover:

- Desktop `1440x900` and mobile `390x844`.
- `/` seeded with personal Notes, Work Area file Notes, Projects, and Contacts/Materials widgets.
- Notes personal mode: source chip, search/tag filtering, quick capture, last-opened personal note modal.
- Work Area file-note mode: source chip, Markdown view/edit, save refresh, no duplicate modal/root ids, no mobile overflow.
- Project file-note negative: traversal-like project note paths are rejected or unavailable through UI/tool-backed route evidence.
- Projects widget: household project goals/materials/contacts/blockers/next actions/progress/outputs/notes, no repo-only language.
- Contacts/Materials widget: selected project binding visible, contacts/materials rows useful and bounded.
- Mobile modal scrolling and control reachability.
- Screenshot visual critique against `/manage`, `/agents`, and Work Area file explorer language.

## Risk Assessment

Residual risk is browser-only for Phase 03: UI behavior, visual density, modal overflow, and browser-observed HTMX boundaries still need delegated Playwright validation. No open code-level confinement/tool test finding remains from this focused revalidation.

## Recommendations

- Proceed to delegated Phase 03 browser proof using the checklist above.
- Keep Phase 03 status below `fully_validated` until browser evidence is reconciled.

## Follow-ups

- `.gitignore` and `.internal-dev/reviews/2026-05-28-model-alias-internal-review.md` were not considered Phase 03 evidence per task instruction.

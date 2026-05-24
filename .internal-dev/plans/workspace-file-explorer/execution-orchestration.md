# Workspace File Explorer Execution Orchestration

Status: ready-for-execution
Created: 2026-05-24
Scope: orchestration map only; no product implementation
Source suite: `.internal-dev/plans/workspace-file-explorer/`

## Source Plan Summary

The advanced plan suite is sufficient for execution orchestration. It defines the locked workspace-file-explorer scope, acceptance criteria AC1-AC14, validation criteria VC1-VC8, domain work units WU-01 through WU-12, stop rules, closeout duties, and upstream SimplyPages boundary.

Key execution rule: Magenta mutations run one phase at a time, with a validation/red-team gate after each mutating phase. Non-mutating research, validation checklist preparation, and SimplyPages API-shape review may run in parallel. Upstream SimplyPages mutation must not start until the Magenta service/view-model contract is stable enough to avoid redesign churn and the upstream dirty-state gate is resolved.

Current baseline observed for this orchestration pass:

- Magenta branch: `feature/workspace-file-explorer`.
- Dirty user files to preserve: `.internal-dev/inbox/queue.md`, `.internal-dev/inbox/read.md`.
- Plan suite is uncommitted under `.internal-dev/plans/workspace-file-explorer/`.
- Implementation target branch is already the requested `feature/workspace-file-explorer`; Phase 1 may start directly after a final `git status --short` confirms no overlap with assigned files.

## Missing Gates And Assumptions

No missing execution gates block orchestration.

Assumptions to reverify at execution time:

- `WorkAreaExplorerService` can be refactored/wrapped without broad runtime redesign.
- Repository-owned SQLite self-migration plus `schema.sql` remains the correct schema pattern.
- `orchestration_events` may be unsuitable for file action visibility; Phase 1 can choose a minimal `workspace_file_actions` table if inspection confirms this.
- Upstream SimplyPages checkout has dirty state and must be isolated before mutating work.
- Markdown rendering must be checked for package/security fit before reuse.

User-decision gates:

- U1: If a new action-log table is needed, proceed minimally unless user rejects schema growth.
- U2: If upstream dirty state blocks safe branch/PR work, ask whether to use temp clean clone, worktree, or current checkout.
- U3: Stop if any required flow needs arbitrary browsing outside workspace roots.
- U4: Stop if non-UTF-8 editing is required; design explicit encoding handling first.

## Execution Graph

### Parallel Group A: Non-Mutating Startup Research

Can run immediately and in parallel with Phase 1. It is not required before Phase 1 starts.

- Validation matrix agent: convert `06-validation-redteam-plan.md` into a command checklist and fixture list; no edits.
- SimplyPages API-shape research agent: inspect upstream docs/demo/source and propose a generic module contract; no edits.
- Docs impact reviewer: list docs/package guides likely to change; no edits.
- Security red-team planner: expand traversal/symlink/deletion test inputs; no edits.

Stop if any agent finds criteria ambiguity that changes AC1-AC14. Route criteria/spec insufficiency back to `advanced_planning_agent`.

### Phase 0: Baseline Branch And Dirty-State Gate

Mutating status: normally non-mutating, except branch creation if needed.

Owner: main orchestrator or setup worker.

Required checks:

```bash
git status --short --branch
git branch --show-current
```

Expected changed files: none, unless updating this orchestration artifact.

Gate:

- Confirm branch is `feature/workspace-file-explorer`; create/switch only if not already there.
- Confirm `.internal-dev/inbox/queue.md` and `.internal-dev/inbox/read.md` remain untouched by workers.
- Stop if dirty files overlap Phase 1 targets.

Commit gate: no commit unless planning/progress artifacts are intentionally added.

### Phase 1: Workspace Domain Foundation

Work units: WU-02, WU-03, WU-04.

Mutating status: serial mutating, one implementation worker only.

Expected changed files:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerService.java`
- New records/classes under `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/`
- `src/main/resources/schema.sql`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerServiceTest.java`
- New workspace package tests, likely metadata/action-log repository and service tests
- Possible `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md` update only if package responsibility materially changes
- Applicable `.internal-dev/plans/workspace-file-explorer/` progress/evidence notes only

Validation commands:

```bash
mvn test -Dtest=WorkAreaExplorerServiceTest,WorkspaceFileMetadataRepositoryTest,WorkspaceFileMetadataServiceTest,WorkspaceFileActionLogRepositoryTest,WorkspaceFileActionServiceTest
git status --short
```

Validation gate:

- Independent validation worker runs focused tests and red-team review against AC1, AC3, AC4, AC5, AC6, AC7, AC9, AC10 as applicable to service/domain behavior.
- Stop on any path escape, root mutation, symlink traversal, missing tag-follow, missing durable action log, host absolute path leak, or product-code edit outside assigned scope.

Commit gate:

```bash
git add <Phase 1 changed files only>
git commit -m "Add workspace file explorer domain foundation"
```

Do not stage `.internal-dev/inbox/queue.md` or `.internal-dev/inbox/read.md`.

### Phase 2: Viewer, Editor, And API Contracts

Work units: WU-05, WU-08.

Mutating status: serial mutating.

Expected changed files:

- Workspace text/preview policy classes under `ai/orchestration/workspaces`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaController.java`
- Possible rendering helpers in `src/main/java/io/mindspice/magenta2/api/web/`
- `src/test/java/io/mindspice/magenta2/api/web/WorkAreaControllerTest.java`
- Workspace/API tests for text, markdown, image, binary, error payloads, and route compatibility
- `src/test/java/io/mindspice/magenta2/ai/chat/rendering/ChatMarkdownRendererTest.java` only if existing renderer is reused

Validation commands:

```bash
mvn test -Dtest=WorkAreaExplorerServiceTest,WorkAreaControllerTest,WorkspaceFileActionServiceTest
git status --short
```

Validation gate:

- Validate AC1, AC3, AC6, AC7, AC8, AC9, AC13.
- Stop if invalid/non-UTF-8 content can be silently rewritten, markdown renders unsafe script/raw HTML, size limits can be bypassed, controller owns filesystem logic, or API errors leak host paths.

Commit gate:

```bash
git add <Phase 2 changed files only>
git commit -m "Add workspace file viewer and API contracts"
```

### Phase 3: SimplyPages Upstream Module

Work unit: WU-09.

Mutating status: mutating, but outside Magenta in `/home/hickelpickle/Code/Java/cannasite/java-html-framework`.

Start rule:

- Non-mutating upstream research may run during Phase 1.
- Upstream implementation must wait until Phase 1 validation passes and the Magenta explorer/picker view-model contract is stable enough to hand off.
- If Phase 2 materially changes view/editor slots or route needs, pause upstream implementation until the contract is reconciled.

Expected changed files in upstream repo:

- New FileExplorer/FilePicker module classes/config records
- `simplypages/src/main/resources/static/css/framework.css`
- Optional narrow generic JS
- Demo controller/page/tests/docs
- Upstream `.internal-dev/changelogs/` entry per upstream guidance

Validation commands in upstream repo:

```bash
./mvnw -pl simplypages test
./mvnw -pl demo test
./mvnw -pl demo spring-boot:run
```

Validation gate:

- Validate AC11, AC12, AC14 upstream portion.
- Browser validation of demo desktop/mobile is delegated to validation worker.
- Stop if dirty upstream changes would be overwritten, module couples to Magenta routes/security/tags/audit, or JS grows into a client app.

Commit/GitHub gate:

- Commit upstream changes on isolated branch, suggested `feature/reusable-file-explorer-module`.
- Push and open draft PR to `dhickel/SimplyPages`.

### Phase 4: Magenta HTMX UI And Picker Integration

Work units: WU-06, WU-07.

Mutating status: serial mutating.

Dependencies:

- Phase 1 and Phase 2 passed.
- Phase 3 module is available locally or dependency/source update strategy is explicitly chosen.

Expected changed files:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- Possible new API/web view-model/helper classes
- `src/main/resources/static/css/avatar-dashboard.css`
- Possible shared CSS in `magenta.css` or `orchestration.css`
- Narrow JS under `src/main/resources/static/js/` only for justified local behavior
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- New/updated web/controller tests

Validation commands:

```bash
mvn test -Dtest=WorkAreaControllerTest,AvatarDashboardControllerTest
git status --short
```

Validation gate:

- Validate AC2, AC3, AC6, AC8, AC9, AC10, AC12, AC14.
- Standard CRUD/navigation must remain HTMX-first; any JS must be narrow and justified.
- Stop if UI is modal-only, low-density to the point of hiding useful content, overlapping/clipped, unusable on mobile, or picker returns/display absolute host paths.

Commit gate:

```bash
git add <Phase 4 changed files only>
git commit -m "Integrate workspace file explorer UI and picker"
```

### Phase 5: Documentation And Internal Closeout Prep

Work unit: WU-10 plus `07-closeout-plan.md`.

Mutating status: serial mutating.

Expected changed files:

- `docs/api/00-index.md`
- `docs/technical/api-reference.md` if route details changed
- `docs/technical/workspaces-tools-outputs.md`
- `docs/end-user/avatar-dashboard.md`
- `docs/end-user/projects-and-workspaces.md` if picker/output behavior changed
- `docs/technical/frontend-htmx.md` if module/JS policy needs documentation
- Applicable package `AGENTS.md` files
- `.internal-dev/changelogs/<date>-workspace-file-explorer.md`
- `.internal-dev/knowledge/<topic>.md`
- `.internal-dev/focus/unfinished-work.md` only for approved deferred items
- `.internal-dev/focus/architecture-focus.md` and `.internal-dev/focus/decisions.md` only if durable architecture decisions changed
- `.internal-dev/bugs/` reports for discovered out-of-scope bugs, mirrored to GitHub Issues

Validation commands:

```bash
git status --short
mvn test -Dtest=WorkAreaExplorerServiceTest,WorkAreaControllerTest,AvatarDashboardControllerTest
```

Validation gate:

- Docs must not claim chunked editing, external metadata reconciliation, broad picker rollout, or Avatar action timeline if not implemented.
- Stop if bug reports are created locally but not mirrored to GitHub where required.

Commit gate:

```bash
git add <Phase 5 changed files only>
git commit -m "Document workspace file explorer behavior"
```

### Phase 6: Final Independent Validation And Remediation

Work units: WU-11, WU-12.

Mutating status: non-mutating validation first; remediation mutates only if assigned after failures.

Validation commands:

```bash
mvn test -Dtest=WorkAreaExplorerServiceTest,WorkspaceFileMetadataRepositoryTest,WorkspaceFileMetadataServiceTest,WorkspaceFileActionLogRepositoryTest,WorkspaceFileActionServiceTest,WorkAreaControllerTest,AvatarDashboardControllerTest
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Delegated Playwright scope:

- `/avatar?tab=work-areas`
- Work Areas widget from dashboard tab if still present
- One picker integration route
- Desktop 1440x900 and mobile 390x844 screenshots
- Navigation, actions, delete modals, tag inspector, text editor, markdown View/Edit, image viewer, binary fallback, picker selection
- Visual quality critique and JS/HTMX justification

Remediation gate:

- Validation failures return to the smallest scoped remediation worker.
- Criteria/spec ambiguity returns to `advanced_planning_agent`.
- Do not advance or close while validation failures remain unless user accepts risk and the blocker is recorded.

Commit gate:

```bash
git add <remediation files only>
git commit -m "Remediate workspace file explorer validation findings"
```

Only commit if remediation changed files.

## Subagent Roster

| Agent | Model | Reasoning | Mutating | Ownership Boundary | Required Inputs | Expected Output | Stop Rules |
| --- | --- | --- | --- | --- | --- | --- | --- |
| main_orchestrator | gpt-5.3-codex | medium | planning/coordination only | Branch/status gates, subagent dispatch, artifact updates, commit authorization | This file, `00`, `03`, `05`, `06`, `07`, repo guidance | Phase status, prompts, validation decisions, commit instructions | Stop on dirty overlap, unresolved validation failure, missing criteria |
| phase1_domain_worker | gpt-5.3-codex | medium | yes | WU-02/WU-03/WU-04 only; workspace service/schema/repository/tests | Exact Phase 1 prompt below | Implemented domain foundation, focused tests, changed-file report | Stop on path/tag/log uncertainty or out-of-scope edit need |
| phase1_validator | gpt-5.3-codex | medium | no | Validate Phase 1 only | Exact Phase 1 validation prompt below | Pass/fail by criterion with commands/evidence | Stop on security/path/log/tag failure |
| viewer_api_worker | gpt-5.3-codex | medium | yes | WU-05/WU-08 only | Source suite and Phase 1 commit | Viewer/editor policies, API routes/tests | Stop on encoding, unsafe markdown, controller filesystem logic |
| upstream_research_worker | gpt-5.3-codex | medium | no | SimplyPages docs/demo/source review only | `04-upstream-simplypages-pr-plan.md` plus upstream repo guidance | Proposed generic module API and risks | Stop if upstream contract depends on unstable Magenta internals |
| upstream_module_worker | gpt-5.3-codex | medium | yes, upstream only | WU-09 in SimplyPages isolated branch/clone | `04-upstream-simplypages-pr-plan.md`, stable Magenta view model | Upstream commit and draft PR | Stop on dirty upstream overlap or Magenta-specific leakage |
| magenta_ui_worker | gpt-5.3-codex | medium | yes | WU-06/WU-07 only | Phases 1-3 outputs | HTMX explorer UI and first picker integration | Stop on JS transport CRUD, visual breakage, absolute path leakage |
| docs_closeout_worker | gpt-5.3-codex | medium | yes | WU-10 and `07-closeout-plan.md` artifacts only | Implemented behavior and validation evidence | Docs, changelog, knowledge, focus/decisions where applicable | Stop if docs exceed implemented behavior |
| validation_redteam_agent | gpt-5.3-codex | medium | no by default | Independent validation, Playwright, red-team checks | `06-validation-redteam-plan.md`, implementation branch | Evidence-backed pass/fail report and screenshot paths | Stop if Playwright cannot run without user-approved deferral |
| remediation_worker | gpt-5.3-codex | medium | yes | Only failed criterion files assigned by orchestrator | Validator report and relevant plan files | Minimal fix and rerun failed checks | Stop if fix requires plan/spec expansion |
| advanced_planning_agent | gpt-5.3-codex | high | no | Criteria/spec revision only | Failed validation showing plan insufficiency | Revised plan/gate recommendation | Stop after producing revised planning artifact |

Every worker and validator must read the relevant `.internal-dev/plans/workspace-file-explorer/` files first and may update only applicable `.internal-dev/plans/workspace-file-explorer/` planning/progress artifacts unless its phase explicitly assigns closeout files.

## Exact Phase 1 Implementation Prompt

```text
You are implementation_worker_agent using model gpt-5.3-codex with reasoning effort medium.

Repository: /home/hickelpickle/Code/Java/magenta2
Branch: feature/workspace-file-explorer
Assignment: Phase 1 Workspace Domain Foundation, covering WU-02, WU-03, and WU-04 only.

Read first, in this order:
1. AGENTS.md
2. .internal-dev/AGENTS.md
3. .internal-dev/focus/AGENTS.md
4. .internal-dev/plans/workspace-file-explorer/00-specification-lock.md
5. .internal-dev/plans/workspace-file-explorer/03-domain-work-units.md sections WU-02, WU-03, WU-04
6. .internal-dev/plans/workspace-file-explorer/05-orchestration-handoff.md
7. .internal-dev/plans/workspace-file-explorer/execution-orchestration.md
8. src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md
9. src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md, if present/relevant

Do not touch .internal-dev/inbox/queue.md or .internal-dev/inbox/read.md.
Do not edit UI, controllers, docs, Avatar files, SimplyPages files, or closeout files in this phase unless a package guide update is required by a material responsibility change.

Ownership boundary:
- You may edit WorkAreaExplorerService and add workspace package records/classes/services/repositories.
- You may edit src/main/resources/schema.sql.
- You may add/update workspace package tests.
- You may update only applicable .internal-dev/plans/workspace-file-explorer/ progress/evidence notes if needed.

Implement:
- Explicit root/path request/result records for explorer operations.
- Reusable path normalization, root confinement, absolute/traversal rejection, separator normalization, and symlink escape rejection.
- Service-owned list, preview metadata, create folder, create .txt, create .md, rename, move, copy, delete preflight, delete execute, and compatibility wrappers for existing APIs until controllers migrate.
- DB-backed generic file labels/tags with system labels note and work-area, add/remove/list operations, and metadata follow helpers for rename/move/copy/delete.
- Durable file action logging for successful mutations and meaningful failed preflight attempts, without file contents or host absolute paths. Inspect orchestration_events semantics before choosing the final sink; prefer a minimal workspace_file_actions table if orchestration_events would trigger automation or blur semantics.

Stop immediately and report if:
- Any path traversal, absolute path, separator trick, symlink, stale path, or root mutation cannot be conclusively rejected.
- Recursive delete could traverse or delete symlink targets.
- Tags cannot follow Magenta-managed rename, move, or copy.
- Destructive actions can complete without durable log evidence.
- The action log would need to store file contents or host absolute paths.
- You need to edit files outside the Phase 1 ownership boundary.
- Dirty worktree state overlaps your targets.

Run and report:
git status --short --branch
mvn test -Dtest=WorkAreaExplorerServiceTest,WorkspaceFileMetadataRepositoryTest,WorkspaceFileMetadataServiceTest,WorkspaceFileActionLogRepositoryTest,WorkspaceFileActionServiceTest
git status --short

Expected final output:
- Summary of implemented Phase 1 behavior.
- Exact changed files.
- Exact test commands and pass/fail results.
- Any stop-rule risks or deferred questions.
- Do not commit unless the orchestrator explicitly authorizes the Phase 1 commit after validation.
```

## Exact Phase 1 Validation Prompt

```text
You are validation_redteam_agent using model gpt-5.3-codex with reasoning effort medium.

Repository: /home/hickelpickle/Code/Java/magenta2
Branch: feature/workspace-file-explorer
Assignment: Validate Phase 1 Workspace Domain Foundation only. Do not remediate.

Read first:
1. AGENTS.md
2. .internal-dev/AGENTS.md
3. .internal-dev/focus/AGENTS.md
4. .internal-dev/plans/workspace-file-explorer/00-specification-lock.md
5. .internal-dev/plans/workspace-file-explorer/03-domain-work-units.md sections WU-02, WU-03, WU-04
6. .internal-dev/plans/workspace-file-explorer/06-validation-redteam-plan.md
7. .internal-dev/plans/workspace-file-explorer/execution-orchestration.md
8. Phase 1 worker's final report and changed-file list

Do not touch .internal-dev/inbox/queue.md or .internal-dev/inbox/read.md.
Do not edit product code. Do not run Playwright for Phase 1 unless the orchestrator expands scope; Phase 1 is service/schema/repository validation.

Validate:
- AC1 root confinement: traversal, absolute paths, mixed separators, symlink file escape, symlink directory component escape, stale paths, empty/root path, root delete/rename/move/copy rejection.
- AC3 service mutation behavior: create folder, create .txt, create .md, rename, move, copy, delete preflight, delete execute, collision rejection, move-into-descendant rejection.
- AC4 tags: generic DB labels, note and work-area labels, add/remove/list, rename/move/copy/delete follow behavior, external missing-file orphan handling without crash.
- AC5 action log: durable rows for create, rename, copy, move, delete, tag add/remove, and failed preflight attempts where implemented; no file contents or host absolute paths in logs.
- AC6 service support for modal delete flow: file one-step and directory two-step preflight state.
- AC7/AC9/AC10 only to the extent Phase 1 created service metadata/contracts for later viewer/picker work.

Run:
git status --short --branch
mvn test -Dtest=WorkAreaExplorerServiceTest,WorkspaceFileMetadataRepositoryTest,WorkspaceFileMetadataServiceTest,WorkspaceFileActionLogRepositoryTest,WorkspaceFileActionServiceTest

If tests are missing for a required Phase 1 proof, inspect code and report the missing test as a validation failure unless the criterion is explicitly assigned to a later phase.

Report format:
- Overall result: PASS or FAIL.
- Pass/fail by AC and WU-02/WU-03/WU-04.
- Commands run and exact result.
- Evidence: test names, relevant file/line references, and any manual code-review checks.
- Blocking findings with severity and suggested remediation ownership.
- Residual risks.

Hard fail if:
- Any path escape or root mutation succeeds or is not tested.
- Recursive delete can follow symlink targets.
- Tags do not follow Magenta-managed rename/move/copy.
- Destructive actions can complete without durable log entry.
- Logs include file contents or host absolute paths.
- Worker edited outside Phase 1 scope without recorded approval.
```

## Red-Team And Remediation Policy

- Security/path/encoding/destructive-action failures are blocking.
- UI visual failures block only after UI phases, but visual validation must include screenshots and critique.
- Implementation drift returns to a scoped remediation worker.
- Spec/criteria insufficiency returns to `advanced_planning_agent`; do not let workers invent new scope.
- Non-blocking polish may be deferred only with user approval and an `.internal-dev/focus/unfinished-work.md` entry.
- Do not advance to another mutating unit while validation failures remain unresolved unless the user explicitly accepts the risk and the blocker is recorded.

## Integration Strategy

Magenta owns workspace roots, root-relative paths, confinement, mutations, labels, action logging, Work Area semantics, Avatar integration, route handlers, and persistence.

SimplyPages owns only generic server-rendered module/view-model UI: explorer shell, picker shell, breadcrumbs, toolbar, card/list entries, inspector/viewer slots, confirmation modal patterns, configured HTMX attributes, neutral CSS, and demos.

Upstream integration should use a stable Magenta view-model contract after Phase 1 validation. Upstream work can start as non-mutating research before then, but mutating upstream implementation should wait until the Phase 1 domain contract is known and should pause if Phase 2 changes viewer/editor slot requirements.

## Final Closeout Requirements

Before final user report after implementation:

- Magenta phase commits exist and exclude unrelated dirty files.
- Upstream SimplyPages branch is committed and draft PR URL is recorded, if Phase 3 proceeded.
- Magenta docs and API docs reflect implemented routes, limits, schemas, and caveats.
- `.internal-dev/changelogs/`, `.internal-dev/knowledge/`, `.internal-dev/focus/unfinished-work.md`, `.internal-dev/focus/architecture-focus.md`, and `.internal-dev/focus/decisions.md` are updated only where applicable.
- Bugs discovered out of scope are logged under `.internal-dev/bugs/` and mirrored to GitHub Issues.
- Final validation report includes command results, Spring startup result, Playwright screenshot paths, pass/fail by AC/VC, browser console/network notes, blocker/remediation history, commits, PR links, and residual risks.


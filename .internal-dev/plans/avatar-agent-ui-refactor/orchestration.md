# Avatar Agent UI Refactor Orchestration Plan

## Source Plan Summary

This orchestration turns `implementation-plan.md` into a staged implementation campaign. It is deliberately strict about ownership because the target touches large shared files: Avatar UI, Avatar persistence, runtime workspace services, assignment schema, output routing, explorer endpoints, planner organizer data, submit forms, docs, and validation.

## Shared Notes

Use `.codex-orchestration/avatar-agent-ui-refactor/notes.md`.

Workers must read it before starting and append concise results before finishing. The coordinator owns shared-note structure and serial merge decisions.

## Global Rules

- Use one branch, no worktrees unless the user explicitly changes the constraint.
- Keep the main thread open as coordinator.
- Code-editing work is serial. Non-mutating review, validation design, and documentation review may run in parallel.
- Every worker must declare owned paths before editing.
- Stop on unexpected dirty files in owned paths.
- Stage explicit paths only.
- Commit at the end of each completed phase or tightly coupled phase group.
- Use `gpt-5.3-codex` with medium reasoning for all testing and Playwright validation agents, per repo instruction.
- UI validation must be delegated to a Playwright validation subagent and include screenshots.

## Execution Graph

### Group A: Non-Mutating Design Review, Parallel

Run these before code edits if implementation begins from this suite:

1. Workspace/runtime reviewer:
   - Verify exact runtime alias construction path and output materialization call sites.
   - Output: file list and risks.
2. SimplyPages/UI reviewer:
   - Verify best SimplyPages components and demo patterns for row/column decorator controls.
   - Output: recommended component structure and raw-HTML avoid list.
3. Submit-surface reviewer:
   - Inventory all routes/forms that enqueue `AssignmentRequest`.
   - Output: complete target list and plan-chat exclusions.

These agents do not modify files.

### Phase 01: Avatar Layout Persistence

Owner:

- `src/main/resources/avatar-schema.sql`
- `src/main/java/io/mindspice/magenta2/avatar/**`
- `src/test/java/io/mindspice/magenta2/avatar/**`

Validation:

- Avatar repository/service tests.
- Migration tests from old layout.

Commit:

- `Add Avatar row widget layout persistence`

### Phase 02: Layout Editor UI

Owner:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- new Avatar web component files
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`

Serialization:

- Do not overlap with explorer/planner web edits.

Validation:

- Controller tests for layout fragments.
- Spring startup if route wiring changed.
- Focused Playwright screenshots for `/avatar` view/edit.

Commit:

- `Redo Avatar dashboard layout editor`

### Phase 03: Work Area Persistence

Owner:

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/**`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/**`

Serialization:

- `schema.sql` is tightly gated; no other schema worker can run.

Validation:

- Workspace confinement/path tests.
- Work Area mark/unmark/default Home tests.

Commit:

- `Add runtime Work Area persistence`

### Phase 04: Assignment Runtime And Output Routing

Owner:

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/**`
- selected output resolver/materialization call sites
- runtime tests

Serialization:

- Must start after Phase 03.
- Coordinate any edits to `PlanService`, workflow runner, job services, and output services.

Validation:

- Assignment repository/service tests.
- Output routing tests.
- Runtime alias proof tests.

Commit:

- `Route assignments through selected Work Areas`

### Phase 05: Explorer UI And Work Area Widget

Owner:

- `AvatarDashboardController`
- `AvatarDashboardComponents`
- new explorer components/services if web-layer only
- `avatar-dashboard.css`
- controller tests

Serialization:

- Starts after Phase 03 and after Phase 02 web edits are merged.

Validation:

- Controller tests for browse/preview/edit/create/rename/delete/mark/unmark.
- Negative tests for traversal, symlink, protected delete, and unsafe edit.
- Playwright screenshots for explorer modal.

Commit:

- `Add Avatar Work Area explorer`

### Phase 06: Planner Organizer Data And Modals

Owner:

- `avatar-schema.sql`
- `src/main/java/io/mindspice/magenta2/avatar/**`
- `AvatarDashboardController`
- `AvatarDashboardComponents`
- web/avatar tests

Serialization:

- Starts after Phase 02 web pattern is stable.
- Do not overlap with explorer web edits.

Validation:

- Recurrence projection tests.
- Planner CRUD/modal controller tests.

Commit:

- `Add Avatar planner organizer modals`

### Phase 07: Submit Form Integration

Owner:

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- reusable picker components under web package
- related controller tests

Serialization:

- Starts after Phase 04.
- `OrchestrationController.java` is large and shared; single worker only.

Validation:

- Submit form controller tests.
- Template schedule/reaction field persistence tests.
- Browser screenshots for submit pickers.

Commit:

- `Add Work Area pickers to assignment submits`

### Phase 08: Final Docs, Internal Dev, And Validation

Owner:

- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- new docs under `docs/technical/`
- package `AGENTS.md` if conventions changed
- `.internal-dev/changelogs/**`
- `.internal-dev/knowledge/**`
- `.internal-dev/focus/**`
- plan archive/handoff files

Validation:

- Full selected `mvn test`.
- Bounded Spring startup.
- Playwright subagent screenshots across `/avatar`, `/dashboard`, `/agents`, submit surfaces, desktop/mobile.
- Red-team checklist in `validation-red-team.md`.

Commit:

- `Finalize Avatar Work Area UI refactor docs`

## Subagent Roster

### Runtime Reviewer

- Model: inherited or `gpt-5.3-codex` medium if testing/validation.
- May modify files: no.
- Scope: runtime aliases, output materialization, assignment request creation.
- Expected output: exact files/methods, risks, and recommended phase ordering.

Prompt summary:

> Read `.internal-dev/plans/avatar-agent-ui-refactor/implementation-plan.md`, `.codex-orchestration/avatar-agent-ui-refactor/notes.md`, workspace/runtime package guides, and inspect assignment/output runtime code. Do not edit files. Report exact files and methods needed for selected Work Area aliases and output routing, plus compatibility risks.

### SimplyPages UI Reviewer

- Model: inherited.
- May modify files: no.
- Scope: SimplyPages docs/demo and current Avatar components.
- Expected output: component design, HTMX/OOB route contract, styling cautions.

Prompt summary:

> Read the Avatar plan, style guideline note, SimplyPages editing docs, and demo editing controller. Do not edit files. Recommend the concrete SimplyPages-native structure for row/column widget editing and identify any raw HTML fallbacks that are justified.

### Submit Surface Reviewer

- Model: inherited.
- May modify files: no.
- Scope: all assignment enqueue forms/routes and plan-chat exclusions.
- Expected output: route list and field propagation map.

Prompt summary:

> Inspect web controllers for every path that builds `AssignmentRequest` or assignment templates. Do not edit files. Return the complete list of submit surfaces that need Work Area/output routing controls and the plan-chat routes that must remain unchanged.

### Implementation Workers

Use one worker per phase. Each worker:

- reads the plan suite and shared notes;
- declares owned paths;
- checks `git status --short`;
- edits only owned paths;
- runs focused tests;
- appends notes;
- returns changed files, validation results, blockers.

### Validation Workers

Use `gpt-5.3-codex` medium. They may not modify production code. They can create local screenshots and validation notes under target output directories when requested.

### Closeout Review Worker

After all implementation phases, run a non-mutating review to verify:

- docs updated;
- `.internal-dev` workflow complete;
- package guides updated if boundaries changed;
- commits are phase-scoped;
- final validation evidence is adequate;
- no untracked implementation artifacts remain.

## Remediation Policy

- Failed focused tests block the next code-editing phase.
- Failed Playwright validation blocks UI phase sign-off.
- Failed runtime proof blocks completion of Work Area/output routing.
- Security red-team failures must be fixed unless the user explicitly approves a deferred blocker.
- Deferred blockers go into `.internal-dev/focus/unfinished-work.md` and must be called out in the final response.

## Commit And Push Policy

- Commit after each completed implementation phase.
- Use explicit path staging.
- Before push, run `git status --short` and `git log --oneline --decorate -n 8`.
- Push the branch to `origin`.
- Final summary must include commit hash or PR/branch link.

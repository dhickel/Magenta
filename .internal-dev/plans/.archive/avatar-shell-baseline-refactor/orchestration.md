# Avatar Shell Baseline Refactor Orchestration Plan

## Source Plan Summary

This orchestration maps `implementation-plan.md` into a strict execution sequence for a UI-heavy refactor that touches shared Avatar web files, shell state persistence, CSS, narrow frontend JS, and delegated browser validation. The implementation must preserve the current Avatar runtime boundary and existing in-place dashboard editing while replacing the shell behavior around it.

## Shared Notes

Use `.internal-dev/plans/avatar-shell-baseline-refactor/shared-notes.md`.

Every agent must:

- read the shared notes before work;
- append concise notes before finishing;
- avoid overwriting or reverting work from other agents.

## Global Rules

- Planning is complete before implementation begins; do not improvise additional architecture.
- Create a dedicated feature branch before the first implementation phase.
- Code-editing work is strictly serial.
- Non-mutating review and validation-design tasks may run in parallel.
- `AvatarDashboardController.java`, `AvatarDashboardComponents.java`, `avatar-dashboard.css`, and Avatar shell JS are treated as high-conflict files with single-writer ownership.
- All Playwright validation is delegated and must use `gpt-5.3-codex` with reasoning `high`.
- Closeout includes docs plus `.internal-dev` updates.

## Execution Graph

### Group A: Parallel Non-Mutating Prep

Run these first because they inform the edit phases without creating write conflicts.

1. Shell/UI pattern reviewer
   - Model: `gpt-5.4`
   - Reasoning: `high`
   - Scope: compare Avatar shell requirements against `/agents`, `/dashboard`, and current Avatar component structure.
   - Output: exact component/helper targets, CSS reuse guidance, and anti-pattern warnings.

2. Data-source reviewer for Queue/History/Profile/Outputs/Work Areas
   - Model: `gpt-5.4`
   - Reasoning: `high`
   - Scope: inventory the exact service calls and fragments to reuse for Avatar tabs without a new data model.
   - Output: target methods, gaps, and any coupling risks.

3. Validation-design reviewer
   - Model: `gpt-5.3-codex`
   - Reasoning: `high`
   - Scope: define exact Playwright checks and screenshot set for the future validation run.
   - Output: executable validation checklist aligned with `validation-red-team.md`.

### Phase 01: Shell State And Fragment Contract

Owner files:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`

Model:

- `gpt-5.4`
- reasoning `high`

Goal:

- Add the tab-route contract, URL normalization, and tab-fragment behavior for the unified Avatar shell.

Validation gate:

- Focused controller tests for shell-state route defaults and tab normalization.

### Phase 02: Unified Avatar Shell And Tabs

Owner files:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`

Model:

- `gpt-5.4`
- reasoning `high`

Goal:

- Replace the toolbar shell with unified header plus tab row plus persistent chat rail container.
- Remove Organizer and manual refresh from visible shell chrome.
- Ensure dashboard remains default and only editable tab.

Validation gate:

- Controller tests for shell render and tab-panel structure.
- Visual spot-check screenshots after this phase before proceeding to deeper tab work.

### Phase 03: Row Decoration Layering And Dashboard Chrome

Owner files:

- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`
- `AvatarDashboardControllerTest.java`

Model:

- `gpt-5.4`
- reasoning `high`

Goal:

- Introduce first-class row decoration rendered above widget chrome.
- Convert edit entry to compact icon-first controls inside the new shell/dashboard header area.

Validation gate:

- Markup assertions for row decoration.
- Browser screenshot showing row controls above widget controls.

### Phase 04: Non-Dashboard Avatar Tabs

Owner files:

- `AvatarDashboardController.java`
- `AvatarDashboardComponents.java`
- `AvatarDashboardControllerTest.java`

Model:

- `gpt-5.4`
- reasoning `high`

Goal:

- Implement queue/history/profile/outputs/work-areas tab panels from existing service families.
- Keep them non-editable and visually aligned with agent operational panels.

Validation gate:

- Controller tests for each fragment route.
- Browser tab-switch check that the chat rail persists and no full page reload occurs.

### Phase 05: Divider Resize And Shell JS

Owner files:

- `src/main/resources/static/js/avatar-shell.js`
- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`
- `AvatarDashboardController.java`
- `AvatarDashboardControllerTest.java`

Model:

- `gpt-5.4`
- reasoning `high`

Goal:

- Add narrow desktop-only divider drag behavior and width persistence.

Validation gate:

- Manual/controller-level proof of persistence endpoint behavior.
- Browser validation of width persistence across tab switches and reload.

### Phase 06: Docs, Deferred Work, And Final Validation

Owner files:

- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `.internal-dev/focus/unfinished-work.md`
- any required changelog/knowledge closeout files

Model:

- `gpt-5.4`
- reasoning `high`

Goal:

- Update docs, capture deferred auto-refresh work, and run final validation suite.

Validation gate:

- Docs consistency review.
- Delegated Playwright pass.
- Bounded Spring startup.

## Subagent Roster

### Planning Agent

- Model: `gpt-5.5`
- Reasoning: `high`
- Role: senior plan reviewer
- Status: planning-only input to this suite; no code changes

Prompt summary:

> Produce a decision-complete implementation plan for the Avatar shell baseline refactor, with explicit attention to shell tabs, dashboard-only editing, persistent right chat rail, row-decoration layering, no Organizer, and deferred refresh policy.

### Shell/UI Reviewer

- Model: `gpt-5.4`
- Reasoning: `high`
- May modify files: no
- Scope: compare current Avatar shell to `/agents` and `/dashboard`, recommend exact helper/component structure

Prompt summary:

> Read the Avatar shell baseline plan, current Avatar components/CSS, and agent tab patterns. Do not edit files. Return exact render/helper boundaries, CSS reuse opportunities, and visual pitfalls to avoid.

### Runtime/Data Reviewer

- Model: `gpt-5.4`
- Reasoning: `high`
- May modify files: no
- Scope: inventory the service/data sources for Avatar queue/history/profile/outputs/work-areas tabs without a new data model

Prompt summary:

> Inspect Avatar controller code and orchestration service usage. Do not edit files. Identify the exact existing service calls and route patterns that Avatar tabs should compose, plus any coupling risks.

### Implementation Worker 1: Shell State And Fragments

- Model: `gpt-5.4`
- Reasoning: `high`
- May modify files: yes
- Ownership:
  - `AvatarDashboardController.java`
  - `AvatarDashboardControllerTest.java`

Prompt summary:

> You own Avatar shell-state routes and tab-fragment behavior only. Read the shared notes first. You are not alone in the codebase; do not revert others' work. Implement URL-backed active-tab handling plus tab fragment routes, then update tests and report exact changed files.

### Implementation Worker 2: Shell Layout And Dashboard Chrome

- Model: `gpt-5.4`
- Reasoning: `high`
- May modify files: yes
- Ownership:
  - `AvatarDashboardComponents.java`
  - `avatar-dashboard.css`
  - `AvatarDashboardControllerTest.java`

Prompt summary:

> You own the unified Avatar shell markup, tab row, dashboard-only edit chrome, and row decoration layering. Read the shared notes first. You are not alone in the codebase; do not revert others' work. Remove Organizer/manual refresh from visible shell UI, keep dashboard editing in place, and report exact changed files.

### Implementation Worker 3: Divider Resize JS

- Model: `gpt-5.4`
- Reasoning: `high`
- May modify files: yes
- Ownership:
  - `src/main/resources/static/js/avatar-shell.js`
  - `AvatarDashboardComponents.java`
  - `avatar-dashboard.css`
  - `AvatarDashboardController.java`

Prompt summary:

> You own the narrow JS needed for desktop divider drag and persistence. Read the shared notes first. You are not alone in the codebase; do not revert others' work. Keep JS minimal, mobile-safe, and browser-local-state-backed, then report exact changed files and validation performed.

### Playwright Validation Agent

- Model: `gpt-5.3-codex`
- Reasoning: `high`
- May modify files: no
- Scope: full focused UI validation loop only

Prompt summary:

> Run the focused Playwright validation loop for the Avatar shell baseline refactor. Validate desktop and mobile `/avatar`, `/avatar?edit=true`, tab switches, divider persistence, chat rail persistence, and visual consistency against `/agents`. Use screenshots and image-based critique. Report failures precisely and do not modify files.

## Remediation Policy

- If any controller/service test fails after a code-edit phase, stop and remediate before moving on.
- If Playwright finds visual regressions, run one remediation edit phase at a time, then rerun only the failed validation slice before broad final validation.
- If the bounded startup check fails due to missing secrets or required local services, stop and record the specific blocker instead of marking the work complete.

## Final Validation Scope

- Targeted Avatar controller/service tests
- Any affected orchestration/web tests for reused panel sources
- Bounded Spring startup
- Delegated Playwright screenshots and critique
- Docs consistency
- `.internal-dev` deferred-work and closeout verification

## Integration And Handoff

- Merge serial phases only after each validation gate passes.
- Keep the shared notes file current enough that the next worker does not have to rediscover state.
- Final synthesis must report:
  - files changed;
  - validation after each edit phase;
  - whether deferred auto-refresh was recorded;
  - any remaining UX inconsistencies found by Playwright.

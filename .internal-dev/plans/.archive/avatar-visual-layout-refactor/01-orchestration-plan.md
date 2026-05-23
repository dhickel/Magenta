# Avatar Visual Layout Refactor Orchestration Plan

## Source Plan Summary

The work corrects `/avatar` UI quality and agent process. It must produce durable instructions, a reusable SimplyPages knowledge file, in-place layout editing, visual Playwright proof, email checkpoints, and a final wait for Dwight's email instructions.

## Shared Notes

Use `.codex-orchestration/avatar-visual-layout-refactor/notes.md`.

Every agent must read the shared notes before work and append concise results before finishing.

## Model Assignments

- Planning and research: `gpt-5.5`, reasoning `xhigh`.
- Implementation: `gpt-5.3-codex`, reasoning `high`.
- Playwright and test validation: `gpt-5.3-codex`, reasoning `medium`.
- Email monitoring: lightweight AgentMail/email-followup workflow.

## Execution Graph

### Phase 00: Research And Process Artifacts

Owner: coordinator plus read-only research agent.

Outputs:

- Plan suite under `.internal-dev/plans/avatar-visual-layout-refactor/`.
- Shared notes file.
- Root `AGENTS.md` updates.
- Avatar package `AGENTS.md` updates.
- SimplyPages Avatar knowledge file.

Validation:

- Verify instructions name visual Playwright criteria, scratch page rules, and SimplyPages source references.

Email:

- Send a phase-complete email with high-level status and detailed artifact list.

### Phase 01: Visual Shell Correction

Owner: single implementation agent or coordinator.

Primary files:

- `AvatarDashboardController.java`
- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`

Required behavior:

- Compact the Avatar shell.
- Use full dashboard width responsibly.
- Keep chat/status rail integrated, not stranded.
- Keep multiple useful widgets in the first viewport.

Validation:

- Focused controller tests if route output changes materially.
- Desktop and mobile Playwright screenshots.

Email:

- Send phase-complete email with screenshots path, changes, and remaining risks.

### Phase 02: In-Place Decorated Editing

Owner: single implementation agent or coordinator.

Primary files:

- `AvatarDashboardController.java`
- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`
- Avatar layout tests if behavior changes.

Required behavior:

- Real grid renders editable decoration controls.
- Move/resize/add-row/add-widget/delete controls operate on the actual dashboard surface.
- Module detail icon is available outside edit mode and opens module-specific modal/drawer.
- Separate modal layout editor is removed, demoted, or retained only as fallback with no sign-off dependency.

Validation:

- HTMX route checks for every layout mutation.
- Playwright edit-mode screenshot and interaction pass.

Email:

- Send phase-complete email with control coverage and any visual defects fixed.

### Phase 03: Scratch Page And Knowledge Reinforcement

Owner: implementation/documentation agent.

Required behavior:

- Add internal scratch route only if useful during planning/validation.
- Do not link it from production nav.
- Do not cite scratch as source truth in knowledge docs.
- Extract durable lessons into `.internal-dev/knowledge/`.

Validation:

- If scratch exists, screenshot it and confirm it is isolated.
- Verify knowledge docs cite SimplyPages docs/demo and production files, not scratch.

Email:

- Send phase-complete email describing any scratch use and extracted lessons.

### Phase 04: Final Validation And Closeout

Owner: validation agent plus coordinator.

Required checks:

- Relevant unit/controller tests.
- Bounded Spring startup.
- Playwright desktop/mobile normal and edit-mode screenshots.
- Visual critique against explicit criteria.
- `.internal-dev` closeout: changelog, knowledge, focus decisions/unfinished work if applicable.
- Docs updates where user-facing behavior changed.
- Git status review and commit.

Email:

- Send final HTML/plain-text report.
- Start email wait and process more than one message if multiple arrive.

## Subagent Roster

### SimplyPages/Avatar Research Agent

- Model: `gpt-5.5`.
- Reasoning: `xhigh`.
- May modify files: no.
- Scope: current Avatar UI code, SimplyPages docs/demo, style notes, prior Avatar plan artifacts.
- Output: exact implementation constraints, file references, and ambiguities.

### Implementation Agent

- Model: `gpt-5.3-codex`.
- Reasoning: `high`.
- May modify files: yes, one phase at a time.
- Scope: only files named by the active phase.
- Rule: do not revert or overwrite other work; stop on unexpected dirty owned paths.

### Playwright Visual Validation Agent

- Model: `gpt-5.3-codex`.
- Reasoning: `medium`.
- May modify files: no, except screenshots/log artifacts under `target/`.
- Scope: run application, capture screenshots, interact with edit mode, inspect visual quality.
- Output: pass/fail visual critique with paths to screenshots and concrete defects.

### Email Monitor

- May modify files: no.
- Scope: AgentMail inbox for Dwight messages after wait start.
- Rule: report multiple qualifying messages, not just the first, when they are available.

## Remediation Policy

- Failed visual validation blocks sign-off.
- Trivial Playwright environment issues may be retried once.
- If Playwright cannot run, report the blocker by email and chat; do not mark UI complete.
- If a SimplyPages limitation blocks clean implementation, file a local bug/idea and ask whether to fix SimplyPages directly.

## Closeout Contract

- Changelog in `.internal-dev/changelogs/`.
- Durable SimplyPages/UI lessons in `.internal-dev/knowledge/`.
- Focus updates if this changes Avatar current focus or architecture direction.
- Documentation updates in `docs/` for user-visible edit behavior.
- Git commit that includes implementation plus `.internal-dev` artifacts.

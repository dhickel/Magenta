# Phase 05 Worker Directive: Skill Browser, Editor, And Guided Creation UI

## Objective

Build the user-facing Skill browser/editor MVP with browse, detail, diagnostics, directory overview, file viewer/editor, add/edit file, `SKILL.md` editing, agent assignment controls, and guided skill creation.

## Agent Assignment

- Worker: `implementation_worker_agent`, `gpt-5.3`, xhigh reasoning.
- Validator: `validation_redteam_agent`, `gpt-5.5`, high reasoning.
- Browser proof: Playwright/browser validation agent after code-level validator provides a concrete checklist.

## Required Reading

- Phase 04 implementation report and validator result.
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/knowledge/entity-selector-htmx-pattern.md`
- `.internal-dev/knowledge/plans-list-status-chip-and-delete-pattern.md`
- `.internal-dev/knowledge/operational-ui-htmx-inline-editing-pattern.md` if directly relevant.
- SimplyPages docs and demos relevant to HTMX fragments, components/modules, and editing patterns:
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo`
- Existing Magenta UI patterns:
  - `/plans` master/detail in `OrchestrationController`
  - Work Area/project file editor fragments
  - `EntitySelectorComponents`

## Editable Targets

- `src/main/java/io/mindspice/magenta2/api/web/` skill page/fragments/controllers.
- `src/main/resources/static/css/orchestration.css` or a skill-specific CSS file if existing patterns require it.
- Narrow JavaScript only if explicitly justified and scoped.
- Controller/UI tests under `src/test/java/io/mindspice/magenta2/api/web/`.
- Docs/specs/knowledge if UI behavior or reusable decisions change.

## Forbidden Scope

- Do not implement backend parser/assignment behavior here beyond tiny gaps found by UI wiring.
- Do not use raw HTML strings when SimplyPages components/functions cover the need, except for unavoidable advanced fragment cases consistent with existing code.
- Do not use a marketing/landing-page layout.
- Do not expose a general app-root file manager.
- Do not add large client-side state for standard CRUD/list/detail interactions.
- Do not execute skill scripts.

## Experience Contract

- Layout: dense operational master/detail page with skill list/filter on the left and selected skill detail/editor on the right at desktop widths; stacked but usable on mobile.
- List rows: skill name, status chip, short description, assignment indicator, diagnostics marker.
- Detail: metadata summary, validation diagnostics, optional directory indicators, file tree/list, selected file viewer/editor, and assignment panel.
- Editor: `SKILL.md` text editing is prominent, with save, refresh/revalidate, and diagnostics feedback. Textarea sizing must avoid cramped or overflowing controls.
- File creation: users can add safe text files under allowed skill subpaths. The UI should make `scripts/`, `references/`, and `assets/` visible without implying scripts run in Magenta.
- Guided creation: Q&A flow inspired by saved plan chat/builder asks for skill name, when-to-use description, workflow instructions, optional references/scripts/assets, then writes a valid scaffold.
- Agent assignment: reuse selector patterns where practical; updates happen through HTMX fragments and are reflected without full reload.
- Visual failure examples to avoid: stranded empty columns, oversized hero blocks, nested decorative cards, clipped file paths, buttons with overflowing text, hidden diagnostics, editor forms that push the real content below the fold, or mobile stacking that makes save/assign controls unreachable.

## Implementation Steps

1. Inspect relevant SimplyPages docs/demo code before UI edits and name what was used in the report.
2. Add or complete `/skills` page and fragments around Phase 04 APIs/services.
3. Add navigation entry in the appropriate operational nav.
4. Implement list/filter/detail fragments with status chips and diagnostics.
5. Implement file viewer/editor and add-file controls using server-side validation.
6. Implement assignment controls with HTMX and reusable selector behavior where practical.
7. Implement guided creation:
   - server-backed draft state or form steps;
   - scaffold generation for valid `SKILL.md`;
   - safe slug/name validation;
   - clear diagnostics after creation.
8. Add controller/rendering tests for key fragments and negative states.
9. Prepare concrete Playwright checklist for validator/browser agent.
10. Update docs/knowledge for UI reuse decisions and any JS justification.

## Acceptance Criteria

- Users can browse skills, inspect details, view diagnostics, view/edit `SKILL.md`, add/edit files, refresh validation, create a guided skill scaffold, and assign/unassign skills to agents.
- UI uses HTMX/server fragments for normal CRUD/list/detail flows.
- Any JavaScript is narrow and justified in docs/worker report.
- UI is responsive and visually consistent with Magenta's operational console.
- Browser validation screenshots and visual critique pass on desktop and mobile.

## Negative Checks

- No route/page claims project-local skill loading is active.
- No UI lets users escape the skill repository root.
- No UI hides malformed skills entirely if diagnostics should be shown.
- No UI eagerly loads optional resource file contents except selected/viewed files.
- No browser screenshot shows major overflow, dead zones, incoherent gutters, or unreadable controls.

## Validation Commands

```bash
mvn -Dtest='*Skill*Controller*,*Skill*Web*,*OrchestrationController*' test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

The startup command is for worker/validator smoke evidence. Focused Playwright validation runs after code-level validation using the checklist below.

## Playwright Checklist For Browser Agent

- Start app with an isolated temp Magenta root containing:
  - one valid skill with `scripts/`, `references/`, and `assets/`;
  - one malformed skill with readable diagnostics;
  - at least one runtime agent.
- Desktop:
  - open `/skills`;
  - filter list;
  - select valid skill;
  - inspect metadata, directories, and diagnostics;
  - edit `SKILL.md`, save, refresh/revalidate;
  - add a text file under `references/`;
  - assign and unassign the skill to an agent;
  - run guided creation to create a new valid skill.
- Mobile:
  - verify list/detail stacking;
  - open editor;
  - verify controls remain reachable and text wraps.
- Capture screenshots and critique alignment, density, spacing, hierarchy, overflow, control affordances, and first-viewport usefulness.

## Stop Conditions

- Stop if SimplyPages docs/examples point to a reusable pattern that conflicts with planned UI architecture.
- Stop if UI requires backend behavior not available from Phase 04.
- Stop if Playwright cannot run against a live app; report blocker and do not mark UI fully validated.

## Do Not Close Unless

- UI implementation used or deliberately justified deviation from SimplyPages/HTMX patterns.
- Controller/rendering tests pass.
- Playwright checklist has been executed and reconciled by the validator.
- Desktop and mobile screenshots show a coherent operational UI.

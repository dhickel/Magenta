# Validation Checklist: Work Area UI Polish

## Validator Role

Use `validation_redteam_agent` (`gpt-5.5` high). The validator is non-mutating except for simple one-place fixes allowed by policy: typos, stale links, missing imports, formatting, trivial literal/comparison mistakes, or similarly obvious few-line local corrections. Do not self-edit schemas, service security, API contracts, broad UI layout, dependencies, or multi-file logic.

## Code-Level Review

Verify against `00-specification-lock.md` and `worker-directive.md`:

- Inspector expanded has name/path/tags/Tag Editor/metadata/preview only.
- Inspector has no bottom View/Rename/Delete/Copy/Move action group.
- Old `Preview & Details` heading and explanatory viewer hint prose are removed.
- Collapsed inspector is an intentional slim rail/panel with visible expand icon and selected-path preserving route when available.
- Row action controls are icons with `aria-label`, `title`, consistent class sizing, and preserved HTMX route/target/swap behavior.
- Full-row selection still works and row action clicks are guarded from row navigation.
- `Close Workspace` label is present and behavior unchanged.
- Editor modal has full-window styling, resize affordance where feasible, top-right close, top-left Save/Undo/Redo/Revert icon actions, and real Edit/Preview/Split tabs.
- Plain text editor exposes only Edit mode.
- Markdown preview/split uses unsaved content without persisting; save remains explicit HTMX.
- Image previews are contained; text/markdown previews are bounded and sanitized/escaped.
- No path traversal, symlink, unsafe read, size-limit, service ownership, or Work Area layout security behavior is weakened.
- Docs and `.internal-dev` closeout updates are accurate; no stale claims that this work is planned/pending after completion.

## Automated Validation

Require command evidence:

- `git diff --check`
- Focused controller/fragment tests, at minimum `mvn -Dtest=AvatarDashboardControllerTest test`
- `mvn test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

If any command is skipped or blocked, record the exact blocker and decide whether it blocks sign-off under repo policy.

## Browser Validation Handoff

After code-level review passes, dispatch a separate Playwright/browser validation agent. Do not inline browser proof in the main implementation workflow. The browser agent should run against the styled `/avatar?tab=work-areas` surface with the app running.

Required coverage:

- Desktop and mobile screenshots of the Work Area browser with inspector expanded.
- Desktop and mobile screenshots of inspector collapsed and then re-expanded.
- Long filename/truncation case. Use an existing fixture if present; otherwise create a safe temporary Work Area fixture.
- Row action icons for Open/View, Rename, Delete, Copy, Move; verify tooltips/accessible labels where possible and that clicking actions does not trigger row selection/navigation.
- Directory/unavailable preview: inspector box says `Preview unavailable`.
- Image preview: thumbnail/contained image renders without stretching/overflow.
- Text preview: compact text excerpt renders bounded.
- Markdown preview: rendered markdown preview preserves spacing for lists, blockquotes, and code/code-like blocks where fixture content permits.
- Editor modal for markdown: Edit, Preview, Split modes; modal dimensions stable across toggles; unsaved preview updates without save; save persists and reopen shows saved content; undo/redo/revert work locally.
- Editor modal for plain text: only Edit mode appears.
- Modal resize affordance exists and resize does not break layout.
- Browser console and network logs show no relevant errors.
- Page-level horizontal overflow check at desktop and mobile.

Visual critique must explicitly assess:

- alignment, spacing, density, and hierarchy;
- clipped/overflowing controls or text;
- action hit targets and focus affordance;
- collapsed inspector usefulness;
- table/list width behavior;
- editor stability across modes;
- mobile stacking and first-viewport usefulness.

## Final Reconciliation

The same validator must reconcile Playwright artifacts before final pass/fail. Do not pass solely on automated tests.

Before final sign-off:

- Run a stale-reference sweep over docs and `.internal-dev` touched by this plan for old artifact paths, `/tmp` evidence, stale agent ids, `pending`, `planned`, `not implemented`, `TODO`, and outdated phase wording.
- Confirm `.internal-dev/changelogs/2026-05-27-workarea-ui-polish.md` or equivalent exists.
- Confirm finalized plan artifacts are ready to archive after completion.
- Confirm any model/tool fallback is recorded as TOOLING_CONSTRAINT in validation evidence.

## Repair Routing

- `code_defect`: fresh scoped implementation repair worker (`gpt-5.3` high by default) unless it is a trivial validator edit.
- `docs_or_evidence_defect`: fresh scoped repair worker unless trivial.
- `browser_harness_defect`: fix browser harness/evidence first; change product code only if browser evidence proves a product bug.
- `plan_defect`: return to planning for revised criteria.
- `validator_error`: correct the checklist or use a fresh validator.
- Second failure on the same targeted issue: use fresh scoped `gpt-5.5` high repair worker.

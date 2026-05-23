# Avatar Visual Layout Refactor Notes

## Global Assumptions

- The current `/avatar` screenshots are unacceptable even if core interactions technically work.
- Planning and research agents use `gpt-5.5` with `xhigh` reasoning when available.
- Implementation agents use `gpt-5.3-codex` with high reasoning.
- Validation and Playwright agents use `gpt-5.3-codex` with medium reasoning per repo policy.
- UI changes must include visual Playwright review, not only click-path confirmation.
- Email updates go to `dwight.hickel@gmail.com` after every phase and at final completion.

## Active Agents

- Coordinator: main Codex thread.
- Research agent: SimplyPages and Avatar UI read-only investigation.
- Email monitor: watches for Dwight email instructions during execution.

## Completed Work

- Created orchestration notes.

## Validation Results

- Pending.

## Remediation Notes

- Pending.

## Blockers

- None currently.

## Closeout Work

- Changelog, knowledge, focus updates, docs, and commit are required after implementation.

## Final Validation Status

- Pending.

## Handoff Notes

- Scratch UI surfaces are allowed for planning and layout validation only. They are not source-of-truth documentation.
- Phase 01/02 implementation pass: visual shell and in-place decorated edit mode compile cleanly.
- Added row/widget live decorations, widget detail modal route, and empty-row delete support.
- Playwright validation found blockers: detail trigger selector discoverability and add-widget overlay interception, plus visual dead zones/control density polish.
- Remediation in progress: detail trigger data attributes/text, inline add-widget catalog, normal-mode row fill CSS, tighter decoration/control styling.
- Full validation: `mvn -q test` passed.
- Startup validation: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` started successfully on an ephemeral port and was stopped by timeout.
- Playwright rerun artifacts: `target/playwright-avatar-visual-refactor-rerun/`; previous blockers passed; remaining edit-mode density tracked as deferred polish.

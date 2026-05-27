# Validation Checklist - Work Area Markdown Editor Follow-up

## Validator Role

Use `validation_redteam_agent` with `gpt-5.5` high reasoning when available. If unavailable, record `TOOLING_CONSTRAINT` and use the nearest capable validation role only if it can produce useful evidence.

## Code And Contract Review

- Confirm the implementation stayed within the target scope and did not redesign the Work Area explorer or Avatar shell.
- Confirm `AvatarDashboardController` remains thin and delegates rendering/policy appropriately.
- Confirm Work Area file policy remains in `WorkAreaExplorerService`; no controller or fragment path hand-concatenation bypasses service guards.
- Confirm preview route is non-persistent.
- Confirm preview rendering and saved rendering are sanitized and do not allow script/raw HTML injection.
- Confirm JavaScript, if added, is narrow: mode switching, dirty state, undo/revert, and preview sync only.
- Confirm save/CRUD remains HTMX-first.
- Confirm markdown CSS is container-scoped and does not globally reset lists, tables, blockquotes, or code.
- Confirm chat/planning/thinking markdown surfaces were not regressed by shared CSS.
- Confirm source styling/highlighting claims match actual behavior.
- Confirm docs/spec/changelog updates match the final behavior and do not leave stale rendered-tab/save-to-switch language.

## Required Automated Evidence

Review command results from the implementation worker:

```bash
mvn -Dtest=AvatarDashboardControllerTest test
```

If relevant files changed, require:

```bash
mvn -Dtest=WorkAreaExplorerServiceTest test
mvn -Dtest=ChatMarkdownRendererTest test
```

Require, unless blocked:

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

If any command is skipped, validate that the skip reason is specific and acceptable under repo policy.

## Browser Validation Checklist

After code-level validation passes, define and dispatch a separate Playwright/browser validation agent using the repo policy. The browser agent should use `gpt-5.2` medium reasoning when available; if unavailable, record `TOOLING_CONSTRAINT`.

The browser pass must cover desktop and mobile screenshots for:

- Work Area markdown rendered viewer with headings, paragraphs, lists, nested lists, blockquotes, code block, inline code, and table.
- Markdown editor in `Edit` mode.
- Markdown editor in `Preview` mode using unsaved textarea changes.
- Markdown editor in `Split` mode using unsaved textarea changes.
- Save persistence: save raw markdown, reopen/refresh, verify rendered preview reflects saved content.
- Undo/redo or revert behavior for local unsaved edits.
- Plain text file edit/save behavior.

Visual quality checks:

- Bullet markers remain inside the rendered container.
- Lists and nested lists have readable indentation and spacing.
- Blockquotes have internal padding/border and do not crowd adjacent text.
- Code blocks and tables scroll inside the container and do not overflow the modal/page.
- Buttons and status text do not overlap on desktop or mobile.
- Split mode stacks or remains usable on mobile.
- The editor matches Avatar operational density: compact controls, small radii, thin borders, clear hierarchy.
- Console and server logs do not show relevant errors during interactions.

The browser agent should not create arbitrary user files as the primary proof path. Use existing/seeded demo files or a controlled setup.

## Stale-Reference Sweep

Before final sign-off, run a focused sweep over changed docs and `.internal-dev` artifacts for:

- stale artifact paths or `/tmp` evidence paths;
- `pending`, `planned`, `not implemented`, or `TODO` claims that no longer match reality;
- old `Rendered`/`Text` save-to-switch wording;
- stale agent ids or model claims;
- syntax-highlighting claims that exceed actual behavior.

## Pass Criteria

- All acceptance criteria in `00-specification-lock.md` pass.
- Automated tests and startup smoke pass, or blockers are explicitly reported and user-approved.
- Browser screenshots and interaction evidence satisfy the UI/UX criteria.
- Sanitization and non-persistence of preview are proven by tests or clear evidence.
- Docs/spec/changelog are updated and consistent.
- No untracked out-of-scope changes are included.

## Failure Routing

- `code_defect`: send to a fresh scoped `gpt-5.3` high-reasoning repair worker unless it is a simple one-place validator edit.
- `docs_or_evidence_defect`: send to a fresh scoped `gpt-5.3` high-reasoning repair worker unless it is a simple one-place validator edit.
- `browser_harness_defect`: repair browser script/evidence first; change product code only after browser evidence proves a real product bug.
- `plan_defect`: return to planning for revised criteria/directive before more coding.
- `validator_error`: correct checklist or use a fresh validator before dispatching product repair.
- Same targeted issue failing twice after repair attempts: escalate to a fresh scoped `gpt-5.5` high-reasoning repair agent.

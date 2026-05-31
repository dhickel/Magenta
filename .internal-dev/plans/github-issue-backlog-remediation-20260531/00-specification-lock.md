# GitHub Issue Backlog Remediation Specification Lock

Date: 2026-05-31
Repository: `/home/hickelpickle/Code/Java/magenta2`
GitHub repository: `dhickel/Magenta`
Work classification: large

## Objective

Diagnose the currently open GitHub issues #9 through #19 and #33, then remediate them sequentially through delegated implementation workers and independent validators. Issue #8 is explicitly user-deferred and remains open because dashboard editing has moved and dashboard work outside SlotKey enforcement risks regressions. Issue #34 is tracked as a future typed-ID refactor target and is not part of this remediation pass unless the user explicitly pulls it into scope. This plan is execution-ready, but it does not implement product fixes.

## Source Inputs

- Open GitHub issue bodies from `gh issue list --repo dhickel/Magenta --state open --limit 100 --json number,title,body,labels,url,createdAt,updatedAt`.
- Living specs reviewed for this planning pass: `.internal-dev/specifications/index.md`, `simplypages.md`, `web.md`, `api.md`, `services.md`, `architecture.md`, `service-graph.md`, and repo workflow guides.
- Targeted knowledge reviewed: `workflow-route-model.md`, `simplypages-avatar-layout-and-editing.md`, `avatar-work-area-ui-refactor.md`, `live-chat-mcp-workflow-testing.md`, `orchestration-lease-heartbeat-and-task-sse.md`, and `regression-gap-test-patterns.md`. Treat references to "Avatar UI" in older knowledge as stale product terminology; current user-facing UI truth is Home dashboard/dashboard surfaces.
- SimplyPages docs reviewed for SlotKey work: `02-dynamic-pages-with-slotkey-rendercontext.md`, `03-template-rendercontext-slotkey-reference.md`, and `02-dynamic-fragment-caching-patterns.md`.

## Locked Execution Model

- Main thread is coordinator-only. It dispatches workers, validators, Playwright/browser agents, git closeout, GitHub closeout, and email reports.
- Implementation workers use `gpt-5.3` high reasoning unless the user changes it before dispatch.
- Phase validators use separate `gpt-5.5` high-reasoning validation agents.
- Browser/Playwright validation, when applicable, uses a separate `gpt-5.5` high-reasoning browser validation agent.
- Second validation failure for the same targeted issue escalates repair to a fresh `gpt-5.5` high-reasoning repair worker.
- Final quality review is not run until all phase validations pass.

## Acceptance Criteria

- Every in-scope issue listed in this plan has a self-contained worker directive with exact issue scope, targets to inspect/edit, reproduction probes, acceptance criteria, validation commands, docs/spec updates, closeout expectations, and stop conditions.
- #8 is documented as out of scope for this remediation pass and must remain open unless the user later re-accepts dashboard editor density work.
- #34 is documented as a tracked future/refactor issue and must remain open unless the user later approves a dedicated typed-ID refactor pass.
- Execution order prioritizes critical security/persistence and runtime correctness before UI/refactor work.
- Combined-fix decisions are explicit and rollback-friendly.
- #33 has a first-class SlotKey/package-guide directive that requires SlotKey/RenderContext enforcement in all frontend-related `AGENTS.md` package guides and a concrete SimplyPages audit/refactor path.
- #14 through #19 directives require concrete reproduction probes/tests before implementation changes.
- The SlotKey UI phase includes an Experience Contract and requires delegated Playwright screenshots with visual critique.
- The plan preserves known pre-existing uncommitted changes in `.gitignore`, `AGENTS.md`, and `.internal-dev/reviews/2026-05-28-model-alias-internal-review.md`.

## Validation Criteria

- Each mutating phase requires focused tests, relevant package/API/UI tests, and bounded Spring startup unless explicitly blocked.
- Security and persistence phases require negative tests proving the old unsafe behavior is impossible, not just happy-path coverage.
- Runtime/concurrency phases require tests that reproduce the issue first or encode the race/terminal-state invariant close to the boundary.
- The SlotKey UI phase requires delegated Playwright checks against a running app using isolated SQLite state, desktop and mobile screenshots, and visual critique.
- Validators must write reports under `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/`.
- The orchestrator must maintain `artifacts/github-issue-backlog-remediation-20260531/validation-summary.json` as the canonical evidence index across phases.

## Negative Criteria

- Do not implement product fixes from this planning pass.
- Do not let the main thread implement planned code changes.
- Do not bulk-fix unrelated issues or refactor broad packages for style.
- Do not close GitHub issues until the relevant worker, validator, required browser validation, commit, push, and report gates pass.
- Do not mark UI work validated without screenshots and a visual-quality critique.
- Do not revert or overwrite existing uncommitted user/previous-run changes.
- Do not replace SimplyPages/HTMX patterns with ad hoc raw HTML or broad JavaScript unless a directive explicitly allows a narrow exception.

## Non-Goals

- Formal migration tooling such as Flyway/Liquibase unless a worker proves the current issue cannot be safely remediated without it and stops for user approval.
- A full redesign of workflow orchestration, chat streaming, or Home dashboard/dashboard widget surfaces beyond the issue-specific fixes.
- Any dashboard editor density/empty-row remediation for #8; leave #8 open for later because the editing system moved and the user does not want regression risk from that stale issue.
- Any typed-ID refactor remediation for #34; leave #34 open for a dedicated cross-domain ID-type pass after the current issue remediation run.
- Deep end-to-end browser campaigns beyond focused changed-surface proof unless the user approves expansion.

## Assumptions

- The GitHub issue list supplied by the user and verified with `gh issue list` is the current open backlog for this plan.
- The current checkout branch may already contain user/previous-run changes; workers must use `git status` before edits and preserve unrelated work.
- Some open issues may already be partially fixed in the current checkout; workers must still reproduce or verify the invariant before deciding the implementation is closeout-only.
- The app can normally run with isolated SQLite databases for startup/browser validation. If local AI/model services are unavailable for chat/browser SSE proof, workers and validators may use the deterministic local stub pattern described in `live-chat-mcp-workflow-testing.md`, but must record that setup.
- Current product terminology is Home dashboard/dashboard editor. `AvatarDashboardComponents`, `AvatarService`, `avatar.sqlite`, and similar names are legacy code/data names only unless a worker explicitly proves a small code rename is safe and in scope.

## User Decision Gates

- Stop for the user if a phase requires a broad architecture change outside the issue, new dependencies, formal migration tooling, or a breaking API contract.
- Stop if Playwright/browser validation is blocked after documented recovery and no approved fallback exists.
- Stop if issue #33 cannot be closed with a bounded SlotKey first pass because the audit proves multiple independent UI refactors are required.

## Stop Rules

- Stop on uncommitted-file conflict that would require reverting user work.
- Stop when a required model/tool cannot be selected and record `TOOLING_CONSTRAINT`.
- Stop when reproduction contradicts the GitHub issue and no actual defect remains; route as closeout-only with evidence rather than inventing a fix.
- Stop after two failed validation cycles for the same targeted issue and escalate repair model per policy.

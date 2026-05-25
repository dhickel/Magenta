# Final Orchestration Plan

Status: ready for execution
Created: 2026-05-24

## Artifact Index

- Specification lock: `.internal-dev/plans/workspace-file-explorer-rewrite/00-specification-lock.md`
- Current-state analysis: `.internal-dev/plans/workspace-file-explorer-rewrite/01-current-state-analysis.md`
- Target design: `.internal-dev/plans/workspace-file-explorer-rewrite/02-target-design.md`
- Shared senior guidance: `.internal-dev/plans/workspace-file-explorer-rewrite/shared/senior-engineer-guidance.md`
- Living implementation notes: `.internal-dev/plans/workspace-file-explorer-rewrite/shared/implementation-notes.md`
- Validation matrix: `.internal-dev/plans/workspace-file-explorer-rewrite/shared/validation-matrix.md`
- Work units: `.internal-dev/plans/workspace-file-explorer-rewrite/work-units/README.md`
- Worker directives:
  - `.internal-dev/plans/workspace-file-explorer-rewrite/worker-directives/phase-01-research-and-spec-reconciliation.md`
  - `.internal-dev/plans/workspace-file-explorer-rewrite/worker-directives/phase-02-domain-services-and-tags.md`
  - `.internal-dev/plans/workspace-file-explorer-rewrite/worker-directives/phase-03-api-and-fragments.md`
  - `.internal-dev/plans/workspace-file-explorer-rewrite/worker-directives/phase-04-file-explorer-ui-rewrite.md`
  - `.internal-dev/plans/workspace-file-explorer-rewrite/worker-directives/phase-05-viewer-copy-move-rename-delete.md`
  - `.internal-dev/plans/workspace-file-explorer-rewrite/worker-directives/phase-06-docs-closeout-and-gate-validation.md`
- Closeout email/report plan: `.internal-dev/plans/workspace-file-explorer-rewrite/closeout-report-plan.md`

## Execution Rules

- Main thread coordinates only; planned implementation is delegated to `implementation_worker_agent` with model `gpt-5.5`, medium reasoning, one directive at a time.
- After each mutating phase, dispatch `validation_redteam_agent` with model `gpt-5.5`, high reasoning, non-mutating validation against the directive, spec lock, target design, and validation matrix.
- For all testing including Playwright, use model `gpt-5.3-codex` with reasoning effort `medium` where the runtime supports explicit model/effort selection.
- Every failed validation caused by implementation drift returns to a scoped fix worker, then revalidation.
- Every failed validation caused by ambiguous/flawed criteria returns to the advanced planning agent for a revised plan artifact before more coding.
- Do not proceed to the next mutating phase until validation passes or the user explicitly accepts a recorded risk.
- Do not recreate a repo-local email ledger. Use direct AgentMail daemon/wait dispatch.
- Work start email has already been sent. Run `mailctl status` at every gate before sending gate emails.

## Setup Gate

Before dispatching Phase 01:

```bash
git status --short --branch
git branch --show-current
```

Expected branch: `feature/workspace-file-explorer`.

Expected pre-existing dirty files: this plan suite if not yet committed.

If not on the expected branch, stop for user/orchestrator decision. If additional dirty files overlap phase scope, inspect and preserve user changes.

Commit the new plan suite before implementation starts if the orchestrator wants phase commits to begin from a clean planning baseline:

```bash
git add .internal-dev/plans/workspace-file-explorer-rewrite
git commit -m "Plan workspace file explorer rewrite"
```

Do not stage unrelated files.

## Phase Dispatch And Gates

### Gate P1: Research/Spec Reconciliation Complete

Dispatch:

- Worker: `implementation_worker_agent`
- Directive: `worker-directives/phase-01-research-and-spec-reconciliation.md`

Validation:

- `validation_redteam_agent` verifies no production/test/schema/runtime edits and confirms branch/dependency/source drift evidence.

Email report:

- Gate name: `P1 Research/Spec Reconciliation`
- Include branch/dirty state, dependency/source drift, fixture inventory, validation result, and next phase.

Commit:

- Commit only `shared/implementation-notes.md` if changed and orchestrator wants a phase checkpoint.

### Gate P2: Domain Services And Tags Complete

Dispatch:

- Worker: `implementation_worker_agent`
- Directive: `worker-directives/phase-02-domain-services-and-tags.md`

Validation:

- Run targeted tests in directive.
- Red-team path confinement, symlink, tags, copy/move/rename/delete metadata behavior, action logs, and schema drift.

Email report:

- Gate name: `P2 Domain Services And Tags`
- Include files changed, service behavior, schema changes if any, test output summary, red-team findings, residual risks.

Commit:

```bash
git add <phase-02-files-only>
git commit -m "Prepare workspace explorer domain metadata and tags"
```

### Gate P3: API And Fragments Complete

Dispatch:

- Worker: `implementation_worker_agent`
- Directive: `worker-directives/phase-03-api-and-fragments.md`

Validation:

- Targeted controller/service tests.
- Red-team thin-controller boundary, route/fragment targets, OOB refresh consistency, error visibility.

Email report:

- Gate name: `P3 API And Fragments`
- Include route changes, fragment target contract, tests, residual risks, next phase.

Commit:

```bash
git add <phase-03-files-only>
git commit -m "Add workspace explorer API and fragment contracts"
```

### Gate P4: File Explorer UI Rewrite Complete

Dispatch:

- Worker: `implementation_worker_agent`
- Directive: `worker-directives/phase-04-file-explorer-ui-rewrite.md`

Validation:

- Targeted tests.
- Playwright validation subagent with desktop/mobile screenshots and visual critique.
- Red-team no-card regression, required columns, inspect panel separation, HTMX-first behavior.

Email report:

- Gate name: `P4 Details/List UI Rewrite`
- Include screenshots path/summary, visual critique, structural tests, any JS justification, residual risks.

Commit:

```bash
git add <phase-04-files-only>
git commit -m "Rewrite workspace explorer as details list UI"
```

### Gate P5: Viewer And Operations Complete

Dispatch:

- Worker: `implementation_worker_agent`
- Directive: `worker-directives/phase-05-viewer-copy-move-rename-delete.md`

Validation:

- Targeted tests.
- Playwright validation subagent covers Markdown/text/image viewer, Markdown failure, unsupported binary fallback, inspect copy/move, row/panel rename/delete, refresh consistency.
- Red-team operation confinement and stale UI behavior.

Email report:

- Gate name: `P5 Viewer And File Operations`
- Include operation coverage, viewer screenshots/evidence, targeted tests, residual risks.

Commit:

```bash
git add <phase-05-files-only>
git commit -m "Complete workspace explorer viewer and file operations"
```

### Gate P6: Docs Closeout And Gate Validation Complete

Dispatch:

- Worker: `implementation_worker_agent`
- Directive: `worker-directives/phase-06-docs-closeout-and-gate-validation.md`

Validation:

- Targeted tests.
- Full `mvn test`.
- Bounded Spring startup.
- Red-team docs/internal-dev accuracy and previous-plan supersession handling.

Email report:

- Gate name: `P6 Docs Closeout And Validation`
- Include docs changed, changelog/knowledge/focus updates, full test/startup result, blockers or residual risks.

Commit:

```bash
git add <phase-06-files-only>
git commit -m "Document workspace explorer rewrite closeout"
```

## Fix/Revalidate Loop

For implementation drift:

1. Validator writes findings with file/line references and criteria mapping.
2. Orchestrator dispatches a scoped fix worker with only the failing phase directive plus findings.
3. Fix worker edits only files needed for the finding.
4. Run the same phase validation again.
5. Send email update only when the gate ultimately passes or a user decision is needed.

For flawed/ambiguous criteria:

1. Stop coding.
2. Return to advanced planning agent.
3. Update the relevant plan artifact and implementation notes.
4. Email the user with the criteria issue and proposed correction.
5. Resume only after criteria are clear.

## Final Quality Review

After all phase validation passes, dispatch a non-mutating final quality-review subagent, preferably `gpt-5.5` xhigh reasoning if supported. It must:

1. Verify the work was completed fully, properly, and to acceptance criteria.
2. Search for edge cases, missed concerns, code smells, poor quality, UX failures, security-sensitive regressions, test gaps, docs drift, `.internal-dev` gaps, release blockers, dependency/source drift, and stale old-plan references.
3. Review Playwright screenshots and validation evidence.
4. Verify no repo-local email ledger was recreated and no unrelated files were staged.
5. Verify phase commits are present and scoped.

Must-address findings require another fix and validation loop before completion.

## GitHub Work

If a GitHub repository is configured and out-of-scope bugs are created under `.internal-dev/bugs/`, mirror them to GitHub Issues when created or compiled. Before finishing, check related closed GitHub Issues; if corresponding issue is already closed, archive the local bug report.

If the user requests publishing after final review, use the GitHub plugin/skill flow to push the branch and open a draft PR. Do not open a PR as part of this plan unless explicitly requested.

## Email Closeout And Listening

After final quality review passes and any must-address findings are fixed/revalidated:

1. Run `mailctl status`.
2. Send final HTML email report with plain-text fallback using `agentmail` / `email-followup-wait` according to `closeout-report-plan.md`.
3. Include work done, implemented behavior, validation evidence, screenshots summary, residual risks, senior recommendations, commits, docs, and `.internal-dev` artifacts.
4. Start low-token AgentMail listening using the email-followup-wait workflow.
5. Do not drop email listening while a wait-for-response contract is active. Check `mailctl status` at gates and after compaction/resume.

## Senior Engineer Notes

The sequencing is intentionally conservative because this task is mostly about avoiding a repeat miss. Do not let a passing backend phase justify a weak UI phase, and do not let a polished screenshot justify unsafe filesystem behavior. The final success condition is both: a recognizable details/list explorer and root-confined operations with real validation evidence.

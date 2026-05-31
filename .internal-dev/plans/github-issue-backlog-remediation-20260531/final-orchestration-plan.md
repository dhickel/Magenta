# Final Orchestration Plan

## Dispatch

Use the phase order in `work-units/README.md`. Dispatch one implementation worker at a time with the matching `worker-directives/phase-XX-*.md` file.

Default roles:

- Implementation: `implementation_worker_agent`, `gpt-5.3`, high reasoning.
- Phase validation: `validation_redteam_agent`, `gpt-5.5`, high reasoning.
- Browser validation: separate Playwright/browser validation agent, `gpt-5.5`, high reasoning.
- Final quality review: `validation_redteam_agent`, `gpt-5.5`, xhigh reasoning after all phases pass.

## Phase Gate

For each phase:

1. Confirm git status and branch.
2. Dispatch worker with one directive.
3. Receive worker summary and changed files.
4. Dispatch validator with directive, diff, worker summary, command output, and evidence.
5. Dispatch browser validation only when required by the validator/directive.
6. Route remediation by failure type.
7. When validation passes, commit the phase and push.
8. Close the GitHub issue(s) with commit reference.
9. Send email report through `email-followup-wait`.
10. Update `artifacts/github-issue-backlog-remediation-20260531/validation-summary.json`.

## Stale-Reference Sweep

Before final quality review, sweep:

- `.internal-dev/plans/github-issue-backlog-remediation-20260531/`
- `.internal-dev/changelogs/`
- affected `.internal-dev/specifications/`
- affected `docs/`

Look for stale `/tmp` evidence paths, old artifact paths, stale issue status, pending/planned/not implemented claims, TODO markers, and outdated model/tooling wording.

## Final Quality Review Gate

Run final quality review only after all phase validators pass. It must verify:

- All planned issues are either closed with commit references or explicitly blocked with user-approved reason.
- Canonical evidence index matches phase reports.
- Browser proof exists and is reconciled for #33 and any chat/browser phase requiring it.
- #8 remains open and was not dispatched, committed, or closed under this plan.
- Docs/spec/changelog updates are coherent and do not contradict code.
- The active plan can be moved to `.archive/` only after the whole backlog remediation is finalized.

## Tooling Constraint Handling

If any required model, GitHub, email, Maven, startup, or browser tool cannot be used, record `TOOLING_CONSTRAINT` in the phase report and evidence index, then stop for main-thread/user approval before using a fallback.

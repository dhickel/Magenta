---
schema_version: 1
document_type: final-orchestration-plan
status: active
created: 2026-05-25
owner: unassigned
---

# Final Orchestration Plan

## Artifact Index

- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/00-specification-lock.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/01-current-state-analysis.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/02-target-design.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/shared/senior-engineer-guidance.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/shared/implementation-notes.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/shared/validation-matrix.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/work-units/README.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/worker-directives/phase-01-implementation.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/worker-directives/phase-02-playwright-validation.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/closeout-report-plan.md`

## Dispatch Sequence

1. Confirm the branch:

```bash
git status --short --branch
```

2. If phase work has not started on a dedicated branch, either continue on `feature/avatar-chat-left-resizable` with user approval or create a dedicated branch from it before mutation.
3. Dispatch one `implementation_worker_agent` using `worker-directives/phase-01-implementation.md`.
4. Require the worker to run focused code validation and bounded Spring startup.
5. Review the implementation report and `shared/implementation-notes.md`.
6. Dispatch one Playwright validation worker using `worker-directives/phase-02-playwright-validation.md`.
7. Do not proceed to completion unless Playwright passes.

## Diagnose-Fix-Revalidate Loop

If Playwright fails:

- If the implementation drifted from the directive, dispatch a scoped fix worker using the Phase 01 directive plus the validator's exact findings. The fix worker may edit only the same Phase 01 allowed files unless the orchestrator obtains user approval.
- If the validation shows the plan's assumptions are wrong, return to planning: update `00-specification-lock.md`, `02-target-design.md`, `shared/validation-matrix.md`, and the relevant worker directive before more code.
- After any fix, rerun code validation and then rerun Phase 02 Playwright validation.
- Record each failure/fix/revalidation in `shared/implementation-notes.md`.

## Final Quality Review Methodology

After Phase 02 passes, delegate a final non-mutating quality review across the branch. Prefer `gpt-5.5` xhigh reasoning if runtime supports explicit model/effort selection. The reviewer must first verify that the work fully satisfies the plan criteria, then look for:

- missed edge cases;
- poor code quality or brittle JS;
- UX failures not caught by the focused test;
- mobile overflow or interaction regressions;
- docs drift;
- `.internal-dev` closeout gaps;
- security-sensitive regressions;
- release blockers.

Must-address findings return to the diagnose-fix-revalidate loop. Advisory findings can be recorded as residual risk only with orchestrator/user acceptance.

## Commit And GitHub Gate

After validation and final review pass:

1. Run `git status --short`.
2. Confirm only intended implementation, docs, tests, and `.internal-dev` files changed.
3. Ensure changelog and any unfinished-work updates are included.
4. Commit the completed phase work on the plan branch.
5. If the repository uses GitHub for this work, push and open/update the relevant PR or issue references as directed by the user.
6. If any new `.internal-dev/bugs/` report was created, mirror it to GitHub Issues and archive local bugs whose corresponding GitHub issue is already closed.

## Docs And Closeout Gate

Docs must be current before completion:

- `docs/end-user/avatar-dashboard.md` describes bottom-right corner resizing.
- `docs/technical/avatar-dashboard-fragments.md` describes the corner resize JS, localStorage keys, and mobile behavior.
- `.internal-dev/changelogs/2026-05-25-avatar-chat-corner-resize-sticky-repair.md` summarizes behavior and validation.

## Email Closeout

After the plan is fully executed, validation passes, final quality review passes, and commit/GitHub gates are complete:

1. Use the `agentmail` / `email-followup-wait` workflow.
2. Send an HTML email report with plain-text fallback using `closeout-report-plan.md`.
3. Include work done, implemented behavior, validation evidence, residual risks, and senior recommendations.
4. Run `mailctl status` at the gate before waiting.
5. Wait for a response using the low-token AgentMail/email-followup-wait path. Do not drop email listening while a wait-for-response contract is active.

## Senior Engineer Notes

Keep the orchestration narrow. There is one implementation worker and one validation worker. The only loop is diagnose-fix-revalidate when browser evidence fails. Avoid expanding this into a broader Avatar polish campaign.

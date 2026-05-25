# Final Orchestration Plan

## Artifact Index

- `.internal-dev/plans/avatar-chat-left-resizable/handoff-report.md`
- `.internal-dev/plans/avatar-chat-left-resizable/00-specification-lock.md`
- `.internal-dev/plans/avatar-chat-left-resizable/01-current-state-analysis.md`
- `.internal-dev/plans/avatar-chat-left-resizable/02-target-design.md`
- `.internal-dev/plans/avatar-chat-left-resizable/shared/senior-engineer-guidance.md`
- `.internal-dev/plans/avatar-chat-left-resizable/shared/implementation-notes.md`
- `.internal-dev/plans/avatar-chat-left-resizable/shared/validation-matrix.md`
- `.internal-dev/plans/avatar-chat-left-resizable/work-units/README.md`
- `.internal-dev/plans/avatar-chat-left-resizable/worker-directives/phase-01-avatar-left-chat-rail.md`
- `.internal-dev/plans/avatar-chat-left-resizable/closeout-report-plan.md`

## Dispatch

1. Main thread hands `worker-directives/phase-01-avatar-left-chat-rail.md` to one mutating `implementation_worker_agent` using GPT-5.3 medium, per user request.
2. Worker reads the supporting docs named in the directive and implements only that phase.
3. Worker appends phase status, decisions, validation evidence, blockers, and remediation notes to `shared/implementation-notes.md`.

## Validation Gate

After the mutating worker finishes, dispatch a non-mutating validator with GPT-5.3 medium, matching the repo validation instruction for tests. The validator checks:

- directive scope compliance;
- acceptance criteria;
- negative checks;
- focused Maven test evidence;
- bounded startup evidence;
- docs and `.internal-dev` closeout updates;
- no Playwright claim unless user approval changed.

## Fix Loop

- If validation fails because implementation drifted, dispatch a scoped fix worker against the same directive plus validator findings.
- Re-run the same validation gate.
- If validation fails because criteria are ambiguous or wrong, return to planning and revise this plan suite before more coding.

## Final Quality Review

After validation passes, run a final non-mutating review pass across the branch. Prefer GPT-5.5 xhigh if available; otherwise use the best available high-reasoning reviewer. The review first verifies the requested behavior was completed to criteria, then checks edge cases, code quality, brittle tests, docs drift, `.internal-dev` gaps, UX risks, and release blockers.

Must-address final findings require another fix and validation loop before completion.

## Commit And GitHub Gate

The implementation worker should leave a clean, focused diff. After validation and final review pass:

- inspect `git status --short`;
- commit implementation, docs, tests, and `.internal-dev` updates together;
- if a GitHub PR or issue workflow is requested later, use the repo's GitHub process then.

## Email Closeout Gate

After full execution and validation pass, send an HTML email report with a plain-text fallback using AgentMail/email-followup-wait. Check `mailctl status` at the gate, send the report, then wait for a reply using the low-token wait workflow if remote-mode or wait-for-response is active.

## Summary

Operational UI remains divergent from alpha feature contract in task/workflow conversational execution and several HTMX-first interaction surfaces.

## Scope

- Orchestration dashboard/editor UX only
- Plan/task/workflow/job/project/inbox/settings surfaces
- No backend schema migration in this bug record

## Reproduction

1. Open `/settings` and attempt save path without JS hook execution.
2. Open `/inbox` and `/outputs`; observe primary data/actions rely on page JS transport.
3. Execute workflow with approval/user gate and inspect operator UI for threaded user-response control.
4. Use plan editor bulk save expectations vs. inline row edits and verify full-form collection behavior.

## Expected

- HTMX-first interaction for standard operational CRUD/actions unless explicitly justified.
- Task/workflow run conversational controls and user-reply path available in orchestration UI.
- Full plan editor save semantics consistent across scalar and collection fields.

## Actual

- Settings save is JS action handler dependent.
- Inbox/outputs are JS-transport surfaces.
- Gate/chat controls are backend-capable but UI-thin.
- Full-form save semantics for collection sections are not fully normalized.

## Evidence

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `.internal-dev/reviews/2026-05-13-operational-ui-divergence-review.md`
- `.internal-dev/notes/operational-ui-contract-missing-features.md`

## Impact

- Limits alpha readiness for operational workflows requiring approvals, user consultation, and robust task/workflow management.
- Raises UX reliability risk when JS transport code fails or drifts.

## Status

Open

## Next Action

Implement targeted parity passes:
1. settings HTMX save path
2. inbox/outputs HTMX-first refactor or explicit exception policy
3. task/workflow run conversation + gate management UI

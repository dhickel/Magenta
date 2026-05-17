# Severity Rubric

## Scope

Public-alpha quality findings are classified by user-visible impact, data integrity risk, operational recoverability, and likelihood during normal alpha usage.

## Critical

- Prevents application startup or makes the primary public UI unusable.
- Causes durable data loss, cross-user/path escape, destructive action without clear intent, or unrecoverable corruption.
- Allows unintended command/file access outside the configured runtime/workspace contract.
- Blocks all validation for a required alpha workflow with no usable fallback.

## High

- Breaks a core public-alpha workflow such as chat, plan submission, workflow submission, agent queue control, project workspace access, outputs, or settings persistence.
- Produces misleading operational state that would cause an operator to make the wrong recovery decision.
- Violates submit-to-agent semantics by directly executing when the UI contract promises queued work, or the inverse.
- Causes persisted state to diverge from displayed state after normal CRUD actions.

## Medium

- Affects an important but bounded workflow, has a workaround, or is limited to stale data, incomplete diagnostics, or confusing but non-destructive UI.
- Schema/repository drift that is recoverable but likely to break warm-DB upgrades or older alpha data.
- Stale Docker/Podman text or identifiers remaining in the filesystem-backed runtime UI/docs when it can mislead users.

## Low

- Cosmetic, naming, minor copy, or style-policy issue with limited operational risk.
- Test harness weakness that reduces confidence but does not hide an already observed functional failure.
- Refactor opportunity with no current user-facing breakage.

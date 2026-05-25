---
status: active
created: 2026-05-25
owner: main-thread-orchestrator
classification: small
source_intent: .internal-dev/plans/specifications-store-workflow/replacement-handoff.md
---

# Final Orchestration Plan

## Agents

Use one `implementation_worker_agent` for the implementation. Use one validator/main-thread review pass after the worker reports back.

No Playwright subagent is required unless the worker touches UI behavior. No integration validator is required because this is a small single-unit workflow/documentation change.

## Sequence

1. Main thread confirms the worktree state and records unrelated pre-existing changes.
2. Main thread gives `00-specification-lock.md` and `01-implementation-worker-directive.md` to one `implementation_worker_agent`.
3. Worker implements the flat specifications store, migrates/drops focus and notes, updates AGENTS guidance, writes changelog, runs static validation, and reports results.
4. Validator/main thread uses `02-validation-checklist.md`.
5. If validation fails, return the checklist remediation handoff to the same implementation worker.
6. After validation passes, create one git commit containing the implementation and `.internal-dev` updates.

## Commit Gate

Before commit:

```bash
git status --short
git diff --name-status
git diff --check
```

Commit message suggestion:

```text
Replace internal-dev focus and notes with specifications workflow
```

The commit must include the specifications store, AGENTS updates, changelog, migrated/dropped focus/notes changes, and any workflow knowledge update created by the implementation. Do not include unrelated pre-existing changes such as `.internal-dev/reviews/test-suite-quality-review.md`.

## Final Acceptance

The work is complete only when:

- the validation checklist passes;
- deleted focus/notes data has a migration/drop audit in `specifications/workflow.md`;
- active workflow references no longer point to `.internal-dev/focus/` or `.internal-dev/notes/`;
- no product validation is falsely claimed for docs-only work;
- a commit exists for the completed implementation and internal-dev updates.

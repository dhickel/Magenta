---
status: active
created: 2026-05-25
owner: main-thread-orchestrator
classification: small
source_intent: chat queued mid-turn messages
---

# Final Orchestration Plan

## Agents

Use one `implementation_worker_agent` with `gpt-5.5`, medium reasoning, mutating. Give it `00-specification-lock.md` and `01-implementation-worker-directive.md`.

Use one `validation_redteam_agent` with `gpt-5.5`, high reasoning, non-mutating. Give it the implementation report, changed files, `00-specification-lock.md`, and `02-validation-checklist.md`.

Use one separate Playwright/browser validation agent after code-level validation. The browser agent should execute only the concrete delegated checklist in `02-validation-checklist.md` and report screenshots, assertions, console/network findings, and blockers back to the validator.

No integration validator is required because this is a small one-unit plan.

## Sequence

1. Main thread records current `git status --short` and unrelated existing files.
2. Main thread delegates implementation to one worker.
3. Worker implements backend queue, API, browser UI, tests, docs/spec updates, changelog, and local validation.
4. Worker returns a report with changed files, route/table shapes, validation commands/results, startup result, and any blockers.
5. Validator reviews code and docs against `02-validation-checklist.md`.
6. If code-level validation passes, main thread delegates the Playwright checklist to a browser validation agent.
7. Browser agent returns assertions, screenshots, console/network findings, and exact app/DB/model setup.
8. Validator reconciles browser evidence with the checklist and records pass/fail.
9. If validation fails, return the remediation handoff to the same worker and then resume the same validator after fixes.
10. After validation passes, main thread performs repo closeout and commit.

## Commit And Closeout Gate

Before commit:

```bash
git status --short
git diff --name-status
git diff --check
```

The commit must include implementation, tests, docs, specs, changelog, and any knowledge/package-guide updates created for this task. Do not include unrelated pre-existing files such as currently untracked `.internal-dev/research/` or `.internal-dev/reviews/test-suite-quality-review.md` unless the user explicitly scopes them in.

Suggested commit message:

```text
Add persistent mid-turn chat message queue
```

After commit, archive this active plan directory only if the repo workflow for the implementation pass treats the work as finalized; otherwise leave it active until the user accepts the result.

## Stop Rules For Main Thread

Stop and consult the user if:

- implementation requires changing normal SSE event semantics beyond queue drain;
- Playwright validation is blocked and no approved fallback exists;
- the worker discovers queued messages should apply to saved plan chat or agent side-panel chat to satisfy the user-visible behavior;
- a schema migration risk appears that cannot be covered by current repository bootstrap and tests;
- local model/runtime services are unavailable and a deterministic stub setup is not already accepted for browser validation.

## Final Acceptance

The task is ready to report complete only when:

- validator passes code-level criteria;
- Playwright evidence passes the slow-turn queue scenarios and visual checks;
- specs/docs/changelog closeout is complete;
- bounded startup succeeds or a user-approved blocker is recorded;
- a commit exists with the implementation and internal-dev updates.

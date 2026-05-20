# Phase 03 Final Validation And Closeout

## Context
The combined branch contains issue #3 implementation and issue #5 validator hardening. Final validation must prove both sets of acceptance criteria after integration.

## Goal
Run independent validation, preserve closeout records, and prepare the branch for push and GitHub issue closeout.

## In Scope
- Full Maven test suite.
- Spring Boot startup smoke.
- Focused Playwright MCP validation for chat/SSE/plan execution surfaces.
- `.internal-dev` changelog, knowledge, archived plan, and review records.
- Git commit and branch push.

## Out of Scope
- Full production-style browser campaign.
- Live model happy-path `plan_complete` execution beyond automated tests.

## Implementation Steps
- Run final validation on a validation subagent using the repo-required validation model.
- Record residual risks and artifacts in orchestration notes.
- Add closeout review record.
- Commit final validation records without unrelated dirty files.
- Push branch and close GitHub issue #3; add a follow-up comment to issue #5 for the additional hardening.

## Validation
- `mvn -q test` passed.
- Startup smoke passed by reaching app startup before timeout termination.
- Focused Playwright MCP validation passed with expected negative-path 400/409 responses.

## Exit Criteria
- No hard validation blockers remain.
- Residual risk is explicitly recorded: focused browser validation did not run a full successful live `plan_complete` happy path, but validator behavior is covered by automated tests.
- Task-related files are committed separately from unrelated worktree changes.

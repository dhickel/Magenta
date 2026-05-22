# Phase 07: Integration, Review, And Closeout

## Context

After source phases complete, the refactor needs integrated validation, documentation, `.internal-dev` workflow completion, final architecture/code review, remediation, and final commits.

## Goal

Close the refactor with evidence that implementation, docs, validation, and architecture alignment are complete.

## In Scope

- Final targeted and broad test validation.
- Spring context smoke.
- Playwright MCP validation for changed UI surfaces.
- Docs updates.
- Package guide updates if responsibilities changed.
- `.internal-dev` changelog, bugs, knowledge, deferred notes, and final review artifacts.
- xhigh architecture/code review.
- Serial remediation loops.
- Final closeout commit.

## Out of Scope

- New feature scope beyond remediation.
- Archiving finalized plan artifacts before the user agrees the whole refactor is complete.
- Filing GitHub Issues without asking the user.

## Implementation Steps

1. Inspect actual source changes and update relevant docs.
2. Update package `AGENTS.md` files if package responsibilities, public surfaces, or conventions changed.
3. Add `.internal-dev` changelog and knowledge entries.
4. Log out-of-scope bugs immediately and ask before filing GitHub Issues.
5. Confirm deferred future ideas before adding notes.
6. Run final integration validation.
7. Run xhigh final architecture/code review.
8. Fix review blockers serially and rerun affected validation.
9. Commit closeout artifacts after validation passes.
10. Append final status and commit hash to `agent-notes.md`.

## Validation

- Aggregate targeted phase tests.
- `mvn test` when feasible.
- Spring context smoke.
- Playwright MCP validation for changed UI surfaces.
- Final xhigh architecture/code review.
- Git status/staging check before final commit.

## Exit Criteria

- All phase commits are complete.
- Docs and `.internal-dev` workflow are complete.
- Final validation is passed or user-approved blockers are recorded.
- Final xhigh review has no blocking findings.
- Closeout commit is created and recorded.

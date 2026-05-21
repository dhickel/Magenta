# Orchestration Suite: Services And UX Architecture Refactor

## Source Plan Summary

This suite follows the completed workspace/file architecture refactor and reviews whether Magenta's backend services, APIs, frontend pages, and integration flows actually expose the intended architecture:

```text
agent only            -> agent workspace context
agent + project       -> project workspace context
job assignment        -> repeatable orchestration/work-unit context
job persistent space  -> opt-in per-assignment job workspace
```

Shared notes path:

- `.internal-dev/plans/services-ux-architecture-refactor/agent-notes.md`

All subagents must read the shared notes before starting and append concise notes before finishing.

## Global Orchestration Rules

- Use branch `services-ux-architecture-refactor`.
- Never revert or overwrite changes from other agents or pre-existing dirty files.
- Keep code-modifying work strictly serial.
- Run non-mutating review, planning, and risk/test-design agents in parallel when safe.
- Planning, review, and implementation agents use high reasoning unless a more specific rule applies.
- Testing/validation agents use `gpt-5.3-codex` with medium reasoning.
- Final architecture/code/UX review uses xhigh reasoning.
- Playwright validation must run in a subagent, never inline, and only against changed UI surfaces unless explicit approval expands scope.
- If a blocking infrastructure dependency prevents real validation, stop and consult the user.

## Initial Read-Only Review Group

### Backend Services Review

- May modify files: no.
- Scope: project, job, assignment, plan/task, workflow, output, workspace, and API service layers.
- Expected output: divergence findings with file/line references, missing service capabilities, data/API gaps, and recommended implementation phases.

### Frontend And UX Review

- May modify files: no.
- Scope: SimplyPages/HTMX pages for projects, jobs, plans/tasks, workflows, outputs, agent/workspace status, and assignment flows.
- Expected output: user-visible mismatches, missing controls, misleading copy, navigation gaps, and validation scenarios.

### Integration/API Review

- May modify files: no.
- Scope: controllers, request/response records, route tests, API docs, and frontend-service handoff points.
- Expected output: contract gaps and compatibility risks between backend services and UI.

### Risk And Testing Review

- May modify files: no.
- Scope: concurrency/race risks, leasing/locking readiness, validation strategy, test gaps, Playwright requirements, and rollback/compatibility considerations.
- Expected output: risk register and recommended testing procedures to inform the implementation plan.

## Planning And Implementation Flow

1. Synthesize the read-only reviews into an advanced implementation plan.
2. Split code work into serial phases with narrow ownership.
3. Validate each phase with targeted tests and smoke checks.
4. Use Playwright subagents for UI changes.
5. Commit after each validated phase.
6. Run final validation and xhigh review.
7. Remediate any blocker findings.
8. Complete docs, changelogs, knowledge notes, and final closeout commit.

## Closeout Requirements

- Update end-user docs for changed behavior and UX.
- Update technical/API docs for changed services, routes, payloads, schemas, or architecture.
- Update package guides when package responsibilities change.
- Write normal changelog and, for substantive refactor scope, a technical changelog.
- Record reusable architecture or validation knowledge.
- Log out-of-scope bugs immediately and ask before filing GitHub Issues.

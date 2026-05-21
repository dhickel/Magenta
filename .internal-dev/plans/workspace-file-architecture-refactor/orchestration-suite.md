# Orchestration Suite: Workspace File Architecture Refactor

## Source Plan Summary

The refactor will align Magenta's file architecture with the effective workspace rule:

```text
project attached -> project workspace
no project       -> agent workspace
```

The implementation is intentionally serial for code edits. Non-mutating review, validation, and test-design agents may run in parallel when they do not write source files or race with active implementation.

Shared notes path:

- `.internal-dev/plans/workspace-file-architecture-refactor/agent-notes.md`

All agents must read the shared notes before starting and append concise notes before finishing.

## Global Orchestration Rules

- Use the branch `workspace-file-architecture-refactor`.
- Never revert or overwrite changes from other agents.
- Keep code-modifying phases serial.
- Each implementation phase is a separate subagent subplan with fresh context and narrow ownership.
- Each code-editing phase must validate before commit.
- Each completed implementation phase should end with a commit.
- Planning, review, and implementation agents use high reasoning unless a more specific repo rule applies.
- Testing/validation agents use `gpt-5.3-codex` with medium reasoning per repo validation policy.
- Final architecture/code review agent uses xhigh reasoning.
- Use Playwright only through a validation subagent and only when UI interactions changed.
- Do not mark blocked validation as complete. If infrastructure blocks real execution validation, stop and consult the user.

## Execution Graph

1. Baseline validation group, non-mutating except approved test characterization.
2. Phase 01 implementation: baseline characterization and regression tests.
3. Phase 01 validation agent.
4. Phase 01 remediation loop if needed.
5. Phase 01 commit gate.
6. Phase 02 implementation: effective workspace resolver and run metadata.
7. Phase 02 validation agent.
8. Phase 02 remediation loop if needed.
9. Phase 02 commit gate.
10. Phase 03 implementation: task/plan runtime paths, aliases, output publishing, gated loose discovery.
11. Phase 03 validation agent.
12. Phase 03 remediation loop if needed.
13. Phase 03 commit gate.
14. Phase 04 implementation: workflow waiting, async context propagation, durable outputs, retention.
15. Phase 04 validation agent.
16. Phase 04 remediation loop if needed.
17. Phase 04 commit gate.
18. Phase 05 implementation: project API/schema owner-agent migration and explicit `projectId`.
19. Phase 05 validation agent, including Playwright if UI changed.
20. Phase 05 remediation loop if needed.
21. Phase 05 commit gate.
22. Phase 06 implementation: job persistent workspace policy and legacy orchestration reconciliation.
23. Phase 06 validation agent.
24. Phase 06 remediation loop if needed.
25. Phase 06 commit gate.
26. Documentation and closeout agents.
27. Final integration validation agent.
28. Final xhigh architecture/code review agent.
29. Final remediation loop, serial if code changes are required.
30. Final closeout commit gate.

## Subagent Roster

### Baseline Validation Agent

- Model/reasoning: `gpt-5.3-codex`, medium.
- May modify files: no, unless explicitly reassigned as Phase 01 implementation.
- Scope: run current targeted tests, inspect failures, report current baseline.
- Expected output: commands run, pass/fail result, blockers, relevant failure excerpts.

Prompt summary:

Read `agent-notes.md`, `implementation-plan.md`, and phase 01. Run the targeted baseline tests listed in the plan. Do not edit source. Append validation notes and report whether implementation can start.

### Phase 01 Implementation Agent: Characterization And Regression Tests

- Model/reasoning: high.
- May modify files: yes, test files only unless fixture helpers are required.
- Ownership: tests named in phase 01.
- Expected output: changed tests, validation run, commit readiness.

Prompt summary:

Add focused tests for effective workspace resolution expectations, project output placement, workflow waiting assignment mapping, workflow async context propagation, workflow durable output separation, active/waiting temp retention, gated loose discovery, and chat-file separation. Keep tests deterministic and avoid broad refactors.

### Phase 02 Implementation Agent: Effective Workspace Resolver

- Model/reasoning: high.
- May modify files: yes.
- Ownership: workspace path/resolver services, plan run metadata, schema tests.
- Expected output: resolver added, call sites minimally integrated, tests passing.

Prompt summary:

Implement a central effective workspace resolver and shared workspace layout helpers. Persist effective run metadata at run creation while preserving old fields and behavior. Do not move all execution paths yet unless required by tests.

### Phase 03 Implementation Agent: Task/Plan Paths And Outputs

- Model/reasoning: high.
- May modify files: yes.
- Ownership: task/plan services, file/shell tools, output artifact service, related tests.
- Expected output: task/plan runs use effective workspace, aliases match contract, explicit publishing exists, loose discovery is gated/confined.

Prompt summary:

Move task/plan runtime paths onto the resolver, update tool aliases, add explicit output publishing, and preserve loose discovery behind a compatibility gate with realpath confinement. Do not hard-remove loose discovery.

### Phase 04 Implementation Agent: Workflow Execution

- Model/reasoning: high.
- May modify files: yes.
- Ownership: workflow runner/service, orchestration runner, workflow/runtime tests.
- Expected output: workflow waiting assignments remain waiting, async context propagates, durable workflow outputs are separate from temp, retention is correct.

Prompt summary:

Fix workflow assignment status mapping for `WAITING`, propagate `OrchestrationTaskContext` across async workflow task execution, and publish workflow final outputs under effective durable workspace output paths.

### Phase 05 Implementation Agent: Project API And Migration

- Model/reasoning: high.
- May modify files: yes.
- Ownership: project model/service/repository/controller, request records, operational UI project surfaces, route/schema/UI tests.
- Expected output: project owner-agent no longer required, explicit `projectId` accepted, legacy `workspaceId` compatibility preserved.

Prompt summary:

Treat project owner-agent removal as an explicit migration/API phase. Make project ownership nullable or compatibility-only, preserve response compatibility, use memberships for agent association, and add explicit `projectId` to submission paths without repurposing `workspaceId`.

### Phase 06 Implementation Agent: Job Workspace Policy

- Model/reasoning: high.
- May modify files: yes.
- Ownership: job services/repositories/controllers, assignment/runtime integration, legacy orchestration job bridge as needed, tests.
- Expected output: persistent job workspaces are opt-in and assignment/run isolated; project-scoped jobs publish under project outputs.

Prompt summary:

Implement explicit persistent job workspace policy keyed by assignment/run identity. Ensure job output metadata links to project/agent/job/run correctly. Reconcile legacy orchestration job behavior with a controlled compatibility or migration decision.

### Phase Validation Agents

- Model/reasoning: `gpt-5.3-codex`, medium.
- May modify files: no.
- Ownership: phase-specific validation commands only.
- Expected output: commands, pass/fail, failure evidence, residual risk.

Prompt summary:

Read the completed phase notes and run the exact validation gate for that phase. Do not edit files. Append validation results to `agent-notes.md`. If validation fails, report the smallest remediation target.

### Remediation Agents

- Model/reasoning: high for code remediation; `gpt-5.3-codex` medium for validation reruns.
- May modify files: yes only for the specific failed phase scope.
- Ownership: the failed check's minimal source/test area.
- Expected output: fix, failed check rerun, updated notes.

Prompt summary:

Fix only the validation failure assigned. Do not broaden the phase or refactor unrelated code. Re-run the failed check when feasible and append remediation notes.

### Documentation And Closeout Agents

- Model/reasoning: high for architecture/docs review, medium for mechanical docs updates if delegated.
- May modify files: yes, docs and `.internal-dev` artifacts only.
- Ownership: docs affected by actual changes, package `AGENTS.md` updates when responsibilities changed, changelog, knowledge, bugs, deferred notes.
- Expected output: closeout artifacts, docs validation, commit readiness.

Prompt summary:

Inspect actual implementation changes and repo guidance. Update user-facing docs, technical/API docs, package guides, `.internal-dev` changelog, knowledge, bug reports, and deferred notes as required. Ask before filing GitHub Issues for bug reports.

### Final Integration Validation Agent

- Model/reasoning: `gpt-5.3-codex`, medium.
- May modify files: no.
- Ownership: broad final validation command set and UI checks if applicable.
- Expected output: final validation result, skipped checks with blockers, residual risk.

Prompt summary:

Run the full final validation plan after all phases and closeout docs are complete. Include targeted tests, `mvn test` when feasible, Spring context smoke, and Playwright MCP validation for changed UI surfaces.

### Final Architecture/Code Review Agent

- Model/reasoning: xhigh.
- May modify files: no.
- Ownership: final review only.
- Expected output: ordered findings with file/line references, risk assessment, remediation requirements, sign-off status.

Prompt summary:

Perform a final architecture/code review. Pay extra attention to fragile/complex refactor targets, assumptions, gotchas, robustness, path confinement, leases/races, output correctness, chat-file separation, staged loose discovery, projectId/workspaceId compatibility, workflow waiting/resume behavior, ThreadLocal context propagation, job workspace isolation, and alignment with `current-architecture-focus.md`.

## Validation Gates

Every phase gate must record:

- Git status before validation.
- Commands run.
- Pass/fail result.
- Failure excerpts.
- Remediation performed.
- Commit hash after successful commit.

Minimum gates:

- Phase 01: targeted baseline/characterization tests.
- Phase 02: workspace/path/schema/plan metadata tests and Spring smoke.
- Phase 03: task/plan/file/shell/output tests and Spring smoke.
- Phase 04: workflow/runtime/output attribution tests and Spring smoke.
- Phase 05: project/API/schema/UI route tests, Spring smoke, Playwright if UI changed.
- Phase 06: job/runtime/schema/output tests and Spring smoke.
- Final: aggregate targeted tests, `mvn test` when feasible, Spring smoke, Playwright if applicable, xhigh final review.

## Remediation Policy

- A failed phase validation blocks the next code-editing phase.
- Remediation is serial and limited to the failed phase's ownership boundary.
- After remediation, rerun the failed validation first.
- If the failure indicates a bad phase boundary or architectural assumption, pause and update the plan/notes before continuing.
- If required infrastructure or secrets block validation, stop and consult the user. Do not substitute unit-only coverage as completion.
- Record user-approved deferred blockers explicitly in `agent-notes.md`.

## Commit Gates

Before each phase commit:

1. Confirm only intended files are staged.
2. Confirm unrelated worktree changes are not reverted or included.
3. Confirm phase validation passed or user explicitly accepted a blocker.
4. Append phase summary and validation to `agent-notes.md`.
5. Commit implementation plus required phase notes/docs for that phase.

After each commit:

1. Append commit hash to `agent-notes.md`.
2. Confirm branch remains `workspace-file-architecture-refactor`.
3. Start the next phase only after the commit gate is complete.

## Documentation And Closeout

Required closeout work:

- Update `docs/` for changed behavior, APIs, schema, services, and configuration.
- Update relevant package `AGENTS.md` files when package responsibilities or public surfaces change.
- Add `.internal-dev/changelogs/<date>-workspace-file-architecture-refactor.md`.
- Add `.internal-dev/knowledge/` entries for reusable workspace/output lessons.
- Log out-of-scope bugs immediately under `.internal-dev/bugs/`.
- Ask the user before filing any GitHub Issues from bug reports.
- Confirm deferred ideas before writing `.internal-dev/notes/`.
- Move finalized plan artifacts to `.archive/` only when the refactor is fully complete and user agrees it is finalized.

## Handoff Strategy

The main orchestrator should keep phase state in `agent-notes.md`, start only one implementation agent at a time, and require validation evidence before handing off to the next phase. If review or validation agents identify contradictions between code and this plan, update the plan artifacts first, then continue from the latest validated phase.

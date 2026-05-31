# Current State Analysis

## Git State

Current observed branch during planning: `feature/dashboard-widget-suite`.

Pre-existing uncommitted changes observed and must be preserved:

- `.gitignore`
- `AGENTS.md`
- `.internal-dev/reviews/2026-05-28-model-alias-internal-review.md`

Workers must run `git status --short --branch` before edits and avoid reverting unrelated changes.

## Issue Inventory

| Issue | Title | Primary Domain | Planning Diagnosis |
| --- | --- | --- | --- |
| #9 | SQL injection vectors via string concatenation in repositories | security/persistence | Needs first because it is critical and affects shared repositories. Confirm current code still concatenates identifiers in `ChatSessionMetadataRepository`, `AuditRepository`, and `PlanRepository`; add whitelist/identifier tests. |
| #10 | Silent exception swallows in WorkflowRepository schema migrations | persistence/schema | Critical schema-drift risk. Fix after #9 because both touch repository migration patterns but not the same files except general persistence conventions. |
| #11 | GlobalExceptionHandler passes null to its own constructor | web/error handling | High but small. Current no-arg constructor exists and tests instantiate it. Remove or make explicit default safe after security/persistence. |
| #12 | Executor rejection can poison conversation turn queue | chat execution concurrency | Current `QueuedTurn.submit()` marks `submitted = true` before `workExecutor.submitChat`; rejection can leave the head turn stuck. Needs rejection test before fix. |
| #13 | CANCEL_REQUESTED assignments can be overwritten by late lease writes | orchestration runtime | `saveAssignmentIfLeaseOwner` currently allows `RUNNING` or `CANCEL_REQUESTED`. Existing tests cover interrupted rows but not late writes while status remains `CANCEL_REQUESTED`. |
| #19 | Pending chat FIFO ordering can race under concurrent enqueue | chat persistence | `ChatPendingMessageRepository.enqueue` uses `max(message_order)+1` without uniqueness or per-conversation lock. Needs concurrent enqueue probe. |
| #14 | SSE error callbacks can leave active chat/plan execution state registered | chat/SSE lifecycle | `ChatController.streamResolved` records `onError` disconnect but does not run `domainCleanup` there. Needs lifecycle callback probe. |
| #15 | Plain streaming advertises interrupt token but cannot accept interrupts | chat/SSE interrupts | `start` advertises `turnId`/`interruptToken`; plain path does not phase/update active turn beyond default behavior. Combine with #14 because both touch active-turn lifecycle and stream contracts. |
| #16 | TASK_RUN entry points bypass required run name | API/runtime submission | `TaskController` and `WorkflowController` enforce names; `PlanController` and generic `AgentOrchestrationController` still pass no `runDisplayName`; `AssignmentTemplateParser` does not enforce it. |
| #17 | PASS_THROUGH routes validated and executed as single-port mappings | workflow route semantics | Docs/knowledge say full-map pass-through; validator/runtime require and use ports. Needs validation and runtime fixture first. |
| #18 | DELEGATION nodes fabricate completed child plan runs | workflow execution | `WorkflowRunner.executeDelegationNode` starts and completes child runs with empty outputs. Needs supported-scope triage and real evidence requirement. |
| #8 | Avatar edit mode renders excessive empty-row chrome | out of scope/deferred | User correction: the Avatar UI abstraction is stale, dashboard editing has moved, and dashboard work outside the SlotKey issue should be skipped to avoid regressions. Leave this GitHub issue open. |
| #33 | Refactor dashboard/static pages toward reusable SlotKey templates | UI/SimplyPages refactor/docs | Large refactor. Must include all frontend-related `AGENTS.md` enforcement plus bounded SlotKey/RenderContext audit/refactor of Home dashboard/dashboard widgets/static page structures. |
| #34 | Refactor target: replace cross-domain raw String IDs with typed ID value objects | future/refactor | Filed from a dedicated scan of raw `String` ID usage across assignment, orchestration, plan/task, workflow, API, workspace, and output domains. Track in the issues list, but leave open for a dedicated typed-ID refactor pass after the current remediation run. |

## Architecture Fit

- Controllers should remain thin and delegate validation/service behavior.
- Repositories own SQL and migration details; security/persistence fixes should stay local to repository helper methods and tests.
- Workflow behavior lives in `io.mindspice.magenta2.ai.orchestration.workflow`; do not resurrect legacy `ai.chat.workflow`.
- Runtime assignment lifecycle lives in `io.mindspice.magenta2.ai.orchestration.runtime`; cancellation guards belong in repository/service transition boundaries, not UI code.
- Browser chat pending queue is service-owned and separate from `ActiveTurnRegistry` and chat memory.
- Home dashboard/dashboard editor UI should remain dense operational tooling aligned with `/dashboard`, `/agents`, and SimplyPages editing demo patterns. Do not preserve or expand "Avatar UI" as a product abstraction; use legacy class/data names only where current code still requires them. Do not remediate dashboard editor density/empty-row behavior in this plan outside the SlotKey issue.

## Contract Conflicts And Risks

- #17 is a confirmed code/spec mismatch: `workflow-route-model.md` defines `PASS_THROUGH` as no-port full-output forwarding, while code treats it as a source/target port mapping.
- #18 may require a product decision if `DELEGATION` is not supported in current alpha. The bounded fix should reject/hold unsupported delegation rather than fabricate completion.
- #15 has a contract decision: either make plain stream interrupts actionable or stop advertising interrupt capability for non-interruptible turns. The directive recommends making advertised tokens truthful through phase management where feasible, but requires the worker to verify browser/API behavior first.
- #33 must not force SlotKeys into highly dynamic structures; use SlotKey templates only where DOM structure is stable and values/fragments change.
- #34 is intentionally not folded into current workflow fixes; broad cross-domain ID typing would increase rollback and regression risk while active runtime issues remain open.

## Validation Blind Spots

- Existing tests cover some adjacent behavior but not the exact old side effects for #12, #13, #14, #15, #17, #18, and #19.
- UI screenshots are required for #33; route-load tests alone are insufficient. #8 remains open and out of scope.
- Some issues may be partly fixed on the current branch while GitHub remains open. The execution workflow should close issues only after current-code evidence and closeout gates.

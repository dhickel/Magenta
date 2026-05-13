# Phase 01 - Unified Plan/Task Core

## Context

Plan mode and task mode currently overlap but persist to different tables and use different model tools. The refactor should make them one backend concept while preserving the robust plan/task behaviors: planning conversation, approval, typed fields, tool-mediated completion, evidence, validation feedback, and run snapshots.

## Goal

Replace separate plan/task persistence and tooling with a unified plan/task aggregate where a task is a finalized executable plan.

## In Scope

- Create unified records: `PlanDefinition`, `PlanRun`, `PlanFieldDefinition`, `PlanStep`, and supporting enums.
- Replace `ai_chat_plans`, `ai_chat_plan_steps`, `ai_task_definitions`, `ai_task_drafts`, and `ai_task_runs` with clean-break unified tables.
- Implement destructive app DB reset for this refactor. Since full app data reset is allowed, remove old migration compatibility logic for these tables.
- Keep the model-facing planning tools but make plan and reusable task authoring share the same service methods.
- Support two prompt surfaces:
  - in-session planning: hides task-template details unless outputs are needed;
  - reusable task authoring: exposes inputs/outputs, prompt profile, and run defaults.
- Add deliverables back as a first-class list separate from outputs.
- Add field type `user_message` and field flag `array`.
- Implement no-input and no-output behavior:
  - no inputs: runtime prompt says inputs are intentionally empty and begins from the plan steps;
  - no outputs: completion validates deliverables and validation criteria only.

## Out of Scope

- Docker execution and filesystem output copying.
- Workflow gates and job/project orchestration.
- Dashboard redesign beyond keeping existing routes compiling.
- Legacy data migration.

## Implementation Steps

1. Read current `ai.chat.plan` and `ai.chat.task` classes and identify the minimal public behavior to preserve: plan begin, question queueing, ready-for-approval, approval, run start, report, complete, needs-review, and final message.
2. Add new unified domain records under `ai.chat.plan`.
   - Prefer records for request/response/data carriers.
   - Keep names plain: `PlanDefinition`, `PlanRun`, `PlanFieldDefinition`, `PlanRunStatus`, `PlanKind`.
3. Replace schema with new clean-break tables:
   - `plan_definitions`
   - `plan_runs`
   - optional `plan_conversation_state` if conversation plan state should remain separated from reusable definitions.
4. Implement a `PlanRepository` or adapt `ChatPlanRepository` to own the new tables.
   - Use JSON columns for lists and snapshots.
   - Register Jackson Java time modules.
   - Snapshot the full `PlanDefinition` into every `PlanRun`.
5. Collapse `TaskService` behavior into `PlanService`.
   - Keep `TaskService` only as a short-lived compatibility facade if needed for compile order, but do not build new behavior there.
   - Prefer exposing reusable task operations as `PlanService` methods with `PlanKind.TASK_TEMPLATE`.
6. Replace `TaskTools` and `PlanSaveTools` with one tool component.
   - Tool names can remain stable where useful (`plan_*`, `task_*`) but route to the same service.
   - Add a single completion path that validates declared outputs and deliverables.
7. Update `ChatService` plan/task execution hooks to call unified plan run APIs.
   - Avoid changing unrelated turn handling or chat memory behavior.
8. Update API controllers to add `/api/plans` routes.
   - Existing `/api/tasks` may be removed or become a thin alias only if needed to keep UI compiling during this phase.
9. Update tests:
   - Replace task-specific repository/service tests with unified plan/task tests.
   - Keep tests for prompt mode, queued questions, approval, run snapshots, missing required inputs, missing outputs, and no-output completion.

## Validation

- Focused tests:
  - `PlanServiceTest`
  - `ChatPlanRepositoryTest` or replacement repository tests
  - `PlanSaveToolsTest`
  - `ChatServiceTest` task/plan execution coverage
  - relevant controller tests for `/api/plans`
- Validate DB reset against an old schema fixture by creating old tables, starting/reinitializing schema, and asserting new tables exist and old plan/task tables are gone.

## Exit Criteria

- There is one authoritative plan/task persistence model.
- A reusable task is represented as a finalized `PlanDefinition`.
- In-session plan mode and reusable task authoring share the same mutation/completion service.
- Deliverables and outputs are both present and semantically distinct.
- Completion cannot mark a run complete unless declared outputs are supplied or no outputs are declared.


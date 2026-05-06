# Task and Workflow System Implementation Plan

## Context

Magenta currently has a chat-scoped plan system with plan mode, approval, saved-plan execution, validation evidence, and a mostly unused "save as task" status. Tasks should become reusable pieces of work built from the same planning concepts, but with typed execution-time inputs and concrete named outputs so tasks can later be chained into workflows.

The current plan system already has useful patterns to preserve:

- Conversation-scoped interactive planning with queued user questions.
- Keyed model tools for deterministic draft edits.
- Approval before execution.
- Fresh execution context with runtime instructions.
- Evidence and validator-gated completion.
- Thin web controllers delegating to services and repositories.

The task/workflow implementation must build on those patterns without broad refactors that risk regressing plan mode.

## Goal

Implement a reusable task system and a minimal linear workflow system.

The first version should support:

- Agent-assisted task creation through a task-specific planning mode.
- Manual task creation and editing in a task UI.
- Typed input/output definitions for tasks.
- Isolated task runs with explicit input values and named output values.
- A workflow UI/API that chains 2-3 task steps by mapping task outputs into downstream task inputs.
- SSE progress for task and workflow execution.
- Reproducible MCP validation fixtures for one task run and one 3-step workflow run.

## In Scope

- New task definition, task draft, task run, workflow definition, and workflow run persistence.
- Generic queued-question tool and backend service usable by plan mode and task mode.
- Replacement of `plan_ask_questions` with generic `ask_user_questions`.
- Task-specific model tools and prompts.
- Task and workflow HTTP APIs.
- Separate `/tasks` and `/workflows` pages.
- Full manual task editor for v1 task fields.
- Linear workflow editor and runner.
- Focused unit/controller tests plus MCP fixture docs/scripts.

## Out of Scope

- Workflow DAGs or branching.
- Task scheduling or recurring tasks.
- Background job orchestration beyond current stream/request execution patterns.
- Strict JSON schema validation.
- Task versioning UI.
- Subagent coordination.
- Marketplace/library sharing of tasks.

## Implementation Steps

### 1. Add Shared Queued Questions

Create a shared question service/model that can support both plan and task interaction modes.

Expected behavior:

- The model calls `ask_user_questions` with one to five concrete questions.
- The active interaction context determines where questions are stored.
- The UI continues to show one question at a time with index/count progress.
- Answer submission records the answer in chat history and advances the queue.
- When the final queued question is answered, the relevant mode resumes its model turn.

Implementation guidance:

- Replace `plan_ask_questions` in plan prompts and tests.
- Remove `plan_ask_questions` from the primary model prompt/tool contract.
- Keep the implementation simple: the generic tool may delegate into plan/task services internally.
- Do not duplicate question UI logic for tasks; reuse the existing planning-panel behavior or extract a small shared interaction panel renderer.

Gotcha:

- The current tool is mode-gated through `PlanToolExecutionContext`. Introduce a more generic interaction context or extend the existing context carefully so plan execution tools remain gated.

### 2. Add Task Domain Types

Add task records using Java records where practical.

Task definition fields:

- `id`
- `title`
- `summary`
- `goal`
- `notes`
- `deliverables`
- `inputDescription`
- ordered `inputs`
- `outputDescription`
- ordered `outputs`
- `assumptions`
- ordered `steps`
- `validationCriteria`
- `createdAt`
- `updatedAt`

Task input/output definition fields:

- `name`
- `type`: one of `string`, `long_text`, `file_path`, `json`, `number`, `boolean`
- `description`
- `required`
- optional `schema`
- optional `example`

Task run fields:

- `id`
- `taskId`
- `status`
- `inputValuesJson`
- `outputValuesJson`
- `taskSnapshotJson`
- `executionEvidenceJson`
- `validationFeedbackJson`
- `finalMessage`
- `errorText`
- timestamps.

Storage decisions:

- Task definitions overwrite in place.
- Task runs must snapshot the full task definition used for execution.
- Runtime values are stored as keyed JSON objects by definition name.
- Use ordered JSON lists for inputs/outputs unless row tables are clearly simpler in the existing repository style.

### 3. Add Task Drafts and Task Mode

Add task draft state by conversation id. This should be separate from `ai_chat_plans`.

Task mode lifecycle:

1. User starts task creation from UI/API.
2. Task prompt first asks the user to specify intended inputs.
3. Model must confirm proposed input definitions with `ask_user_questions`.
4. Model may persist an input only after user confirmation.
5. Model proceeds to goal, outputs, deliverables, assumptions, steps, and validation criteria.
6. Model marks task draft ready for approval.
7. User approves and saves a task definition.

Prompt requirements:

- Explain that tasks are reusable work units, unlike one-off plans.
- Explain placeholders such as `<InputName>` as generic runtime values.
- Tell the model to plan how to use inputs without needing concrete values during creation.
- Make outputs concrete named data products for workflow chaining.
- Require outputs to also be represented in deliverables so existing validation expectations remain aligned.
- Preserve the current turn contract: every task-mode turn must end in queued questions or ready-for-approval.

### 4. Add Task Tools

Add task-specific tools instead of overloading plan tools.

Required task tools:

- `task_set_goal`
- `task_set_task`
- `task_put_item`
- `task_delete_item`
- `task_ready_for_approval`
- `task_report`
- `task_complete`

`task_put_item` sections:

- `deliverable`
- `input`
- `output`
- `assumption`
- `note`
- `step`
- `validation_criterion`

For `input` and `output`, the tool must accept typed definition fields rather than a raw string.

Execution tools:

- `task_report` records evidence during a task run.
- `task_complete` records evidence, final message, and output values.
- `task_complete` should fail clearly if required declared outputs are missing.

### 5. Add Task Execution

Task execution should mirror saved-plan execution where useful.

Expected behavior:

- A task run starts with a fresh isolated task-run context.
- The execution prompt includes the task snapshot and concrete runtime input values.
- The model sees declared outputs and must return output values keyed by output name.
- Execution streams progress over SSE.
- On success, run status becomes completed and output values are persisted.
- On timeout/error/client disconnect, run status becomes needs review or failed with evidence/error text.

Do not rely on final assistant text extraction for workflow chaining. Downstream steps must consume persisted named output values.

### 6. Add Workflow Domain

Implement v1 workflows as linear ordered chains.

Workflow definition fields:

- `id`
- `title`
- `summary`
- ordered steps.

Workflow step fields:

- `stepKey`
- `taskId`
- ordered input bindings.

Input binding kinds:

- literal value
- previous step output reference.

Compatibility rules:

- Missing required inputs block run.
- Output/input type mismatch produces a warning but does not block save or run.
- No branching or DAG execution in v1.

Workflow run fields:

- `id`
- `workflowId`
- `status`
- `workflowSnapshotJson`
- step run references/statuses
- final output values
- final message
- error text
- timestamps.

### 7. Add Workflow Execution

Workflow execution is sequential.

For each step:

1. Resolve literal inputs and previous-step output bindings.
2. Create an isolated task run with a task definition snapshot.
3. Stream task progress as workflow step progress.
4. Persist step outputs.
5. Stop on failed task run and persist workflow failure.

The final workflow result is the final step output map plus a concise final message.

### 8. Add APIs and UI

Task APIs:

- list tasks
- get task
- create/update task manually
- start task creation interaction
- approve task draft
- run task via SSE
- list/get task runs.

Workflow APIs:

- list workflows
- get workflow
- create/update workflow
- run workflow via SSE
- list/get workflow runs.

UI:

- Add separate `/tasks` page.
- Add separate `/workflows` page.
- Keep chat page uncluttered.
- Task page supports full manual editing for all task definition fields.
- Task run form is generated from input definitions.
- Workflow page supports a linear 2-3 step chain editor with literal and previous-output bindings.
- Render type mismatch warnings inline without blocking save.

Keep controllers thin; services own behavior; repositories own persistence.

## Validation

### Automated Tests

Repository tests:

- Task definition CRUD preserves ordered inputs/outputs.
- Task draft persistence supports queued questions and approval state.
- Task run stores input values, output values, evidence, errors, and task snapshot.
- Workflow definition stores ordered steps and bindings.
- Workflow run stores step statuses and final outputs.

Service tests:

- Task mode begins by asking for input definitions.
- Inputs are not persisted until user confirmation.
- Task approval requires goal, output/deliverable coverage, steps, and validation criteria.
- Task execution persists named output values.
- Workflow run maps output values into downstream task inputs.
- Type mismatch creates warning but does not block save/run.

Tool tests:

- `ask_user_questions` works in plan and task modes.
- Plan prompt no longer references `plan_ask_questions`.
- Task tools are gated to task mode/task execution mode.
- `task_complete` rejects missing required outputs.

Controller/UI tests:

- `/tasks` and `/workflows` render expected controls.
- Task CRUD payloads bind correctly.
- Workflow CRUD payloads bind correctly.
- Task and workflow SSE endpoints emit start/progress/error/done.

Regression tests:

- Existing plan creation, approval, execution, and validation still pass.
- Slash commands remain limited to `/new` and `/plan`.

### Startup Smoke

After implementation, run:

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-task-workflow-smoke.sqlite'
```

## MCP Workflow Validation

Create reproducible fixtures under:

```text
.internal-dev/test-fixtures/task-workflow/
```

Fixture contents:

- three task creation prompts
- task input fixture values
- one workflow definition fixture
- expected structural assertions.

Hybrid workflow:

1. Task 1 accepts a search topic string, performs web/search-style research, and outputs `research_notes`.
2. Task 2 accepts `research_notes` plus a formatting instruction and outputs `structured_summary`.
3. Task 3 accepts `structured_summary` plus an audience string and outputs `final_report`.

MCP validation steps:

1. Start app on isolated SQLite database.
2. Open `/tasks`.
3. Create or import the three tasks.
4. Verify typed inputs and outputs render.
5. Run Task 1 standalone with fixture input.
6. Assert terminal run status and named `research_notes` output.
7. Open `/workflows`.
8. Create a linear three-step workflow.
9. Bind Task 1 `research_notes` to Task 2 input.
10. Bind Task 2 `structured_summary` to Task 3 input.
11. Run workflow via SSE.
12. Reload workflow run detail.
13. Assert all three step runs exist.
14. Assert each step has stored inputs and outputs.
15. Assert final workflow output contains `final_report`.

Use a stable fallback seed text in fixtures so structure/status assertions remain deterministic even if live web results vary.

## Exit Criteria

- Task mode can create and approve a reusable task with typed inputs and outputs.
- Manual `/tasks` UI can create/edit/run a task.
- `/workflows` UI can chain three tasks linearly and run them.
- Task runs persist named output values.
- Workflow runs pass named outputs into downstream task inputs.
- Existing plan tests and behavior remain intact.
- MCP fixture documents and validates a single task run and a 3-step workflow run.

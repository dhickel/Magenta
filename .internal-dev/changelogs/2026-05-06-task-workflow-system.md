# Date

2026-05-06

# Change Summary

Rebuilt the task and workflow v1 system after removing the prior uncommitted implementation. Added schema-backed task definitions, task drafts, task runs, workflow definitions, workflow runs, typed inputs/outputs, named runtime output persistence, shared `ask_user_questions`, task tools, task/workflow APIs, SSE run endpoints, and `/tasks` plus `/workflows` browser pages.

# Files

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/InteractionQuestionTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/task/`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- Focused task/workflow tests under `src/test/java/`
- `.internal-dev/test-fixtures/task-workflow/`

# Behavioral Impact

- Plan mode now uses the generic `ask_user_questions` tool name while preserving queued planning questions and answer progression.
- Reusable tasks can be created, edited, listed, approved from drafts, run over SSE, and inspected after execution.
- Task runs persist input values, output values keyed by declared output name, task definition snapshots, evidence, final messages, errors, and timestamps.
- Workflows support two or three ordered linear task steps with literal and prior-step output bindings.
- Workflow runs execute steps sequentially, persist step task-run references and values, warn on type mismatches, and block missing required inputs.
- `/tasks` and `/workflows` expose editor and run surfaces for the new APIs.

# Risks

- The browser workflow editor is intentionally compact and uses JSON binding editing for detailed bindings; the API/service model supports the v1 binding semantics.
- Task/model execution support is wired through task execution contexts and task tools, while the controller SSE path uses the deterministic service-run path for reliable local validation.
- Playwright MCP browser validation could not run in this session because the MCP browser profile was already in use; API/SSE validation covered the run contracts.

# Follow-up Items

- Add richer workflow binding controls that generate literal and previous-output bindings without manual JSON editing.
- Add a model-backed task execution controller path once the desired model/runtime timeout behavior is settled.

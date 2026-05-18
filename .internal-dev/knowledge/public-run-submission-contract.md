# Public Run Submission Contract

Public plan, task, and workflow run controls submit saved definitions to durable agent assignments instead of executing inline.

- Default public submit priority is `9` unless the caller explicitly supplies another priority.
- Plan and task run submissions create `TASK_RUN` assignments with the saved task id in assignment input as `taskId`.
- Workflow run submissions create `WORKFLOW_RUN` assignments with the saved workflow id in assignment input as `workflowId`.
- Public SSE run endpoints may acknowledge assignment submission, but should not stream model-backed direct execution.
- Direct execution helpers may remain for internal workers and tests, but should not be reachable from public run controls.

## Web API Package

This package owns HTTP and web-facing entry points.

### Responsibilities
- Expose chat REST endpoints, SSE streaming endpoints, command endpoints, context usage payloads, structured tool activity updates, and simple web routes.
- Expose chat plan state, queued planning questions/actions, approval/continue actions, validation criteria, deliverables, validation feedback, and execution evidence.
- Expose runtime settings, agent profile, workspace-link, job, assignment, inbox, schedule, event-reaction, and agent side-panel chat APIs as thin orchestration entry points.
- Expose assignment lifecycle controls, including guarded queue cleanup, retained terminal history, history purge, and read-only audit transcript fragments, through thin orchestration entry points.
- Expose operational dashboard summary and output artifact query APIs as thin read models for orchestration UI pages.
- Keep inherited shell compatibility resources local to the web layer when a shell asset reference cannot be removed directly.
- Keep controllers thin and delegate behavior to services.
- Keep HTTP status handling clear and local to the web layer.

### Change guidance
- Treat controller request and response changes as public API changes.
- Task run SSE responses include the task conversation id, task run id, status, output values, errors, and tool/progress events from model-backed execution.
- Public operational job APIs use `JobDefinition` records, allow empty `DRAFT` jobs, and expose job item routes separately from run routes.
- Do not put chat, persistence, or orchestration logic in controllers.
- Keep command parsing small and explicit.
- Keep this guide updated when web routes, API contracts, or controller conventions change.

### Validation
- Add or update controller tests for new routes, request validation, status codes, and response shapes.
- Check browser client behavior when web-facing API contracts change.

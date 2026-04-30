## Web API Package

This package owns HTTP and web-facing entry points.

### Responsibilities
- Expose chat REST endpoints, SSE streaming endpoints, command endpoints, context usage payloads, structured tool activity updates, and simple web routes.
- Expose chat plan state, including lightweight acceptance criteria and execution evidence.
- Keep controllers thin and delegate behavior to services.
- Keep HTTP status handling clear and local to the web layer.

### Change guidance
- Treat controller request and response changes as public API changes.
- Do not put chat, persistence, or orchestration logic in controllers.
- Keep command parsing small and explicit.
- Keep this guide updated when web routes, API contracts, or controller conventions change.

### Validation
- Add or update controller tests for new routes, request validation, status codes, and response shapes.
- Check browser client behavior when web-facing API contracts change.

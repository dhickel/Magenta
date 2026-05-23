## Web API Package

This package owns HTTP and web-facing entry points.

### Responsibilities
- Expose chat REST endpoints, SSE streaming endpoints, command endpoints, context usage payloads, structured tool activity updates, and simple web routes.
- Expose chat plan state, queued planning questions/actions, approval/continue actions, validation criteria, deliverables, validation feedback, and execution evidence.
- Treat `/chat` planning routes as anonymous, session-local, non-saved planning. They must not create saved `/plans` definitions or submit anonymous plans to agents.
- Treat `/api/plans/*planning-chat*` as saved plan chat routes. These routes are plan-scoped and must not use `/api/chat`, `ai_chat_session_metadata`, or chat session list architecture.
- Expose runtime settings, agent profile, workspace-link, job, assignment, inbox, schedule, event-reaction, and agent side-panel chat APIs as thin orchestration entry points.
- Expose assignment lifecycle controls, including guarded queue cleanup, retained terminal history, history purge, and read-only audit transcript fragments, through thin orchestration entry points.
- Expose operational dashboard summary and output artifact query APIs as thin read models for orchestration UI pages.
- Keep inherited shell compatibility resources local to the web layer when a shell asset reference cannot be removed directly.
- Own the public-alpha HTTP access gate: read-only routes stay public, while unsafe mutation/control routes require the configured alpha credential and CSRF token.
- Keep controllers thin and delegate behavior to services.
- Keep HTTP status handling clear and local to the web layer.

### Change guidance
- Treat controller request and response changes as public API changes.
- Public plan/task/workflow run controls submit saved definitions to agent assignments; direct model-backed execution stays internal/test-only when needed.
- Public task and workflow run stream routes acknowledge queued assignment submission instead of streaming inline model execution.
- Public operational job APIs use `JobDefinition` records, allow empty `DRAFT` jobs, and expose job item routes separately from run routes.
- For SimplyPages-facing web surfaces, prefer reusable components/modules instead of one-off markup patterns.
- If UI behavior or structure is used in multiple places and is more than bare functionality, promote it into a reusable component/module.
- For similar views, prefer shared render structures and slot-key based reuse over duplicated near-identical templates.
- Before changing `/avatar` styling or layout, read `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md` and preserve the `/dashboard` plus per-agent dashboard operational style language unless the user explicitly changes direction.
- Preserve HTMX-compatible security failures and keep CSRF compatibility in the shared shell/client helpers when adding browser mutation routes.
- Do not put chat, persistence, or orchestration logic in controllers.
- Keep command parsing small and explicit.
- Keep this guide updated when web routes, API contracts, or controller conventions change.

### Validation
- Add or update controller tests for new routes, request validation, status codes, and response shapes.
- Check browser client behavior when web-facing API contracts change.
- For any web/UI change, capture Playwright screenshots of affected screens for agent-side visual review/debugging and verify non-breaking behavior, layout integrity, and sound UI/UX patterns before sign-off.
- Treat screenshots as internal validation artifacts first; use them in user communication when they help localize or explain UI issues.

# Security

Current alpha access is intentionally open at the application layer. Magenta does not currently enforce built-in HTTP authentication, authorization, or CSRF checks on web/API routes.

## Access Rules

`GET`, `HEAD`, and `OPTIONS` routes are public, and unsafe methods (`POST`, `PUT`, `PATCH`, `DELETE`) currently reach controller/service validation directly without an app-layer auth gate.

This keeps local/trusted alpha operation simple, but it is not a multi-user security model.

## Error Shape

Request failures are primarily domain and lifecycle failures:

- Validation/input failures generally return `400`.
- Missing records generally return `404`.
- Lifecycle/state conflicts generally return `409`.
- Unexpected failures return controller or framework `5xx` errors.

## Public Read Surface

Public alpha reads include, but are not limited to:

- Browser pages and fragments.
- Chat history/session reads.
- Model summaries.
- Dashboard summary.
- Plan/task/workflow/job/project/workspace/output reads.
- Agent profile and runtime state reads.
- Selector options/selected/validation GET routes.

Do not put sensitive secrets, raw credentials, or unrestricted filesystem content behind public `GET` routes. If a new read route exposes sensitive content, it needs a different security policy before implementation.

## Mutation Surface

Unsafe routes include:

- Chat turns, commands, interrupts, and conversation metadata changes.
- Plan/task/workflow/job/project/agent/settings CRUD.
- Assignment lifecycle controls.
- Schedule/reaction changes.
- Workspace link writes.
- Inbox read/handled/respond actions.
- Output state changes if added later.

Because there is no app-layer auth gate, these routes rely on controller/service validation and bounded runtime behavior for safety.

## Tool Safety

Agent tools are controlled outside HTTP security:

- Approved tool names are validated through `ChatToolRegistry`.
- Shell commands are constrained by per-agent allowlists.
- Wildcard shell allowlists from legacy file config are ignored unless the explicit unsafe wildcard override is enabled.
- File tools resolve paths through scoped roots.
- Output downloads resolve real paths and reject paths escaping the output service data root.

These checks should stay in tool/workspace services, not controllers.

## Additional Safety Controls

- Plain path segment validation for filesystem/path-derived ids.
- Inert rendering of user-authored workflow graph text.
- Assignment lifecycle route-agent scoping.

## Current Limitations

Alpha mode is not a multi-user authorization model. Deploy behind trusted network boundaries and add a dedicated auth/authorization layer before exposing sensitive operational surfaces broadly.

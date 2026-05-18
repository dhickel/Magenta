# Security

Alpha access security is implemented in [`AlphaSecurityConfiguration`](../../src/main/java/io/mindspice/magenta2/api/web/AlphaSecurityConfiguration.java). It is intentionally simple: public read routes, protected unsafe mutations, HTTP Basic alpha credentials, and cookie-backed CSRF tokens.

## Access Rules

The security filter chain permits:

- All `GET /**`
- All `HEAD /**`
- All `OPTIONS /**`

All other methods require authentication:

- `POST`
- `PUT`
- `PATCH`
- `DELETE`

This means read-only API routes, page routes, static assets, fragment GETs, and selector GETs are public in alpha mode. Mutating JSON and HTMX routes are protected.

## Alpha Credentials

Credentials come from `magenta.alpha-access`:

- `magenta.alpha-access.username`, default `alpha`
- `magenta.alpha-access.password`, default `change-me-alpha`

`application.yml` maps these to `MAGENTA_ALPHA_USERNAME` and `MAGENTA_ALPHA_PASSWORD` environment variables. `AlphaSecurityConfiguration` requires both values to have text and registers a single in-memory user with role `ALPHA`.

Passwords are configured with Spring's `{noop}` encoder prefix, so deployment should supply a non-default secret through environment/config.

## CSRF

CSRF uses `CookieCsrfTokenRepository.withHttpOnlyFalse()`, which exposes the token cookie to browser JavaScript. `CsrfCookieFilter` forces token generation by resolving the request token after the CSRF filter.

Unsafe browser requests must include the expected CSRF header. `alpha-security.js` is the browser helper that reads the cookie and attaches the header for client-side requests.

HTMX unsafe requests must also carry the token. Shell/client helpers should preserve that behavior when new mutation routes are added.

## Error Shape

Security failures are response-shape aware:

- Unauthenticated requests return `401` and `WWW-Authenticate: Basic realm="Magenta Alpha"`.
- Access denied requests return `403`.
- CSRF failures return message `CSRF token missing or invalid.`.
- HTMX requests (`HX-Request: true`) receive `text/html`, a small `.mag-auth-error` alert body, and `HX-Trigger: magenta:security-error`.
- Non-HTMX requests receive JSON such as `{"error":"Authentication required."}`.

This matters for operational UI: mutation controls should surface HTMX security failures instead of failing silently.

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

Protected unsafe routes include:

- Chat turns, commands, interrupts, and conversation metadata changes.
- Plan/task/workflow/job/project/agent/settings CRUD.
- Assignment lifecycle controls.
- Schedule/reaction changes.
- Workspace link writes.
- Inbox read/handled/respond actions.
- Output state changes if added later.

Controllers should validate ownership/identity at the service layer where relevant. Current alpha security authenticates the operator, not multiple end users.

## Tool Safety

Agent tools are controlled outside HTTP security:

- Approved tool names are validated through `ChatToolRegistry`.
- Shell commands are constrained by per-agent allowlists.
- Wildcard shell allowlists from legacy file config are ignored unless the explicit unsafe wildcard override is enabled.
- File tools resolve paths through scoped roots.
- Output downloads resolve real paths and reject paths escaping the output service data root.

These checks should stay in tool/workspace services, not controllers.

## Current Limitations

Alpha mode is not a multi-user authorization model. It provides a single operator access gate for unsafe actions. Any future multi-user deployment needs route-level authorization, object ownership, audit review, and likely different public read rules before exposing sensitive operational data broadly.

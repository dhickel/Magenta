# Runtime Architecture

## Design intent

Lean local runtime with a single IO boundary and explicit ownership:

- config load/validation
- session lifecycle
- routed input/output
- context compaction
- model/tool orchestration

## Service ownership

- `Magenta`: composition root and orchestration owner.
- `SessionManager`: lifecycle + in-memory session registry.
- `SessionRouter`: input policy enforcement and output event fanout.
- `ContextManager`: context state and compaction.
- `ModelRunner`: model/tool loop execution.
- `OllamaClient`: provider transport.
- `SecurityManager`: session tool policy decisions + authorization.
- `ToolManager`: stateless tool dispatch fallback.
- `DatabaseService`: detached SQLite persistence service for todo + session context state.

## Invariants

- Public interaction is handle-first (`SessionHandle`).
- One or more active input routes per session are supported (insertion-order evaluation).
- Multiple output routes per session are supported.
- `FinalOutput` is always emitted per completed assistant step.
- `StreamedOutput` is emitted only through output routes (no callback bypass path).
- Turn ingress failures call `SessionConfig.onError` and do not escape external input consumers.

## Failure behavior

- startup config failures are fail-fast
- startup fails fast when an enabled agent references unresolved tool IDs
- unknown/inactive handles raise deterministic validation errors
- input policy denials emit `InputRoutingEvent` and skip turn execution
- output listener failures are isolated and reported through router diagnostics
- session close prunes all input/output routes

## Extension points

- output filtering via `OutputRoutePolicy` (output filter-tag allowlist)
- tool execution policy via runtime-wrapped `SessionConfig.toolBridge`
- descriptor-driven authorization metadata via `ToolSecurityDescriptor` wiring from built-in tool catalog to `SecurityManager`
- single mutable session tool policy (`setToolPolicy(...)`)
- compaction behavior via model compaction settings + summarizer seam

## Known constraints

- in-memory session/routing registry only (no durable session lifecycle registry yet)
- todo + context/message persistence are durable SQLite-backed via `DatabaseService`
- single provider transport (`OllamaClient`)
- security ingress currently scoped to tool execution path

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

## Invariants

- Public interaction is handle-first (`SessionHandle`).
- Exactly one active input route per session.
- Multiple output routes per session are supported.
- `FinalOutput` is always emitted per completed assistant step.
- `StreamedOutput` is emitted only through output routes (no callback bypass path).
- Turn ingress failures call `SessionConfig.onError` and do not escape external input consumers.

## Failure behavior

- startup config failures are fail-fast
- unknown/inactive handles raise deterministic validation errors
- input policy denials emit `InputRoutingEvent` and skip turn execution
- output listener failures are isolated and reported through router diagnostics
- session close prunes all input/output routes

## Extension points

- output filtering via `OutputRoutePolicy` (output-kind allowlist)
- tool execution policy via wrapped `SessionConfig.toolBridge`
- compaction behavior via model compaction settings + summarizer seam

## Known constraints

- in-memory session/routing only
- single provider transport (`OllamaClient`)
- centralized `SecurityService` not yet wired

# Session Architecture

## Design intent

Represent each conversation as explicit session state with clear lifecycle operations and typed history.

## Responsibilities

- `SessionManager`: start/resume/fork/list/close and in-memory registry ownership.
- `Session`: immutable session envelope with mutable `Context` reference.
- `SessionConfig`: runtime callback contract for integration behavior.
- `SessionMessage`: sealed ADT for message history.

## Explicit non-goals

- alias-based lookup/routing
- persistent session registry
- remote session management

## Invariants

- Session IDs are generated UUIDs.
- `resume` and `fork` require existing session ID.
- Forked sessions copy source context snapshot.
- System prompt for started sessions is resolved from configured prompt IDs.
- If no alias is supplied, alias defaults to `session-<first8-uuid>`.

## Lifecycle transitions

```text
start(agentId, alias, config)
-> validate agent/model
-> resolve system prompt
-> load or create context
-> create Session and register in map

resume(sessionId)
-> map lookup or fail

fork(sessionId, alias, overrideConfig?)
-> resume source
-> copy source context
-> start new session with same agent
```

## Failure behavior

- start fails for missing/disabled agent or model.
- start fails if prompt ID is missing.
- resume/fork fail for unknown session ID.

## Extension points

- `SessionConfig` callback wiring for UI/eventing/tool execution.
- Future persistence can be attached via context load/store seams.

## Known constraints

- `SessionConfig.onError` is emitted from `Magenta.runSessionTurn` and errors still propagate.
- Session registry lifetime matches `Magenta` process lifetime.

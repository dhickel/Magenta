# Session Architecture

## Design intent

Represent conversation state as typed session data with explicit lifecycle operations and safe external handles.

## Responsibilities

- `SessionManager`: `start`/`resume`/`fork`/`list`/`close`.
- `Session`: immutable identity/config envelope + mutable `Context` reference.
- `SessionHandle`: external session reference (`sessionId`, liveness predicate, immutable `SessionSettingsView` snapshot).
- `SessionConfig`: execution controls (`params` with `blockingOnly`/`toolsEnabled`/`streamingEnabled`, plus `toolBridge`, `onError`).

## Invariants

- Session IDs are UUIDs.
- `resume`/`fork` require existing session IDs.
- `fork` copies source context snapshot.
- started sessions resolve system prompts from configured prompt IDs.
- blank alias normalizes to `session-<first8-uuid>`.

## Lifecycle transitions

```text
start(agentId, alias, config)
-> validate agent/model
-> resolve prompts
-> create/load context
-> register session

resume(sessionId)
-> lookup or fail

fork(sessionId, alias, overrideConfig?)
-> resume source
-> copy context
-> start new session
```

## Failure behavior

- missing/disabled agent/model/prompt fails startup for that session operation
- unknown session ID fails `resume`/`fork`
- internal turn exceptions trigger `SessionConfig.onError` at submit ingress

## Known constraints

- session registry lifetime is process lifetime
- durable session persistence is future-phase

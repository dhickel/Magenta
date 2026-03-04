# Runtime Troubleshooting

## Config parse and validation failures

Symptoms:

- startup fails before `Magenta` construction
- error includes parse location (file + line + column)

Checks:

1. verify files and include paths under `configs/`
2. fix unknown keys / unresolved IDs
3. verify enabled agents reference enabled models/prompts

## Session handle and lifecycle errors

Symptoms:

- `Session not found: <uuid>` from lifecycle calls
- routing calls fail after close because session handle is inactive

Checks:

1. ensure handle belongs to current process/runtime instance
2. avoid using handles after `closeSession`
3. remember in-memory registry is cleared on process restart

## Input routing issues

Symptoms:

- no turn executed after input submit
- final input routing events report `DENIED_POLICY` or `SESSION_INACTIVE`

Checks:

1. confirm at least one input route is registered for that handle
2. verify `InputRoutePolicy` allows the input filter/source
3. set `routingEventLevel=ALL` temporarily for diagnostics

## Output routing issues

Symptoms:

- no streaming tokens delivered
- listener stops receiving after callback failure

Checks:

1. confirm output route is registered and not filtered by `OutputRoutePolicy`
2. for streamed output listeners, ensure session `streamingEnabled=true`
3. confirm listener callback is non-throwing

## Tool bridge failures

Symptoms:

- turn fails in tool loop
- tool output content defaults to not-handled

Checks:

1. verify `toolsEnabled=true` and `toolBridge` wiring
2. validate bridge input parsing and error handling
3. wrap bridge with policy guards where needed

## onError behavior

`SessionConfig.onError` is notification-only; ingress swallows turn exceptions after callback.
`SessionException` includes the originating `SessionHandle` for multi-session consumers.

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
- `Unknown session handle: <uuid>` from router calls

Checks:

1. ensure handle belongs to current process/runtime instance
2. avoid using handles after `closeSession`
3. remember in-memory registry is cleared on process restart

## Input routing issues

Symptoms:

- no turn executed after input submit
- `DENIED_POLICY` or `SESSION_INACTIVE` input routing events

Checks:

1. confirm input route is registered for that handle
2. verify `InputRoutePolicy` allows the configured input filters and source
3. use `InputRoutingEvent.Level.ALL` temporarily for diagnostics

## Output routing issues

Symptoms:

- no streaming tokens delivered
- listener stops receiving after callback failure

Checks:

1. confirm output route is registered and not filtered out by `OutputRoutePolicy`
2. for streamed output, ensure session `streamingEnabled=true`
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

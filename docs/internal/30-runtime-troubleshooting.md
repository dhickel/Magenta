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

## Security denial surprises

Symptoms:

- tool requests denied with `validation_error` or `denied` despite tool being enabled
- path operations prompt/deny when using symlinked directories

Checks:

1. verify active `SecurityManager.ToolPolicy` for the session (`allowedPaths`, tool allow/deny, command rules).
2. confirm tool argument key matches descriptor-defined keys for that tool.
3. for path tools, confirm resolved target remains inside approved `allowedPaths` roots.
4. if out-of-root behavior is expected, ensure approval callback is configured and operational.

## Shell policy validation failures

Symptoms:

- `shell_command` returns security `validation_error` for command structure

Checks:

1. remove command chaining/operators (`;`, pipes, redirects, substitutions) for strict security mode.
2. verify command rules match parsed command prefix tokens (quote-aware parsing).
3. if rules are configured and no rule matches in `BLACKLIST`, expect mode fallback (allowed unless another gate blocks it).
4. add an explicit catch-all `PROMPT` rule (`commandPrefix: []`) when interactive approval is required for all shell commands.

## SQLite tool gating failures

Symptoms:

- `sqlite_query` / `sqlite_exec` fails with `invalid_sql_kind`

Checks:

1. validate SQL parses cleanly (parser failures are fail-closed).
2. `sqlite_query` supports one read statement only.
3. `sqlite_exec` rejects read statements, `ATTACH`/`DETACH`, `PRAGMA`, and unknown statement classes.

## TODO tool naming confusion

Symptoms:

- prompts reference `todo_read` and tool call is not handled
- model/tool usage appears inconsistent for TODO state reads

Checks:

1. use canonical TODO tools only: `todo_create`, `todo_list`, `todo_update`, `todo_delete`.
2. treat `todo_list` as the TODO read operation for current session state.
3. verify prompt/task text uses canonical IDs to avoid model confusion.

## onError behavior

`SessionConfig.onError` is notification-only; ingress swallows turn exceptions after callback.
`SessionException` includes the originating `SessionHandle` for multi-session consumers.

## Event logging and debug visibility

Symptoms:

- expected debug traces not visible in logs
- expected runtime actions not found in logs

Checks:

1. confirm event files are being written under `workspaceRoot/logs/`:
   - `session-events.jsonl`
   - `session-events.pretty.json` (only when `instance.observability.pretty_logs_enabled=true`)
2. verify effective log level:
   - config: `instance.observability.log_level`
   - CLI override: `--log-level <off|error|info|debug|trace>`
3. remember full tool payloads are only logged at `DEBUG` and `TRACE`; `ERROR`/`INFO` store 1 KB preview-capped payload metadata.
4. verify listeners were registered for the correct session handle and event type.
5. if using callback compatibility fields (`onRouting`/`onSecurity`/`onError`), verify callbacks are provided in the `SessionConfig` passed at start/fork.

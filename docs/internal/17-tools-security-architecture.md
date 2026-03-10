# Tools and Security Architecture

## Scope

Defines the current tool execution and authorization architecture in the implemented runtime slice.

## Built-in tool surface

Current built-ins:

- `read_file`
- `list_directory`
- `file_metadata`
- `grep_files`
- `search_replace`
- `write_file`
- `delete_file`
- `shell_command`
- `sqlite_query`
- `sqlite_exec`
- `todo_create`
- `todo_list`
- `todo_update`
- `todo_delete`
- `list_agents`
- `delegate_agent`

All tools return structured payloads normalized by `ToolManager` (`status`, `code`, `message`, optional `data`).

## Tool family overview

Canonical usage groupings:

- File operations:
  - `read_file`, `list_directory`, `file_metadata`, `grep_files`, `search_replace`, `write_file`, `delete_file`
- Shell execution:
  - `shell_command`
- SQLite operations:
  - `sqlite_query` (read-only), `sqlite_exec` (mutating)
- Todo/task-state operations:
  - `todo_create`, `todo_list`, `todo_update`, `todo_delete`
- Agent orchestration:
  - `list_agents`, `delegate_agent`

TODO lifecycle is canonicalized as: `todo_create -> todo_list -> todo_update -> todo_delete`.
There is no separate `todo_read` tool; `todo_list` is the read operation.

## Execution pipeline

Tool path is runtime-owned and single-route:

```text
ModelRunner tool call
-> SessionConfig.toolBridge (Magenta-wrapped)
-> SecurityManager.authorize(...)
-> ToolManager.execute(...)
-> ToolManager.normalizeResult(...)
-> security/tool events emitted to callbacks/routes
```

No alternate privileged tool route is supported.

## Descriptor-driven security integration

`SecurityManager` no longer relies on hardcoded tool-name sets for key extraction.

Per-tool security metadata is supplied as `ToolSecurityDescriptor` via `ToolManager`:

- `pathKeys` + `requiresPath`
- optional `defaultPathWhenMissing` for tools that intentionally default to workspace-relative paths
- `commandKeys` + `requiresCommand`
- `urlKeys` + `requiresUrl`
- optional descriptor validator callback for specialized checks

Built-in descriptors are declared with annotated catalog registration, then injected into `SecurityManager` from `Magenta`.

Todo tools do not use path descriptors. Authorization treats `todo_*` as one policy identity key (`todo`) after the agent tool gate, so allow/deny is mode/list driven instead of `allowedPaths` driven.

## Path policy semantics (`allowedPaths`)

- `allowedPaths` in config represents approved path roots.
- Candidate tool paths are resolved against workspace root and compared using resolved real targets (symlink-aware by existing ancestor resolution).
- If descriptor marks path required and no descriptor key is present, decision is `validation_error` (fail-closed).
- If descriptor defines `defaultPathWhenMissing` and no path argument is present, security validates that implicit path (for example `"."`) against `allowedPaths`.
- If `allowedPaths` is empty, no root restriction is applied.
- If resolved target is outside approved roots, approval callback is invoked; deny or missing callback rejects request.

## Shell command policy semantics

- Command extraction is descriptor-driven (`cmd`/`command` for built-ins).
- Parsing is quote/escape-aware and policy token matching uses parsed tokens (not whitespace split).
- Shell operators/chaining (`;`, `|`, `&&`, redirects, command substitution, etc.) are rejected by security validation in strict mode.
- Rule matching is prefix-based over parsed tokens.
- `allowedCommands` still gates first executable token when configured.
- Unmatched command rules do not implicitly prompt/deny; in `BLACKLIST` mode they fall through to the mode decision.
- A command rule with an empty `commandPrefix` acts as an explicit catch-all (for example, prompt all shell commands).

## SQLite policy semantics

SQL classification is parser-based (JSqlParser) and fail-closed.

`sqlite_query`:

- exactly one statement required
- only parsed read-select category allowed
- `PRAGMA` and parse failures rejected (`invalid_sql_kind`)

`sqlite_exec`:

- one or more statements required
- rejects read-only, `ATTACH`/`DETACH`, `PRAGMA`, and unknown categories
- parser failures rejected (`invalid_sql_kind`)
- transaction wrapping behavior remains controlled by `transactional` argument

## Tool contract and schema behavior

- Annotated tools generate provider tool schemas.
- `grep_files` `filePattern` supports both full relative-path glob matching and basename matching for nested files (for example `fractal.lisp`).
- `grep_files` `pattern` matches file content lines (not filenames); use `filePattern` for filename filtering.
- `search_replace` now exposes typed nested edit schema (`startAnchor`, `endAnchor`, `replacement`, optional `expectedText`) instead of raw `JsonNode` nested shape.
- Added discovery tools:
  - `list_directory`: bounded metadata listing without content read
  - `file_metadata`: one-path stat inspection

## Observability and tests

- Security decisions emit `SecurityManager.SecurityEvent` through session callback (`onSecurity`).
- Tool outputs remain route-visible as tool/context output events.
- Gate tests cover:
  - functionality payload shape
  - policy outcomes (`allowed`, `denied`, `validation_error`, `override_allowed`)
  - integration path proving denied requests have no side effects

## Known constraints

- Security policy state is in-memory per session.
- Web tools are not currently implemented in built-in surface; web policy handling remains forward-compatible in `SecurityManager`.
- Out-of-root approval uses blocking callback path in current terminal implementation.
- `delegate_agent` currently supports synchronous ephemeral delegation only (no async job queue mode).

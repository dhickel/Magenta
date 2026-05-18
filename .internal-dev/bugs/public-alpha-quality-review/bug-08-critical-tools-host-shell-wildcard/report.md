# Shell Tool Runs Host Commands With Wildcard Configuration

## Summary

The filesystem runtime shell tool runs host `ProcessBuilder` commands; validation only constrains the working directory and executable token.

## Scope

`AgentShellToolService` and default/example configuration.

## Reproduction

1. Enable shell tools with wildcard allowed commands.
2. Invoke shell command that reads absolute paths or uses allowed shells to run arbitrary effects.

## Expected

Filesystem runtime tools should enforce assignment/workspace confinement and avoid host-wide command effects.

## Actual

The first token is checked against an allowlist, the working directory is confined, and then a raw host process starts. Example config allows wildcard tools/commands.

## Evidence

- `AgentShellToolService.java:90` validates first token against allowed commands.
- `AgentShellToolService.java:178` starts a host `ProcessBuilder`.
- `AgentShellToolService.java:333` confines only working directory.
- `config/ai-config.example.json:63` includes wildcard approved tools/allowed commands.

## Impact

Critical: a tool-capable agent can affect host files/processes outside the intended workspace contract when commands permit it.

## Status

Resolved in workspace for subplan 01; pending orchestrator commit and any external validation gate.

## Next Action

Commit the subplan 01 workspace changes, then continue domain 02 with file tool confinement in subplan 02.

## Resolution Notes

- Added explicit `unsafeAllowWildcardShellCommands` config. Wildcard shell commands are disabled by default and ignored during legacy profile seeding unless this override is true.
- Removed wildcard approved-tool and shell-command defaults from `config/ai-config.example.json`.
- Constrained shell working directory resolution to the active run workspace/output path when `OrchestrationTaskContext.hostWorkspacePath` is present, with current-project workspace scope support for `projects/{projectId}`.
- Rejected shell wrapper executables, absolute filesystem path arguments, parent traversal arguments, and shell-control tokens even when wildcard shell commands are explicitly enabled.
- Added focused tests for workspace-local execution, denied absolute/unrelated/project paths, shell wrappers, wildcard defaults, and unsafe wildcard override behavior.

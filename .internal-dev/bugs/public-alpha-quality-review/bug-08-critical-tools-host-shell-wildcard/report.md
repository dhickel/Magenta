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

Open.

## Next Action

Replace wildcard defaults, constrain command effects to an assignment workspace, and add explicit deny/allow tests for absolute path access and shell wrappers.

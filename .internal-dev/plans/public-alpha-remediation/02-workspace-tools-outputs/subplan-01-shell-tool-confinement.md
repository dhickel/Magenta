# Subplan 01: Shell Tool Confinement

## Goal

Remove wildcard shell defaults and constrain shell execution to the active assignment workspace contract.

## Implementation Steps

1. Inspect `AgentShellToolService`, tool config loading, and `config/ai-config.example.json`.
2. Replace wildcard default approved commands with explicit safe defaults or disabled-by-default behavior.
3. Validate working directory against active assignment workspace and linked project scopes.
4. Reject shell wrappers or absolute path access patterns that can trivially escape the contract.
5. Add tests for allowed workspace-local commands and denied absolute/unrelated paths.
6. Include a yolo override

## Validation

Focused shell tool tests, config example check, and runtime smoke.

# Date

2026-04-25

# Change Summary

Added optional glob filtering for `file_list`, wildcard tool approval, and a structured `shell_exec` tool for allowed Linux commands inside the configured agent data root.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/`
- `src/main/java/io/mindspice/magenta2/ai/config/user/`
- `config/ai-config.example.json`
- `config/prompts/system.md`

# Behavioral Impact

- `file_list` can narrow entries with a Java glob matched against data-root-relative paths.
- `approvedTools: ["*"]` approves all registered Spring AI tools for the agent.
- `allowedShellCommands: ["*"]` allows `shell_exec` to run any bare executable name within its other constraints.
- `shell_exec` runs commands with structured arguments, a confined working directory, capped timeout, and bounded output.

# Risks

Wildcard tool and shell command approval grants broad local capability inside the configured data root. Deployment configs should use explicit command names unless the agent is trusted for broad filesystem operations.

# Follow-up Items

Consider a user approval workflow before adding raw shell-string execution, custom environments, stdin, or long-running command support.

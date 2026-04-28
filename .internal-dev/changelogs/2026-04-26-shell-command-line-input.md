# Date

2026-04-26

# Change Summary

Changed `shell_exec` from separate executable and argument fields to a single command-line string. The shell tool now splits the command line internally, validates the first executable token against the configured allowlist, and still runs the parsed argv through `ProcessBuilder`.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolServiceTest.java`
- `config/prompts/system.md`
- `.internal-dev/notes/2026-04-26-shell-command-line-parser-deficiencies.md`

# Behavioral Impact

- Models can call `shell_exec` with command strings such as `mv old.txt new.txt` or `printf "hello world"`.
- `shell_exec` no longer exposes a separate `args` schema field.
- Shell operators are not interpreted; parsed tokens are passed directly to `ProcessBuilder`.

# Risks

- Current parser and validation are intentionally limited for local testing and should not be treated as a complete shell policy layer.

# Follow-up Items

- Strengthen command-line validation and argument policy before broader use.

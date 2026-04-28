# Shell Command Line Parser Deficiencies

## Context

`shell_exec` now accepts a single command line string and splits it internally before running `ProcessBuilder`.

## Deficiencies

- The parser is intentionally small and only handles whitespace, single quotes, double quotes, and backslash escapes.
- Shell operators such as `&&`, `||`, pipes, redirects, command substitution, glob expansion, variable expansion, and environment assignment are not interpreted.
- Allowlist validation applies to the first executable token only.
- Arguments are not yet policy-validated beyond the working directory confinement applied to process execution.

## Next Action

Add stronger command-line validation before exposing this beyond local testing, including explicit handling or rejection of shell operators and per-command argument policy where needed.

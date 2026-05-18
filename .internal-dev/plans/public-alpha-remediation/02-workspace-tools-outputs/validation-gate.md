# Workspace, Tools, and Outputs Validation Gate

## Validator Instructions

Read domain workspace/tool/output review files and bug reports 08, 09, 10, 13, 22, 23, and 24 before validating.

## Required Checks

- Shell wildcard defaults removed or made non-effective.
- File tools cannot read/write unrelated runtime data.
- Web fetch rejects public-to-private redirects.
- Project lease creates usable promised path for tools.
- Allocation failure fails execution immediately.
- Output symlink and attribution regressions pass.
- Focused tests, full `mvn test`, and bounded startup pass.

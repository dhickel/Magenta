# Scope

Post-remediation architecture review for the `root-relative-workspace-migration` branch after commit `d4975ea` and the final runner resolver hardening.

Reviewed areas:

- Workspace PATH-link read/display behavior for current-root absolute links and stale outside-root absolute links
- Job and runner root-relative path resolver fallback behavior
- Runtime tests for root-relative persisted paths and host-path execution context handoff
- SQLite parent directory handling for file-backed `jdbc:sqlite:file:` URI forms
- Closeout docs, changelogs, and reusable knowledge notes

# Findings

No high or medium blockers remain.

## Low - Stale workspace PATH links are hidden rather than marked

Stale absolute PATH links outside the current data root are now filtered from normal list/display responses instead of being shown as raw host paths. This closes the old-host-path exposure risk, but operators cannot see those stale rows through the normal workspace-link surface.

This behavior is acceptable for the current migration contract because stale old-root workspace data is intentionally not imported, repaired, or copied. Future migration/import tooling can expose stale rows explicitly if operator repair workflows need that visibility.

# Prior Blocker Closure

- Workspace links: closed. Current-root absolute PATH links are compatibility-read and returned as root-relative values without rewriting database rows; stale outside-root absolute links are filtered.
- JobService fallback/storage: closed. JobService creates a fallback resolver when workspace directories are available and only falls back to string storage when no resolver can exist.
- OrchestrationRunnerService fallback: closed after review. The runner now mirrors the fallback resolver construction so root-relative job workspace paths resolve to host paths when workspace directories are available.
- Tests: closed. Updated tests assert root-relative persisted values and resolve them against the test data root for filesystem assertions, while runtime context tests still assert absolute host paths.
- SQLite URI parent creation: closed. File-backed `jdbc:sqlite:file:` URI forms are handled while in-memory SQLite forms remain excluded.

# Sign-Off

Architecture/code sign-off: pass.

Release sign-off remains dependent on the final post-hardening validation gate.

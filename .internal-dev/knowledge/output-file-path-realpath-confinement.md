# Topic

Output file_path realpath confinement

# Source References

- `.internal-dev/plans/public-alpha-remediation/02-workspace-tools-outputs/subplan-06-output-symlink-materialization.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactServiceAttributionTest.java`

# Key Takeaways

- `file_path` output materialization must check the source path's real target, not only the lexical path string.
- Resolve candidate source paths with `toRealPath()` and compare against `WorkspaceDirectoryService.dataRoot().toRealPath()` before copying or saving artifact metadata.
- Use `LinkOption.NOFOLLOW_LINKS` when checking source and destination existence so broken symlinks can produce clear failures and existing destination symlinks do not hide collisions.
- Bare filename and relative output values should keep resolving against the run output directory; the resolved source must still pass real data-root confinement.
- When the source is already the destination path, validate its real target first. This prevents an escaped symlink in the output directory from being registered as a legitimate artifact.

# Engine Relevance

This pattern protects host filesystem output materialization without changing the user-visible `file_path` contract for normal run output files.

# Open Questions

- Domain 02 subplan 07 should verify output attribution assumptions after this materialization hardening.

# Output file_path Symlink Hardening

## Date

2026-05-18

## Change Summary

Implemented public alpha remediation bug-23 / domain 02 subplan 06. `OutputArtifactService` now resolves `file_path` output sources with `toRealPath()` and requires the real source target to stay under the real data root before copying or registering the artifact.

## Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactServiceAttributionTest.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-23-medium-output-file-path-symlink/report.md`
- `.internal-dev/knowledge/output-file-path-realpath-confinement.md`

## Behavioral Impact

- Bare filenames and relative `file_path` outputs still resolve against the run output directory.
- Valid source files inside the data root continue to copy into the output directory.
- Symlinks under the data root that resolve outside the data root are rejected before copy or artifact registration.
- Missing files and broken symlinks now fail with explicit materialization errors.

## Risks

Tests or ad hoc callers that previously materialized `file_path` outputs from temporary directories outside `dataRoot` must now place those files under the configured data root, matching runtime output allocation.

## Validation

- Focused validation passed with `mvn -Dtest=OutputArtifactServiceAttributionTest test`.
- `git diff --check` passed.
- Bounded Spring Boot startup reached a healthy app on ephemeral port `45683` before timeout shutdown.

## Follow-up Items

- Domain 02 subplan 07 output attribution remains out of scope for this change.

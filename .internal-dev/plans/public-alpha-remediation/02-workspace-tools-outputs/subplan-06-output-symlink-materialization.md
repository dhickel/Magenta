# Subplan 06: Output Symlink Materialization

## Goal

Prevent `file_path` output materialization from copying files outside `dataRoot` through symlinks.

## Implementation Steps

1. Replace lexical normalize checks with `toRealPath()`-based confinement against real data root.
2. Handle missing file and broken symlink cases with clear failure.
3. Preserve valid output copy behavior.
4. Add symlink escape regression test.

## Validation

Output materialization tests for valid file, outside symlink, broken symlink, and missing file.

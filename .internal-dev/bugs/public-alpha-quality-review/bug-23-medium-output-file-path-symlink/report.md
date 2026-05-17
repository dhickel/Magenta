# Output file_path Materialization Can Follow Symlinks

## Summary

`file_path` output materialization checks lexical `dataRoot` confinement before copying, so symlinks under `dataRoot` can point outside.

## Scope

`OutputArtifactService` materialization from `file_path` outputs.

## Reproduction

1. Create a symlink under `dataRoot` pointing outside.
2. Reference it as an absolute `file_path` output.
3. Materialization copies target contents into output directory.

## Expected

Materialization should resolve real paths and reject symlink escapes before copying.

## Actual

Absolute file path validation uses normalized lexical path before `Files.copy(...)`.

## Evidence

- `OutputArtifactService.java:260` checks `Path.normalize().startsWith(dataRoot)`.
- `OutputArtifactService.java:282` copies the file.

## Impact

Medium: data outside `dataRoot` can be copied into managed output artifacts if symlink setup is possible.

## Status

Open.

## Next Action

Use `toRealPath()` and data-root realpath confinement before materializing output files.

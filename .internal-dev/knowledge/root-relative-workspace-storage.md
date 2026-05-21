# Topic

Root-relative Magenta workspace storage.

# Source References

- `src/main/java/io/mindspice/magenta2/core/config/MagentaRootConfiguration.java`
- `src/main/java/io/mindspice/magenta2/core/config/MagentaRootProperties.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/RootRelativePathService.java`
- `docs/technical/configuration-operations.md`
- `.internal-dev/plans/root-relative-workspace-migration/implementation-plan.md`

# Key Takeaways

- Treat `magenta.root.path` as the product runtime root. The default root is `${user.home}/.magenta`.
- Keep the SQLite database and data root together by default: `<magenta.root.path>/magenta.sqlite` and `<magenta.root.path>/root`.
- Store Magenta-owned filesystem path columns as data-root-relative strings for new writes.
- Resolve persisted path strings through `RootRelativePathService`; do not use `Path.of(storedValue)` directly on database path columns.
- Keep runtime tool context paths as resolved host filesystem paths. Database storage is portable; tool execution still needs actual host paths.
- Compatibility reads are intentionally narrow: absolute paths are accepted only when they are under the current configured data root.

# Engine Relevance

This rule is central to root portability and future migration work. It lets Magenta move a database and root together without rewriting every owned path row, while keeping old current-root rows readable during the transition.

# Open Questions

- Should a future migration command rewrite unstructured JSON/transcript path references, or only authoritative owned path columns?
- Should path-kind metadata be added to distinguish relative/absolute/stale values after the compatibility period?
- Should startup diagnostics report stale path rows without repairing them?

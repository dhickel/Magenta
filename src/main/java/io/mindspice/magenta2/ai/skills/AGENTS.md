## Agent Skills Package

This package owns Agent Skills filesystem discovery, `SKILL.md` parsing/validation, and metadata persistence for the Magenta root skill repository.

### Responsibilities
- Resolve the Magenta-owned skill repository at `<magenta-root>/skills`.
- Enforce confined path resolution for skill filesystem operations.
- Parse and validate `SKILL.md` frontmatter/body with stable diagnostics.
- Discover skill directories and persist metadata, diagnostics, and optional-directory visibility flags.
- Keep malformed skills visible as metadata diagnostics without crashing discovery.

### Change guidance
- Keep this package backend-domain only. Do not add web/controller rendering logic here.
- Do not scan project-local `.agents/skills` in MVP.
- Treat `allowed-tools` as experimental metadata only unless explicitly approved otherwise.
- Do not eagerly load `scripts/`, `references/`, or `assets/` file content during discovery.
- Keep path-confinement checks explicit and test-backed (normalization + realpath + root prefix checks).

### Validation
- Parser tests for valid/invalid frontmatter, required fields, and lenient warning behavior.
- Discovery tests for root scan behavior, malformed safety, optional-directory flags, and refresh-after-edit metadata changes.
- Path-confinement tests for traversal/symlink escape rejection.

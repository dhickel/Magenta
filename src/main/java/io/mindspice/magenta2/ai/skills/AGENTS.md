## Agent Skills Package

This package owns Agent Skills filesystem discovery, `SKILL.md` parsing/validation, metadata persistence, agent assignment metadata, runtime catalog filtering, and dedicated activation behavior for the Magenta root skill repository.

### Responsibilities
- Resolve the Magenta-owned skill repository at `<magenta-root>/skills`.
- Enforce confined path resolution for skill filesystem operations.
- Parse and validate `SKILL.md` frontmatter/body with stable diagnostics.
- Discover skill directories and persist metadata, diagnostics, and optional-directory visibility flags.
- Persist agent skill assignments separately from approved tool metadata.
- Build runtime skill catalogs using assigned, enabled, loadable skills only.
- Activate assigned skills by returning the parsed `SKILL.md` body plus resource listings without eager resource reads.
- Deduplicate repeat activations per conversation/session context.
- Keep malformed skills visible as metadata diagnostics without crashing discovery.

### Change guidance
- Keep this package backend-domain only. Do not add web/controller rendering logic here.
- Do not scan project-local `.agents/skills` in MVP.
- Treat `allowed-tools` as experimental metadata only unless explicitly approved otherwise.
- Do not store skill assignments in `agent_profiles.approved_tool_names_json`.
- Do not eagerly load `scripts/`, `references/`, or `assets/` file content during discovery.
- Keep path-confinement checks explicit and test-backed (normalization + realpath + root prefix checks).

### Validation
- Parser tests for valid/invalid frontmatter, required fields, and lenient warning behavior.
- Discovery tests for root scan behavior, malformed safety, optional-directory flags, and refresh-after-edit metadata changes.
- Path-confinement tests for traversal/symlink escape rejection.

# Senior Engineer Guidance

## Planning Boundary

This suite is a plan only. Product code, tests, schemas, runtime configuration, and behavior are implemented by workers after the main thread explicitly begins execution.

## Architecture Principles

- Keep Agent Skills first-class without making Magenta a generic plugin marketplace.
- Prefer one clear skill domain package over scattering parser, repository, prompt, and UI code across existing packages.
- Keep root skill repository confinement explicit and testable. Treat app-root skill editing as security-sensitive filesystem work.
- Use SQLite metadata for discovered skills and assignments, but keep disk `SKILL.md` as the editable skill content source.
- Keep catalog disclosure compact. Progressive disclosure is a hard product and spec constraint.
- Keep assignment semantics honest: MVP is agent assignment only; future layers must be documented as deferred, not half-implemented.
- Keep controllers thin. All validation, filesystem resolution, parsing, metadata refresh, and assignment lookup belongs in services.
- Avoid introducing migration frameworks, registries, package installers, script runners, or subagent execution unless the user explicitly expands scope.

## Official Spec Discipline

Every worker and validator touching spec-sensitive code must open the official Agent Skills pages before editing or approving:

- `https://agentskills.io/specification`
- `https://agentskills.io/client-implementation/adding-skills-support`
- `https://agentskills.io/skill-creation/best-practices`
- `https://agentskills.io/skill-creation/using-scripts`

The local research file is a map, not an authority. If it disagrees with the official pages, use the official pages or stop and document a deliberate Magenta divergence.

## Validation Bias

The tests must be specification-focused, not just happy-path Magenta tests. The minimum suite includes:

- root repository discovery;
- valid frontmatter parsing;
- missing/invalid required fields;
- directory/frontmatter name mismatch handling;
- optional directory visibility;
- catalog-only loading;
- full body activation/loading;
- activation deduplication;
- assigned/unassigned availability;
- loader refresh after edits;
- safe malformed-skill handling;
- final `gpt-5.5` xhigh spec-adherence validation.

## UI Guidance

The skill browser/editor is operational software. Reuse master/detail list, status chip, HTMX fragment, selector, and text-editor patterns before inventing new UI machinery. Use JavaScript only for narrow behavior where HTMX/server fragments are the wrong tool.

The UI implementation work unit must be assigned to a `gpt-5.3` xhigh implementation agent because it combines SimplyPages conventions, file editing, guided creation, responsive layout, and browser proof.

## Failure Handling

Malformed skills are data, not fatal errors. They should appear in diagnostics where useful and be skipped from runtime catalog/activation when required fields are unusable.

If a worker discovers a necessary behavior is not covered by the official specification, record it as a Magenta product decision or deferred feature rather than guessing.

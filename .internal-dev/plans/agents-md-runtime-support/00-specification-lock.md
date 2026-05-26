# Specification Lock

## Classification

Medium.

This is a feature spanning runtime workspace generation, confined `AGENTS.md` resolution, prompt/context assembly, tests, and documentation/specification updates. It does not require schema migration or visible UI by default.

## Objective

Implement Magenta runtime support for `AGENTS.md` and generated starter workspace guidance so:

- Magenta-created agent workspaces get starter `AGENTS.md` guidance exactly once on first creation.
- Magenta agents working in project or Work Area contexts resolve applicable `AGENTS.md` instructions predictably.
- Runtime prompt/context injection preserves Magenta's explicit layering semantics.

## Mandatory Source Of Truth

Implementation and validation agents must verify current behavior-sensitive requirements against the official specification site before changing or approving spec-sensitive behavior:

- Official source: <https://agents.md/>
- Local research context: `.internal-dev/research/agents-md-specification-research.md`
- Local reusable reference: `.internal-dev/knowledge/agents-md-specification-reference.md`

The research and knowledge files are context, not truth. If the official site and local summaries disagree, the implementation must follow the official site except for Magenta's explicitly documented divergence below.

## Locked Magenta Interpretation

Magenta deliberately interprets `AGENTS.md` runtime context as layered instructions:

1. Explicit user prompt and current user task instructions override all `AGENTS.md` content.
2. All ancestor `AGENTS.md` files from the bound root to the active working path remain active as baseline/context.
3. When instructions conflict, the closest applicable `AGENTS.md` has precedence.
4. Nested context changes as the working path changes; no-longer-applicable nested files must be absent or explicitly de-emphasized from the effective context.
5. Runtime resolution must never traverse outside the bound project, Work Area, or effective workspace root.

This differs from the official site's shorthand that the closest file wins. Magenta documents the divergence as: ancestor files remain active for non-conflicting guidance, while closest-wins is conflict precedence.

## Acceptance Criteria

- New agent workspaces receive a starter `AGENTS.md` only when the workspace root is first created.
- Existing or user-edited `AGENTS.md` files are never overwritten, regenerated, or normalized.
- Starter content is hard-coded in the application for now.
- Starter guidance covers workspace root expectations, `/home` persistent agent-owned files/scripts, `/runs` staging semantics, `<runId>/outputs` as agent-written output staging, `/workareas` as user-controlled Work Areas, and project/job-bound working expectations.
- Resolver handles no-file, root-only, nested-only, and root-plus-nested cases.
- Resolver prevents traversal outside the bound project, Work Area, or workspace root.
- Runtime prompt/context behavior reflects layered ancestor semantics with closest conflict precedence.
- Runtime resolution is clear when a job/task is bound to a project or Work Area.
- Specifications explain the intentional divergence from the external shorthand.
- Docs explain agent workspace structure and generated guidance.
- Package-level `AGENTS.md` files are updated only if implementation changes local development guidance.

## Negative Criteria

- Do not implement product code in this planning phase.
- Do not add a database schema change unless the implementation worker proves it is necessary and returns to planning first.
- Do not add UI unless a worker discovers an existing visible surface must change to expose or configure this behavior; return to planning before adding UI scope.
- Do not introduce configurable starter-template storage in this phase.
- Do not overwrite a pre-existing `AGENTS.md`, even if it looks generated.
- Do not allow path traversal, symlink escape, or absolute-path lookup outside the bound root.
- Do not treat `.internal-dev/research/agents-md-specification-research.md` as authoritative.
- Do not replace ancestor instructions with only the closest file.

## Required Specification And Docs Updates

- `.internal-dev/specifications/architecture.md`: runtime `AGENTS.md` behavior and root-bound confinement.
- `.internal-dev/specifications/services.md`: resolver, starter generation, prompt/context integration service contracts.
- `.internal-dev/specifications/service-graph.md`: allowed service dependency direction for workspace/prompt integration.
- `.internal-dev/specifications/decisions.md`: deliberate Magenta divergence from the external shorthand.
- `docs/technical/workspaces-tools-outputs.md`: agent workspace guidance and runtime resolution.
- `docs/end-user/agents.md`: generated workspace guidance behavior and no-overwrite guarantee.
- Relevant package `AGENTS.md` files only if the implementation changes local development guidance.

## Validation Criteria

- Unit tests for `AGENTS.md` discovery/resolution cover the full specification-focused matrix in `shared/validation-matrix.md`.
- Service tests prove starter file creation on first workspace creation and no overwrite afterward.
- Prompt/context tests prove layered injection, conflict-precedence wording/order, context changes between subtrees, and unload/de-emphasis of no-longer-applicable nested context.
- Startup smoke passes after Spring wiring changes.
- Focused browser validation is required only if implementation changes a visible UI surface.
- Final spec-adherence review is performed by a `gpt-5.5` xhigh validation agent against the official site, allowing only Magenta's documented layering divergence.

## Stop Rules

- Stop and return to planning if official site behavior materially conflicts with this lock beyond the documented divergence.
- Stop and return to planning if implementation requires schema changes, new UI scope, configurable template storage, or broad prompt architecture refactoring.
- Stop if resolver confinement cannot be proven with real path normalization and tests.
- Stop if runtime injection cannot identify the bound root for project/Work Area/job execution contexts.

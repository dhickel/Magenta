# Implementation Notes

## Required First Step For Every Worker And Validator

Before making or approving spec-sensitive behavior, open <https://agents.md/> and record the exact current facts used. Treat `.internal-dev/research/agents-md-specification-research.md` and `.internal-dev/knowledge/agents-md-specification-reference.md` as context only.

## Suggested Packages And Files

Likely implementation targets:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationTaskContext.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/`

Likely docs/spec targets:

- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/service-graph.md`
- `.internal-dev/specifications/decisions.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/end-user/agents.md`
- Package `AGENTS.md` files only where local development guidance changes.

## Architecture Guidance

- Prefer one small resolver/service with immutable result records.
- Keep filesystem path logic centralized and confined with existing workspace services/helpers.
- Keep controllers out of scope unless a visible/API surface is explicitly added.
- Do not add persistence for generated content in this phase.
- Do not let prompt assembly read filesystem paths directly if a workspace service can own the lookup.
- Make no-file behavior explicit and cheap.
- Preserve existing prompt assembly invariants unless tests prove a necessary narrow adjustment.

## Security Guidance

- Resolve paths through normalized absolute paths and real paths where files exist.
- A requested path outside the bound root must be rejected before reading `AGENTS.md`.
- Symlink escapes must not be followed into readable context outside the bound root.
- Missing files are not errors.
- Oversized files should be bounded if implementation reads arbitrary project files; if a size limit is introduced, document it in specs/docs and tests.

## Test Design Guidance

Use temporary directories with real files and nested subtrees. Include cases where:

- root has no `AGENTS.md`;
- only root has `AGENTS.md`;
- only nested directory has `AGENTS.md`;
- both root and nested directories have `AGENTS.md`;
- sibling subtrees have different nested files;
- a nested path attempts to escape via `..`;
- a symlink points outside the root;
- existing `AGENTS.md` contains user-edited content and must remain unchanged.

Prompt tests should assert source labels/order and precedence wording, not only raw content inclusion.

## Closeout Guidance

Implementation closeout must include:

- Changelog entry under `.internal-dev/changelogs/`.
- Affected specification updates.
- Docs updates.
- Knowledge update only if implementation discovers reusable gotchas not already captured.
- Plan archival only after implementation, validation, spec-adherence review, and final closeout pass.

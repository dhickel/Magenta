# Worker Directive: Phase 03 Resolver And Runtime Binding

## Objective

Implement confined `AGENTS.md` discovery/resolution for bound roots and active working paths, including project, Work Area, and effective workspace context semantics.

## Required Source Verification

Before editing, verify <https://agents.md/> for nested file/precedence behavior. Treat closest-wins as official shorthand and Magenta ancestor retention as the documented project divergence.

## Editable Files

- New resolver/result classes under `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/` unless a better package is justified in the report.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationTaskContext.java` only for small additive helpers/value access if needed.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java` only if active path capture requires narrow integration.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java` only if working-directory capture requires narrow integration.
- Resolver tests under `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/`.

## Forbidden Scope

- Do not inject prompt text in this phase unless trivial plumbing is needed for tests; Phase 04 owns prompt injection.
- Do not read outside the bound root.
- Do not follow symlink escapes.
- Do not add schema, UI, or API changes.

## Supporting Docs To Read

- `.internal-dev/plans/agents-md-runtime-support/02-target-design.md`
- `.internal-dev/plans/agents-md-runtime-support/shared/validation-matrix.md`
- `.internal-dev/knowledge/agent-shell-workspace-alias-resolution.md`
- `docs/technical/workspaces-tools-outputs.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`

## Implementation Steps

1. Define resolver inputs: bound root and active path.
2. Resolve active path to a confined real/normalized path under the bound root.
3. Walk from bound root to active path and collect applicable `AGENTS.md` files in root-to-leaf order.
4. Return immutable layer records with source path, relative directory, content, and precedence rank.
5. Add tests for no-file, root-only, nested-only, root-plus-nested, sibling context switching, traversal rejection, and symlink escape.
6. Document in code/test names that Magenta preserves ancestor layers while closest layer has conflict precedence.

## Acceptance Criteria

- Full resolver matrix in `shared/validation-matrix.md` is covered for discovery/security behavior.
- Resolver is independent enough for prompt assembly to call without duplicating path logic.
- Bound root confinement is test-proven.
- Nested-only behavior works within the bound root.

## Negative Checks

- No broad filesystem scan.
- No caching that can serve stale content across different roots unless invalidation is proven.
- No string-prefix-only security checks.

## Validation Commands

```bash
mvn -Dtest='*AgentsMd*Test,*Workspace*Test,*AgentFileToolServiceTest,*AgentShellToolServiceTest' test
git diff --check -- src/main/java src/test/java
```

## Stop Conditions

- Runtime cannot identify a reliable bound root for project/Work Area execution.
- Symlink confinement cannot be proven.
- Resolving Work Area narrowed context conflicts with broader owner-root baseline and needs product clarification.

## Do Not Close Unless

- Resolver tests cover every required discovery/security case.
- Implementation report explains project-bound and Work Area-bound root behavior.
- No prompt injection is left half-wired.

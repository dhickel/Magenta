# Worker Directive: Phase 01 Contracts And Documentation

## Objective

Update Magenta's durable specs and docs to define runtime `AGENTS.md` support, starter workspace guidance, root-bound resolution, prompt/context integration, and Magenta's documented divergence from the external closest-wins shorthand.

## Required Source Verification

Before editing, open <https://agents.md/> and record the current official facts used. Do not rely on `.internal-dev/research/agents-md-specification-research.md` as truth.

## Editable Files

- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/service-graph.md`
- `.internal-dev/specifications/decisions.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/end-user/agents.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md` only if package guidance changes.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/AGENTS.md` only if package guidance changes.

## Forbidden Scope

- Do not edit product Java code.
- Do not add tests in this phase unless a repo policy tool requires doc-only checks.
- Do not add UI/API scope.
- Do not create new specification files unless existing files cannot own the contract.

## Supporting Docs To Read

- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `.internal-dev/research/agents-md-specification-research.md`
- `.internal-dev/knowledge/agents-md-specification-reference.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/end-user/agents.md`

## Implementation Steps

1. Verify official `AGENTS.md` facts from <https://agents.md/>.
2. Add or update spec rows for runtime `AGENTS.md` resolution, starter generation, prompt/context injection, confinement, and no-overwrite behavior.
3. Add a decision documenting Magenta's interpretation: ancestor files remain active, closest file wins only on conflicts, user prompts override all.
4. Update technical docs with workspace starter content semantics and runtime resolution rules.
5. Update end-user agent docs with generated guidance and no-overwrite behavior.
6. Update package `AGENTS.md` files only if implementation guidance for those packages changes.

## Acceptance Criteria

- Specs clearly define loading, layering, workspace generation, prompt/context integration, and root confinement.
- Docs explain generated starter guidance and no-overwrite behavior.
- Magenta divergence from official shorthand is explicit and justified.
- Docs do not present local research claims as official spec text.

## Negative Checks

- No Java code changes.
- No schema/API/UI claims unless explicitly marked out of scope or future.
- No duplicate competing contract across spec files.

## Validation Commands

```bash
rg -n "AGENTS.md|closest|ancestor|overwrite|starter|prompt|context" .internal-dev/specifications docs
git diff --check -- .internal-dev/specifications docs src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md src/main/java/io/mindspice/magenta2/ai/chat/service/AGENTS.md
```

## Stop Conditions

- Official site contradicts the locked Magenta behavior beyond the documented divergence.
- Existing specs contain conflicting active decisions that require user/product resolution.

## Do Not Close Unless

- Official spec verification is recorded in the implementation report.
- Specs and docs both include Magenta's divergence.
- Validator can see exactly where each acceptance criterion is documented.

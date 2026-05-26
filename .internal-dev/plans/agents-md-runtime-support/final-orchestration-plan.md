# Final Orchestration Plan

## Objective

Deliver Magenta runtime `AGENTS.md` support and generated agent workspace guidance through delegated implementation, validation, final spec-adherence review, and `.internal-dev` closeout.

## Main-Thread Setup

1. Create a dedicated branch before implementation begins.
2. Dispatch workers with exactly one directive from `worker-directives/`.
3. Require every worker and validator to verify <https://agents.md/> before spec-sensitive work.
4. Keep this plan separate from any Agent Skills plan.
5. Commit each completed phase after its validator passes, per repo policy.

## Execution Gates

| phase | directive | dependency | validator |
| --- | --- | --- | --- |
| 01 | `worker-directives/phase-01-contract-docs.md` | branch only | docs/spec validator |
| 02 | `worker-directives/phase-02-workspace-starter-generation.md` | Phase 01 pass | workspace/service validator |
| 03 | `worker-directives/phase-03-resolver-runtime-binding.md` | Phase 01 pass | resolver/security validator |
| 04 | `worker-directives/phase-04-prompt-integration-validation-closeout.md` | Phase 02 and 03 pass | prompt/integration validator plus final spec-adherence validator |

Phase 02 and Phase 03 may run in parallel after Phase 01 if file ownership is kept separate. Phase 04 is serial.

## Validation Flow

- Each mutating phase is validated before dependent work proceeds.
- If validation fails, the same worker resumes with the remediation handoff and the same validator rechecks the failed criteria and affected regressions.
- Use a fresh validator if criteria change, remediation crosses domains, more than two failed cycles occur, or the validator missed an obvious issue.
- If criteria are ambiguous or flawed, return to planning before more coding.

## Required Final Gates

Before closeout:

- Full specification-focused test suite passes, including no-file, root-only, nested-only, root-plus-nested, closest precedence, ancestor retention, subtree switching, stale nested unload/de-emphasis, traversal rejection, symlink escape, no-overwrite, and runtime injection cases.
- `mvn test` passes.
- Bounded startup smoke passes:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

- Focused browser validation is either not applicable because no visible UI changed, or completed by a separate Playwright agent with screenshots and visual critique.
- Final `gpt-5.5` xhigh spec-adherence validator checks official site behavior and allows only the documented Magenta layering divergence.
- Specs, docs, package guides, changelog, and knowledge updates are reconciled.

## Closeout Gates

- Add `.internal-dev/changelogs/<date>-agents-md-runtime-support.md`.
- Update knowledge only for reusable implementation gotchas discovered during execution.
- Create/mirror/archive bugs if any out-of-scope defects are found.
- Move finalized plan artifacts to `.internal-dev/plans/.archive/agents-md-runtime-support/` only after implementation, validation, and final reporting are complete.
- Commit final closeout on the dedicated branch.
- Main thread reports final behavior, validation evidence, and any blocked/deferred items to the user.

## Stop Rules

- Stop for user/planning clarification if official spec behavior conflicts with the locked Magenta interpretation beyond the documented divergence.
- Stop if schema, UI, configurable template storage, or broad prompt refactor becomes necessary.
- Stop if runtime bound-root confinement cannot be proven.
- Stop if required startup or execution validation is blocked by infrastructure; report the blocker rather than substituting unit-only validation.

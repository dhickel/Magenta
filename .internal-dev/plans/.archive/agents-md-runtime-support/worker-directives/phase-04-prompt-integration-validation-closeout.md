# Worker Directive: Phase 04 Prompt Integration, Validation, And Closeout

## Objective

Inject resolved `AGENTS.md` layers into runtime prompt/context behavior, prove layered ancestor semantics with closest conflict precedence, and complete implementation closeout artifacts.

## Required Source Verification

Before editing or validating, verify <https://agents.md/> and record the official facts used. The final validator must repeat this independently.

## Editable Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java`
- Narrow collaborating service/wiring classes needed to supply resolved `AGENTS.md` context.
- Prompt/context tests under `src/test/java/io/mindspice/magenta2/ai/chat/service/` or runtime tests.
- `.internal-dev/changelogs/<date>-agents-md-runtime-support.md`
- `.internal-dev/knowledge/<domain>.md` only if new reusable gotchas are discovered.
- Plan files only for status/closeout notes if repo workflow requires them.

## Forbidden Scope

- Do not change PLAN/TASK/EXECUTE_TASK prompt replacement semantics except for narrow, tested `AGENTS.md` append/injection behavior.
- Do not expose a new UI/API surface.
- Do not mark validation complete without final spec-adherence review.
- Do not archive this plan until all validators pass and final closeout is done.

## Supporting Docs To Read

- `.internal-dev/plans/agents-md-runtime-support/00-specification-lock.md`
- `.internal-dev/plans/agents-md-runtime-support/shared/validation-matrix.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/AGENTS.md`
- `.internal-dev/knowledge/worktype-profile-prompt-behavior.md`
- `.internal-dev/specifications/services.md`

## Implementation Steps

1. Inject resolver output into model-backed runtime contexts that have a bound root.
2. Format context with source labels, root-to-leaf order, and explicit precedence text.
3. Ensure no-file behavior omits the block cleanly.
4. Add prompt/context tests for ancestor retention, closest precedence wording, subtree switching, and no-longer-applicable nested context.
5. Run all relevant focused tests, then full test/startup gates after prior phases pass.
6. Complete changelog and any needed knowledge/spec/doc reconciliation.

## Acceptance Criteria

- Runtime prompt/context includes applicable ordered layers when bound root and active path exist.
- Explicit user prompt precedence is preserved.
- Ancestor layers remain present for non-conflicting guidance.
- Closest layer conflict precedence is clear.
- Moving between subtrees does not keep stale nested context active.
- No unbound ordinary chat path causes arbitrary filesystem reads.

## Negative Checks

- No prompt block appears for missing files or missing bound root.
- No stale nested layer survives a context switch.
- No undocumented divergence from the official spec.

## Validation Commands

```bash
mvn -Dtest='*PromptContext*Test,*AgentsMd*Test,*Workspace*Test,*Orchestration*Test' test
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
git diff --check
```

If a visible UI changed unexpectedly, stop and request a Playwright checklist before claiming completion.

## Final Spec-Adherence Review

After unit validators pass, dispatch a `validation_redteam_agent` using `gpt-5.5` with xhigh reasoning. It must:

- Open <https://agents.md/> directly.
- Compare implementation/spec/docs/tests to the official site.
- Allow only Magenta's documented divergence: ancestor context is retained while closest file has conflict precedence.
- Fail if tests cover only happy paths or if local research is treated as source of truth.

## Stop Conditions

- Prompt architecture needs broad refactor.
- Official spec check reveals a conflict not documented in Phase 01.
- Startup cannot run due to unresolved wiring errors.

## Do Not Close Unless

- Focused and full tests have passed or blockers are explicitly recorded.
- Startup smoke passed or blocker is explicitly user-approved.
- Final spec-adherence validator passed.
- Changelog exists and records spec impact.

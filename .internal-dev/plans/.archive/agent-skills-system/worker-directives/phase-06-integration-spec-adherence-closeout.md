# Phase 06 Worker Directive: Integration, Spec-Adherence, And Closeout

## Objective

Complete cross-phase integration validation, final official-spec adherence review, startup/full test proof, browser proof reconciliation, `.internal-dev` closeout, docs/changelog updates, and archive readiness for the Agent Skills system.

## Agent Assignment

- Worker: `implementation_worker_agent`, `gpt-5.3`, high reasoning for closeout/remediation coordination.
- Integration validator: `validation_redteam_agent`, `gpt-5.5`, high reasoning.
- Final spec-adherence validator: `validation_redteam_agent`, `gpt-5.5`, xhigh reasoning, non-mutating.

## Required Reading

- All phase worker reports and validator results.
- All files in this plan suite.
- Official Agent Skills pages listed in `00-specification-lock.md`.
- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `docs/AGENTS.md`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` if browser/chat activation validation touches chat flows.

## Editable Targets

- `.internal-dev/changelogs/<date>-agent-skills-system.md`
- `.internal-dev/knowledge/agent-skills-specification-reference.md`
- Additional `.internal-dev/knowledge/<domain>.md` for reusable UI/spec-validation lessons.
- `.internal-dev/specifications/*` and `docs/*` only for closeout corrections discovered during validation.
- This plan suite for status notes, if repo policy expects it before archiving.
- Bug reports under `.internal-dev/bugs/` only for newly discovered out-of-scope defects, with GitHub mirroring if required by repo policy.

## Forbidden Scope

- Do not implement new feature behavior in closeout unless it is a narrow remediation from failed validation.
- Do not archive active plan artifacts before implementation and validation are complete.
- Do not mark spec adherence passed if official pages were not opened by the final validator.
- Do not treat unit-only validation as sufficient for UI/runtime work.

## Implementation Steps

1. Gather phase validation evidence:
   - focused tests;
   - full `mvn test`;
   - bounded startup;
   - Playwright screenshots/report;
   - official-spec review notes.
2. Run final validation commands from `shared/validation-matrix.md`.
3. Ensure all specs/docs/knowledge/package guidance agree with implemented behavior.
4. Create or update changelog with:
   - implementation summary;
   - files/domains changed;
   - behavioral impact;
   - specification impact;
   - validation evidence;
   - risks/follow-ups.
5. Create/update knowledge notes for reusable official-spec lessons, parser/validation choices, and UI reuse decisions.
6. Create bugs for out-of-scope defects and mirror to GitHub if this repo has a GitHub remote and the defect is active.
7. Run final `gpt-5.5` xhigh spec-adherence validation:
   - compare code/tests/docs behavior to official spec;
   - list intentional Magenta divergences;
   - fail undocumented divergences.
8. If all validation passes, mark plan suite archive-ready. Actual archive move can happen at final main-thread closeout/commit gate.

## Acceptance Criteria

- Full automated tests pass or blockers are explicitly user-approved and documented.
- Bounded startup passes or blocking local dependency is explicit and not waved through.
- Playwright proof and visual critique have been reconciled by validator.
- Final spec-adherence validator passes against official specification pages.
- Specs/docs/knowledge/changelog/package guidance are consistent.
- Deferred project-local and layered assignment behavior is documented but not implemented.

## Negative Checks

- No undocumented divergence from official Agent Skills required format.
- No active docs claim future scopes are available.
- No malformed-skill crash path remains.
- No UI/browser validation gap is hidden.
- No unmirrored active local bug remains if GitHub issue mirroring is required.

## Validation Commands

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
rg -n "Agent Skills|agent skills|skills/|SKILL.md|\\.agents/skills|allowed-tools|project-local|deferred|activate_skill" src/main/java src/test/java docs .internal-dev/specifications .internal-dev/knowledge
git status --short
```

## Final Spec-Adherence Review Checklist

The `gpt-5.5` xhigh validator must verify against official pages:

- Directory shape and required `SKILL.md`.
- Frontmatter fields, required status, and constraints.
- `name` field constraints and parent-directory match behavior.
- `description` requirements.
- Optional `license`, `compatibility`, `metadata`, and experimental `allowed-tools`.
- Optional `scripts/`, `references/`, `assets/`.
- Progressive disclosure: catalog metadata, activated instructions, resources on demand.
- File reference conventions and resource listing behavior.
- Validation behavior for malformed skills and documented Magenta leniency/divergence.
- Client implementation guidance around filtering, no-skill omission, activation, structured wrapping, resource listing, permission boundaries, and deduplication.

## Stop Conditions

- Stop if final spec-adherence review finds undocumented divergence.
- Stop if full tests/startup/browser validation fail and remediation scope is not narrow.
- Stop if docs/specs and code disagree on MVP vs deferred scope.

## Do Not Close Unless

- Integration validator passes.
- Final `gpt-5.5` xhigh spec-adherence validator passes.
- Changelog and knowledge closeout are complete.
- Main thread has enough evidence to commit and report outcome.

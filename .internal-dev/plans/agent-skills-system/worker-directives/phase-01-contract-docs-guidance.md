# Phase 01 Worker Directive: Contracts, Docs, And Governance Baseline

## Objective

Update Magenta's intended contracts and developer guidance for first-class Agent Skills before product code begins, distinguishing MVP root-repository/agent-assignment behavior from deferred project-local and layered assignment behavior.

## Agent Assignment

- Worker: `implementation_worker_agent`, `gpt-5.3`, high reasoning.
- Validator: `validation_redteam_agent`, `gpt-5.5`, high reasoning.

## Required Reading

- `AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `.internal-dev/research/agent-skills-specification-research.md` as context only
- Official pages:
  - `https://agentskills.io/specification`
  - `https://agentskills.io/client-implementation/adding-skills-support`
  - `https://agentskills.io/skill-creation/best-practices`
  - `https://agentskills.io/skill-creation/using-scripts`
- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/specifications/deferred-features.md`
- `.internal-dev/specifications/index.md`
- `docs/AGENTS.md`

## Editable Targets

- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/specifications/deferred-features.md`
- `.internal-dev/specifications/index.md` only if a new spec file is created; prefer not to create one.
- `.internal-dev/knowledge/agent-skills-specification-reference.md`
- New `.internal-dev/knowledge/<domain>.md` only if needed for UI reuse or spec validation lessons.
- `docs/end-user/00-index.md`
- New or updated `docs/end-user/agent-skills.md`
- `docs/technical/00-index.md`
- New or updated `docs/technical/agent-skills.md`
- Package `AGENTS.md` files that need future guidance, likely:
  - `src/main/java/io/mindspice/magenta2/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/chat/service/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/chat/tool/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`

## Forbidden Scope

- Do not edit product Java code, tests, schemas, runtime config, CSS, JavaScript, or templates in this phase.
- Do not add implementation promises that exceed this plan's MVP.
- Do not describe project-local `.agents/skills/` loading as active behavior.
- Do not treat `allowed-tools` as enforced permissions unless explicitly documented as deferred/experimental.

## Implementation Steps

1. Re-open the official Agent Skills pages and note exact requirements checked in the worker report.
2. Update architecture/services/API/web/SimplyPages specs with compact entries for:
   - Magenta root `skills/` repository;
   - parser/loader/catalog/activation services;
   - DB-backed metadata and agent assignment;
   - skill browser/editor/guided creation UI;
   - HTMX-first, reusable browser/editor expectations.
3. Add durable decisions for:
   - MVP root repository under `MagentaRootProperties.path()/skills`;
   - dedicated skill domain/service surface;
   - dedicated activation tool unless Phase 03 proves otherwise;
   - deferred project-local and layered assignment support.
4. Add accepted deferred feature entries for:
   - project-local `.agents/skills/`;
   - user-home/client-native skill scopes;
   - project/job/task/workflow/chat/session assignment layers;
   - script execution/trust/registry/package flows if referenced.
5. Add end-user docs for skill creation/editing/assignment at a scaffold level. It may describe planned behavior but must clearly say it is the intended implementation contract until code lands.
6. Add technical docs for the intended service/API/runtime contract at a scaffold level.
7. Update package `AGENTS.md` guidance so future workers know where skill code belongs and which boundaries to respect.
8. Update knowledge with reusable spec-validation lessons and any UI reuse guidance discovered while reading official docs.

## Acceptance Criteria

- Specs clearly separate MVP active scope from deferred future scope.
- Docs explain the root `skills/` repository and `SKILL.md` basics without over-claiming implemented behavior if this phase runs before code.
- Package guidance names likely skill ownership boundaries.
- Decisions and deferred features record tradeoffs and review timing.
- Official spec pages are referenced as source-of-truth for spec-sensitive validation.

## Negative Checks

- `rg -n "\\.agents/skills|project-local|user-home|registry|marketplace|allowed-tools" .internal-dev/specifications docs src/main/java/io/mindspice/magenta2/*/AGENTS.md src/main/java/io/mindspice/magenta2/AGENTS.md` should show deferred/experimental wording where applicable.
- Specs must not say the research report is authoritative.
- Docs must not imply script execution is available from the UI.

## Validation Commands

```bash
rg -n "Agent Skills|agent skills|skills/|SKILL.md|\\.agents/skills|allowed-tools|project-local|deferred" .internal-dev/specifications .internal-dev/knowledge docs src/main/java/io/mindspice/magenta2
git diff -- .internal-dev/specifications .internal-dev/knowledge docs src/main/java/io/mindspice/magenta2
```

No `mvn test` is required if this phase changes only docs/specs/guidance, but the validator may run targeted checks if package guidance formatting is uncertain.

## Stop Conditions

- Stop if the official specification contradicts the root repository MVP in a way that cannot be documented as client-specific behavior.
- Stop if a new specification file seems necessary; ask the main thread before expanding the flat spec store.
- Stop if package guidance would require editing product code to remain coherent.

## Do Not Close Unless

- Official pages were consulted and named in the report.
- Every changed intended contract says MVP vs deferred where relevant.
- Docs and specs agree on root `skills/` and agent-only assignment MVP.
- Validator has reviewed for over-claiming and spec drift.

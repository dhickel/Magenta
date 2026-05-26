# Work Units

## Phase 01 - Contracts And Documentation

Lock the intended Magenta contract in specs/docs before code changes.

Editable targets:

- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/service-graph.md`
- `.internal-dev/specifications/decisions.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/end-user/agents.md`
- Relevant package `AGENTS.md` only if local package guidance must change.

## Phase 02 - Workspace Starter Generation

Generate starter workspace `AGENTS.md` on first agent workspace creation without overwriting existing files.

Editable targets:

- `ai.orchestration.workspaces` workspace services/helpers.
- `ai.orchestration.agents.AgentProfileService` only as needed to call workspace generation at durable storage creation.
- Focused workspace/agent service tests.

## Phase 03 - Resolver And Runtime Binding

Add confined `AGENTS.md` discovery/resolution and root-binding behavior for project, Work Area, and effective workspace contexts.

Editable targets:

- `ai.orchestration.workspaces` or a closely justified runtime package.
- `OrchestrationTaskContext` only if a small additive helper/value is required.
- Resolver/security tests.

## Phase 04 - Prompt/Context Injection And Final Validation

Inject resolved layered guidance into runtime prompts/context and complete integration validation.

Editable targets:

- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java`
- Narrow collaborating service injection/wiring.
- Prompt/context tests.
- Final changelog/knowledge/plan-closeout artifacts after validation.

## Dependencies

- Phase 01 should complete before mutating product code.
- Phase 02 and Phase 03 may proceed after Phase 01.
- Phase 04 depends on Phase 03 and should reconcile Phase 02 starter docs/tests.
- Final spec-adherence validation happens after all unit validators pass.

## Session Policy

Use one consistent implementation worker session per phase and one consistent validator session per phase. If validation fails, resume the same worker with the validator's remediation handoff, then resume the same validator.

# Summary Model for Title Jobs

## Context

Magenta currently selects the active chat model for background conversation title jobs. User configuration should use a model-level `summeryModel` field for internal summary/title work, not a summarization agent.

## Goal

Add a `summeryModel` configuration field and route internal summary/title work through it.

## In Scope

- Add a top-level `summeryModel` field to user AI config.
- Validate configured summary model keys against configured models.
- Use the resolved summery model for context compaction and `CONVERSATION_TITLE` jobs.
- Update example config and focused tests.

## Out of Scope

- Public job APIs.
- Additional summary workflows beyond title generation.
- Public summarization APIs.

## Implementation Steps

1. Update AiConfig shape and loader validation.
2. Inject config into AgentJobService and resolve the title model from `summeryModel`.
3. Update example config and tests.
4. Run focused and full tests.

## Validation

- Loader tests cover `summeryModel`.
- Agent job service tests verify title jobs use the configured summery model.
- Full Maven test suite passes.

## Exit Criteria

- The example config points `summeryModel` at `local-qwen`.
- New title jobs and context compaction call the configured summery model.

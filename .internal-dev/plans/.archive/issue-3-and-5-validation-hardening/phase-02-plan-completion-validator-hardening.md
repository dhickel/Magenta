# Phase 02 Plan Completion Validator Hardening

## Context
Follow-up review of issue #5 confirmed `plan_complete` normally used a separate clean validator model call, but the behavior was implicit and undertested. The validator could fall back to the executor model, prior artifact paths were not auto-read unless repeated, and validation input lacked explicit untrusted-data framing.

## Goal
Make plan completion validation provably independent, cleanly testable, fail-closed, and complete with respect to approved criteria, execution evidence, artifacts, and final output.

## In Scope
- Local `PlanCompletionValidator` boundary.
- Exact validator request tests.
- Fail-closed validator model resolution.
- Artifact carry-forward from prior `plan_report` evidence.
- Prompt/input hardening that frames plan/evidence/artifacts/final message as untrusted data.
- Documentation and knowledge updates.

## Out of Scope
- General task completion validation.
- Broad AI configuration redesign.
- Browser UI redesign for validation state.

## Implementation Steps
- Introduce a validator interface and default chat-model implementation.
- Build a clean validator request from approved plan markdown, evidence, artifact contents, prior feedback, and proposed final message.
- Remove executor-model fallback when no planning validator model resolves.
- Include prior `Artifact:` evidence paths when reading validation artifacts.
- Add focused tests for prompt/request shape, model use, preflight skips, artifact carry-forward, and final completion gating.

## Validation
- `mvn -q -Dtest=PlanServiceTest test`
- `mvn -q -Dtest=PlanServiceTest,PlanSaveToolsTest,ChatServiceTest test`
- `mvn -q test`
- Startup smoke with `timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

## Exit Criteria
- Normal `plan_complete` validation uses a clean validator request that tests can inspect.
- Completion fails closed when validator model resolution is unavailable.
- Earlier reported artifact paths are available to the validator without requiring the executor to repeat them.
- Final completion remains gated by parsed validator results and Magenta's completion contract.

# Phase 01 - Review State

## Context
GitHub issue #5 reports that validator/remediation state can leak into the user review flow. Related issue #4 identifies a concrete mode bug: `PlanStatus.NEEDS_REVIEW` falls through to `PlanMode.PLAN`, which reopens planning controls.

## Goal
Keep `NEEDS_REVIEW` in an execution-review lifecycle instead of draft planning. The browser should continue showing plan evidence and validation feedback, but it must not show `Planning active` or `Cancel planning` as the primary state.

## In Scope
- Update `PlanService.mode(...)` so `NEEDS_REVIEW` is not `PLAN`.
- Update `PlanService.runtimeInstructions(...)` so review states do not receive PLAN-mode prompt instructions.
- Tighten `chat-client.js` planning panel behavior and copy for review states.
- Add focused regression tests for the mode transition.

## Out of Scope
- Adding a full review workflow with new revise/replan actions.
- Broad UI redesign of the chat plan panel.
- Fixing malformed model tool-call JSON from issue #3.

## Implementation Steps
1. In `PlanService.mode(...)`, treat `NEEDS_REVIEW` like a non-planning state. Use `PlanMode.NORMAL` unless a stronger execution-review enum is already present after inspection.
2. In `PlanService.runtimeInstructions(...)`, exclude `NEEDS_REVIEW` from the planning-instructions branch.
3. In `src/main/resources/static/js/chat-client.js`, keep `renderPlanningPanel(...)` inactive for `NEEDS_REVIEW`; ensure status copy says the plan needs review and points to evidence/feedback.
4. Add or update `PlanServiceTest` coverage proving `SESSION_PLAN + NEEDS_REVIEW` does not resolve as `PLAN`.

## Validation
- Run focused plan tests: `./mvnw -q -Dtest=PlanServiceTest test`.
- Include browser validation after the UI phase if an application startup is available.

## Exit Criteria
- `PlanService.mode(conversationId)` returns a non-`PLAN` mode for `NEEDS_REVIEW`.
- The client does not render generic planning controls for review states.
- Evidence and validation feedback remain visible in the plan status area.

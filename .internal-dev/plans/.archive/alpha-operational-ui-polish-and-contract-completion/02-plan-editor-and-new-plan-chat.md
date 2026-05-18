# Phase 02 - Plan Editor And New Plan Chat

## Context

`PlanDefinition` already stores the full finalized plan shape: title, summary, goal, notes, deliverables, structured inputs, structured outputs, assumptions, steps, validation criteria, execution evidence, validation feedback, prompt profile, planning model, execution model, settings override JSON, draft question state, and final message. The current UI exposes much of this but still treats some sections as partial row editors, leaves execution evidence and validation feedback mostly read-only/invisible, and still uses "Worktype" wording instead of the more precise manager/profile language used elsewhere.

The user also wants a way to launch planning chat from New Plan. The existing "Continue in Chat" creates a prompt fragment, but the future sprint for chat loops with mid-chat planning/task planning is explicitly deferred in `.internal-dev/notes/alpha-deferred-targets.md`.

## Goal

Make `/plans` a complete structured editor for every persisted plan field and add a New Plan Chat launch that uses existing plan chat flow without pretending the deferred mid-chat loop hardening is complete.

## In Scope

- Full editor parity with `PlanDefinition`.
- Typed row editors for list and structured fields.
- UI add/delete/reorder/update for each list-like field.
- Model/profile/manager override controls.
- A New Plan Chat entrypoint that starts the existing planning conversation flow.
- Rename UI "Worktype" to "Manager Type" or "Prompt Profile" consistently.

## Out of Scope

- Future sprint: exhaustive mid-chat planning loop and task-planning loop hardening.
- New arbitrary JSON editors for structured fields when a schema-shaped editor is possible.
- Changing `/chat` route behavior.

## Implementation Steps

1. Inventory `PlanDefinition` and create an editor coverage matrix. Required fields:
   - scalar: `title`, `summary`, `goal`, `notes`, `planningTask`, `finalMessage`;
   - lists: `deliverables`, `assumptions`, `validationCriteria`, `executionEvidence`, `validationFeedback`, `pendingQuestions`;
   - typed lists: `inputs`, `outputs`, `steps`;
   - config/profile: `promptProfile`, `planningModel`, `executionModel`, `settingsOverrideJson`;
   - metadata: `kind`, `status`, `conversationId`, timestamps.
2. Read SimplyPages editing docs before implementation:
   - `docs/getting-started/03-editing-system-first-implementation.md`
   - `docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
   - `docs/reference/editing-api-reference.md`
   Use component builders and HTMX fragments; avoid raw HTML strings unless the framework cannot express the element.
3. Add explicit row-fragment endpoints for every editable list:
   - `POST /plans/_editor/{planId}/{section}`
   - `PUT /plans/_editor/{planId}/{section}/{index}` for indexed list items;
   - `DELETE /plans/_editor/{planId}/{section}/{index}`;
   - optional reorder endpoints if current order fields need direct movement.
4. For `inputs` and `outputs`, row editors must match `PlanFieldDefinition`: name, `PlanFieldType`, required, array, description, schema, example. `array` must be a checkbox or toggle, not text. `type` must be a select.
5. For `steps`, row editors must match `PlanStep(order, text)`. Persist order deterministically after add/delete.
6. For execution evidence, validation feedback, and final message, decide read-only vs editable:
   - execution evidence and validation feedback can be displayed read-only by default;
   - if editable, mark the editor as operator override and persist through row endpoints;
   - final message should be visible in Advanced and editable only if plan status allows it.
7. Replace "Worktype" label with "Manager Type" if the persisted concept is a management prompt/profile. If code names stay `promptProfile`/`WorkTypeProfile`, add a small naming note in the plan and UI labels so the user does not see conflicting terms.
8. Add model dropdowns using canonical model keys from `chatService.availableModelOptions()` or `AiConfig.models()`. Do not save raw remote names into `planningModel` or `executionModel`.
9. Add "New Plan Chat" next to "New Plan":
   - creates or selects a plan-mode conversation using existing chat/plan APIs;
   - redirects or links to the existing chat flow with plan mode active;
   - preserves deliverables/inputs/outputs fields already present in the draft.
10. Keep the current "Continue in Chat" if useful, but relabel it clearly if it only creates a prompt handoff and does not open a live plan-mode conversation.

## Validation

- Repository/service tests prove every persisted `PlanDefinition` field round-trips after editor mutations.
- Controller tests cover add/update/delete for deliverables, assumptions, steps, validation criteria, inputs, outputs, evidence/feedback visibility, prompt profile, planning model, and execution model.
- Negative tests reject unknown plan field types, unknown model keys, invalid row indexes, and invalid schema JSON when schema validation is introduced.
- Playwright MCP:
  - create a new plan;
  - add input/output rows with type, required, array, schema, and example;
  - add/delete/reorder deliverables, assumptions, steps, and validation criteria;
  - save and reload the plan;
  - verify every edited value persists.
- Browser validation checks New Plan Chat starts the existing plan chat path without breaking `/chat`.

## Exit Criteria

- `/plans` can load and edit every meaningful finalized-plan field.
- List-shaped data is edited as rows, not JSON blobs or newline text.
- New Plan Chat exists and is honest about the deferred deeper chat-loop hardening.


# Phase 03 - Plan Editor And Worktype Profiles

## Context

The plan editor currently exposes raw implementation concepts, direct run controls, and field rows that do not give enough room for descriptions or schemas. The user wants manual creation/editing plus a way to drop into a plan creation chat loop.

## Goal

Redesign the plan/task template UI around structured editing, worktype profiles, and submit-to-agent behavior. Remove direct run controls from the page.

## In Scope

- Plan/task template editor UI.
- Inputs/outputs structured field editor.
- Remove `example` from user-facing input/output editor.
- Replace "Prompt Profile" with worktype profile.
- Add default worktype profile definitions and prompt append behavior.
- Add "Submit to agent" flow.
- Preserve existing chat-based plan interaction flow.
- Add support to open/chat-continue an existing plan draft.

## Out of Scope

- Autonomous job/workflow creation via agent.
- Dashboard-level prompts/tools.
- Removing compatibility `/api/tasks` endpoints.

## Implementation Steps

1. Define canonical user-facing plan concepts.
   - Screen title: "Plans" or "Task Templates" depending route decision. Avoid "Plans & Tasks" if the page has mixed semantics.
   - `SESSION_PLAN` should not be directly user-editable on reusable task template screen unless the screen explicitly supports in-session plans.
   - Hide raw `kind` and `status` unless needed in an advanced metadata panel.

2. Replace input/output row editor.
   - Create a reusable schema field editor:
     - name: text input;
     - type: select from `PlanFieldType`;
     - required: checkbox;
     - array: checkbox;
     - description: textarea, not single-line input;
     - schema: JSON textarea with validation;
     - remove `example` from UI per user request.
   - Keep `PlanFieldDefinition.example` in backend only if compatibility requires it; set it to `null` from the new editor.
   - Render each field as an expandable row:
     - collapsed state: name, type, required, array, short description.
     - expanded state: full description and schema editor.

3. Make list-like structures list editors.
   - Deliverables: ordered list editor.
   - Steps: ordered list editor using `PlanStep(order, text)`, not newline parsing.
   - Validation criteria: ordered list editor.
   - Assumptions: ordered list editor.
   - Notes: rich multiline text area, because backing type is a single string.

4. Interaction delivery model (HTMX first).
   - Use HTMX for all default editor actions:
     - create/save/update/delete;
     - field add/remove;
     - list item reorder operations where feasible;
     - submit-to-agent form posts and result fragments.
   - Use server-rendered fragments for row expand/collapse and validation message rendering when practical.
   - Add JavaScript only where it is the path of least resistance (for example advanced keyboard affordances, drag-and-drop reorder that would otherwise be brittle, or schema editor UX polish). Keep JS scoped to those behaviors.

5. Rename prompt profile to worktype profile.
   - Introduce `WorkTypeProfile` enum or reuse existing storage with explicit constants:
     - `CODING_CENTRIC`
     - `DATA_CENTRIC`
     - `RESEARCH_CENTRIC`
   - Public UI label: "Worktype".
   - Public DTO field: `workTypeProfile`.
   - Accept legacy `promptProfile` request field temporarily and map it to `workTypeProfile`.
   - Database can continue using `prompt_profile` column for this phase if a column rename adds too much migration risk.

6. Add profile prompt text.
   - Add a small service such as `WorkTypeProfileService`.
   - It should return append-only system text for the selected profile.
   - Coding-centric system message:

```text
Worktype: coding-centric.
Prioritize repository evidence, small coherent code changes, tests, startup smoke checks, and clear implementation closeout. Prefer existing project patterns over new abstractions.
```

   - Data-centric system message:

```text
Worktype: data-centric.
Prioritize data contracts, schema clarity, source provenance, validation, transformation correctness, and reproducible outputs. Call out assumptions about missing or dirty data.
```

   - Research-centric system message:

```text
Worktype: research-centric.
Prioritize source quality, recency where relevant, citations, uncertainty tracking, and separating evidence from inference. Avoid unsupported conclusions.
```

   - Append this profile text after mode-specific runtime instructions where it will not override PLAN/TASK mandatory terminal-state rules.

7. Replace direct run controls with submit-to-agent.
   - Remove plan run panel and `Run` button from the UI.
   - Add "Submit to agent" button or panel:
     - agent select;
     - optional model override;
     - priority;
     - optional job/project context once those contracts exist;
     - generated input form from required inputs.
   - Submit creates a `WorkAssignment` with assignment type `TASK_RUN` or plan execution equivalent.
   - UI response shows assignment id, queue position/status, owner agent, and link to `/agents/{agentId}`.
   - Do not stream raw run logs on the plan page.

8. Preserve and extend chat planning flow.
   - Keep existing plan mode for creating plans by chat.
   - Add "Continue in chat" for existing plan/task drafts.
   - The chat prompt for entering an existing plan draft must include:
     - current plan state;
     - instruction to read/grok the existing plan before asking questions;
     - instruction to continue questioning the user if the plan lacks context;
     - instruction to summarize current state and ask for guidance if the plan appears complete or context is insufficient.
   - This prompt work can be partially implemented here for plans. Job/workflow chat creation is deferred to future features.

9. Server validation.
   - Enforce unique input and output names.
   - Enforce nonblank descriptions for required inputs and outputs.
   - Validate schema JSON when present.
   - Ensure `array=true` is preserved through save/load.

## Validation

- Unit tests:
  - plan save maps `workTypeProfile` to persistence and prompt assembly;
  - legacy `promptProfile` still maps during compatibility period;
  - `example` omitted by UI save does not break existing records;
  - duplicate field names fail with 400.
- Browser validation:
  - create plan/task template;
  - add input with multiline description and JSON schema;
  - add output with array flag;
  - save, reload, and verify structure is preserved;
  - core editing flows issue HTMX requests by default rather than ad hoc page-level fetch logic;
  - submit to agent creates assignment and does not show direct run log;
  - continue-in-chat opens/uses existing draft context.
- Run `mvn test` and bounded startup smoke.

## Exit Criteria

- Plan inputs/outputs match their backing data shape.
- User-facing "example" is removed from input/output editing.
- Worktype profiles are selectable and appended to model prompts.
- Plan page has no direct run affordance.
- Existing chat planning flow remains functional.

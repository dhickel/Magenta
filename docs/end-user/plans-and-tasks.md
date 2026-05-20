# Plans And Tasks

Use `/plans` to create saved plan/task definitions and submit them to agents. In the current UI, plans are the user-facing editor for structured executable task definitions.

## Page Layout

The page has:

- A sidebar with **New Plan**, **New Plan Chat**, a filter box, and plan cards.
- A detail panel for the selected plan with top **Editing Details** and **Planning Chat** tabs. Only the selected tab's window is rendered in the detail area.
- In-editor sections for scalar fields, deliverables, inputs, outputs, steps, validation criteria, assumptions, evidence, feedback, questions, model choices, and submission.

## Create A Plan

1. Open `/plans`.
2. Select **New Plan**.
3. Enter the plan name in the modal.
4. Fill **Summary**, **Goal**, and **Notes** in the **Editing Details** tab.
5. Save the plan.
6. Add structured sections after the draft exists.

Use **New Plan Chat** when you want Magenta to interview you and build a saved task draft in `/plans`. Enter the draft name in the modal; Magenta creates the saved draft and opens the **Planning Chat** tab. This chat is plan-scoped, does not appear in the `/chat` session sidebar, and is separate from anonymous in-chat planning.

## Structured Plan Sections

- **Deliverables**: high-level user-visible or operational outcomes such as code/file edits, reports, state changes, management operations, or transient updates.
- **Inputs**: structured runtime fields the agent must receive when the plan runs.
- **Outputs**: named structured values intended for workflow chaining, downstream plan inputs, or highly specific directed task results.
- **Steps**: ordered execution instructions.
- **Validation Criteria**: checks that prove the task is complete.
- **Assumptions**: explicit defaults or constraints.
- **Evidence**: proof collected while executing or reviewing.
- **Feedback**: validation or review feedback.
- **Questions**: open questions that still need user input.

Inputs and outputs have a name, type, required flag, array flag, description, and optional schema. Use simple names that will be easy to map in jobs and workflows.

## Saved Plan Chat

The embedded saved plan chat asks four deterministic opening questions for new chat drafts before any later model-backed planning behavior can run:

1. Whether the plan has runtime inputs, including field names, types, required flags, array flags, schemas, and examples, or “no inputs”.
2. Goal.
3. High-level deliverables, with structured outputs handled separately.
4. Specific structured outputs for workflow chaining or downstream use, or “no outputs”.

Saved plan chat updates the selected saved draft. The manual editor remains authoritative for direct edits. Open the **Planning Chat** tab on an existing draft to continue with “Any details you want to provide before continuing?” Open it on an approved plan to start from “What do you need to change in this plan?”

When you save manual edits and the plan already has chat history, Magenta appends a concise context message to the saved plan chat describing the edited fields. This keeps the next chat turn aware of changes made outside the transcript without treating the edit notice as an answer to the current prompt.

## Models And Manager Type

The editor exposes:

- **Manager Type** for the work profile.
- **Planning Model** for plan drafting.
- **Execution Model** for running the plan.

Model fields are populated from configured models. If the current model is no longer available, the UI may still show it with a warning or as a current value.

## Finalize A Plan

Finalize only after the plan has enough detail for an agent to execute without guessing:

- Clear goal.
- Concrete deliverables.
- Required inputs and expected outputs.
- Ordered steps.
- Validation criteria.
- Important assumptions.

Finalize changes the plan status from draft to approved. It does not execute the plan.

## Submit To Agent

1. Open an existing plan.
2. Select **Submit to Agent**.
3. Choose an agent.
4. Optionally choose a model override and workspace.
5. Set priority.
6. Fill runtime inputs generated from the plan's input definitions.
7. Submit.

The workspace field uses a searchable selector where available. The agent field in the plan submit panel may still be a plain dropdown. Do not copy opaque workspace IDs if the selector is visible; type a workspace label, owner, path, or ID and choose the matching option.

After submit, Magenta creates an assignment. Open the linked agent page and use the queue, diagnostics, transcript, history, workspace, and outputs tabs to follow execution.

## Runs And History

Plan submissions create agent assignments and may create task run records. The plan page focuses on definition authoring and submission. For runtime state, use:

- The agent **Queue** tab for active assignments.
- The agent **History** tab for terminal assignments, diagnostics, and transcripts.
- `/outputs` for artifacts.
- `/jobs` when the plan runs as part of a job.

## Common Validation Failures

- **Title is required**: every plan must have a title.
- **Cannot determine field index**: a structured field row did not submit its expected form data. Refresh and retry the row edit.
- **Field index out of range**: another tab or user changed the field list. Refresh before editing.
- **No active agents available**: create or enable an agent.
- **Plan not found**: the plan was deleted or the page is stale.
- **Required runtime input omitted**: fill every required input before submitting, especially when the plan is used inside a job.

## Alpha Limits

Plans are saved definitions, not direct execution buttons. Submit saved work to an agent. Anonymous `/chat` plans are separate ad hoc sessions and are not saved into `/plans`. Concurrent edits can make list indexes stale, so refresh before editing the same plan in multiple tabs.

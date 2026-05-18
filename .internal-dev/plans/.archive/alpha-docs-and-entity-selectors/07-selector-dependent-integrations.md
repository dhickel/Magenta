# Selector Dependent Integrations

## Context

Some manual ID fields depend on another field. These should be handled after low-risk selector integrations prove the shared component.

Highest-priority dependent fields:

- Agent submit form: `assignmentType` currently controls how `targetId` is interpreted as task/plan, workflow, or job.
- Job item add/edit form: `itemType` controls whether `planId` or `workflowId` is relevant.
- Plan input guidance currently listens to `planId` changes through `/jobs/_editor/_plan-inputs`; that behavior must be preserved.

## Goal

Replace dependent manual target fields with selectors that refresh based on the controlling type and validate the selected target exists.

## In Scope

- Agent submit `assignmentType -> targetId`.
- Job item `itemType -> planId/workflowId`.
- Preserve existing plan input guidance.
- Optional output `runId` selector if backend already supports run options without broad new query work.

## Out of Scope

- Changing assignment type semantics.
- Changing job item data model.
- Replacing workflow node route field selectors, unless a bug is found and approved.

## Implementation Steps

1. Agent submit target selector.
   - Current path: `agentSubmitForm` and `submitToAgent`.
   - Replace generic `targetId` input with a target selector fragment.
   - `assignmentType=TASK_RUN` should search task/plan definitions using the same id field name `targetId`.
   - `assignmentType=WORKFLOW_RUN` should search workflows.
   - `assignmentType=JOB_RUN` should search jobs.
   - Changing assignment type should refresh only the target selector fragment through HTMX.
   - Preserve `submitToAgent` request parameter names and server-side validation.

2. Job item add form.
   - Current path: `jobEditorFragment`, `jobItemFromParams`, and `/jobs/_editor/_plan-inputs`.
   - Replace manual `planId` and `workflowId` inputs with a selector fragment keyed by `itemType`.
   - For `PLAN`, render `planId` selector and keep `workflowId` blank.
   - For `WORKFLOW`, render `workflowId` selector and keep `planId` blank.
   - When a plan is selected, trigger the existing plan-input guidance refresh.
   - Preserve `bindingsJson`, `modelOverride`, and `priority` fields.

3. Job item edit rows.
   - If existing job item edit rows expose manual plan/workflow IDs, replace them too.
   - If they only display selected IDs, add an edit affordance only if it exists already.

4. Server-side validation.
   - Do not rely only on UI validation.
   - `submitToAgent` must reject mismatched/missing target IDs with clear messages.
   - `jobItemFromParams` must continue rejecting missing plans and add equivalent workflow validation.

5. Documentation.
   - End-user job and agent docs should describe choosing targets by search.
   - Technical docs should document the dependent selector behavior.

## Validation

- Controller tests:
  - assignment type target selector returns task/workflow/job options as appropriate.
  - submit rejects unknown target ID for each assignment type.
  - job item PLAN rejects unknown plan ID.
  - job item WORKFLOW rejects unknown workflow ID.
  - selecting a plan still renders required input guidance.
- Focused Playwright subagent:
  - `/agents` detail submit form: switch assignment types, search/select target, submit, see assignment created.
  - `/jobs` editor: add plan item via selector and confirm input guidance; add workflow item via selector.
- Startup smoke after integration.

## Exit Criteria

- The worst ID-entry flow, agent submit target ID, is replaced.
- Job item plan/workflow references are selected and validated through the shared component.


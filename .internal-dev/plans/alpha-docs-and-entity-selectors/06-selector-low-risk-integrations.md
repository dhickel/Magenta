# Selector Low-Risk Integrations

## Context

After the backend contract and reusable component exist, replace straightforward fields first. This lowers risk before dependent selectors are changed.

## Goal

Replace isolated manual ID/model fields with reusable selectors while preserving current form behavior.

## In Scope

Targets identified in `OrchestrationController`:

- Plan submit form `workspaceId`.
- Workflow submit form `workspaceId`.
- Job editor `projectId`.
- Project editor `ownerAgentId` if it still uses static select and can benefit from shared selector.
- Job editor `ownerAgentId` if replacing static select is low risk.
- Settings `defaultAgentId`.
- Schedule form `jobId`, `modelOverride`, `workspaceId`.
- Reaction form `modelOverride`, `workspaceId`.
- Existing model selects may be replaced only if the shared component preserves current missing-model behavior.

## Out of Scope

- Agent submit target selector.
- Job item plan/workflow dependent selector.
- Output run-id selector unless it is trivial after backend support.

## Implementation Steps

1. Replace plan submit `workspaceId`.
   - Current path: `OrchestrationController.submitToAgentPanel`.
   - Use workspace selector with optional blank.
   - Preserve submit payload parameter name `workspaceId`.

2. Replace workflow submit `workspaceId`.
   - Current path: `workflowSubmitToAgentPanel`.
   - Same contract as plan submit.

3. Replace job editor `projectId`.
   - Current path: `jobEditorFragment`.
   - Use optional project selector.
   - Preserve saved job value and missing-current warning.

4. Replace settings `defaultAgentId`.
   - Current path: settings form rendering near runtime settings.
   - Use agent selector.
   - Preserve blank allowed behavior if settings currently allow it.

5. Replace schedule form selectors.
   - Current path: `scheduleForm`.
   - `jobId` should be a job selector.
   - `modelOverride` should use model selector with blank default.
   - `workspaceId` should use workspace selector.
   - Preserve assignment type and input JSON fields.

6. Replace reaction form model/workspace selectors.
   - Current path: `reactionForm`.
   - Preserve filter JSON and input JSON.

7. Update docs.
   - End-user docs should no longer tell users to manually copy opaque IDs for these fields.
   - Technical frontend docs should note selector component reuse.

## Validation

- Focused controller/render tests for each changed form.
- Existing submit/update tests still pass.
- Manual HTMX checks can render each form without a server error.
- Focused Playwright subagent validates:
  - `/plans` submit form workspace search/manual validation.
  - `/workflows` submit form workspace search/manual validation.
  - `/jobs` project selector and schedule form selectors.
  - `/settings` default agent selector.

## Exit Criteria

- Low-risk fields no longer require blind ID entry.
- Manual entries show validation state before submission.


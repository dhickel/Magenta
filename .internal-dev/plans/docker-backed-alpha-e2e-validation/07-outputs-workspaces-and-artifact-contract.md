# Phase 07: Outputs, Workspaces, And Artifact Contract

## Context

The alpha contract distinguishes deliverables from outputs, requires task/workflow outputs to be materialized into output directories, and expects temp workspaces to be cleaned while agent/job/project spaces persist appropriately.

## Goal

Validate through Playwright that outputs and workspace state are visible, attributable, downloadable/readable where appropriate, and consistent with Docker-backed execution.

## In Scope

- `/outputs` page.
- Agent outputs tab.
- Workspace tab.
- Job/project output filtering.
- Output type validation.
- Output files created under expected directories.
- Persistent agent home/workspace behavior.
- Temp task workspace cleanup after completion.
- Job/project workspace persistence.

## Out of Scope

- Full file browser/editor implementation if not part of alpha.
- External cloud storage.

## Implementation Steps

1. Use output-producing runs from phases `03`, `04`, and `05`.
2. Open `/outputs` in Playwright and filter by:
   - agent
   - job
   - project
   - workflow/run if exposed
   - type
3. Open agent detail outputs tab and verify it shows only that agent's outputs.
4. Open workspace tab and verify real workspace/home/output metadata, not placeholder API text.
5. From browser-origin actions, read/download/view at least one text output and one JSON output if supported.
6. Verify a message output is copied/materialized when the contract says user-facing message outputs must be preserved.
7. Verify no-output tasks do not produce fake output rows.
8. Verify temp workspaces from completed tasks are cleaned or marked as non-persistent, while agent home/job/project spaces remain.
9. Verify archive/disable preserves or archives workspace data according to the lifecycle flow from phase `02`.

## Validation

Required Playwright checks:
- Output list is populated from real runs.
- Filters change results without showing unrelated agent/job/project artifacts.
- Output details expose file path/type/status/owner metadata.
- Workspace UI shows roots, links, leases, or clear empty/error states.
- Agent outputs tab does not show global unrelated outputs.
- Completed task temp workspace is not treated as persistent user data.

## Exit Criteria

- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/07-outputs-workspaces-artifact-contract-evidence.md` exists.
- Any attribution, filtering, workspace placeholder, cleanup, or output validation defect is logged.

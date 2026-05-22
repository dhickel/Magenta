# Phase 04: Workflow Execution Correctness

## Context

Workflow execution has two concrete defects: assignment-backed waiting workflows can be marked failed, and async task nodes lose thread-local orchestration context. Workflow outputs also need durable placement separate from temp.

## Goal

Fix workflow status bridging, propagate orchestration context through async workflow work, and publish workflow outputs under effective durable workspace outputs.

## In Scope

- `OrchestrationRunnerService` workflow assignment status mapping.
- `WorkflowRunner` async context propagation.
- Workflow temp/output separation.
- Workflow waiting/resume metadata preservation.
- Active/waiting workflow temp retention.
- Related workflow/runtime tests.

## Out of Scope

- Project owner-agent migration.
- Job persistent workspace policy.
- Broad workflow engine redesign.
- Handoff file design beyond current metadata/output artifacts.

## Implementation Steps

1. Map workflow `WAITING` to assignment `WAITING`; do not mark non-completed waiting workflows as failed.
2. Capture current orchestration context before async task execution, set it in worker threads, and clear/restore in `finally`.
3. Use the effective workspace resolver for workflow durable output paths.
4. Keep workflow temp under run temp paths and retain temp while status is active or waiting.
5. Ensure resume uses original run/workspace context.
6. Add regression tests for waiting assignment resume, context propagation, output placement, and retention.
7. Append phase notes and validation results.

## Validation

- `WorkflowRunnerTest`.
- `OrchestrationRuntimeTest`.
- Output artifact attribution tests.
- Spring context smoke.

## Exit Criteria

- Waiting workflows remain resumable through assignments.
- Async workflow task nodes retain correct context.
- Workflow outputs are durable artifacts, not temp-only files.
- Waiting/active temp retention is covered.
- Phase validation passes and the phase is committed.

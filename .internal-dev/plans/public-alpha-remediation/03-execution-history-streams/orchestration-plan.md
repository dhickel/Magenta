# Execution, History, and Streams Orchestration Plan

## 1. Objective

Make public plan/task/workflow/job execution observable and recoverable through assignment submission. Preserve chat transcripts during execution, emit stable SSE event names, and reject invalid scheduled/reaction assignment templates at save time.

## 2. Inputs And Assumptions

The user clarified that public "run" actions mean submit the saved definition to an agent as high priority. Direct execution may remain only as internal/test implementation detail if not reachable from public routes.

## 3. Scope

In scope: public direct-run route removal/gating, chat `Execute now` replacement, priority consistency, transcript preservation, plan-run SSE mapping, job Start Run submission, schedule/reaction template validation, tests.

Out of scope: redesigning assignment queues or adding new execution product concepts.

## 4. Current-State Analysis

Reviews found direct chat/API/UI execution routes, chat execution clearing persisted memory, plan stream events named by class instead of semantic event, job Start Run creating queued job runs without assignments, and schedule/reaction invalid assignment templates failing later at runtime.

## 5. Target Design

- Public run controls create high-priority assignments tied to saved definitions.
- Direct execution endpoints are removed from public UI or explicitly internal-only.
- Chat transcript remains durable; execution context is isolated without deleting history.
- Plan SSE emits stable semantic event names using existing support.
- Jobs, schedules, and reactions validate assignment templates before persistence.

## 6. Implementation Plan

Execute subplans in order. Submit-to-agent contract changes should land before transcript/SSE/job refinements to avoid validating obsolete routes.

## 7. Validation Plan

- Route tests prove public run actions create assignments, not direct runs.
- Transcript preservation test proves pre-execution chat messages remain.
- SSE endpoint test asserts semantic event names.
- Job Start Run test proves `JOB_RUN` assignment creation.
- Schedule/reaction save tests reject invalid assignment type.
- Full `mvn test`, bounded startup, and focused browser check for changed public controls.

## 8. Handoff Checklist

Update progress and implementation notes, record any route removals or compatibility notes, complete `.internal-dev` workflow, and commit.

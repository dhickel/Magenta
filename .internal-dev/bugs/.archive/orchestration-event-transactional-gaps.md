# Bug Report: Transactional Gaps in Orchestration Event Handling

**Date**: 2026-05-07
**Reporter**: Comprehensive Review Agent
**Status**: Open
**Severity**: Major

## Description
The `OrchestrationEventService` handles event reactions by iterating through them and creating assignments individually without a global transaction. Partial failures can lead to some reactions firing while others fail, leaving the system in an inconsistent state.

## Affected Files
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationEventService.java`

## Evidence
In `handle(event)`, the service iterates over reactions and calls `assignmentService.create(...)` for each. If one call fails, the event might not be marked as `handled_at`, but some assignments will have been created. On retry, duplicate assignments may be created for the successful ones.

## Impact
Data inconsistency and duplicate work assignments in the orchestration system.

## Recommended Fix
Wrap the event handling loop in a `@Transactional` block to ensure that either all reactions fire and the event is marked as handled, or none fire.

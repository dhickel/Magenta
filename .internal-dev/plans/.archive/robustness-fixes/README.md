# Robustness Fixes — Implementation Plan

**Date:** 2026-05-07
**Source:** `.internal-dev/reviews/comprehensive_2026-05-07/robustness_review.md` and `threading_state_review.md`

## Summary

The robustness fix pass addressed durable task execution context, model-backed task execution, job item resilience, and transactional event handling. Remaining future concerns are deployment/runtime hardening items that are not part of the completed placeholder-execution fix.

## Phases (ordered by dependency)

| Phase | Issue | Severity | Effort |
|-------|-------|----------|--------|
| [01 — Durable Execution Context](phase-01-durable-execution-context.md) | In-memory `executionRunsByConversationId` lost on restart; EXECUTE_TASK mode not wired; placeholder task execution reachable from user-facing paths | Major | Medium |
| [02 — Job Item Resilience](phase-02-job-item-resilience.md) | No retry or continue-on-failure for job items | Major | Medium |
| [03 — Transactional Event Handling](phase-03-transactional-event-handling.md) | Event reaction loop not atomic | Major | Small |

## Deferred

| Issue | Reason |
|-------|--------|
| [C: Distributed lease clock drift](deferred-issues.md#issue-c-distributed-lease-race-conditions) | Single-instance SQLite deployment. Irrelevant without multi-node. |
| [F: ThreadLocal context propagation](deferred-issues.md#issue-f-threadlocal-context-propagation-risks) | No cross-thread tool execution exists. Future risk, not current bug. |
| [G: Lease heartbeat](../../../notes/2026-05-08-orchestration-long-running-task-hardening.md) | Future hardening for long model-backed job items. |

## Key Discovery

Investigation of `executionRunsByConversationId` (flagged in both reviews) revealed a deeper issue: the `registerExecutionContext()` method that populated the map was not reached by production task execution. The completed implementation makes task execution chat/model-backed, persists active run context in chat session metadata, and removes placeholder completion from user-facing paths.

## Implementation Order

Completed and archived.

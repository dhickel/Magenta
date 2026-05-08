# Phase 03: Transactional Event Handling

## Context

`OrchestrationEventService.handle()` (lines 28-41) iterates through matching `AgentEventReaction` records and calls `assignmentService.create()` for each one. There is no `@Transactional` annotation on `publish()` or `handle()`, so each `assignmentService.create()` commits independently.

If processing 3 reactions and the 2nd throws:
- The 1st reaction's assignment was already created and committed
- The 3rd reaction never fires
- The event's `handledAt` is never set (the `saveEvent` at line 37 only runs on success)
- The event remains in the DB with `handledAt = null`, but nothing re-processes it

This leaves the system in an inconsistent state: partial side effects with no recovery path. Currently the risk is low because:
1. Only one reaction action type exists (`ENQUEUE_ASSIGNMENT`)
2. `assignmentService.create()` is a simple DB insert that rarely fails
3. The system is single-instance

But as the event/reaction system grows (more action types, more reaction sources), the risk increases.

## Goal

Wrap the `handle()` loop in a single transaction so event reactions are atomic — either all reactions fire and the event is marked handled, or none persist.

## In Scope

- Add `@Transactional` to `OrchestrationEventService.handle()` (or `publish()`)
- Verify the transaction propagates correctly through `assignmentService.create()` → `repository.saveAssignment()`
- Ensure `@Transactional` works with the existing `JdbcTemplate`-based data access (not JPA)

## Out of Scope

- Retry logic for failed event processing (the event stays unhandled; a future polling mechanism could re-process it)
- Event sourcing or outbox pattern
- Async event handling (events are currently processed synchronously in `publish()`)
- Changes to `ScheduleService.pollDueSchedules()` — it already has `@Transactional`

## Design Decisions

### Transaction boundary: `publish()` vs `handle()`

**Chosen: `@Transactional` on `publish()`**, covering both `saveEvent()` (initial save with `handledAt=null`) and `handle()` (reaction processing + final `saveEvent()` with `handledAt`).

Rationale:
- The initial event save and its handling are logically one unit of work
- If `handle()` fails, the initial event shouldn't be persisted either (no orphaned unhandled events)
- `publish()` is the only caller of `handle()` — no other code path processes events

### Why not `@Transactional` on `handle()` alone?

If only `handle()` is transactional but `publish()`'s initial `saveEvent()` isn't covered, a failure in `handle()` still leaves the initial unhandled event in the DB. This is cleaner than the current state (all-or-nothing) but still leaves an orphan. Wrapping both in `publish()` is cleaner.

### Transaction propagation with Spring JDBC

Spring's `@Transactional` works with `JdbcTemplate` via `DataSourceTransactionManager`. Since the project configures a `DataSource` bean (standard Spring Boot), `@Transactional` will work without additional configuration. The `OrchestrationRuntimeRepository` already uses `@Transactional` on `acquireLease()`, confirming the transaction infrastructure is in place.

### Impact on ScheduleService

`ScheduleService.pollDueSchedules()` (line 63) is already `@Transactional` and calls `eventService.publish()` within its transaction. With `publish()` now also `@Transactional`, the default `REQUIRED` propagation means event publishing joins the existing schedule transaction. This is correct — if the schedule transaction rolls back, event side effects should roll back too.

## Implementation Steps

### Step 1: Add @Transactional to publish()

**File:** `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationEventService.java`

Add `@Transactional` to the `publish()` method (line 19):
```java
@Transactional
public OrchestrationEvent publish(EventType eventType, String sourceType, String sourceId, Map<String, Object> payload) {
```

This single annotation covers the entire method body: initial `saveEvent`, `handle()` loop, and final `saveEvent` with `handledAt`.

### Step 2: Verify import

Ensure the import is present:
```java
import org.springframework.transaction.annotation.Transactional;
```

(Already used in `OrchestrationRuntimeRepository`, so the project has the dependency.)

## Validation

1. **Unit/Integration test**: Publish an event that triggers multiple reactions. Mock one `assignmentService.create()` call to throw. Verify:
   - No assignments were persisted (transaction rolled back)
   - The event was not saved (no orphaned unhandled event)
2. **Transaction propagation test**: Verify that when `ScheduleService.pollDueSchedules()` (which is `@Transactional`) calls `publish()`, the event handling joins the existing transaction rather than starting a new one
3. **Smoke test**: Spring context starts, event publishing works end-to-end (happy path)
4. **Existing tests pass**: `OrchestrationEventService` tests, if any, continue to work

## Exit Criteria

- [ ] `@Transactional` added to `OrchestrationEventService.publish()`
- [ ] Event save + reaction processing + handledAt update are atomic
- [ ] Transaction propagation works correctly with `ScheduleService`'s existing transaction
- [ ] Existing tests pass
- [ ] Spring context starts cleanly

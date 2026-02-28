# Phase 01: Session Input and Callback Contract Update

## Context

Runtime turn ingress was string-only (`runUserTurn`) and callback naming was ambiguous (`onMessageStored`). External event delivery had no runtime-owned routing seam. Error callback existed but was not emitted by runtime turn execution.

## Goal

Implement a simple, data-driven ingress and callback contract that supports user and non-user turn triggers, typed callback ergonomics, runtime-owned event routing, and a catch-all error callback emission path.

## In Scope

- Add `SessionInput` and `SessionInputKind`.
- Add `runSessionTurn(UUID, SessionInput)` with `runUserTurn` delegation.
- Add `SessionRoutePolicy` and Magenta route registration/publish methods.
- Add `SessionMessage.InboundMsg`.
- Rename callback surface to `onMessageAppended` with backward-compatible `onMessageStored` alias.
- Add typed message callbacks in `SessionConfig`.
- Emit `onError` from runtime turn catch path before rethrow.
- Update runtime/internal docs for behavior alignment.

## Out of Scope

- Scheduler/tick loop implementation.
- Durable queue/bus persistence.
- SecurityService integration and authorization event model.
- SessionConfig hot mutability.

## Implementation Steps

1. Extend session input contracts with `SessionInput`, `SessionInputKind`, and `SessionRoutePolicy`.
2. Extend `SessionMessage` ADT with `InboundMsg`.
3. Refactor `SessionConfig` to include canonical `onMessageAppended`, typed message callbacks, and a unified `emitMessageAppended` dispatcher.
4. Refactor `ModelRunner.runTurn` to consume pre-appended context and use callback dispatcher.
5. Implement `Magenta.runSessionTurn` to:
   - resume + compact,
   - append persisted input as `UserMsg` or `InboundMsg`,
   - run model loop,
   - emit `onError` and rethrow on failures.
6. Add lightweight route registry methods in `Magenta` (`registerSessionRoute`, `unregisterSessionRoute`, `publishToSessions`, `sessionInputConsumer`).
7. Update internal docs for new API and error semantics.

## Validation

- `mvn -q -DskipTests compile` succeeds.
- Search confirms callback dispatch uses `emitMessageAppended` in model/runtime message append paths.
- Search confirms `runUserTurn` delegates to `runSessionTurn`.
- Search confirms `onError` is called in runtime catch path.

## Exit Criteria

- New ingress and callback APIs are available and compile.
- Existing `runUserTurn` behavior remains available.
- Runtime emits `onError` before propagating exceptions.
- Documentation reflects implemented behavior.

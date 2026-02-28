# Phase 01: Sealed Session Input Adapters

## Context

Runtime ingress used a flat `SessionInput` record with a single kind enum. Session adapter APIs requested by design discussion (`messageConsumerFor`, `eventConsumerFor`) were not available on `SessionManager`.

## Goal

Refactor session input handling to sealed message/event input interfaces with minimal complexity, add policy-gated SessionManager bus adapters, and keep runtime orchestration centralized.

## In Scope

- Replace flat input model with sealed `SessionInput` hierarchy.
- Split input kinds into message and event kind enums.
- Add `messageConsumerFor` and `eventConsumerFor` on `SessionManager` with policy gating.
- Add `onMessageInput` and `onEventInput` callbacks to `SessionConfig`.
- Update turn path in `Magenta` to emit input callbacks and map sealed inputs to persisted messages.
- Update internal docs impacted by API/behavior changes.

## Out of Scope

- Scheduler/tick service implementation.
- Durable queue/bus persistence.
- Security service integration.

## Implementation Steps

1. Replace `SessionInput`/`SessionInputKind` with sealed interfaces + leaf records and split kind enums.
2. Update `SessionRoutePolicy` to separate allowed message kinds and event kinds.
3. Add `SessionManager` turn submitter dependency and adapter factories.
4. Add input callback consumers and dispatch method to `SessionConfig`.
5. Update `Magenta` constructor wiring and run turn flow.
6. Update `SessionMessage.InboundMsg` metadata for message/event domain + kind text.
7. Update docs and validate with compile.

## Validation

- `mvn -q -DskipTests compile` passes.
- Session manager exposes `messageConsumerFor` and `eventConsumerFor`.
- Session config exposes `onMessageInput` and `onEventInput`.
- No remaining code references to removed `SessionInputKind`.

## Exit Criteria

- Sealed session input hierarchy is active in runtime.
- Adapter APIs exist on `SessionManager` and enforce policy before forwarding.
- Runtime still supports compatibility `runUserTurn` path.
- Internal docs reflect new adapter/input model.

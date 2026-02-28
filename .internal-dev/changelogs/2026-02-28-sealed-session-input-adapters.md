# Sealed Session Input Adapters

## Date

2026-02-28

## Change Summary

Implemented sealed input and adapter refactor for session ingress:
- Replaced flat `SessionInput` + `SessionInputKind` with sealed `SessionInput` hierarchy (`MessageInput`, `EventInput` and leaf records).
- Added `MessageInputKind` and `EventInputKind` enums.
- Refactored `SessionRoutePolicy` to split allowed message kinds/event kinds.
- Added `SessionManager.messageConsumerFor` and `SessionManager.eventConsumerFor` policy-gated adapters.
- Added `onMessageInput` and `onEventInput` callbacks to `SessionConfig` with dispatch helper.
- Updated `Magenta` to construct `SessionManager` with turn submitter callback and emit input callbacks in turn flow.
- Updated inbound message mapping to carry input domain/kind text fields.
- Updated internal docs for new input and adapter model.

## Files

- `src/main/java/io/mindspice/magenta/systems/session/SessionInput.java`
- `src/main/java/io/mindspice/magenta/systems/session/MessageInput.java`
- `src/main/java/io/mindspice/magenta/systems/session/EventInput.java`
- `src/main/java/io/mindspice/magenta/systems/session/MessageInputKind.java`
- `src/main/java/io/mindspice/magenta/systems/session/EventInputKind.java`
- `src/main/java/io/mindspice/magenta/systems/session/UserMessageInput.java`
- `src/main/java/io/mindspice/magenta/systems/session/BusMessageInput.java`
- `src/main/java/io/mindspice/magenta/systems/session/SystemEventInput.java`
- `src/main/java/io/mindspice/magenta/systems/session/TimerWakeEventInput.java`
- `src/main/java/io/mindspice/magenta/systems/session/SessionRoutePolicy.java`
- `src/main/java/io/mindspice/magenta/systems/session/SessionManager.java`
- `src/main/java/io/mindspice/magenta/systems/session/SessionConfig.java`
- `src/main/java/io/mindspice/magenta/systems/session/SessionMessage.java`
- `src/main/java/io/mindspice/magenta/systems/Magenta.java`
- `docs/internal/01-runtime-developer-guide.md`
- `docs/internal/15-callback-contract-architecture.md`
- `docs/internal/20-integration-patterns.md`
- `docs/internal/21-sequence-walkthroughs.md`

## Behavioral Impact

- Session turn ingress is now explicitly typed by sealed message/event inputs.
- SessionManager can provide per-session policy-gated message/event consumers for bus wiring.
- Session callbacks now expose pre-persistence input events (`onMessageInput` / `onEventInput`).

## Risks

- Adapter denies are currently silent no-ops.
- No automated tests currently cover all adapter policy combinations.

## Follow-up Items

- Add tests for adapter policy decisions and callback ordering.
- Decide whether denied adapter inputs should emit explicit audit callbacks/events.

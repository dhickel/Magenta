# Session Input and Callback Contract

## Date

2026-02-28

## Change Summary

Implemented a typed session ingress contract and callback contract cleanup:
- Added `SessionInput` + `SessionInputKind` for typed turn triggers.
- Added `Magenta.runSessionTurn(UUID, SessionInput)` and kept `runUserTurn` as compatibility delegation.
- Added runtime-owned route policy support with `SessionRoutePolicy` and route registration/publish methods.
- Added `SessionMessage.InboundMsg` for persisted non-user inputs.
- Added canonical `onMessageAppended` callback plus typed message callbacks in `SessionConfig`.
- Kept `onMessageStored` as deprecated compatibility alias.
- Wired `onError` emission in runtime turn catch path and rethrow behavior.
- Updated internal runtime docs to match implemented behavior.

## Files

- `src/main/java/io/mindspice/magenta/systems/session/SessionConfig.java`
- `src/main/java/io/mindspice/magenta/systems/session/SessionMessage.java`
- `src/main/java/io/mindspice/magenta/systems/session/SessionInput.java`
- `src/main/java/io/mindspice/magenta/systems/session/SessionInputKind.java`
- `src/main/java/io/mindspice/magenta/systems/session/SessionRoutePolicy.java`
- `src/main/java/io/mindspice/magenta/systems/model/ModelRunner.java`
- `src/main/java/io/mindspice/magenta/systems/Magenta.java`
- `docs/internal/01-runtime-developer-guide.md`
- `docs/internal/10-runtime-architecture.md`
- `docs/internal/12-session-architecture.md`
- `docs/internal/14-model-ollama-architecture.md`
- `docs/internal/20-integration-patterns.md`
- `docs/internal/21-sequence-walkthroughs.md`
- `docs/internal/30-runtime-troubleshooting.md`

## Behavioral Impact

- Runtime can now accept non-user trigger inputs through typed contract.
- Callback consumers can subscribe by message subtype without manual pattern matching.
- Runtime turn failures now emit `onError` consistently before exception propagation.
- Event/bus integration can be done through runtime-managed route registration and `SessionInput` publishing.

## Risks

- Route publishing is synchronous and currently fail-fast on first thrown exception.
- No automated tests currently validate dispatch order or route policy behavior.

## Follow-up Items

- Add tests for callback dispatch and onError emission invariants.
- Decide `publishToSessions` failure policy (fail-fast vs best-effort) before production use.

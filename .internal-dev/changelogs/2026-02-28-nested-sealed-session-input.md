# Nested Sealed SessionInput Hierarchy

## Date

2026-02-28

## Change Summary

Refactored session input contracts so the full sealed hierarchy is nested under `SessionInput`.

- Moved all message/event input sealed contracts, enums, and records into `SessionInput` as nested types.
- Updated runtime and callback signatures to use `SessionInput.*` types.
- Removed standalone input-related files that are now nested.
- Added an explicit AGENTS rule requiring sealed hierarchies to be nested under one root contract when practical.
- Updated internal docs/examples to use nested type names.

## Files

- `src/main/java/io/mindspice/magenta/systems/session/SessionInput.java`
- `src/main/java/io/mindspice/magenta/systems/session/SessionConfig.java`
- `src/main/java/io/mindspice/magenta/systems/session/SessionRoutePolicy.java`
- `src/main/java/io/mindspice/magenta/systems/session/SessionManager.java`
- `src/main/java/io/mindspice/magenta/systems/Magenta.java`
- `AGENTS.md`
- `docs/internal/01-runtime-developer-guide.md`
- `docs/internal/15-callback-contract-architecture.md`
- `docs/internal/20-integration-patterns.md`
- `docs/internal/21-sequence-walkthroughs.md`
- deleted standalone input files under `src/main/java/io/mindspice/magenta/systems/session/`

## Behavioral Impact

No functional behavior change to turn execution semantics; API/type references now resolve through nested `SessionInput` members.

## Risks

- Downstream code that imported removed standalone input types must migrate to nested `SessionInput.*` types.

## Follow-up Items

- Add regression tests around adapter and callback typing once test harness is available.

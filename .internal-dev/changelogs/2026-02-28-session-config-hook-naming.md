# SessionConfig Hook Naming Cleanup

## Date

2026-02-28

## Change Summary

Renamed SessionConfig callback getters/builders to hook-suffixed names and removed alias methods.

- Canonicalized callback API naming to `*Hook` across message/input/token/error callbacks.
- Removed backward-compat alias methods (user requested no aliases).
- Updated runtime call sites and internal docs to use hook names.

## Files

- `src/main/java/io/mindspice/magenta/systems/session/SessionConfig.java`
- `src/main/java/io/mindspice/magenta/systems/model/ModelRunner.java`
- `src/main/java/io/mindspice/magenta/systems/Magenta.java`
- `docs/internal/01-runtime-developer-guide.md`
- `docs/internal/15-callback-contract-architecture.md`
- `docs/internal/90-documentation-quality-checklist.md`

## Behavioral Impact

No runtime behavior change. Public callback API names are now hook-only.

## Risks

Code using prior callback method names will fail to compile until migrated to `*Hook` names.

## Follow-up Items

- Add a short migration note if external consumers exist outside this repo.

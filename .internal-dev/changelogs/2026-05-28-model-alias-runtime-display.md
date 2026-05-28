# Changelog: Model Alias Runtime Display

## Date
2026-05-28

## Change Summary
Fixed runtime model defaults and model lists so UI-facing chat/orchestration paths use configured model aliases while preserving provider `remoteModelName` resolution for routing.

The live runtime settings finding was that persisted settings can override file config. In the observed local DB, `default_model=local-qwen` takes precedence over the file-configured `defaultModel=deepseek-v4-max`; that is retained behavior, not silently rewritten.

## Files
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettingsService.java` — added alias-key accessors for runtime model fields and normalized remote-name submissions to alias keys before persistence.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` — returned alias keys for default/planning models and available model lists used by UI/controller selectors.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/RequestResolver.java` — resolved default and planning request models as alias keys before router conversion.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouter.java` — honored file-configured `defaultModel` when no model is supplied.
- `src/test/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettingsServiceTest.java` — covered alias-key accessors and remote-name normalization on save.
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouterTest.java` — covered no-model default routing through the configured default alias.
- `docs/technical/configuration-operations.md` — documented alias persistence/display versus remote routing.

## Behavioral Impact
Runtime settings still take precedence over file config, but displayed/selectable model values are alias keys. Provider model names remain accepted where routing already supports them, legacy persisted remote-name fields are mapped to aliases for display/routing, and new runtime settings saves normalize model references to aliases.

## Specification Impact
No new specification entries. This implements the existing `WEB-20260525-08` and `SVC-20260525-13` alias/default contracts.

## Risks
Low. Existing routing methods still return provider names for model calls; the changed paths are UI/request-facing default and list helpers.

## Follow-up Items
- If an operator has a stale runtime default alias such as `local-qwen`, it will continue to take precedence until changed through runtime settings.

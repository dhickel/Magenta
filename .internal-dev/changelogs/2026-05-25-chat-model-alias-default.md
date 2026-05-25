# Date

2026-05-25

# Change Summary

Set the file-configured default browser chat model to the `deepseek-v4-max` alias and changed the `/chat` model selectors to display and submit configured alias keys instead of provider remote model names.

# Files

- `config/ai-config.example.json`: added `deepseek-v4-max` and made it the default model.
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`: renders `/chat` model dropdown options from alias-based model options.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`: honors file-configured `defaultModel` before default-agent fallback when runtime settings are absent, and maps remote model names back to aliases for selector state.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/RequestResolver.java`: honors file-configured `defaultModel` before default-agent fallback when runtime settings are absent.
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`: verifies `/chat` renders aliases in model selectors.
- `.internal-dev/specifications/web.md`, `.internal-dev/specifications/services.md`, `.internal-dev/knowledge/spring-ai-model-options-routing.md`: recorded alias and default-model contracts.
- `docs/end-user/chat.md`, `docs/technical/configuration-operations.md`: documented alias display and default precedence.

# Behavioral Impact

On fresh file-config-backed installs without a persisted runtime override, anonymous `/chat` defaults to `deepseek-v4-max`. The `/chat` Agent Model and Planning Model dropdowns now show aliases such as `deepseek-v4-max`; submitted chat payloads use those aliases and are resolved to provider model names by the existing model router.

# Specification Impact

Updated web and services specifications to require alias-based `/chat` model selector labels/values and file-configured `defaultModel` precedence before legacy default-agent model fallback.

# Risks

Existing persisted runtime settings can still override the file-configured default model until the operator changes or clears those settings. Provider credentials in local AI config remain deployment-specific.

# Follow-up Items

- None.

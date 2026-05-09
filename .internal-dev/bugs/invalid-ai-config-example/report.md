# Summary

`config/ai-config.example.json` is named as JSON but contains unquoted YAML-like keys and scalar values, so the strict JSON loader fails when the default config path is used.

# Scope

Startup configuration only. This was discovered during UI smoke validation and is out of scope for the SimplyPages UI refactor.

# Reproduction

Run the app without overriding `app.ai.config-path`:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18082 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-simplypages-refactor.sqlite'
```

# Expected

The default example config should either parse successfully or the default path should point to a file format that matches its contents.

# Actual

Startup fails while creating `aiConfig` with a Jackson JSON parse error: `Unexpected character ('d' (code 100)): was expecting double-quote to start field name`.

# Evidence

`AiUserConfigConfiguration` defaults `app.ai.config-path` to `./config/ai-config.example.json`, and `ExternalAiConfigLoader` uses the JSON `ObjectMapper` for `.json` files.

# Impact

Fresh startup smoke tests fail unless callers provide a separate valid JSON/YAML AI config path.

# Status

Open.

# Next Action

Convert the example file to valid JSON, rename it to `.yaml` and update the default path, or change the default to an existing valid fixture.

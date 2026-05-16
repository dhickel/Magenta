## Summary

Default Spring Boot startup points at `./config/ai-config.example.json`, but that file is absent in this checkout.

## Scope

Application startup configuration.

## Reproduction

Run:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

## Expected

The application context starts with the default configuration or the repository provides the referenced example config.

## Actual

Startup fails while creating `aiConfig` because `./config/ai-config.example.json` does not exist.

## Evidence

The failure observed on 2026-05-16 was:

```text
Factory method 'aiConfig' threw exception with message: ./config/ai-config.example.json (No such file or directory)
```

Bounded startup succeeds when a valid temporary `--app.ai.config-path=...` is provided.

## Impact

Agents following the default smoke-test command hit a configuration blocker unrelated to the feature under test.

## Status

Open; out of scope for the agent queue delete and live audit transcript feature.

## Next Action

Decide whether to restore a tracked example config, change the default path, or document the required local config bootstrap.

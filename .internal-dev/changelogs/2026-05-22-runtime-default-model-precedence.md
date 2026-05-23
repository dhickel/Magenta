---
date: 2026-05-22
title: Runtime default model precedence fix
status: complete
---

# Runtime Default Model Precedence Fix

Reviewed the Avatar sprint final-validation note that browser chat attempts logged local model connection failures to `http://localhost:11434`.

## Changed

- `RuntimeSettingsService.defaultModel()` now resolves anonymous/default chat through the explicit runtime/file default model instead of first using the selected default agent profile model.
- Agent-scoped model resolution is preserved through `resolveModel(null, agentDefaultModel)`, so side-panel agent chat and assignment execution can still use a selected agent's configured default model when that path passes it intentionally.
- Regression coverage now verifies anonymous default chat prefers runtime settings while explicit agent-default resolution still wins for agent-scoped calls.

## Impact

The `/avatar` compact chat and normal `/chat` default model should no longer be accidentally pointed at an unavailable local Ollama provider just because the default agent profile was seeded from `agents.magenta.model`.

## Validation

- `mvn -Dtest=RuntimeSettingsServiceTest,OrchestrationRuntimeTest#runtimeSettingsSaveLoadAndModelResolutionPriority test` passed.

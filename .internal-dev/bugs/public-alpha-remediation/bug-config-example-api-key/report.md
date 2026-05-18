# Config Example Contains Real-Looking API Key

## Summary

`config/ai-config.example.json` contained a concrete `apiKey` value that looked like a live provider secret rather than a placeholder.

## Scope

Out of scope for domain 02 subplan 01 shell confinement. This is a configuration hygiene/security follow-up.

## Reproduction

1. Open `config/ai-config.example.json`.
2. Inspect the `models.deepseek-v4.apiKey` value.

## Expected

Example configuration should use a placeholder value or omit secrets entirely.

## Actual

The example file contained a concrete `sk-...` style API key value before the local shell-confinement closeout sanitized it.

## Evidence

- `config/ai-config.example.json` included `models.deepseek-v4.apiKey` with a non-placeholder value.
- 2026-05-18 update: the local example now uses `replace-with-your-deepseek-api-key` before any force-add/commit decision.

## Impact

Potential secret exposure if the value was real or was copied from an operator environment. Even if invalid, it trained agents and operators to place real secrets in example files.

## Status

Sanitized locally during bug-08 shell confinement work; rotation/revocation remains operator-owned if the previous value was live.

## Next Action

Rotate/revoke the prior key if it was ever live.

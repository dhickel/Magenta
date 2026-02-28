# Runtime Troubleshooting

## Parse and validation failures

Symptoms:

- startup fails before runtime creation
- error includes `Config parse failure at ... (line=..., column=...)`

Checks:

1. Confirm file path exists under `configs/`.
2. Fix YAML shape/unknown keys at reported line/column.
3. Verify include patterns match expected files.
4. Verify enabled agents reference enabled models and existing prompts.

## Session lifecycle errors

Symptoms:

- `Session not found: <uuid>` on resume/fork/run turn

Checks:

1. Ensure UUID is from active `SessionManager` instance.
2. Avoid using aliases as resume keys.
3. Remember process restart clears in-memory session registry.

## Model transport failures

Symptoms:

- `Ollama chat failed with status ...`
- `Ollama streaming request failed`

Checks:

1. Verify endpoint URL and Ollama availability.
2. Validate model ID exists in Ollama server.
3. For non-URL endpoint values, check `MAGENTA_OLLAMA_URL` or default host.
4. Retry with blocking-only config to isolate streaming issues.

## Callback and tool bridge failures

Symptoms:

- runtime turn throws during tool loop
- unhandled tool requests become `Tool not handled: <name>` content

Checks:

1. Verify `toolsEnabled` and bridge function wiring.
2. Ensure bridge handles null/invalid arguments defensively.
3. Wrap bridge in policy + error handling if failures must not abort turn.

## onError callback behavior

Symptoms:

- runtime throws and `onError` callback fires

Checks:

1. Confirm callback is set on the session's `SessionConfig`.
2. Verify callback code is non-throwing and side-effect safe.
3. Remember runtime rethrows after callback; caller-level handling is still required.

## Common implementation mistakes and corrections

- Mistake: assuming `onError` swallows failures.
  Correction: `onError` is notification-only; runtime still propagates original exception.
- Mistake: assuming duplicate config IDs are rejected.
  Correction: avoid duplicate IDs in include sets; current loader map behavior overwrites previous entry.
- Mistake: assuming persistent resume across process restarts.
  Correction: treat resume as in-memory only until durable session storage is implemented.

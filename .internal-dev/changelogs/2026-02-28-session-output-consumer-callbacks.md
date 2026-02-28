# Session Output Consumer Callbacks

## Date

2026-02-28

## Change Summary

Implemented output consumer callbacks in `SessionConfig` and wired runtime output delivery in `ModelRunner`:
- Added `onStreamingResponseConsumer` and `onFullResponseConsumer` callbacks.
- Added `emitStreamingCompletionToFullResponse` boolean config flag (default `true`).
- Added `emitStreamingResponse` and `emitFullResponse` helper methods to `SessionConfig`.
- Updated streaming model invocation to emit both token hook and streaming output consumer.
- Emitted full response callback for assistant outputs in blocking and streaming flows (streaming replay configurable).
- Added unit tests for output callback defaults and replay suppression.
- Updated internal callback/runtime docs and checklist references.

## Files

- `src/main/java/io/mindspice/magenta/systems/session/SessionConfig.java`
- `src/main/java/io/mindspice/magenta/systems/model/ModelRunner.java`
- `src/test/java/io/mindspice/magenta/systems/session/SessionConfigTest.java`
- `docs/internal/01-runtime-developer-guide.md`
- `docs/internal/15-callback-contract-architecture.md`
- `docs/internal/20-integration-patterns.md`
- `docs/internal/21-sequence-walkthroughs.md`
- `docs/internal/90-documentation-quality-checklist.md`

## Behavioral Impact

- Session owners can now wire streamed token and full-response output callbacks directly in immutable session config.
- Streaming turns optionally replay finalized full text to full-response callback via a config boolean.
- Input routing boundaries in `SessionManager` remain unchanged.

## Risks

- Full-response callback currently fires for each assistant response iteration, including tool-loop interim assistant outputs.
- Callback exceptions remain fail-fast and bubble through runtime error handling.

## Follow-up Items

- Add `ModelRunner`-level tests with a model client seam for callback ordering and tool-loop scenarios.
- Decide whether to add terminal-only full response callback behavior in a future scoped change.

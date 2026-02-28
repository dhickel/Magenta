# Phase 01: Session Output Consumer Callbacks

## Context

Session output handling was split between internal hooks and token streaming hooks, with no dedicated owner callback for final full responses. Proposed supplier-based output APIs were rejected in favor of callback-only output wiring in immutable `SessionConfig`.

## Goal

Add session-owned output consumers for streaming chunks and full responses, controlled by a simple boolean flag in `SessionConfig`, while preserving existing hook behavior and keeping `SessionManager` focused on input adapters only.

## In Scope

- Add `onStreamingResponseConsumer` and `onFullResponseConsumer` to `SessionConfig`.
- Add `emitStreamingCompletionToFullResponse` boolean in `SessionConfig`.
- Add `emitStreamingResponse` and `emitFullResponse` helper methods.
- Wire `ModelRunner` to emit streaming and full-response callbacks.
- Keep existing message/input/token/error hooks intact.
- Update internal docs for callback contract behavior.

## Out of Scope

- New `SessionManager` output APIs.
- Supplier-based pull output APIs.
- Separate `SessionOutputPolicy` type.
- SecurityService/event audit centralization.

## Implementation Steps

1. Extend `SessionConfig` immutable state and builder with new output consumers and replay boolean.
2. Implement output emission helpers with replay suppression logic for streamed completions.
3. Update `ModelRunner` streaming invocation to emit both token hook and streaming consumer.
4. Emit full-response callback from `ModelRunner` for blocking and streaming turns (subject to replay boolean).
5. Add unit tests for `SessionConfig` output callback defaults and replay suppression behavior.
6. Update runtime/internal docs and checklist references.

## Validation

- `mvn test -q` passes.
- Streaming path emits token hook + streaming consumer callbacks.
- Full response callback suppression works when streaming replay boolean is disabled.
- Existing message append and tool loop behavior remains intact.

## Exit Criteria

- Output callbacks are available via `SessionConfig` and default-safe.
- Streaming full-response replay is configurable by boolean in config.
- No new output APIs were added to `SessionManager`.
- Documentation and changelog artifacts are updated.

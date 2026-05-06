# Context

Tool activity events were emitted from `ChatService.stream`, but the controller subscribed to the blocking stream on the servlet request thread before returning the `SseEmitter`.

# Goal

Allow the HTTP stream response to be established before blocking model/tool work begins so tool events can reach the browser as each tool iteration completes.

# In Scope

- Move chat stream subscription work off the request thread.
- Preserve existing cancellation, error, and completion handling.
- Add focused controller coverage proving `/api/chat/stream` returns while the chat stream is still blocked.

# Out of Scope

- Replacing Spring AI `ToolCallingManager`.
- Emitting within a single Spring AI batch of tool calls.
- Changing chat DTOs or browser rendering.

# Implementation Steps

- Apply `subscribeOn(Schedulers.boundedElastic())` in the stream controller subscription.
- Keep the existing `Disposable` reference and emitter callbacks.
- Add a delayed Flux test that would block without async subscription.

# Validation

- Run `ChatControllerTest`.
- Run full `mvn test`.

# Exit Criteria

- Controller returns an `SseEmitter` before delayed chat stream completion.
- Existing stream cancellation/error/done wiring remains unchanged.

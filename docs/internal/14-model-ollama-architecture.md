# Model and Ollama Architecture

## Design intent

Keep model execution (`ModelRunner`) separate from provider transport (`OllamaClient`), with router awareness only at orchestration layer (`Magenta`).

## Responsibilities

`ModelRunner`:

- map `ContextElement` context to LangChain4j messages
- execute blocking or streaming turn mode from caller-provided option
- append assistant/tool results to context
- emit typed `OutputRoutingEvent` through provided callback
- provide summarization for compaction

`OllamaClient`:

- build `/api/chat` payloads
- execute blocking and streaming HTTP requests
- parse chat responses/tool calls

## Mode selection

`ModelRunner` uses blocking when any is true:

- `sessionConfig.params().blockingOnly()`
- tool loop is active
- turn option disables streaming
- model reports `supportsStreaming = false`

## Event emission contract

- Streaming provider chunks emit `OutputRoutingEvent(SessionOutput.StreamedOutput)`.
- Assistant completion always emits `OutputRoutingEvent(SessionOutput.FinalOutput)`.
- Context append operations emit `ContextMessageOutput`; tool appends also emit `ToolMessageOutput`.
- `StreamedOutput` chunk payloads are provider-defined boundaries and are not guaranteed to be single tokens.

## Failure behavior

- `OllamaClient` classifies provider failures into typed reasons (`context_overflow`, `output_truncated`, `stream_incomplete`, `http_error`, `malformed_response`) via `ModelClientException`
- `Magenta` emits `SessionEvent.Action.ModelFailure` on typed model failures before rethrowing to session error ingress
- tool bridge exceptions propagate to submit path, where session ingress emits `onError`

## Known constraints

- tool schemas are sent only when session tools are enabled, model tool-calling is supported, and tool specs are discoverable
- schema quality depends on annotated tool parameter typing; nested typed records/lists produce stricter provider schemas than raw JSON nodes
- single provider transport implementation (`OllamaClient`)
- all typed system variants (`system_core`, `system_agent`, `system_task`, `system_state`) map to provider `system` role messages
- `SummaryMsg` context is mapped as user-role context (not system-role instruction) during model request assembly

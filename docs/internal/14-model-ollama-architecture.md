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

- HTTP/transport/parsing errors surface as exceptions
- tool bridge exceptions propagate to submit path, where session ingress emits `onError`

## Known constraints

- tool schemas are not sent in current payload builder
- single provider transport implementation (`OllamaClient`)

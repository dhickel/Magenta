# Model and Ollama Architecture

## Design intent

Keep model execution (`ModelRunner`) separate from provider transport (`OllamaClient`), with router awareness only at orchestration layer (`Magenta`).

## Responsibilities

`ModelRunner`:

- map `SessionMessage` context to LangChain4j messages
- execute blocking or streaming turn mode from caller-provided option
- append assistant/tool results to context
- emit typed `SessionOutputEvent` through provided callback
- provide summarization for compaction

`OllamaClient`:

- build `/api/chat` payloads
- execute blocking and streaming HTTP requests
- parse chat responses/tool calls

## Mode selection

`ModelRunner` uses blocking when any is true:

- `sessionConfig.blockingOnly`
- tool loop is active
- turn option disables streaming
- model reports `supportsStreaming = false`

## Event emission contract

- Streaming token chunks emit `SessionOutputEvent.PartialToken`.
- Assistant completion always emits `SessionOutputEvent.AssistantFinal`.
- Context append operations emit `MessageAppended`; tool appends also emit `ToolMessageAppended`.

## Failure behavior

- HTTP/transport/parsing errors surface as exceptions
- tool bridge exceptions propagate to submit path, where session ingress emits `onError`

## Known constraints

- tool schemas are not sent in current payload builder
- single provider transport implementation (`OllamaClient`)

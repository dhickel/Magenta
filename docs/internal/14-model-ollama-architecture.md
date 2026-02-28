# Model and Ollama Architecture

## Design intent

Separate execution policy (`ModelRunner`) from provider transport (`OllamaClient`).

## Responsibilities

`ModelRunner`:

- map typed context to LangChain4j `ChatMessage` inputs
- choose blocking vs streaming mode
- append assistant/tool results to context
- drive iterative tool loop
- provide summarization operation for compaction

`OllamaClient`:

- build `/api/chat` payloads
- perform blocking and streaming HTTP requests
- parse response text and tool calls
- return normalized `ChatResponse`

## Explicit non-goals

- retries/backoff policy
- cross-provider abstraction
- centralized tool authorization

## Invariants

- Persisted turn input is appended before model request per turn by `Magenta`.
- Every assistant response is appended as `AssistantMsg`.
- Tool calls are converted to `ToolRequest` and bridged through callback.
- `safeText(...)` normalizes null/blank content to `"."`.
- Streaming mode is disabled once tool loop becomes active.

## Turn-loop transitions

```text
request from context snapshot
-> choose blocking/streaming
-> call OllamaClient
-> append AssistantMsg
-> if no tool calls or tools disabled: return
-> for each tool call: bridge + append ToolMsg
-> next iteration (maxIterations bound)
```

Mode selection condition:

```text
useBlocking = session.blockingOnly
           || toolLoopActive
           || !model.supportsStreaming
```

## Failure behavior

- HTTP non-2xx responses throw with status + body.
- transport/parsing/interrupt errors throw `IllegalStateException`.
- bridge exceptions currently bubble to caller.

## Extension points

- add model providers by implementing additional client classes and selecting at runtime owner level.
- augment `SessionConfig` bridge behavior (policy wrappers, telemetry, retry) without changing `ModelRunner` contract.

## Known constraints

- Tool schemas are not sent to provider in current payload builder.
- Endpoint handling uses direct URL when scheme is present; otherwise env/default fallback.
- Runtime turn errors are emitted via `SessionConfig.onError` in `Magenta` before rethrow.

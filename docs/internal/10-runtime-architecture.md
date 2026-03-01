# Runtime Architecture

## Design intent

Provide a lean local runtime with explicit boundaries:

- config graph loading and validation
- session lifecycle
- context compaction
- model turn orchestration
- callback-based integration

## Responsibilities

- `Magenta`: composition root and top-level API.
- `RuntimeConfig`: load and validate runtime configuration graph.
- `SessionManager`: session lifecycle owner.
- `ContextManager`: context state and compaction orchestration.
- `ModelRunner`: turn loop and tool loop orchestration.
- `OllamaClient`: HTTP transport for chat requests.

## Explicit non-goals

- durable session persistence
- built-in tool policy/authorization service
- multi-provider abstraction layer
- distributed runtime ownership

## Runtime invariants

- Runtime services are constructed once per `Magenta` instance.
- Session identity is UUID-based.
- Session context is represented by typed `SessionMessage` ADT only.
- Compaction runs before each session turn execution.
- Turn ingress is typed (`SessionInput`) with user/bus/system/timer kinds.
- Model transport side effects occur only through `OllamaClient`.

## State transitions

```text
Config Loaded -> Runtime Constructed -> Session Started/Resumed/Forked
-> Context Compacted (optional) -> Turn Executed -> Messages Appended
-> Optional Tool Loop -> Final Assistant Text Returned
```

## Failure behavior

- Config parse/validation errors fail fast at startup.
- Unknown session IDs fail fast on `resume`/`fork`/turn execution.
- Model transport failures surface as exceptions.
- Tool bridge failures surface as exceptions unless caller guards bridge logic.
- Turn path exceptions emit `SessionConfig.onError` before propagation.
- External routed input uses `SessionInputRouter` and reports inactive/policy-denied outcomes without throwing by default.

## Extension points

- `SessionConfig` callbacks for output/tool integration.
- `Magenta` route registry stores `SessionInputRouter` adapters for external input fanout.
- `SessionInputRouter` report callbacks (`ALL`/`FAILURE`/`ERROR`) for routing observability.
- Compaction strategy selection via `ModelConfig.compactionStrategy`.
- Summarizer function injection from runtime/model path into compaction strategy.

## Integration boundary summary

```text
Caller code owns policy + side effects (callbacks).
Runtime owns orchestration + typed state transitions.
Transport owns provider protocol execution.
```

## Known constraints

- In-memory session manager only.
- Compaction threshold/token counting depends on configured tokenizer encoding (`jtokkit`, default `cl100k_base`).
- Security enforcement is not centralized in this implementation slice.

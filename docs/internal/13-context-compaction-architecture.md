# Context and Compaction Architecture

## Design intent

Keep history mutation and token-budget control explicit, deterministic, and isolated from session lifecycle.

## Responsibilities

- `Context`: synchronized append/replace state container.
- `ContextManager`: create/copy/load/store contexts and trigger compaction.
- `SessionTokenEstimator`: heuristic token estimate.
- `CompactionStrategy`: strategy contract and selection.

## Explicit non-goals

- tokenizer-accurate token counting
- automatic persistence in this slice
- compaction side effects outside context replacement

## Invariants

- `Context.snapshot()` returns immutable copy.
- Compaction is no-op when estimated tokens are within threshold.
- Strategy selection is deterministic by `compactionStrategyOrDefault()`.
- Rolling window preserves first system message when present.

## State transitions

```text
context snapshot
-> estimate tokens
-> if over threshold: select strategy
-> run strategy(sessionId, messages, targetTokens)
-> replaceAll(compactedMessages)
```

Summarize strategy transition:

```text
over-threshold context
-> split into summarize-segment + recent-tail
-> summarize old segment
-> if summary blank/error: fallback rolling_window
-> build [system?, SummaryMsg, recent...]
-> if still too large: fallback rolling_window
```

## Failure behavior

- Summarization failure is handled through fallback strategy, not silent drop.
- `storeContext` currently performs validation only (`Objects.requireNonNull`) and does no persistence.

## Extension points

- Add alternative `CompactionStrategy` implementations and wire via `forName(...)`.
- Replace estimator when tokenizer-backed estimator is introduced.
- Implement durable persistence behind `storeContext`/`loadContext` seams.

## Known constraints

- Heuristic token estimator can mis-size real provider context usage.
- `CompactionStrategy.forName(...)` defaults unknown names to rolling-window instead of failing.

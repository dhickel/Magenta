# Context and Compaction Architecture

## Design intent

Keep history mutation and token-budget control explicit, deterministic, and isolated from session lifecycle.

## Responsibilities

- `Context`: synchronized append/replace state container.
- `ContextManager`: create/copy/load/store contexts, attach mutation persistence bridge, and trigger compaction.
- `SessionTokenEstimator`: tokenizer-backed token estimate (`jtokkit`) using model `tokenizerEncoding` (default `cl100k_base`).
- `CompactionStrategy`: strategy contract and selection.
- `DatabaseService` (via `SessionContextCommand` bridge): durable context message/session metadata persistence.

## Explicit non-goals

- compaction side effects outside context replacement

## Invariants

- `Context.snapshot()` returns immutable copy.
- Compaction is no-op when estimated tokens are within threshold.
- Leading contiguous prompt-system entries (`system_core`, `system_agent`, `system_task`) are protected and never sent to summarization input.
- `system_state` is excluded from summarization input and reinserted as the last system message before model calls.
- Strategy selection is deterministic by `compactionStrategyOrDefault()`.
- Rolling window preserves all leading system prompt messages when present.
- Compaction is evaluated before each model call, including tool-loop follow-up calls.
- Summarize strategy preserves a raw unsummarized recent tail, aligned to a user/inbound turn boundary.
- Context replacement is skipped when token reduction gain is too small (low-value churn guard).
- Context mutation persistence uses ordered per-session message IDs.
- State snapshot tool usage is bucketed and bounded (`files<=4`, `sql<=4`, `todos<=4`, `other<=8`) with empty buckets omitted.

## State transitions

```text
context snapshot
-> estimate tokens
-> if over threshold: select strategy
-> run strategy(sessionId, messages, targetTokens)
-> if change is negligible: no-op
-> replaceAll(compactedMessages)
```

Summarize strategy transition:

```text
over-threshold context
-> split into summarize-segment + protected recent-tail
-> summarize old segment
-> if summary blank/error: fallback rolling_window
-> build [system?, SummaryMsg, recent...]
-> if still too large: fallback rolling_window
```

## Failure behavior

- Summarization failure is handled through fallback strategy, not silent drop.
- Persistence bridge failures fail the active mutation path; no silent persistence drops.

## Extension points

- Add alternative `CompactionStrategy` implementations and wire via `forName(...)`.
- Expand persistence bridge ADTs (`SessionContextCommand`) for additional query/use-cases.

## Known constraints

- Tokenizer accuracy depends on selecting the model-appropriate encoding.
- `CompactionStrategy.forName(...)` defaults unknown names to rolling-window instead of failing.
- Context compaction reassigns new sequential IDs for the replacement active context; previous active IDs are tracked in dropped-id metadata.
- Legacy `compaction_snapshots` persistence is removed; compaction-state loading is derived from bounded tool/todo/session rows only.

# Session Output Consumer Callbacks Review

## Scope

Review of `SessionConfig` and `ModelRunner` changes introducing output consumer callbacks for streamed chunks and full response delivery with streaming replay control.

## Findings

- Output callbacks are co-located in immutable `SessionConfig` and default to no-op behavior.
- `ModelRunner` now emits streaming token events to both existing token hook and new streaming output consumer.
- Full response callback is emitted on assistant responses, with streamed full replay gated by a config boolean.
- No new output APIs were added to `SessionManager`; input adapter boundaries remain unchanged.
- New unit tests validate replay default and suppression behavior.

## Risk Assessment

- Full response callback currently emits per assistant iteration, including tool-loop intermediate assistant turns.
- Callback exceptions still fail fast through runtime error path; this is consistent but may require consumer hardening.
- `ModelRunner` path-level tests are still limited; behavior confidence relies partly on unit + compile validation.

## Recommendations

- Add `ModelRunner` focused tests using a seam/stub for model transport in a future pass.
- If consumers need only terminal responses, add a dedicated terminal-only callback in a separate scoped change.
- Document tool-loop full-response emission semantics in troubleshooting guidance.

## Follow-ups

- Revisit whether full-response callback should distinguish interim vs terminal assistant turns.
- Add callback ordering assertions once a model transport test seam exists.

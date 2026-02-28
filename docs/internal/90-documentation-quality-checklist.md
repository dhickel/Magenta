# Documentation Quality Checklist

Use this checklist for each material runtime change.

## Implementation accuracy

- [ ] Every documented class/method exists in current code.
- [ ] Behavior statements match observed implementation, including defaults and fallbacks.
- [ ] Known constraints are listed where behavior is intentionally limited.

## Contract completeness

- [ ] Lifecycle semantics (`start`/`resume`/`fork`/turn execution) are documented.
- [ ] Callback semantics (`onMessageAppendedHook`, `onTokenStreamHook`, `onStreamingResponseConsumer`, `onFullResponseConsumer`, `toolBridge`, `onErrorHook`) are documented with current behavior.
- [ ] Compaction and model mode-selection behavior are documented.

## Example quality

- [ ] Examples compile conceptually against current API surface.
- [ ] Examples reflect realistic integration (terminal/UI/tool policy) patterns.
- [ ] Examples do not promise unimplemented services.

## Cross-link integrity

- [ ] `docs/internal/00-index.md` links all canonical docs.
- [ ] Primary guide references deep-dive and operations docs.
- [ ] AGENTS/runtime terminology aligns with internal docs.

## Mismatch disclosure

- [ ] Any code-doc divergence is explicitly called out.
- [ ] If divergence is deferred, a tracking artifact is written to `.internal-dev`.
- [ ] Changelog entry added in `.internal-dev/changelogs/`.

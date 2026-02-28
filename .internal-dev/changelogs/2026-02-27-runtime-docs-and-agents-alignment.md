# Date
2026-02-27

# Change Summary
Added internal developer documentation for current runtime architecture, API surface, lifecycle, callback model, compaction, and model execution flow.
Updated top-level `AGENTS.md` architecture/runtime sections to match the implemented codebase and current runtime contracts.

# Files
- docs/internal/00-index.md
- docs/internal/10-runtime-architecture.md
- AGENTS.md

# Behavioral Impact
No runtime behavior change.
Documentation now reflects current runtime reality (`SessionManager`, `ContextManager`, `ModelRunner`, `OllamaClient`, and single-record `RuntimeConfig`).

# Risks
If runtime implementation changes again without doc updates, docs may drift.

# Follow-up Items
1. Keep `docs/internal/10-runtime-architecture.md` updated on runtime API changes.
2. Add test-focused internal doc page once test suite grows for session/model/tool callbacks.

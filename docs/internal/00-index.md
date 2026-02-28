# Internal Docs Index

Canonical runtime documentation for engineers working in `src/main/java/io/mindspice/magenta/systems`.

## Start here

- `01-runtime-developer-guide.md`: primary end-to-end runtime guide.

## Architecture deep dives

- `10-runtime-architecture.md`: system composition, contracts, and runtime invariants.
- `11-config-architecture.md`: config load/validation contract and failure semantics.
- `12-session-architecture.md`: lifecycle semantics and session integration boundaries.
- `13-context-compaction-architecture.md`: context mutation/compaction behavior and fallback rules.
- `14-model-ollama-architecture.md`: model turn loop and Ollama transport behavior.
- `15-callback-contract-architecture.md`: callback lifecycle, dispatch semantics, and error emission behavior.

## Implementation walkthroughs and operations

- `20-integration-patterns.md`: implementation-grade integration examples.
- `21-sequence-walkthroughs.md`: startup and turn execution flows with diagrams.
- `30-runtime-troubleshooting.md`: diagnosis and correction guidance.

## Maintenance policy

- `90-documentation-quality-checklist.md`: required checklist for runtime documentation updates.

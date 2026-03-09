# Internal Docs Index

Canonical runtime documentation for engineers working in `src/main/java/io/mindspice/magenta`.

## Start here

- `01-runtime-developer-guide.md`: primary end-to-end runtime guide.

## Architecture deep dives

- `10-runtime-architecture.md`: system composition, contracts, and runtime invariants.
- `11-config-architecture.md`: config load/validation contract and failure semantics.
- `12-session-architecture.md`: lifecycle semantics and session integration boundaries.
- `13-context-compaction-architecture.md`: context mutation/compaction behavior and fallback rules.
- `14-model-ollama-architecture.md`: model turn loop and Ollama transport behavior.
- `15-callback-contract-architecture.md`: session/router contract semantics, output event model, and error emission behavior.
- `16-public-api-contract.md`: supported runtime API surface and stability policy.
- `17-tools-security-architecture.md`: built-in tool surface, descriptor-driven security integration, and policy semantics.

## Implementation walkthroughs and operations

- `20-integration-patterns.md`: implementation-grade integration examples.
- `21-sequence-walkthroughs.md`: startup and turn execution flows with diagrams.
- `22-terminal-ui-core-architecture.md`: JLine terminal UI package contract, slash command ADT, and prompt/approval flow.
- `30-runtime-troubleshooting.md`: diagnosis and correction guidance.

## Maintenance policy

- `90-documentation-quality-checklist.md`: required checklist for runtime documentation updates.

## External usage docs

- `../quickstart-chat-loop.md`: detailed single-session chat quick start via `Magenta`.

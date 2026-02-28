# Date
2026-02-27

# Change Summary
Refactored session/context/model runtime into a lean core with `SessionManager`, `ContextManager`, `ModelRunner`, and `OllamaClient`.
Removed drop-in behavior and legacy provider/adapter split around model execution.
Replaced per-turn external tool callback with data-driven per-session `SessionConfig` callbacks and a single `toolBridge` hook.
Moved compaction strategies to `session/compaction` subpackage and kept message ADT defined in the sealed `SessionMessage` interface.

# Files
- src/main/java/io/mindspice/magenta/systems/Magenta.java
- src/main/java/io/mindspice/magenta/Main.java
- src/main/java/io/mindspice/magenta/systems/model/ModelRunner.java
- src/main/java/io/mindspice/magenta/systems/model/OllamaClient.java
- src/main/java/io/mindspice/magenta/systems/session/SessionManager.java
- src/main/java/io/mindspice/magenta/systems/session/ContextManager.java
- src/main/java/io/mindspice/magenta/systems/session/Session.java
- src/main/java/io/mindspice/magenta/systems/session/Context.java
- src/main/java/io/mindspice/magenta/systems/session/SessionConfig.java
- src/main/java/io/mindspice/magenta/systems/session/ToolRequest.java
- src/main/java/io/mindspice/magenta/systems/session/ToolResult.java
- src/main/java/io/mindspice/magenta/systems/session/SessionMessage.java
- src/main/java/io/mindspice/magenta/systems/session/SessionTokenEstimator.java
- src/main/java/io/mindspice/magenta/systems/session/compaction/CompactionStrategy.java
- src/main/java/io/mindspice/magenta/systems/session/compaction/RollingWindowCompactionStrategy.java
- src/main/java/io/mindspice/magenta/systems/session/compaction/SummarizeCompactionStrategy.java
- removed: `systems/agent/*`, `systems/model/ModelGateway` and related adapter/protocol/result files, `systems/session/SessionProvider` and old in-package compaction files

# Behavioral Impact
Session lifecycle is now explicitly `start`, `resume`, and `fork` only.
Session behavior is now callback-driven via `SessionConfig` and supports blocking-only mode, token streaming callbacks, message-stored callbacks, and tool bridge callbacks.
Model turn orchestration is centralized in `ModelRunner`; `OllamaClient` only performs transport and payload conversion.

# Risks
`resume` remains in-memory for now; no durable persistence backing has been added in this slice.
Tool bridge default behavior is no-op/not-handled unless configured in `SessionConfig`.

# Follow-up Items
1. Add persistence implementation behind `ContextManager.loadContext/storeContext` and `SessionManager.resume` lookup strategy.
2. Add unit tests for `ModelRunner` tool-loop callback behavior and `SessionConfig` default/override behavior.
3. Add optional alias index in `SessionManager` if alias-based lookup is needed later.

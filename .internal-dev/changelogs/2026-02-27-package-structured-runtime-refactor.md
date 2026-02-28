# Date
2026-02-27

# Change Summary
Refactored runtime code to remove nested classes and organize by behavior packages: `config`, `agent`, `session`, and `model`.
Split message ADT, compaction strategies, model adapters/results, and config document types into top-level files.
Updated Magenta wiring and Main imports to use new package structure.

# Files
- src/main/java/io/mindspice/magenta/systems/Magenta.java
- src/main/java/io/mindspice/magenta/Main.java
- src/main/java/io/mindspice/magenta/systems/config/RuntimeConfig.java
- src/main/java/io/mindspice/magenta/systems/config/RuntimeModelConfig.java
- src/main/java/io/mindspice/magenta/systems/config/RuntimeAgentConfig.java
- src/main/java/io/mindspice/magenta/systems/config/RootConfigDocument.java
- src/main/java/io/mindspice/magenta/systems/config/InstanceConfigDocument.java
- src/main/java/io/mindspice/magenta/systems/config/IncludeSetDocument.java
- src/main/java/io/mindspice/magenta/systems/agent/AgentProvider.java
- src/main/java/io/mindspice/magenta/systems/agent/AgentRuntime.java
- src/main/java/io/mindspice/magenta/systems/session/*
- src/main/java/io/mindspice/magenta/systems/model/*
- removed old root-level runtime files under `src/main/java/io/mindspice/magenta/systems/`

# Behavioral Impact
No intended runtime behavior change; this is a structural refactor for clearer boundaries and maintainability.
Session lifecycle, model turn policy, and compaction behavior remain the same.

# Risks
Higher file count increases navigation overhead, but boundaries are now explicit and package-scoped by behavior.

# Follow-up Items
1. Add package-level `README` docs under `systems/{config,agent,session,model}` if desired.
2. Add focused unit tests for newly split strategy/util classes.

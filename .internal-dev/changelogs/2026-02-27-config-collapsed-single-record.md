# Date
2026-02-27

# Change Summary
Collapsed runtime config structure into a single `RuntimeConfig` record with nested records/classes.
Removed separate config classes for agent/model/root/include/instance documents.
Kept fail-fast parse behavior with a simple error message that includes file, line/column, and parser message.

# Files
- src/main/java/io/mindspice/magenta/systems/config/RuntimeConfig.java
- removed:
  - src/main/java/io/mindspice/magenta/systems/config/RuntimeAgentConfig.java
  - src/main/java/io/mindspice/magenta/systems/config/RuntimeModelConfig.java
  - src/main/java/io/mindspice/magenta/systems/config/RootConfigDocument.java
  - src/main/java/io/mindspice/magenta/systems/config/InstanceConfigDocument.java
  - src/main/java/io/mindspice/magenta/systems/config/IncludeSetDocument.java
- updated references:
  - src/main/java/io/mindspice/magenta/systems/Magenta.java
  - src/main/java/io/mindspice/magenta/systems/session/Session.java
  - src/main/java/io/mindspice/magenta/systems/session/SessionManager.java
  - src/main/java/io/mindspice/magenta/systems/session/ContextManager.java
  - src/main/java/io/mindspice/magenta/systems/model/ModelRunner.java
  - src/main/java/io/mindspice/magenta/systems/model/OllamaClient.java

# Behavioral Impact
No feature change; configuration loading/validation behavior remains equivalent.
Parse failures now consistently show location details in a single fail-fast error.

# Risks
Low risk; type references changed to nested config types.

# Follow-up Items
1. If desired, collapse additional import verbosity by using explicit aliases/wrappers in high-use files.

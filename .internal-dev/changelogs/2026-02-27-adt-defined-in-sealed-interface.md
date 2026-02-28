# Date
2026-02-27

# Change Summary
Updated the session message ADT so all variants are defined directly inside the sealed `SessionMessage` interface.
Removed standalone ADT variant files and updated all call sites to use `SessionMessage.*` variants and `SessionMessage.ToolCall`.

# Files
- src/main/java/io/mindspice/magenta/systems/session/SessionMessage.java
- src/main/java/io/mindspice/magenta/systems/session/SessionProvider.java
- src/main/java/io/mindspice/magenta/systems/session/SessionTokenEstimator.java
- src/main/java/io/mindspice/magenta/systems/session/RollingWindowCompactionStrategy.java
- src/main/java/io/mindspice/magenta/systems/session/SummarizeCompactionStrategy.java
- src/main/java/io/mindspice/magenta/systems/model/ModelGateway.java
- src/main/java/io/mindspice/magenta/systems/model/TurnResult.java
- src/main/java/io/mindspice/magenta/systems/Magenta.java
- removed: SystemSessionMessage.java, UserSessionMessage.java, AssistantSessionMessage.java, ToolSessionMessage.java, SummarySessionMessage.java, ToolCall.java

# Behavioral Impact
No behavioral runtime changes intended; this is an ADT structure/layout change.

# Risks
Low risk; compile-time sealed ADT references enforce correctness.

# Follow-up Items
1. Keep future ADT expansions in `SessionMessage` to maintain consistency with this rule.

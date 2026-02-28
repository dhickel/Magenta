# Summary
Duplicate IDs in include-resolved model/agent/prompt sets are silently overwritten by `Map.put(...)` instead of being rejected.

# Scope
- Config graph validation integrity
- Startup safety guarantees

# Reproduction
1. Run `mvn test -q -Dtest=RuntimeConfigIntegrationTest#duplicateModelIdsAreSilentlyOverwritten`.
2. Observe load succeeds with one model entry where later file wins.

# Expected
Duplicate IDs should be rejected with a fail-fast validation error.

# Actual
Loader accepts duplicates and silently keeps the last loaded value.

# Evidence
- `src/main/java/io/mindspice/magenta/systems/config/RuntimeConfig.java:106-127`
- `src/main/java/io/mindspice/magenta/systems/config/RuntimeConfig.java:138-148`
- `src/test/java/io/mindspice/magenta/systems/config/RuntimeConfigIntegrationTest.java:89-149`

# Impact
Config collisions can mask misconfiguration and make runtime behavior dependent on file ordering.

# Status
Open

# Next Action
Track source file/line during include load and fail immediately when a duplicate ID is detected.

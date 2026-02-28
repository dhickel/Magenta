# Summary
`RollingWindowCompactionStrategy` undercounts assistant messages with tool calls because it only counts `message.content()` and ignores tool-call name/arguments.

# Scope
- Context compaction correctness
- Token threshold enforcement

# Reproduction
1. Create context with one assistant tool call containing large JSON arguments.
2. Run compaction with `rolling_window` and low threshold.
3. Re-estimate compacted tokens via `SessionTokenEstimator.estimate(...)`.
4. Observe compacted output still exceeds target threshold.

# Expected
Compacted context should respect threshold using a consistent token accounting method.

# Actual
Compaction may return contexts above threshold.

# Evidence
- `src/main/java/io/mindspice/magenta/systems/session/compaction/RollingWindowCompactionStrategy.java:29-35`
- `src/main/java/io/mindspice/magenta/systems/session/SessionTokenEstimator.java:12-15`
- `src/test/java/io/mindspice/magenta/systems/session/ContextManagerCompactionIntegrationTest.java:89-114`

# Impact
Compaction can fail to reduce context sufficiently, increasing risk of model context overruns in production turns.

# Status
Open

# Next Action
Use a message-level estimator in rolling-window selection (`SessionTokenEstimator` with assistant tool-call payload accounting) instead of content-only counting.

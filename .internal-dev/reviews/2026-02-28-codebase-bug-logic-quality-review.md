# Scope
Review of current `Magenta2` runtime code for bugs, logic correctness, test quality/coverage depth, and style consistency. Work included expanding/refactoring the test suite toward integration-level interactions across config loading, routing/session lifecycle, and compaction behavior.

# Findings
1. **High: Default startup config is currently incompatible with strict parser contract.**
- `RuntimeConfig` enforces unknown-key failure but `RootDocument/InstanceConfig` do not model current keys in `configs/magenta.yaml`.
- This makes `RuntimeConfig.loadDefault()` fail, which blocks `Main` default startup path.
- Evidence:
  - `src/main/java/io/mindspice/magenta/systems/config/RuntimeConfig.java:34-37`
  - `src/main/java/io/mindspice/magenta/systems/config/RuntimeConfig.java:287-305`
  - `configs/magenta.yaml:2-10`
  - `src/test/java/io/mindspice/magenta/systems/config/RuntimeConfigIntegrationTest.java:81-86`

2. **High: Duplicate config IDs are silently overwritten.**
- Include loading uses `Map.put(...)` without duplicate detection for models/agents/prompts.
- Violates fail-fast validation expectations and can produce ordering-dependent runtime behavior.
- Evidence:
  - `src/main/java/io/mindspice/magenta/systems/config/RuntimeConfig.java:106-127`
  - `src/main/java/io/mindspice/magenta/systems/config/RuntimeConfig.java:138-148`
  - `src/test/java/io/mindspice/magenta/systems/config/RuntimeConfigIntegrationTest.java:89-149`

3. **Medium: Session publish fanout aborts on stale route IDs.**
- `publishToSessions` assumes route entry session IDs are always live.
- If a routed session is closed, publish throws and terminates delivery for remaining sessions.
- Evidence:
  - `src/main/java/io/mindspice/magenta/systems/Magenta.java:117-125`
  - `src/main/java/io/mindspice/magenta/systems/session/SessionManager.java:66-70`
  - `src/test/java/io/mindspice/magenta/systems/MagentaRoutingIntegrationTest.java:13-22`

4. **Medium: Rolling-window compaction undercounts assistant tool payloads.**
- Rolling-window token accounting ignores `ToolCall` args while estimator includes them.
- Compaction can return context above target threshold.
- Evidence:
  - `src/main/java/io/mindspice/magenta/systems/session/compaction/RollingWindowCompactionStrategy.java:29-35`
  - `src/main/java/io/mindspice/magenta/systems/session/SessionTokenEstimator.java:12-15`
  - `src/test/java/io/mindspice/magenta/systems/session/ContextManagerCompactionIntegrationTest.java:89-114`

5. **Quality/Test Architecture: baseline suite was heavily callback-level and shallow.**
- Prior suite had one file (`SessionConfigTest`) focused on callback emission toggles.
- Added integration coverage now exercises multi-system seams:
  - session manager + context manager + route policies (`SessionManagerIntegrationTest`)
  - context manager + compaction strategies + token estimator (`ContextManagerCompactionIntegrationTest`)
  - runtime config include/validation behavior (`RuntimeConfigIntegrationTest`)
  - runtime routing lifecycle in `Magenta` (`MagentaRoutingIntegrationTest`)

# Risk Assessment
- **Current functional risk: High** due to startup config incompatibility and silent duplicate-ID acceptance.
- **Operational risk: Medium** for fanout routing and compaction threshold enforcement under tool-heavy turns.
- **Maintainability risk: Medium-Low** overall structure is lean and cohesive, but test depth had previously lagged runtime behavior and contract expectations.

# Recommendations
1. Align runtime config schema with the active `configs/magenta.yaml` contract or split strict schema by versioned config type.
2. Implement duplicate-ID rejection with source-file diagnostics during include loading.
3. Harden `publishToSessions` against stale IDs by pruning invalid routes and continuing fanout.
4. Unify compaction accounting with estimator logic that includes assistant tool-call payload size.
5. Continue prioritizing integration tests around lifecycle transitions, not only callback hook smoke tests.

# Follow-ups
- Bug reports created:
  - `.internal-dev/bugs/runtime-config-default-parse-failure/report.md`
  - `.internal-dev/bugs/runtime-config-duplicate-id-overwrite/report.md`
  - `.internal-dev/bugs/magenta-publish-stale-route-crash/report.md`
  - `.internal-dev/bugs/rolling-window-toolcall-token-undercount/report.md`
- New tests added:
  - `src/test/java/io/mindspice/magenta/systems/config/RuntimeConfigIntegrationTest.java`
  - `src/test/java/io/mindspice/magenta/systems/MagentaRoutingIntegrationTest.java`
  - `src/test/java/io/mindspice/magenta/systems/session/SessionManagerIntegrationTest.java`
  - `src/test/java/io/mindspice/magenta/systems/session/ContextManagerCompactionIntegrationTest.java`
  - `src/test/java/io/mindspice/magenta/support/TestRuntimeConfigs.java`
- Validation run: `mvn test -q` (passes with expanded suite).

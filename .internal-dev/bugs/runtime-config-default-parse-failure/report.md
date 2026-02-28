# Summary
`RuntimeConfig.loadDefault()` fails against the repository's shipped `configs/magenta.yaml` because strict unknown-key rejection is enabled while `RootDocument/InstanceConfig` omit multiple active keys.

# Scope
- Runtime config loading at startup
- `Main.main()` default boot path

# Reproduction
1. Run `mvn test -q -Dtest=RuntimeConfigIntegrationTest#loadDefaultRejectsCurrentRepositoryConfigShape`.
2. Observe thrown `IllegalStateException` with parse error details.

# Expected
Default config loading should succeed for the repository's own `configs/magenta.yaml`.

# Actual
Config parsing fails due to unknown fields (for example `workspaceRoot`) in `instance`.

# Evidence
- `src/main/java/io/mindspice/magenta/systems/config/RuntimeConfig.java:34-37`
- `src/main/java/io/mindspice/magenta/systems/config/RuntimeConfig.java:287-305`
- `configs/magenta.yaml:2-10`
- `src/test/java/io/mindspice/magenta/systems/config/RuntimeConfigIntegrationTest.java:81-86`

# Impact
`Main` and any default-config startup path are non-functional until config schema and sample config are aligned.

# Status
Open

# Next Action
Align `RootDocument`/`InstanceConfig` with current YAML schema or narrow `FAIL_ON_UNKNOWN_PROPERTIES` scope with explicit contract/versioning.

# Context
Magenta2 needed a direct port of the legacy config shape while removing validator-class scaffolding and argument parsing.

# Goal
Introduce a single `Config` class that self-loads with strict fail-fast parsing and line-numbered parse failures, then store the loaded config in `Magenta`.

# In Scope
- Add a new `io.mindspice.magenta.config.Config` class with legacy-compatible option structure.
- Implement `Config.loadDefault()` and `Config.load(Path)` static loading.
- Fail fast on unknown properties and parse errors with file/line/column context.
- Wire startup so `Magenta` owns the loaded config instance.

# Out of Scope
- CLI argument parsing and config-path override flags.
- Multi-file `configs/` graph loading/validation.
- Separate validator classes or manager classes.

# Implementation Steps
1. Port legacy config schema into a single class with nested records/classes.
2. Embed strict loader logic in `Config` with Jackson YAML mapper configured for unknown-property failure.
3. Convert parse exceptions into line-aware fail-fast runtime errors.
4. Update `Main` and `Magenta` to load/store config.
5. Compile with Maven to verify.

# Validation
- Run `mvn -q test` to ensure project compiles and tests (if present) pass.

# Exit Criteria
- One config class exists and self-loads.
- No validator-class dependency is required for startup.
- `Magenta` contains the loaded config instance.
- Build/test command succeeds.

# Date
2026-02-27

# Change Summary
Ported legacy config shape into Magenta2 as a single self-loading config class with fail-fast parse behavior including file/line/column context. Wired startup so `Magenta` stores the loaded config instance.

# Files
- `src/main/java/io/mindspice/magenta/config/Config.java`
- `src/main/java/io/mindspice/magenta/Main.java`
- `src/main/java/io/mindspice/magenta/Magenta.java`
- `.internal-dev/plans/legacy-config-port-single-class/phase-01-single-config-loader.md`

# Behavioral Impact
- Startup now attempts to load `config.json` via `Config.loadDefault()`.
- Unknown config keys fail fast during deserialization.
- Parse failures include line and column where available.
- Runtime `Magenta` object now owns a `Config` instance.

# Risks
- Existing `configs/magenta.yaml` layout is not consumed by this loader.
- Missing `config.json` now fails startup immediately.
- Legacy `task_templates` typing is currently generic (`Map<String, Object>`).

# Follow-up Items
- Decide whether to keep legacy single-file config or reintroduce the multi-file `configs/` graph in a separate scoped change.

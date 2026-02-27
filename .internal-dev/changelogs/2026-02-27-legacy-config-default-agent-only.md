# Date
2026-02-27

# Change Summary
Ported the legacy root config into Magenta2 as `config.json`, simplified to a single default agent stack.

# Files
- `config.json`

# Behavioral Impact
- `Config.loadDefault()` now has a concrete root `config.json` to load.
- Config contains one agent (`default`), one model (`default`), one endpoint (`local_ollama`), one security profile (`default`), one color profile (`default`), and one prompt (`default_system`).

# Risks
- The simplified config omits legacy non-default agents and task/delegation templates.
- Tool names in `tool_sets.ALL` are legacy-aligned and may need adjustment once tool IDs are finalized in Magenta2.

# Follow-up Items
- Align `tool_sets.ALL` values with final Magenta2 tool registry IDs when that surface is implemented.

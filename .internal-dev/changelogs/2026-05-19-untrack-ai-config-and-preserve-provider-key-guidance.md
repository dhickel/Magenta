# Date

2026-05-19

# Change Summary

Stopped tracking `config/ai-config.example.json` in git while keeping the file locally available and ignored, and added explicit package guidance that provider API keys must not be removed when cleaning up application-layer auth/CSRF code.

# Files

- `.gitignore` (pre-existing ignore rule verified for `config/ai-config.example.json`)
- `config/ai-config.example.json` (local-only key update; file remains ignored/untracked)
- `src/main/java/io/mindspice/magenta2/ai/config/user/AGENTS.md`

# Behavioral Impact

- The repository no longer tracks `config/ai-config.example.json`, reducing accidental secret commits from local config edits.
- AI config guidance now clearly distinguishes provider credentials (`apiKey`) from HTTP route auth, preventing accidental credential removal during auth cleanup.

# Risks

- Existing clones that still track `config/ai-config.example.json` need this commit pulled to align index state.
- If local config is deleted accidentally, startup still depends on the configured `app.ai.config-path` file existing.

# Follow-up Items

- If needed, add a separate committed template file that contains no secrets and point docs at that template for onboarding.

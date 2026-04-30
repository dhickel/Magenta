# Date

2026-04-29

# Change Summary

Added native `web_search` and `web_fetch` chat tools backed by configured SearXNG search and direct HTTP page fetching.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/web/`
- `src/main/java/io/mindspice/magenta2/ai/config/user/`
- `config/ai-config.example.json`
- `config/prompts/system.md`
- `pom.xml`
- `.internal-dev/notes/2026-04-29-searxng-deployment.md`
- Focused web/config/tool tests under `src/test/java/`

# Behavioral Impact

Agents can search the public web through a configured SearXNG instance and fetch readable text from selected public HTTP(S) pages. Web tools are only registered when `webSearch.enabled` is true. The active local config now enables SearXNG at `http://192.168.1.112:8888`, and the SearXNG host has a matching systemd deployment runbook. Direct fetches reject local/private hosts in production wiring.

# Risks

Search quality depends on the configured SearXNG instance and enabled engines. Page extraction is intentionally simple and may miss content rendered only by JavaScript.

# Follow-up Items

- Consider MCP browser/search integration after the native tool surface proves stable.
- Consider richer page extraction if JavaScript-heavy sources become important.
- Consider moving Magenta to a non-example config filename when the deployment becomes permanent.

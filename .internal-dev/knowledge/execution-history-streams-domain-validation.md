# Topic

Execution, history, and streams domain validation pattern.

# Source References

- `.internal-dev/plans/public-alpha-remediation/03-execution-history-streams/validation-gate.md`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `/tmp/domain03-browser-evidence.json`
- `/tmp/domain03-live-app.log`

# Key Takeaways

- Public run controls should submit saved definitions as queued assignments, not execute inline from public routes.
- High-priority public submissions use priority `9`; browser validation should confirm the persisted assignment type and priority, not only the UI response.
- Direct chat plan execution remains a useful negative probe: both standard and stream endpoints should return 400 with direct execution disabled.
- Schedule and reaction template validation needs both service-level tests and browser-origin HTMX checks because form defaults can diverge from runtime defaults.
- Browser-origin validation should inspect Spring logs and network results together; expected 400 responses from deliberate negative probes should be distinguished from unexpected 500s, stale asset 404s, and broken stream handling.

# Engine Relevance

Future execution-domain changes should preserve submit-to-agent semantics across REST, HTMX, SSE, and JavaScript helper paths. Domain gates should combine focused tests, full `mvn test`, bounded startup, and live app browser checks against an isolated SQLite DB.

# Open Questions

- Whether to add a model-backed saved-plan execution smoke once the validation harness can safely provide deterministic model/runtime dependencies.

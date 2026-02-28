# Summary
`Magenta.publishToSessions(...)` throws if `routePolicies` still contains a closed session ID, aborting publish fanout.

# Scope
- Multi-session routing
- Pub/sub style delivery paths

# Reproduction
1. Start session and register route policy.
2. Close that session through `SessionManager`.
3. Call `publishToSessions(...)` with allowed input.
4. Observe `IllegalStateException: Session not found`.

# Expected
Closed/stale route entries should be skipped or pruned without failing the entire publish operation.

# Actual
Publish throws during first stale route hit.

# Evidence
- `src/main/java/io/mindspice/magenta/systems/Magenta.java:117-125`
- `src/main/java/io/mindspice/magenta/systems/session/SessionManager.java:66-70`
- `src/test/java/io/mindspice/magenta/systems/MagentaRoutingIntegrationTest.java:13-22`

# Impact
One stale route can break broadcast delivery for all sessions.

# Status
Open

# Next Action
On publish, catch missing-session failures, remove stale route IDs, continue delivery for remaining sessions, and emit a structured warning event.
